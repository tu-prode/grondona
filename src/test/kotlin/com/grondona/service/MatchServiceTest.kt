package com.grondona.service

import com.grondona.client.MatchClient
import com.grondona.client.OddsClient
import com.grondona.exception.BadRequestException
import com.grondona.exception.ExternalServiceException
import com.grondona.model.ExternalMatch
import com.grondona.model.ExternalOdds
import com.grondona.model.Group
import com.grondona.model.Match
import com.grondona.model.MatchGroup
import com.grondona.model.MatchStatus
import com.grondona.model.MatchPrediction
import com.grondona.model.MatchStage
import com.grondona.model.MatchSubstatus
import com.grondona.model.PredictionStatus
import com.grondona.model.Team
import com.grondona.model.Tournament
import com.grondona.model.TournamentStatus
import com.grondona.model.User
import com.grondona.repository.MatchRepository
import com.grondona.repository.MembershipRepository
import com.grondona.repository.MatchPredictionRepository
import com.grondona.repository.TeamRepository
import com.grondona.repository.TournamentRepository
import com.grondona.service.engine.TournamentEngine
import com.grondona.service.engine.WorldCupEngine
import com.grondona.utils.oddsToQuota
import io.mockk.*
import io.mockk.impl.annotations.MockK
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDateTime
import java.time.ZonedDateTime
import java.util.*

class MatchServiceTest {

    @MockK
    private lateinit var oddsClient: OddsClient

    @MockK
    private lateinit var matchClient: MatchClient

    @MockK
    private lateinit var teamRepository: TeamRepository

    @MockK
    private lateinit var matchRepository: MatchRepository

    @MockK
    private lateinit var membershipRepository: MembershipRepository

    @MockK
    private lateinit var tournamentRepository: TournamentRepository

    @MockK
    private lateinit var matchPredictionRepository: MatchPredictionRepository

    @MockK
    private lateinit var engine: TournamentEngine

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

    private fun teamFromDB(code: String) = Team(
        id = UUID.randomUUID(), tournament = testTournament,
        name = "Team $code", code = code, englishKey = "$code-en"
    )

    private fun matchFromDB(code: String, home: String, away: String) = Match(
        id = UUID.randomUUID(), code = code, tournament = testTournament, homeGoals = 0, awayGoals = 0,
        homeTeam = Team(tournament = testTournament, name = home, code = home, icon = "test", englishKey = "$home-en"),
        awayTeam = Team(tournament = testTournament, name = away, code = away, icon = "test", englishKey = "$away-en"),
        status = MatchStatus.NOT_STARTED, homeQuota = 1f, drawQuota = 1f, awayQuota = 1f,
        stage = MatchStage.GROUP_STAGE, group = MatchGroup.GROUP_A, startedAt = ZonedDateTime.now().plusDays(1),
    )

    private fun externalMatch(home: String, away: String) = ExternalMatch(
        home = home, away = away, homeGoals = 1, awayGoals = 1, status = MatchStatus.IN_PROGRESS,
        substatus = "30' PT", startedAt = ZonedDateTime.now(), stage = MatchStage.GROUP_STAGE, group = MatchGroup.GROUP_A,
    )

    private fun externalOdds(home: String, away: String) = ExternalOdds(
        homeKey = "$home-en", awayKey = "$away-en", startedAt = ZonedDateTime.now(), homeOdds = 1f, drawOdds = 1f, awayOdds = 1f,
    )

    private fun predictionFromDB(
        match: Match, user: User = testUser, group: Group = testGroup,
        status: PredictionStatus = PredictionStatus.PENDING, homeGoals: Int = 0, awayGoals: Int = 0
    ) = MatchPrediction(
        user = user, group = group, match = match, homeGoals = homeGoals, awayGoals = awayGoals, status = status,
    )

    private val tournamentTeams = (1..10).flatMap { listOf(teamFromDB("H$it"), teamFromDB("A$it")) }

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        every { engine.tournamentId } returns testTournamentId

        matchService = spyk(
            MatchService(
                oddsClient,
                matchClient,
                teamRepository,
                matchRepository,
                membershipRepository,
                tournamentRepository,
                matchPredictionRepository,
                listOf(engine),
                prepareNewMatches = true,
            )
        )
    }

    @Nested
    inner class ExtractMatchesToUpdateTests {

        fun match(id: UUID = UUID.randomUUID(), status: MatchStatus) =
            matchFromDB(code = "M1", home = "H1", away = "A1").copy(id = id, status = status)

        @Test
        fun `extractMatchesToUpdateStatus does not return a match that is not connected by the ID`() {
            val matchesFromDB = listOf(match(status = MatchStatus.FINISHED))
            val matchesFromAPI = listOf(match(status = MatchStatus.FINISHED))

            val matchesToUpdate = MatchService.extractMatchesToUpdateStatus(matchesFromDB, matchesFromAPI)
            assertTrue { matchesToUpdate.isEmpty() }
        }

        @Test
        fun `extractMatchesToUpdateStatus does not return a match that is not started in the API`() {
            val matchingId = UUID.randomUUID()
            val matchesFromDB = listOf(match(id = matchingId, status = MatchStatus.NOT_STARTED))
            val matchesFromAPI = listOf(match(id = matchingId, status = MatchStatus.NOT_STARTED))

            val matchesToUpdate = MatchService.extractMatchesToUpdateStatus(matchesFromDB, matchesFromAPI)
            assertTrue { matchesToUpdate.isEmpty() }
        }

        @Test
        fun `extractMatchesToUpdateStatus does not return a match that is finished in the DB`() {
            val matchingId = UUID.randomUUID()
            val matchesFromDB = listOf(match(id = matchingId, status = MatchStatus.FINISHED))
            val matchesFromAPI = listOf(match(id = matchingId, status = MatchStatus.FINISHED))

            val matchesToUpdate = MatchService.extractMatchesToUpdateStatus(matchesFromDB, matchesFromAPI)
            assertTrue { matchesToUpdate.isEmpty() }
        }

        @Test
        fun `extractMatchesToUpdateStatus returns the status-related fields of the match updated when it is in-progress in the API and not-started in the DB`() {
            val matchingId = UUID.randomUUID()
            val matchesFromDB = listOf(
                match(id = matchingId, status = MatchStatus.NOT_STARTED).copy(
                    homeGoals = null, awayGoals = null, homePenalties = null, awayPenalties = null,
                    homeQuota = 1f, drawQuota = 2f, awayQuota = 3f, substatus = null, finishedAt = null,
                )
            )
            val matchesFromAPI = listOf(
                match(id = matchingId, status = MatchStatus.IN_PROGRESS).copy(
                    homeGoals = 1, awayGoals = 2, homePenalties = 5, awayPenalties = 4,
                    homeQuota = 2f, drawQuota = 3f, awayQuota = 4f, substatus = MatchSubstatus.PENALTIES.label, finishedAt = null,
                )
            )

            val matchesToUpdate = MatchService.extractMatchesToUpdateStatus(matchesFromDB, matchesFromAPI)
            assertEquals(1, matchesToUpdate.size)
            assertEquals(1, matchesToUpdate[0].homeGoals)
            assertEquals(2, matchesToUpdate[0].awayGoals)
            assertEquals(5, matchesToUpdate[0].homePenalties)
            assertEquals(4, matchesToUpdate[0].awayPenalties)
            assertEquals(1f, matchesToUpdate[0].homeQuota)
            assertEquals(2f, matchesToUpdate[0].drawQuota)
            assertEquals(3f, matchesToUpdate[0].awayQuota)
            assertEquals(MatchSubstatus.PENALTIES.label, matchesToUpdate[0].substatus)
            assertNull(matchesToUpdate[0].finishedAt)
        }

        @Test
        fun `extractMatchesToUpdateStatus returns the status-related fields of the match updated when it is in-progress in the API and in-progress in the DB`() {
            val matchingId = UUID.randomUUID()
            val matchesFromDB = listOf(
                match(id = matchingId, status = MatchStatus.IN_PROGRESS).copy(
                    homeGoals = 0, awayGoals = 0, homePenalties = null, awayPenalties = null,
                    homeQuota = 1f, drawQuota = 2f, awayQuota = 3f, substatus = MatchSubstatus.HALFTIME.label, finishedAt = null,
                )
            )
            val matchesFromAPI = listOf(
                match(id = matchingId, status = MatchStatus.IN_PROGRESS).copy(
                    homeGoals = 1, awayGoals = 2, homePenalties = 5, awayPenalties = 4,
                    homeQuota = 2f, drawQuota = 3f, awayQuota = 4f, substatus = MatchSubstatus.PENALTIES.label, finishedAt = null,
                )
            )

            val matchesToUpdate = MatchService.extractMatchesToUpdateStatus(matchesFromDB, matchesFromAPI)
            assertEquals(1, matchesToUpdate.size)
            assertEquals(1, matchesToUpdate[0].homeGoals)
            assertEquals(2, matchesToUpdate[0].awayGoals)
            assertEquals(5, matchesToUpdate[0].homePenalties)
            assertEquals(4, matchesToUpdate[0].awayPenalties)
            assertEquals(1f, matchesToUpdate[0].homeQuota)
            assertEquals(2f, matchesToUpdate[0].drawQuota)
            assertEquals(3f, matchesToUpdate[0].awayQuota)
            assertEquals(MatchSubstatus.PENALTIES.label, matchesToUpdate[0].substatus)
            assertNull(matchesToUpdate[0].finishedAt)
        }

        @Test
        fun `extractMatchesToUpdateStatus returns the status-related fields of the match updated when it is finished in the API and in-progress in the DB`() {
            val matchingId = UUID.randomUUID()
            val finishedAt = ZonedDateTime.now()
            val matchesFromDB = listOf(
                match(id = matchingId, status = MatchStatus.IN_PROGRESS).copy(
                    homeGoals = 0, awayGoals = 0, homePenalties = null, awayPenalties = null,
                    homeQuota = 1f, drawQuota = 2f, awayQuota = 3f, substatus = MatchSubstatus.HALFTIME.label, finishedAt = null,
                )
            )
            val matchesFromAPI = listOf(
                match(id = matchingId, status = MatchStatus.IN_PROGRESS).copy(
                    homeGoals = 1, awayGoals = 2, homePenalties = 5, awayPenalties = 4,
                    homeQuota = 2f, drawQuota = 3f, awayQuota = 4f, substatus = MatchSubstatus.PENALTIES.label, finishedAt = finishedAt,
                )
            )

            val matchesToUpdate = MatchService.extractMatchesToUpdateStatus(matchesFromDB, matchesFromAPI)
            assertEquals(1, matchesToUpdate.size)
            assertEquals(1, matchesToUpdate[0].homeGoals)
            assertEquals(2, matchesToUpdate[0].awayGoals)
            assertEquals(5, matchesToUpdate[0].homePenalties)
            assertEquals(4, matchesToUpdate[0].awayPenalties)
            assertEquals(1f, matchesToUpdate[0].homeQuota)
            assertEquals(2f, matchesToUpdate[0].drawQuota)
            assertEquals(3f, matchesToUpdate[0].awayQuota)
            assertEquals(MatchSubstatus.PENALTIES.label, matchesToUpdate[0].substatus)
            assertEquals(finishedAt, matchesToUpdate[0].finishedAt)
        }

        @Test
        fun `extractMatchesToUpdateQuotas does not return a match that is not connected by the ID`() {
            val matchesFromDB = listOf(match(status = MatchStatus.FINISHED))
            val matchesFromAPI = listOf(match(status = MatchStatus.FINISHED))

            val matchesToUpdate = MatchService.extractMatchesToUpdateQuotas(matchesFromDB, matchesFromAPI)
            assertTrue { matchesToUpdate.isEmpty() }
        }

        @Test
        fun `extractMatchesToUpdateQuotas does not return a match that is other than not started in the API`() {
            val matchingId = UUID.randomUUID()
            val matchesFromDB = listOf(match(id = matchingId, status = MatchStatus.NOT_STARTED))
            val matchesFromAPI = listOf(
                match(id = matchingId, status = MatchStatus.IN_PROGRESS),
                match(id = matchingId, status = MatchStatus.FINISHED),
                match(id = matchingId, status = MatchStatus.SUSPENDED),
            )

            val matchesToUpdate = MatchService.extractMatchesToUpdateQuotas(matchesFromDB, matchesFromAPI)
            assertTrue { matchesToUpdate.isEmpty() }
        }

        @Test
        fun `extractMatchesToUpdateQuotas does not return a match that is other than not started in the DB`() {
            val matchingId = UUID.randomUUID()
            val matchesFromDB = listOf(
                match(id = matchingId, status = MatchStatus.IN_PROGRESS),
                match(id = matchingId, status = MatchStatus.FINISHED),
                match(id = matchingId, status = MatchStatus.SUSPENDED),
            )
            val matchesFromAPI = listOf(match(id = matchingId, status = MatchStatus.NOT_STARTED))

            val matchesToUpdate = MatchService.extractMatchesToUpdateQuotas(matchesFromDB, matchesFromAPI)
            assertTrue { matchesToUpdate.isEmpty() }
        }

        @Test
        fun `extractMatchesToUpdateQuotas does not return a match that is not-started in both the API and the DB but yet locked`() {
            val matchingId = UUID.randomUUID()
            val matchesFromDB = listOf(match(id = matchingId, status = MatchStatus.NOT_STARTED).copy(startedAt = ZonedDateTime.now().plusMinutes(10)))
            val matchesFromAPI = listOf(match(id = matchingId, status = MatchStatus.NOT_STARTED))

            val matchesToUpdate = MatchService.extractMatchesToUpdateQuotas(matchesFromDB, matchesFromAPI)
            assertTrue { matchesToUpdate.isEmpty() }
        }

        @Test
        fun `extractMatchesToUpdateQuotas returns the quotas-related fields updated of the match with when it is not-started the API and not-locked in the DB`() {
            val matchingId = UUID.randomUUID()
            val finishedAt = ZonedDateTime.now()
            val matchesFromDB = listOf(
                match(id = matchingId, status = MatchStatus.NOT_STARTED).copy(
                    homeGoals = 0, awayGoals = 0, homePenalties = null, awayPenalties = null,
                    homeQuota = 1f, drawQuota = 2f, awayQuota = 3f, substatus = null, finishedAt = null,
                )
            )
            val matchesFromAPI = listOf(
                match(id = matchingId, status = MatchStatus.NOT_STARTED).copy(
                    homeGoals = 1, awayGoals = 2, homePenalties = 5, awayPenalties = 4, startedAt = ZonedDateTime.now().plusDays(1),
                    homeQuota = 2f, drawQuota = 3f, awayQuota = 4f, substatus = MatchSubstatus.PENALTIES.label, finishedAt = finishedAt,
                )
            )

            val matchesToUpdate = MatchService.extractMatchesToUpdateQuotas(matchesFromDB, matchesFromAPI)
            assertEquals(1, matchesToUpdate.size)
            assertEquals(0, matchesToUpdate[0].homeGoals)
            assertEquals(0, matchesToUpdate[0].awayGoals)
            assertNull(matchesToUpdate[0].homePenalties)
            assertNull(matchesToUpdate[0].awayPenalties)
            assertEquals(2f, matchesToUpdate[0].homeQuota)
            assertEquals(3f, matchesToUpdate[0].drawQuota)
            assertEquals(4f, matchesToUpdate[0].awayQuota)
            assertEquals(MatchStatus.NOT_STARTED, matchesToUpdate[0].status)
            assertNull(matchesToUpdate[0].substatus)
            assertNull(matchesToUpdate[0].finishedAt)
        }
    }

    @Nested
    inner class UpdateMatchesStatusesTests {

        private val matchesFromDB = (1..10).map { matchFromDB(code = "$it", home = "H$it", away = "A$it") }
        private val externalMatches = (1..10).map { externalMatch(home = "H$it", away = "A$it") }

        private fun populateMatchesToUpdate(matchesFromDB: List<Match>, externalMatches: List<ExternalMatch>): List<Match> {
            val dbMatchesMap = matchesFromDB.filter { it.id != null }.associateBy { it.id!! }
            val mappedMatches = externalMatches.map { it.toExistingMatch(matchesFromDB)!! }
            return mappedMatches.mapNotNull { match ->
                dbMatchesMap[match.id]?.let { match.copy(homeQuota = it.homeQuota, drawQuota = it.drawQuota, awayQuota = it.awayQuota) }
            }
        }

        @Test
        fun `updateMatchesStatuses fails when tournamentId is not supported`() {
            val exception = assertThrows<BadRequestException> {
                matchService.updateMatchesStatuses(UUID.randomUUID())
            }

            assertEquals("Tournament not supported", exception.message)
        }

        @Test
        fun `updateMatchesStatuses fails when the MatchClient fails`() {
            every { matchClient.getMatches(testTournamentId) } throws ExternalServiceException("Unexpected error")

            val exception = assertThrows<ExternalServiceException> {
                matchService.updateMatchesStatuses(testTournamentId)
            }

            assertEquals("Unexpected error", exception.message)
        }

        @Test
        fun `updateMatchesStatuses saves tournament status update`() {
            every { matchClient.getMatches(testTournamentId) } returns externalMatches
            every { matchRepository.findByTournamentId(testTournamentId) } returns matchesFromDB
            every { tournamentRepository.findById(testTournamentId) } returns Optional.of(testTournament)
            every { teamRepository.findByTournamentId(testTournamentId) } returns tournamentTeams

            val matchesToUpdate = populateMatchesToUpdate(matchesFromDB, externalMatches)
            every { engine.calculateTournamentStatus(matchesToUpdate) } returns TournamentStatus.IN_PROGRESS

            every { tournamentRepository.save(any()) } answers { firstArg() }
            every { matchRepository.saveAll(any<List<Match>>()) } answers { firstArg() }

            matchService.updateMatchesStatuses(testTournamentId)

            val slot = slot<Tournament>()
            verify(exactly = 1) { tournamentRepository.save(capture(slot)) }
            val savedTournament = slot.captured
            assertEquals(TournamentStatus.IN_PROGRESS, savedTournament.status)

            verify(exactly = 1) { tournamentRepository.save(capture(slot)) }
        }

        @Test
        fun `updateMatchesStatuses saves updated matches`() {
            every { matchClient.getMatches(testTournamentId) } returns externalMatches
            every { matchRepository.findByTournamentId(testTournamentId) } returns matchesFromDB
            every { tournamentRepository.findById(testTournamentId) } returns Optional.of(testTournament)
            every { teamRepository.findByTournamentId(testTournamentId) } returns tournamentTeams

            val matchesToUpdate = populateMatchesToUpdate(matchesFromDB, externalMatches)
            every { engine.calculateTournamentStatus(matchesToUpdate) } returns null

            verify(exactly = 0) { tournamentRepository.save(any()) }
            every { matchRepository.saveAll(any<List<Match>>()) } answers { firstArg() }

            val returnedMatches = matchService.updateMatchesStatuses(testTournamentId)

            val slot = slot<List<Match>>()
            verify(exactly = 1) { matchRepository.saveAll(capture(slot)) }
            val savedMatches = slot.captured
            savedMatches.forEachIndexed { idx, match ->
                assertEquals("${idx + 1}", match.code)
                assertEquals(1, match.homeGoals)
                assertEquals(1, match.awayGoals)
                assertEquals(MatchStatus.IN_PROGRESS, match.status)
            }

            assertEquals(returnedMatches, savedMatches)
        }

        @Test
        fun `updateMatchesStatuses does not save matches that were not updated`() {
            val nonStartedMatch = externalMatches.last()
            val externalMatches = externalMatches.dropLast(1) + externalMatches.last().copy(status = MatchStatus.NOT_STARTED, substatus = null)

            every { matchClient.getMatches(testTournamentId) } returns externalMatches
            every { matchRepository.findByTournamentId(testTournamentId) } returns matchesFromDB
            every { tournamentRepository.findById(testTournamentId) } returns Optional.of(testTournament)
            every { teamRepository.findByTournamentId(testTournamentId) } returns tournamentTeams

            val matchesToUpdate = populateMatchesToUpdate(matchesFromDB, externalMatches.dropLast(1))
            every { engine.calculateTournamentStatus(matchesToUpdate) } returns null

            verify(exactly = 0) { tournamentRepository.save(any()) }
            every { matchRepository.saveAll(any<List<Match>>()) } answers { firstArg() }

            val returnedMatches = matchService.updateMatchesStatuses(testTournamentId)

            val slot = slot<List<Match>>()
            verify(exactly = 1) { matchRepository.saveAll(capture(slot)) }
            val savedMatches = slot.captured

            assertEquals(externalMatches.size - 1, savedMatches.size)
            assertTrue(savedMatches.none { it.homeTeam.code == nonStartedMatch.home && it.awayTeam.code == nonStartedMatch.away })

            assertEquals(returnedMatches, savedMatches)
        }

        @Test
        fun `updateMatchesStatuses saves new matches`() {
            val externalMatches = externalMatches + externalMatch(home = "H2", away = "A5")
                .copy(status = MatchStatus.NOT_STARTED, startedAt = ZonedDateTime.now().plusDays(1))
            every { matchClient.getMatches(testTournamentId) } returns externalMatches
            every { matchRepository.findByTournamentId(testTournamentId) } returns matchesFromDB
            every { tournamentRepository.findById(testTournamentId) } returns Optional.of(testTournament)
            every { teamRepository.findByTournamentId(testTournamentId) } returns tournamentTeams

            val externalOdds = externalMatches.map {
                externalOdds(home = it.home, away = it.away).copy(homeOdds = 6f, drawOdds = 7f, awayOdds = 8f, startedAt = it.startedAt!!)
            }
            every { oddsClient.getOdds(testTournamentId) } returns externalOdds

            val matchesToUpdate = populateMatchesToUpdate(matchesFromDB, externalMatches.dropLast(1))
            every { engine.calculateTournamentStatus(matchesToUpdate) } returns null

            verify(exactly = 0) { tournamentRepository.save(any()) }
            every { engine.generateMatchesCodes(any()) } answers
                    { firstArg<List<Match>>().mapIndexed { idx, match -> match.copy(code = "M${idx + 10}") } }
            every { matchRepository.saveAll(any<List<Match>>()) } answers { firstArg() }

            val returnedMatches = matchService.updateMatchesStatuses(testTournamentId)

            val slot = slot<List<Match>>()
            verify(exactly = 1) { matchRepository.saveAll(capture(slot)) }
            val savedMatches = slot.captured
            assertEquals(11, returnedMatches.size)
            assertEquals(11, savedMatches.size)
            assertEquals("M10", savedMatches.last().code)
            assertEquals("H2", savedMatches.last().homeTeam.code)
            assertEquals("A5", savedMatches.last().awayTeam.code)
            assertEquals(6f.oddsToQuota(), savedMatches.last().homeQuota)
            assertEquals(7f.oddsToQuota(), savedMatches.last().drawQuota)
            assertEquals(8f.oddsToQuota(), savedMatches.last().awayQuota)
        }

        @Test
        fun `updateMatchesStatuses ignores system finished matches`() {
            val externalMatches = externalMatches.dropLast(1) +
                    externalMatches.last().copy(status = MatchStatus.FINISHED, substatus = "FIN", finishedAt = ZonedDateTime.now())
            val systemMatches = matchesFromDB.map { it.copy(status = MatchStatus.IN_PROGRESS, homeGoals = 1, awayGoals = 1) }.dropLast(1) +
                    matchesFromDB.last().copy(status = MatchStatus.FINISHED, homeGoals = 1, awayGoals = 1, substatus = "FIN")

            every { matchClient.getMatches(testTournamentId) } returns externalMatches
            every { matchRepository.findByTournamentId(testTournamentId) } returns systemMatches
            every { tournamentRepository.findById(testTournamentId) } returns Optional.of(testTournament)
            every { teamRepository.findByTournamentId(testTournamentId) } returns tournamentTeams

            val matchesToUpdate = populateMatchesToUpdate(matchesFromDB, externalMatches.dropLast(1))
            every { engine.calculateTournamentStatus(matchesToUpdate) } returns null

            verify(exactly = 0) { tournamentRepository.save(any()) }
            every { matchRepository.saveAll(any<List<Match>>()) } answers { firstArg() }

            every { matchService.updateMatchPredictionsPoints(any()) } just Runs
            val returnedMatches = matchService.updateMatchesStatuses(testTournamentId)

            val slot = slot<List<Match>>()
            verify(exactly = 1) { matchRepository.saveAll(capture(slot)) }
            val savedMatches = slot.captured
            assertEquals(9, savedMatches.size)
            assertEquals(9, returnedMatches.size)
            assertEquals(systemMatches.map { it.id }.dropLast(1), savedMatches.map { it.id })
            savedMatches.forEach { assertEquals(MatchStatus.IN_PROGRESS, it.status) }
        }

        @Test
        fun `updateMatchesStatuses updates match predictions when any of the matches has finished`() {
            val externalMatches = externalMatches.dropLast(1) +
                    externalMatches.last().copy(status = MatchStatus.FINISHED, substatus = "FIN", finishedAt = ZonedDateTime.now())
            val systemMatches = matchesFromDB.map { it.copy(status = MatchStatus.IN_PROGRESS, homeGoals = 1, awayGoals = 1) }

            every { matchClient.getMatches(testTournamentId) } returns externalMatches
            every { matchRepository.findByTournamentId(testTournamentId) } returns systemMatches
            every { tournamentRepository.findById(testTournamentId) } returns Optional.of(testTournament)
            every { teamRepository.findByTournamentId(testTournamentId) } returns tournamentTeams

            val matchesToUpdate = populateMatchesToUpdate(matchesFromDB, externalMatches)
            every { engine.calculateTournamentStatus(matchesToUpdate) } returns null

            verify(exactly = 0) { tournamentRepository.save(any()) }
            every { matchRepository.saveAll(any<List<Match>>()) } answers { firstArg() }

            every { matchService.updateMatchPredictionsPoints(any()) } just Runs
            matchService.updateMatchesStatuses(testTournamentId)

            verify(exactly = 1) { matchService.updateMatchPredictionsPoints(any()) }
        }
    }

    @Nested
    inner class CheckMatchPredictionsTests {

        @Test
        fun `checkMatchPredictions ignores predictions with status not PENDING`() {
            val predictions = listOf(
                predictionFromDB(
                    match = matchFromDB(code = "1", home = "XXX", away = "YYY").copy(status = MatchStatus.FINISHED),
                    status = PredictionStatus.MISSING
                )
            )

            val results = matchService.checkMatchPredictions(predictions)
            assertEquals(0, results.size)
        }

        @Test
        fun `checkMatchPredictions ignores predictions with match status not FINISHED`() {
            val predictions = listOf(
                predictionFromDB(
                    match = matchFromDB(code = "1", home = "XXX", away = "YYY").copy(status = MatchStatus.IN_PROGRESS),
                    status = PredictionStatus.PENDING
                )
            )

            val results = matchService.checkMatchPredictions(predictions)
            assertEquals(0, results.size)
        }

        @Test
        fun `checkMatchPredictions processes predictions with status PENDING from matches with status FINISHED`() {
            val predictions = listOf(
                predictionFromDB(
                    match = matchFromDB(code = "1", home = "XXX", away = "YYY").copy(status = MatchStatus.FINISHED),
                    status = PredictionStatus.PENDING
                )
            )

            val results = matchService.checkMatchPredictions(predictions)
            assertEquals(1, results.size)
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
        fun `updateMatchesQuotas fails when the MatchClient fails`() {
            every { oddsClient.getOdds(testTournamentId) } throws ExternalServiceException("Unexpected error")

            val exception = assertThrows<ExternalServiceException> {
                matchService.updateMatchesQuotas(testTournamentId)
            }

            assertEquals("Unexpected error", exception.message)
        }

        @Test
        fun `updateMatchesQuotas saves updated quotas`() {
            val systemMatches = (1..10).map { matchFromDB(code = "$it", home = "H$it", away = "A$it") }
            val externalOdds = (1..10).map {
                externalOdds(home = "H$it", away = "A$it")
                    .copy(homeOdds = 2f, drawOdds = 3f, awayOdds = 4f, startedAt = ZonedDateTime.now().plusDays(1))
            }

            every { oddsClient.getOdds(testTournamentId) } returns externalOdds
            every { matchRepository.findByTournamentIdAndStatus(testTournamentId, MatchStatus.NOT_STARTED) } returns systemMatches
            every { teamRepository.findByTournamentId(testTournamentId) } returns tournamentTeams
            every { matchRepository.saveAll(any<List<Match>>()) } answers { firstArg() }

            matchService.updateMatchesQuotas(testTournamentId)

            val slot = slot<List<Match>>()
            verify(exactly = 1) { matchRepository.saveAll(capture(slot)) }
            val savedMatches = slot.captured
            savedMatches.forEachIndexed { idx, match ->
                assertEquals("${idx + 1}", match.code)
                assertEquals(MatchStatus.NOT_STARTED, match.status)
                assertEquals(2F.oddsToQuota(), match.homeQuota)
                assertEquals(3F.oddsToQuota(), match.drawQuota)
                assertEquals(4F.oddsToQuota(), match.awayQuota)
            }
        }
    }
}
