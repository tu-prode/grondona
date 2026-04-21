package com.grondona.service

import com.grondona.client.MatchClient
import com.grondona.exception.BadRequestException
import com.grondona.model.ExternalMatch
import com.grondona.model.Group
import com.grondona.model.GroupUser
import com.grondona.model.Match
import com.grondona.model.MatchStatus
import com.grondona.model.MatchPrediction
import com.grondona.model.PredictionStatus
import com.grondona.model.Team
import com.grondona.model.Tournament
import com.grondona.model.TournamentStatus
import com.grondona.model.User
import com.grondona.repository.MatchRepository
import com.grondona.repository.MembershipRepository
import com.grondona.repository.MatchPredictionRepository
import com.grondona.repository.TournamentRepository
import com.grondona.service.engine.WorldCupEngine
import io.mockk.*
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.*

class MatchServiceTest {

    @MockK
    private lateinit var matchClient: MatchClient

    @MockK
    private lateinit var matchRepository: MatchRepository

    @MockK
    private lateinit var membershipRepository: MembershipRepository

    @MockK
    private lateinit var tournamentRepository: TournamentRepository

    @MockK
    private lateinit var predictionRepository: MatchPredictionRepository

    @InjectMockKs
    private lateinit var matchService: MatchService

    private val testTournamentId = WorldCupEngine.SYSTEM_TOURNAMENT_ID
    private val testTournament: Tournament = Tournament(
        id = testTournamentId, name = "World Cup", status = TournamentStatus.NOT_STARTED,
    )

    private val testUserId = UUID.randomUUID()
    private val testUser = User(
        id = testUserId,
        fullname = "tester",
        username = "tester",
        email = "test@test.com",
        passwordHash = "pass",
    )

    private val testGroupId = UUID.randomUUID()
    private val testGroup = Group(
        id = testGroupId,
        tournament = testTournament,
        name = "Test Group",
        isPrivate = false,
        maxMembers = 20,
        createdAt = LocalDateTime.now(),
        updatedAt = LocalDateTime.now()
    )

    private val testMember = GroupUser(user = testUser, group = testGroup)

    private fun matchFromDB(
        home: String, away: String, status: MatchStatus = MatchStatus.NOT_STARTED, startedAt: LocalDateTime? = null,
        homeGoals: Int = 0, awayGoals: Int = 0, homeQuota: Float = 1f, drawQuota: Float = 1f, awayQuota: Float = 1f,
    ) = Match(
        id = UUID.randomUUID(),
        homeTeam = Team(tournament = testTournament, name = home, code = home, icon = "test"),
        awayTeam = Team(tournament = testTournament, name = away, code = away, icon = "test"),
        status = status, homeQuota = homeQuota, drawQuota = drawQuota, awayQuota = awayQuota, startedAt = startedAt,
        tournament = testTournament, code = "test", homeGoals = homeGoals, awayGoals = awayGoals,
    )

    private fun matchFromAPI(
        home: String, away: String, homeGoals: Int = 0, awayGoals: Int = 0,
        minutes: Int = 0, half: Int = 0, status: String = "TO START",
        homeQuota: Float = 1f, drawQuota: Float = 1f, awayQuota: Float = 1f,
    ) = ExternalMatch(
        code = "XX", home = home, away = away, homeGoals = homeGoals, awayGoals = awayGoals, status = status,
        minutes = minutes, half = half, homeOdds = homeQuota, drawOdds = drawQuota, awayOdds = awayQuota, startedAt = LocalDateTime.now(),
    )

    private fun predictionFromDB(
        match: Match, user: User = testUser, group: Group = testGroup,
        status: PredictionStatus = PredictionStatus.PENDING, homeGoals: Int = 0, awayGoals: Int = 0
    ) = MatchPrediction(
        user = user, group = group, match = match, homeGoals = homeGoals, awayGoals = awayGoals, status = status,
    )

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
    }

    @Nested
    inner class UpdateMatchesStatusesTests {

        @Test
        fun `updateMatchesStatuses should fail when tournamentId is not supported`() {
            val exception = assertThrows<BadRequestException> {
                matchService.updateMatchesStatuses(UUID.randomUUID())
            }

            assertEquals("Tournament not supported", exception.message)
        }

        @Test
        fun `updateMatchesStatuses doesn't update match quotas`() {
            val externalMatch = matchFromAPI(
                home = "QAT", away = "ECU", homeGoals = 0, awayGoals = 0, minutes = 0, half = 1,
                status = "IN_PLAY", homeQuota = 10f, drawQuota = 10f, awayQuota = 10f,
            )
            every { matchClient.getMatches(testTournamentId) } returns listOf(externalMatch)

            val dbMatch1 = matchFromDB(home = "QAT", away = "ECU")
            val dbMatch2 = matchFromDB(home = "ENG", away = "IRN")
            val dbMatch3 = matchFromDB(home = "SEN", away = "NED")
            val dbMatch4 = matchFromDB(home = "USA", away = "WAL")
            every { matchRepository.findByTournamentId(testTournamentId) } returns listOf(dbMatch1, dbMatch2, dbMatch3, dbMatch4)
            every { matchRepository.saveAll(any<List<Match>>()) } answers { firstArg() }
            every { tournamentRepository.findById(testTournamentId) } returns Optional.of(testTournament.copy(status = TournamentStatus.IN_PROGRESS))

            matchService.updateMatchesStatuses(testTournamentId)

            val slot = slot<List<Match>>()
            verify(exactly = 1) { matchRepository.saveAll(capture(slot)) }
            val savedMatches = slot.captured
            assertEquals(1, savedMatches.size)
            assertEquals("QAT", savedMatches[0].homeTeam.code)
            assertEquals("ECU", savedMatches[0].awayTeam.code)
            assertEquals(0, savedMatches[0].homeGoals)
            assertEquals(0, savedMatches[0].awayGoals)
            assertEquals(MatchStatus.IN_PROGRESS, savedMatches[0].status)
            assertEquals("0' PT", savedMatches[0].substatus)
            assertEquals(1f, savedMatches[0].homeQuota)
            assertEquals(1f, savedMatches[0].drawQuota)
            assertEquals(1f, savedMatches[0].awayQuota)

            verify(exactly = 0) { predictionRepository.findByStatusAndMatchIdIn(any(), any()) }
            verify(exactly = 0) { predictionRepository.saveAll<MatchPrediction>(any()) }
            verify(exactly = 0) { membershipRepository.findByGroupId(any()) }
            verify(exactly = 0) { membershipRepository.saveAll<GroupUser>(any()) }
        }

        @Test
        fun `updateMatchesStatuses updates a match during the first half`() {
            val externalMatch = matchFromAPI(
                home = "QAT", away = "ECU", homeGoals = 1, awayGoals = 0, minutes = 19, half = 1, status = "IN_PLAY"
            )
            every { matchClient.getMatches(testTournamentId) } returns listOf(externalMatch)

            val dbMatch1 = matchFromDB(home = "QAT", away = "ECU", status = MatchStatus.IN_PROGRESS)
            val dbMatch2 = matchFromDB(home = "ENG", away = "IRN")
            val dbMatch3 = matchFromDB(home = "SEN", away = "NED")
            val dbMatch4 = matchFromDB(home = "USA", away = "WAL")
            every { matchRepository.findByTournamentId(testTournamentId) } returns listOf(dbMatch1, dbMatch2, dbMatch3, dbMatch4)
            every { tournamentRepository.findById(testTournamentId) } returns Optional.of(testTournament.copy(status = TournamentStatus.IN_PROGRESS))
            every { matchRepository.saveAll(any<List<Match>>()) } answers { firstArg() }

            matchService.updateMatchesStatuses(testTournamentId)

            val slot = slot<List<Match>>()
            verify(exactly = 1) { matchRepository.saveAll(capture(slot)) }
            val savedMatches = slot.captured
            assertEquals(1, savedMatches.size)
            assertEquals("QAT", savedMatches[0].homeTeam.code)
            assertEquals("ECU", savedMatches[0].awayTeam.code)
            assertEquals(1, savedMatches[0].homeGoals)
            assertEquals(0, savedMatches[0].awayGoals)
            assertEquals(MatchStatus.IN_PROGRESS, savedMatches[0].status)
            assertEquals("19' PT", savedMatches[0].substatus)

            verify(exactly = 0) { predictionRepository.findByStatusAndMatchIdIn(any(), any()) }
            verify(exactly = 0) { predictionRepository.saveAll<MatchPrediction>(any()) }
            verify(exactly = 0) { membershipRepository.findByGroupId(any()) }
            verify(exactly = 0) { membershipRepository.saveAll<GroupUser>(any()) }
        }

        @Test
        fun `updateMatchesStatuses updates a match during the added time of the first half`() {
            val externalMatch = matchFromAPI(
                home = "QAT", away = "ECU", homeGoals = 2, awayGoals = 0, minutes = 49, half = 1, status = "IN_PLAY"
            )
            every { matchClient.getMatches(testTournamentId) } returns listOf(externalMatch)

            val dbMatch1 = matchFromDB(home = "QAT", away = "ECU", status = MatchStatus.IN_PROGRESS)
            val dbMatch2 = matchFromDB(home = "ENG", away = "IRN")
            val dbMatch3 = matchFromDB(home = "SEN", away = "NED")
            val dbMatch4 = matchFromDB(home = "USA", away = "WAL")
            every { matchRepository.findByTournamentId(testTournamentId) } returns listOf(dbMatch1, dbMatch2, dbMatch3, dbMatch4)
            every { tournamentRepository.findById(testTournamentId) } returns Optional.of(testTournament.copy(status = TournamentStatus.IN_PROGRESS))
            every { matchRepository.saveAll(any<List<Match>>()) } answers { firstArg() }

            matchService.updateMatchesStatuses(testTournamentId)

            val slot = slot<List<Match>>()
            verify(exactly = 1) { matchRepository.saveAll(capture(slot)) }
            val savedMatches = slot.captured
            assertEquals(1, savedMatches.size)
            assertEquals("QAT", savedMatches[0].homeTeam.code)
            assertEquals("ECU", savedMatches[0].awayTeam.code)
            assertEquals(2, savedMatches[0].homeGoals)
            assertEquals(0, savedMatches[0].awayGoals)
            assertEquals(MatchStatus.IN_PROGRESS, savedMatches[0].status)
            assertEquals("45+4' PT", savedMatches[0].substatus)

            verify(exactly = 0) { predictionRepository.findByStatusAndMatchIdIn(any(), any()) }
            verify(exactly = 0) { predictionRepository.saveAll<MatchPrediction>(any()) }
            verify(exactly = 0) { membershipRepository.findByGroupId(any()) }
            verify(exactly = 0) { membershipRepository.saveAll<GroupUser>(any()) }
        }

        @Test
        fun `updateMatchesStatuses updates a match during the second half`() {
            val externalMatch = matchFromAPI(
                home = "QAT", away = "ECU", homeGoals = 2, awayGoals = 0, minutes = 72, half = 2, status = "IN_PLAY"
            )
            every { matchClient.getMatches(testTournamentId) } returns listOf(externalMatch)

            val dbMatch1 = matchFromDB(home = "QAT", away = "ECU", status = MatchStatus.IN_PROGRESS)
            val dbMatch2 = matchFromDB(home = "ENG", away = "IRN")
            val dbMatch3 = matchFromDB(home = "SEN", away = "NED")
            val dbMatch4 = matchFromDB(home = "USA", away = "WAL")
            every { matchRepository.findByTournamentId(testTournamentId) } returns listOf(dbMatch1, dbMatch2, dbMatch3, dbMatch4)
            every { tournamentRepository.findById(testTournamentId) } returns Optional.of(testTournament.copy(status = TournamentStatus.IN_PROGRESS))
            every { matchRepository.saveAll(any<List<Match>>()) } answers { firstArg() }

            matchService.updateMatchesStatuses(testTournamentId)

            val slot = slot<List<Match>>()
            verify(exactly = 1) { matchRepository.saveAll(capture(slot)) }
            val savedMatches = slot.captured
            assertEquals(1, savedMatches.size)
            assertEquals("QAT", savedMatches[0].homeTeam.code)
            assertEquals("ECU", savedMatches[0].awayTeam.code)
            assertEquals(2, savedMatches[0].homeGoals)
            assertEquals(0, savedMatches[0].awayGoals)
            assertEquals(MatchStatus.IN_PROGRESS, savedMatches[0].status)
            assertEquals("27' ST", savedMatches[0].substatus)

            verify(exactly = 0) { predictionRepository.findByStatusAndMatchIdIn(any(), any()) }
            verify(exactly = 0) { predictionRepository.saveAll<MatchPrediction>(any()) }
            verify(exactly = 0) { membershipRepository.findByGroupId(any()) }
            verify(exactly = 0) { membershipRepository.saveAll<GroupUser>(any()) }
        }

        @Test
        fun `updateMatchesStatuses updates a match during the added time of the second half`() {
            val externalMatch = matchFromAPI(
                home = "QAT", away = "ECU", homeGoals = 2, awayGoals = 0, minutes = 94, half = 2, status = "IN_PLAY"
            )
            every { matchClient.getMatches(testTournamentId) } returns listOf(externalMatch)

            val dbMatch1 = matchFromDB(home = "QAT", away = "ECU", status = MatchStatus.IN_PROGRESS)
            val dbMatch2 = matchFromDB(home = "ENG", away = "IRN")
            val dbMatch3 = matchFromDB(home = "SEN", away = "NED")
            val dbMatch4 = matchFromDB(home = "USA", away = "WAL")
            every { matchRepository.findByTournamentId(testTournamentId) } returns listOf(dbMatch1, dbMatch2, dbMatch3, dbMatch4)
            every { tournamentRepository.findById(testTournamentId) } returns Optional.of(testTournament.copy(status = TournamentStatus.IN_PROGRESS))
            every { matchRepository.saveAll(any<List<Match>>()) } answers { firstArg() }

            matchService.updateMatchesStatuses(testTournamentId)

            val slot = slot<List<Match>>()
            verify(exactly = 1) { matchRepository.saveAll(capture(slot)) }
            val savedMatches = slot.captured
            assertEquals(1, savedMatches.size)
            assertEquals("QAT", savedMatches[0].homeTeam.code)
            assertEquals("ECU", savedMatches[0].awayTeam.code)
            assertEquals(2, savedMatches[0].homeGoals)
            assertEquals(0, savedMatches[0].awayGoals)
            assertEquals(MatchStatus.IN_PROGRESS, savedMatches[0].status)
            assertEquals("45+4' ST", savedMatches[0].substatus)

            verify(exactly = 0) { predictionRepository.findByStatusAndMatchIdIn(any(), any()) }
            verify(exactly = 0) { predictionRepository.saveAll<MatchPrediction>(any()) }
            verify(exactly = 0) { membershipRepository.findByGroupId(any()) }
            verify(exactly = 0) { membershipRepository.saveAll<GroupUser>(any()) }
        }

        @Test
        fun `updateMatchesStatuses updates a match when it finishes, and also updates the predictions`() {
            val externalMatch = matchFromAPI(
                home = "QAT", away = "ECU", homeGoals = 2, awayGoals = 0, minutes = 95, half = 2, status = "COMPLETED"
            )
            every { matchClient.getMatches(testTournamentId) } returns listOf(externalMatch)

            val dbMatch1 = matchFromDB(home = "QAT", away = "ECU", status = MatchStatus.IN_PROGRESS)
            val dbMatch2 = matchFromDB(home = "ENG", away = "IRN")
            val dbMatch3 = matchFromDB(home = "SEN", away = "NED")
            val dbMatch4 = matchFromDB(home = "USA", away = "WAL")
            every { matchRepository.findByTournamentId(testTournamentId) } returns listOf(dbMatch1, dbMatch2, dbMatch3, dbMatch4)
            every { tournamentRepository.findById(testTournamentId) } returns Optional.of(testTournament.copy(status = TournamentStatus.IN_PROGRESS))
            every { matchRepository.saveAll(any<List<Match>>()) } answers { firstArg() }

            val user1 = testUser.copy(id = UUID.randomUUID())
            val user2 = testUser.copy(id = UUID.randomUUID())
            val dbPrediction1 = predictionFromDB(dbMatch1, user = user1, homeGoals = 2, awayGoals = 0)
            val dbPrediction2 = predictionFromDB(dbMatch1, user = user2, homeGoals = 1, awayGoals = 0)
            every {
                predictionRepository.findByStatusAndMatchIdIn(
                    PredictionStatus.PENDING, listOf(dbMatch1.id!!)
                )
            } returns listOf(dbPrediction1, dbPrediction2)
            every { predictionRepository.saveAll(any<List<MatchPrediction>>()) } answers { firstArg() }

            val member1 = testMember.copy(user = user1)
            val member2 = testMember.copy(user = user2)
            every {
                membershipRepository.findByGroupId(testGroupId)
            } returns listOf(member1, member2)
            every { membershipRepository.saveAll(any<List<GroupUser>>()) } answers { firstArg() }

            matchService.updateMatchesStatuses(testTournamentId)

            val slot1 = slot<List<Match>>()
            verify(exactly = 1) { matchRepository.saveAll(capture(slot1)) }
            val savedMatches = slot1.captured
            assertEquals(1, savedMatches.size)
            assertEquals("QAT", savedMatches[0].homeTeam.code)
            assertEquals("ECU", savedMatches[0].awayTeam.code)
            assertEquals(2, savedMatches[0].homeGoals)
            assertEquals(0, savedMatches[0].awayGoals)
            assertEquals(MatchStatus.FINISHED, savedMatches[0].status)
            assertEquals("FINALIZADO", savedMatches[0].substatus)

            val slot2 = slot<List<MatchPrediction>>()
            verify(exactly = 1) { predictionRepository.saveAll(capture(slot2)) }
            val savedPredictions = slot2.captured
            assertEquals(2, savedPredictions.size)
            assertEquals("QAT", savedPredictions[0].match.homeTeam.code)
            assertEquals("ECU", savedPredictions[0].match.awayTeam.code)
            assertEquals(2, savedPredictions[0].homeGoals)
            assertEquals(0, savedPredictions[0].awayGoals)
            assertEquals(PredictionStatus.CORRECT, savedPredictions[0].status)
            assertEquals("QAT", savedPredictions[1].match.homeTeam.code)
            assertEquals("ECU", savedPredictions[1].match.awayTeam.code)
            assertEquals(1, savedPredictions[1].homeGoals)
            assertEquals(0, savedPredictions[1].awayGoals)
            assertEquals(PredictionStatus.PARTIAL, savedPredictions[1].status)

            val slot3 = slot<List<GroupUser>>()
            verify(exactly = 1) { membershipRepository.saveAll(capture(slot3)) }
            val savedMembers = slot3.captured
            assertEquals(2, savedMembers.size)
            assertEquals(user1.id, savedMembers[0].user.id)
            assertEquals(testGroupId, savedMembers[0].group.id)
            assertEquals(user2.id, savedMembers[1].user.id)
        }

        @Test
        fun `updateMatchesStatuses updates two matches being played at the same time`() {
            val externalMatch1 = matchFromAPI(
                home = "POL", away = "ARG", homeGoals = 2, awayGoals = 0, minutes = 67, half = 2, status = "IN_PLAY"
            )
            val externalMatch2 = matchFromAPI(
                home = "QAT", away = "MEX", homeGoals = 0, awayGoals = 2, minutes = 67, half = 2, status = "IN_PLAY"
            )
            every { matchClient.getMatches(testTournamentId) } returns listOf(externalMatch1, externalMatch2)

            val dbMatch1 = matchFromDB(home = "POL", away = "ARG", homeGoals = 1, awayGoals = 0, status = MatchStatus.IN_PROGRESS)
            val dbMatch2 = matchFromDB(home = "QAT", away = "MEX", homeGoals = 0, awayGoals = 2, status = MatchStatus.IN_PROGRESS)
            val dbMatch3 = matchFromDB(home = "CRO", away = "BEL")
            every { matchRepository.findByTournamentId(testTournamentId) } returns listOf(dbMatch1, dbMatch2, dbMatch3)
            every { tournamentRepository.findById(testTournamentId) } returns Optional.of(testTournament.copy(status = TournamentStatus.IN_PROGRESS))
            every { matchRepository.saveAll(any<List<Match>>()) } answers { firstArg() }

            matchService.updateMatchesStatuses(testTournamentId)

            val slot = slot<List<Match>>()
            verify(exactly = 1) { matchRepository.saveAll(capture(slot)) }
            val savedMatches = slot.captured
            assertEquals(2, savedMatches.size)
            assertEquals("POL", savedMatches[0].homeTeam.code)
            assertEquals("ARG", savedMatches[0].awayTeam.code)
            assertEquals(2, savedMatches[0].homeGoals)
            assertEquals(0, savedMatches[0].awayGoals)
            assertEquals(MatchStatus.IN_PROGRESS, savedMatches[0].status)
            assertEquals("22' ST", savedMatches[0].substatus)
            assertEquals("QAT", savedMatches[1].homeTeam.code)
            assertEquals("MEX", savedMatches[1].awayTeam.code)
            assertEquals(0, savedMatches[1].homeGoals)
            assertEquals(2, savedMatches[1].awayGoals)
            assertEquals(MatchStatus.IN_PROGRESS, savedMatches[1].status)
            assertEquals( "22' ST", savedMatches[1].substatus)

            verify(exactly = 0) { predictionRepository.findByStatusAndMatchIdIn(any(), any()) }
            verify(exactly = 0) { predictionRepository.saveAll<MatchPrediction>(any()) }
            verify(exactly = 0) { membershipRepository.findByGroupId(any()) }
            verify(exactly = 0) { membershipRepository.saveAll<GroupUser>(any()) }
        }

        @Test
        fun `updateMatchesStatuses ignores a match not present in the DB`() {
            val externalMatch = matchFromAPI(
                home = "ITA", away = "CHI", homeGoals = 0, awayGoals = 0, minutes = 0, half = 1, status = "IN_PLAY"
            )
            every { matchClient.getMatches(testTournamentId) } returns listOf(externalMatch)

            val dbMatch1 = matchFromDB(home = "POL", away = "ARG")
            val dbMatch2 = matchFromDB(home = "QAT", away = "MEX")
            val dbMatch3 = matchFromDB(home = "CRO", away = "BEL")
            every { matchRepository.findByTournamentId(testTournamentId) } returns listOf(dbMatch1, dbMatch2, dbMatch3)

            matchService.updateMatchesStatuses(testTournamentId)

            verify(exactly = 0) { matchRepository.saveAll<Match>(any()) }
            verify(exactly = 0) { predictionRepository.findByStatusAndMatchIdIn(any(), any()) }
            verify(exactly = 0) { predictionRepository.saveAll<MatchPrediction>(any()) }
            verify(exactly = 0) { membershipRepository.findByGroupId(any()) }
            verify(exactly = 0) { membershipRepository.saveAll<GroupUser>(any()) }
        }

        @Test
        fun `updateMatchesStatuses starts a tournament when first match starts`() {
            val externalMatch = matchFromAPI(
                home = "QAT", away = "ECU", homeGoals = 0, awayGoals = 0, minutes = 0, half = 1, status = "IN_PLAY"
            )
            every { matchClient.getMatches(testTournamentId) } returns listOf(externalMatch)

            val dbMatch1 = matchFromDB(home = "QAT", away = "ECU", status = MatchStatus.IN_PROGRESS)
            val dbMatch2 = matchFromDB(home = "ENG", away = "IRN")
            every { matchRepository.findByTournamentId(testTournamentId) } returns listOf(dbMatch1, dbMatch2)
            every { matchRepository.saveAll(any<List<Match>>()) } answers { firstArg() }

            every { tournamentRepository.findById(testTournamentId) } returns Optional.of(testTournament)
            every { tournamentRepository.save(any<Tournament>()) } answers { firstArg() }

            matchService.updateMatchesStatuses(testTournamentId)

            val slot = slot<Tournament>()
            verify(exactly = 1) { tournamentRepository.save(capture(slot)) }
            val tournament = slot.captured
            assertEquals(testTournament.id, tournament.id)
            assertEquals(TournamentStatus.IN_PROGRESS, tournament.status)
        }
    }

    @Nested
    inner class CheckCompletedPredictionsTests {

        @Test
        fun `checkCompletedPredictions ignores predictions with status not PENDING`() {
            val predictions = listOf(
                predictionFromDB(match = matchFromDB("ARG", "FRA"), status = PredictionStatus.MISSING),
            )
            val results = matchService.checkCompletedPredictions(predictions)

            assertEquals(0, results.size)
        }

        @Test
        fun `checkCompletedPredictions ignores predictions with match status not FINISHED`() {
            val predictions = listOf(
                predictionFromDB(match = matchFromDB("ARG", "FRA", status = MatchStatus.IN_PROGRESS)),
            )
            val results = matchService.checkCompletedPredictions(predictions)

            assertEquals(0, results.size)
        }
    }

    @Nested
    inner class UpdateMatchesQuotasTests {

        @Test
        fun `updateMatchesQuotas should fail when tournamentId is not supported`() {
            val exception = assertThrows<BadRequestException> {
                matchService.updateMatchesQuotas(UUID.randomUUID())
            }

            assertEquals("Tournament not supported", exception.message)
        }

        @Test
        fun `updateMatchesQuotas updates a match quotas before it starts`() {
            val externalMatch1 = matchFromAPI(
                home = "QAT", away = "ECU", status = "TO_START", homeQuota = 1f, drawQuota = 1.5f, awayQuota = 2f,
            )
            val externalMatch2 = matchFromAPI(
                home = "ENG", away = "IRN", status = "IN_PLAY", homeQuota = 2f, drawQuota = 2.5f, awayQuota = 3f,
            )
            every { matchClient.getMatches(testTournamentId) } returns listOf(externalMatch1, externalMatch2)

            val dbMatch1 = matchFromDB(home = "QAT", away = "ECU", homeQuota = 2f, drawQuota = 3f, awayQuota = 4f)
            val dbMatch2 = matchFromDB(home = "ENG", away = "IRN", homeQuota = 4f, drawQuota = 3f, awayQuota = 2f)
            every {
                matchRepository.findByTournamentIdAndStatus(testTournamentId, MatchStatus.NOT_STARTED)
            } returns listOf(dbMatch1, dbMatch2)
            every { matchRepository.saveAll(any<List<Match>>()) } answers { firstArg() }

            matchService.updateMatchesQuotas(testTournamentId)

            val slot = slot<List<Match>>()
            verify(exactly = 1) { matchRepository.saveAll(capture(slot)) }
            val savedMatches = slot.captured
            assertEquals(2, savedMatches.size)
            assertEquals("QAT", savedMatches[0].homeTeam.code)
            assertEquals("ECU", savedMatches[0].awayTeam.code)
            assertEquals(0, savedMatches[0].homeGoals)
            assertEquals(0, savedMatches[0].awayGoals)
            assertEquals(1f, savedMatches[0].homeQuota)
            assertEquals(1.5f, savedMatches[0].drawQuota)
            assertEquals(2f, savedMatches[0].awayQuota)
            assertEquals(MatchStatus.NOT_STARTED, savedMatches[0].status)
            assertNull(savedMatches[0].substatus)
            assertEquals("ENG", savedMatches[1].homeTeam.code)
            assertEquals("IRN", savedMatches[1].awayTeam.code)
            assertEquals(0, savedMatches[1].homeGoals)
            assertEquals(0, savedMatches[1].awayGoals)
            assertEquals(4f, savedMatches[1].homeQuota)
            assertEquals(3f, savedMatches[1].drawQuota)
            assertEquals(2f, savedMatches[1].awayQuota)
            assertEquals(MatchStatus.NOT_STARTED, savedMatches[1].status)
            assertNull(savedMatches[1].substatus)
        }

        @Test
        fun `updateMatchesQuotas doesn't update a match quotas when it will start in less than 15 minutes`() {
            val externalMatch1 = matchFromAPI(
                home = "QAT", away = "ECU", status = "TO_START", homeQuota = 1f, drawQuota = 2.5f, awayQuota = 2f,
            )
            val externalMatch2 = matchFromAPI(
                home = "ENG", away = "IRN", status = "TO_START", homeQuota = 2f, drawQuota = 2.5f, awayQuota = 3f,
            )
            every { matchClient.getMatches(testTournamentId) } returns listOf(externalMatch1, externalMatch2)

            val dbMatch1 = matchFromDB(home = "QAT", away = "ECU", homeQuota = 2f, drawQuota = 3f, awayQuota = 4f,
                startedAt = LocalDateTime.now().plus(10, ChronoUnit.MINUTES))
            val dbMatch2 = matchFromDB(home = "ENG", away = "IRN", homeQuota = 4f, drawQuota = 3f, awayQuota = 2f,
                startedAt = LocalDateTime.now().plus(30, ChronoUnit.MINUTES))
            every {
                matchRepository.findByTournamentIdAndStatus(testTournamentId, MatchStatus.NOT_STARTED)
            } returns listOf(dbMatch1, dbMatch2)
            every { matchRepository.saveAll(any<List<Match>>()) } answers { firstArg() }

            matchService.updateMatchesQuotas(testTournamentId)

            val slot = slot<List<Match>>()
            verify(exactly = 1) { matchRepository.saveAll(capture(slot)) }
            val savedMatches = slot.captured
            assertEquals(1, savedMatches.size)
            assertEquals("ENG", savedMatches[0].homeTeam.code)
            assertEquals("IRN", savedMatches[0].awayTeam.code)
            assertEquals(0, savedMatches[0].homeGoals)
            assertEquals(0, savedMatches[0].awayGoals)
            assertEquals(2f, savedMatches[0].homeQuota)
            assertEquals(2.5f, savedMatches[0].drawQuota)
            assertEquals(3f, savedMatches[0].awayQuota)
            assertEquals(MatchStatus.NOT_STARTED, savedMatches[0].status)
            assertNull(savedMatches[0].substatus)
        }

        @Test
        fun `updateMatchesQuotas doesn't update a match IN_PROGRESS`() {
            val externalMatch1 = matchFromAPI(
                home = "QAT", away = "ECU", status = "TO_START", homeQuota = 2f, drawQuota = 2.5f, awayQuota = 3f,
            )
            val externalMatch2 = matchFromAPI(
                home = "ENG", away = "IRN", status = "IN_PLAY", homeQuota = 2f, drawQuota = 2.5f, awayQuota = 3f,
            )
            every { matchClient.getMatches(testTournamentId) } returns listOf(externalMatch1, externalMatch2)

            val dbMatch1 = matchFromDB(home = "QAT", away = "ECU", homeQuota = 2f, drawQuota = 3f, awayQuota = 4f, status = MatchStatus.IN_PROGRESS)
            val dbMatch2 = matchFromDB(home = "ENG", away = "IRN", homeQuota = 4f, drawQuota = 3f, awayQuota = 2f, status = MatchStatus.IN_PROGRESS)
            every {
                matchRepository.findByTournamentIdAndStatus(testTournamentId, MatchStatus.NOT_STARTED)
            } returns listOf(dbMatch1, dbMatch2)

            verify(exactly = 0) { matchRepository.saveAll<Match>(any()) }
        }

        @Test
        fun `updateMatchesQuotas doesn't update a match FINISHED`() {
            val externalMatch1 = matchFromAPI(
                home = "QAT", away = "ECU", status = "IN_PLAY", homeQuota = 2f, drawQuota = 2.5f, awayQuota = 3f,
            )
            val externalMatch2 = matchFromAPI(
                home = "ENG", away = "IRN", status = "COMPLETED", homeQuota = 2f, drawQuota = 2.5f, awayQuota = 3f,
            )
            every { matchClient.getMatches(testTournamentId) } returns listOf(externalMatch1, externalMatch2)

            val dbMatch1 = matchFromDB(home = "QAT", away = "ECU", homeQuota = 2f, drawQuota = 3f, awayQuota = 4f, status = MatchStatus.FINISHED)
            val dbMatch2 = matchFromDB(home = "ENG", away = "IRN", homeQuota = 4f, drawQuota = 3f, awayQuota = 2f, status = MatchStatus.FINISHED)
            every {
                matchRepository.findByTournamentIdAndStatus(testTournamentId, MatchStatus.NOT_STARTED)
            } returns listOf(dbMatch1, dbMatch2)

            verify(exactly = 0) { matchRepository.saveAll<Match>(any()) }
        }
    }
}
