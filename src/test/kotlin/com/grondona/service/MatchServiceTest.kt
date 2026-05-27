package com.grondona.service

import com.grondona.client.MatchClient
import com.grondona.exception.BadRequestException
import com.grondona.exception.ExternalServiceException
import com.grondona.model.ExternalMatch
import com.grondona.model.Group
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
    private lateinit var matchClient: MatchClient

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

    private fun matchFromDB(code: String, home: String, away: String) = Match(
        id = UUID.randomUUID(), code = code, tournament = testTournament, homeGoals = 0, awayGoals = 0,
        homeTeam = Team(tournament = testTournament, name = home, code = home, icon = "test"),
        awayTeam = Team(tournament = testTournament, name = away, code = away, icon = "test"),
        status = MatchStatus.NOT_STARTED, homeQuota = 1f, drawQuota = 1f, awayQuota = 1f, startedAt = ZonedDateTime.now().plusDays(1),
    )

    private fun matchFromAPI(code: String, home: String, away: String) = ExternalMatch(
        code = code, home = home, away = away, homeGoals = 1, awayGoals = 1, status = "IN_PLAY",
        minutes = 30, half = 0, homeOdds = 1f, drawOdds = 1f, awayOdds = 1f, startedAt = ZonedDateTime.now(),
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
        every { engine.tournamentId } returns testTournamentId

        matchService = spyk(
            MatchService(
                matchClient,
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
    inner class UpdateMatchesStatusesTests {

        private val systemMatches = (1..10).map { matchFromDB(code = "$it", home = "H$it", away = "A$it") }
        private val externalMatches = (1..10).map { matchFromAPI(code = "$it", home = "H$it", away = "A$it") }

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
            every { matchRepository.findByTournamentId(testTournamentId) } returns systemMatches
            every { tournamentRepository.findById(testTournamentId) } returns Optional.of(testTournament)

            val consolidatedMatches = externalMatches.map { it.toMatchUpdated(systemMatches)!! }
            every { engine.calculateTournamentStatus(consolidatedMatches) } returns TournamentStatus.IN_PROGRESS
            every { engine.calculateNewMatches(consolidatedMatches, externalMatches) } returns emptyList()

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
            every { matchRepository.findByTournamentId(testTournamentId) } returns systemMatches
            every { tournamentRepository.findById(testTournamentId) } returns Optional.of(testTournament)

            val consolidatedMatches = externalMatches.map { it.toMatchUpdated(systemMatches)!! }
            every { engine.calculateTournamentStatus(consolidatedMatches) } returns null
            every { engine.calculateNewMatches(consolidatedMatches, externalMatches) } returns emptyList()

            verify(exactly = 0) { tournamentRepository.save(any()) }
            every { matchRepository.saveAll(any<List<Match>>()) } answers { firstArg() }

            val matchesToSave = matchService.updateMatchesStatuses(testTournamentId)

            val slot = slot<List<Match>>()
            verify(exactly = 1) { matchRepository.saveAll(capture(slot)) }
            val savedMatches = slot.captured
            savedMatches.forEachIndexed { idx, match ->
                assertEquals("${idx + 1}", match.code)
                assertEquals(1, match.homeGoals)
                assertEquals(1, match.awayGoals)
                assertEquals(MatchStatus.IN_PROGRESS, match.status)
            }

            assertEquals(matchesToSave, savedMatches)
        }

        @Test
        fun `updateMatchesStatuses does not save matches that were not updated`() {
            val nonStartedCode = externalMatches.last().code
            val externalMatches = externalMatches.dropLast(1) + externalMatches.last().copy(status = "TO_START")
            every { matchClient.getMatches(testTournamentId) } returns externalMatches
            every { matchRepository.findByTournamentId(testTournamentId) } returns systemMatches
            every { tournamentRepository.findById(testTournamentId) } returns Optional.of(testTournament)

            val consolidatedMatches = externalMatches.dropLast(1).map { it.toMatchUpdated(systemMatches)!! } + systemMatches.last()
            every { engine.calculateTournamentStatus(consolidatedMatches) } returns null
            every { engine.calculateTournamentStatus(consolidatedMatches) } returns null
            every { engine.calculateNewMatches(consolidatedMatches, externalMatches) } returns emptyList()

            verify(exactly = 0) { tournamentRepository.save(any()) }
            every { matchRepository.saveAll(any<List<Match>>()) } answers { firstArg() }

            val matchesToSave = matchService.updateMatchesStatuses(testTournamentId)

            val slot = slot<List<Match>>()
            verify(exactly = 1) { matchRepository.saveAll(capture(slot)) }
            val savedMatches = slot.captured

            assertEquals(externalMatches.size - 1, savedMatches.size)
            assertTrue(savedMatches.none { it.code == nonStartedCode })

            assertEquals(matchesToSave, savedMatches)
        }

        @Test
        fun `updateMatchesStatuses saves new matches`() {
            every { matchClient.getMatches(testTournamentId) } returns externalMatches
            every { matchRepository.findByTournamentId(testTournamentId) } returns systemMatches
            every { tournamentRepository.findById(testTournamentId) } returns Optional.of(testTournament)

            val consolidatedMatches = externalMatches.map { it.toMatchUpdated(systemMatches)!! }
            every { engine.calculateTournamentStatus(consolidatedMatches) } returns null
            val newMatches = (11..16).map { matchFromDB(code = "$it", home = "H$it", away = "A$it") }
            every { engine.calculateNewMatches(consolidatedMatches, externalMatches) } returns newMatches

            verify(exactly = 0) { tournamentRepository.save(any()) }
            every { matchRepository.saveAll(any<List<Match>>()) } answers { firstArg() }

            val matchesToSave = matchService.updateMatchesStatuses(testTournamentId)

            val slot = slot<List<Match>>()
            verify(exactly = 1) { matchRepository.saveAll(capture(slot)) }
            val savedMatches = slot.captured
            savedMatches.forEachIndexed { idx, match ->
                assertEquals("${idx + 1}", match.code)
                if (idx < 10) {
                    assertEquals(1, match.homeGoals)
                    assertEquals(1, match.awayGoals)
                    assertEquals(MatchStatus.IN_PROGRESS, match.status)
                } else {
                    assertEquals(0, match.homeGoals)
                    assertEquals(0, match.awayGoals)
                    assertEquals(MatchStatus.NOT_STARTED, match.status)
                }
            }

            assertEquals(matchesToSave, savedMatches)
        }

        @Test
        fun `updateMatchesStatuses ignores system finished matches`() {
            val externalMatches = externalMatches.dropLast(1) + externalMatches.last().copy(status = "COMPLETED")
            every { matchClient.getMatches(testTournamentId) } returns externalMatches

            val systemMatches = systemMatches.map { it.copy(status = MatchStatus.IN_PROGRESS, homeGoals = 1, awayGoals = 1) }.dropLast(1) +
                    systemMatches.last().copy(status = MatchStatus.FINISHED, homeGoals = 1, awayGoals = 1, substatus = "FIN")
            every { matchRepository.findByTournamentId(testTournamentId) } returns systemMatches
            every { tournamentRepository.findById(testTournamentId) } returns Optional.of(testTournament)

            val consolidatedMatches = externalMatches.dropLast(1).map { it.toMatchUpdated(systemMatches)!! } + systemMatches.last()
            every { engine.calculateTournamentStatus(consolidatedMatches) } returns null
            every { engine.calculateNewMatches(consolidatedMatches, externalMatches) } returns emptyList()

            verify(exactly = 0) { tournamentRepository.save(any()) }
            every { matchRepository.saveAll(any<List<Match>>()) } answers { firstArg() }

            every { matchService.updateMatchPredictionsPoints(any()) } just Runs
            matchService.updateMatchesStatuses(testTournamentId)

            val slot = slot<List<Match>>()
            verify(exactly = 1) { matchRepository.saveAll(capture(slot)) }
            val matchesSaved = slot.captured
            assertEquals(9, matchesSaved.size)
            assertEquals(systemMatches.map { it.id }.dropLast(1), matchesSaved.map { it.id })
            matchesSaved.forEach { assertEquals(MatchStatus.IN_PROGRESS, it.status) }
        }

        @Test
        fun `updateMatchesStatuses updates match predictions when any of the matches has finished`() {
            val externalMatches = externalMatches.dropLast(1) + externalMatches.last().copy(status = "COMPLETED", finishedAt = ZonedDateTime.now())
            every { matchClient.getMatches(testTournamentId) } returns externalMatches

            val systemMatches = systemMatches.map { it.copy(status = MatchStatus.IN_PROGRESS, homeGoals = 1, awayGoals = 1) }
            every { matchRepository.findByTournamentId(testTournamentId) } returns systemMatches
            every { tournamentRepository.findById(testTournamentId) } returns Optional.of(testTournament)

            val consolidatedMatches = externalMatches.map { it.toMatchUpdated(systemMatches)!! }
            every { engine.calculateTournamentStatus(consolidatedMatches) } returns null
            every { engine.calculateNewMatches(consolidatedMatches, externalMatches) } returns emptyList()

            verify(exactly = 0) { tournamentRepository.save(any()) }
            every { matchRepository.saveAll(any<List<Match>>()) } answers { firstArg() }

            every { matchService.updateMatchPredictionsPoints(any()) } just Runs
            matchService.updateMatchesStatuses(testTournamentId)

            verify(exactly = 1) { matchService.updateMatchPredictionsPoints(any()) }
        }
    }

    @Nested
    inner class ConsolidateMatchesTests {

        @Test
        fun `consolidateMatches joins set of matches`() {
            val matchIds = (0..9).map { UUID.randomUUID() }
            val storedMatches = (0..9).map { matchFromDB(code = "$it", home = "H$it", away = "A$it").copy(id = matchIds[it]) }
            val newMatches = (0..3).map {
                matchFromDB(code = "$it", home = "H$it", away = "A$it").copy(status = MatchStatus.IN_PROGRESS, homeGoals = 2, awayGoals = 1)
                    .copy(id = matchIds[it])
            }

            val results = matchService.consolidateMatches(newMatches, storedMatches)
            assertEquals(10, results.size)
            results.forEachIndexed { idx, match ->
                assertEquals("$idx", match.code)
                if (idx <= 3) {
                    assertEquals(MatchStatus.IN_PROGRESS, match.status)
                    assertEquals(2, match.homeGoals)
                    assertEquals(1, match.awayGoals)
                } else {
                    assertEquals(MatchStatus.NOT_STARTED, match.status)
                    assertEquals(0, match.homeGoals)
                    assertEquals(0, match.awayGoals)
                }
            }
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
            every { matchClient.getMatches(testTournamentId) } throws ExternalServiceException("Unexpected error")

            val exception = assertThrows<ExternalServiceException> {
                matchService.updateMatchesQuotas(testTournamentId)
            }

            assertEquals("Unexpected error", exception.message)
        }

        @Test
        fun `updateMatchesStatuses saves updated quotas`() {
            val systemMatches = (1..10).map {
                matchFromDB(code = "$it", home = "H$it", away = "A$it")
            }
            val externalMatches = (1..10).map {
                matchFromAPI(code = "$it", home = "H$it", away = "A$it").copy(status = "TO_START", homeOdds = 1F, drawOdds = 2F, awayOdds = 3F)
            }

            every { matchClient.getMatches(testTournamentId) } returns externalMatches
            every { matchRepository.findByTournamentIdAndStatus(testTournamentId, MatchStatus.NOT_STARTED) } returns systemMatches

            verify(exactly = 0) { tournamentRepository.save(any()) }
            every { matchRepository.saveAll(any<List<Match>>()) } answers { firstArg() }

            matchService.updateMatchesQuotas(testTournamentId)

            val slot = slot<List<Match>>()
            verify(exactly = 1) { matchRepository.saveAll(capture(slot)) }
            val savedMatches = slot.captured
            savedMatches.forEachIndexed { idx, match ->
                assertEquals("${idx + 1}", match.code)
                assertEquals(MatchStatus.NOT_STARTED, match.status)
                assertEquals(1F.oddsToQuota(), match.homeQuota)
                assertEquals(2F.oddsToQuota(), match.drawQuota)
                assertEquals(3F.oddsToQuota(), match.awayQuota)
            }
        }
    }
}
