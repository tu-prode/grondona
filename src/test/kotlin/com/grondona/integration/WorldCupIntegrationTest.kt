package com.grondona.integration

import com.fasterxml.jackson.databind.ObjectMapper
import com.grondona.client.MatchClient
import com.grondona.integration.utils.GrondonaClient
import com.grondona.model.Awards
import com.grondona.model.ExternalMatch
import com.grondona.model.MatchStage
import com.grondona.model.MatchStatus
import com.grondona.model.PredictionStatus
import com.grondona.model.TEST
import com.grondona.model.dto.request.CreateMatchRequest
import com.grondona.model.dto.request.CreateMatchesRequest
import com.grondona.model.dto.request.SubmitAwardPredictionRequest
import com.grondona.model.dto.request.SubmitMatchPredictionRequest
import com.grondona.model.dto.request.UpdateTournamentRequest
import com.grondona.otherRandom
import com.grondona.repository.TournamentRepository
import com.grondona.repository.UserRepository
import com.grondona.scheduler.MatchStatusScheduler
import com.ninjasquad.springmockk.MockkBean
import io.mockk.clearMocks
import io.mockk.every
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import java.time.ZonedDateTime
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles(TEST)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class WorldCupIntegrationTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var tournamentRepository: TournamentRepository

    @Autowired
    lateinit var matchScheduler: MatchStatusScheduler

    @MockkBean
    lateinit var matchClient: MatchClient

    @Autowired
    private lateinit var testDatabaseCleaner: TestDatabaseCleaner

    lateinit var grondona: GrondonaClient

    private lateinit var user1Id: UUID
    private lateinit var user2Id: UUID
    private lateinit var user1Token: String
    private lateinit var user2Token: String

    private lateinit var groupId: UUID

    private lateinit var champion: UUID
    private lateinit var topScorer: UUID
    private lateinit var bestPlayer: UUID
    private lateinit var bestGoalkeeper: UUID
    private lateinit var bestYoungPlayer: UUID

    private val codesForLast32 = (73..88).map { it.toString() }
    private val codesForLast16 = (89..96).map { it.toString() }
    private val codesForQuarterfinals = (97..100).map { it.toString() }
    private val codesForSemifinals = (101..102).map { it.toString() }
    private val codesForLastRound = (103..104).map { it.toString() }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    @TestMethodOrder(MethodOrderer.OrderAnnotation::class)
    inner class ManualWorldCupTests {

        private var qualifiedTeams = mutableListOf<UUID>()

        @BeforeAll
        fun setUp() {
            testDatabaseCleaner.cleanAll()
            clearMocks(matchClient)

            grondona = GrondonaClient(mockMvc, objectMapper).withRepositories(userRepository, tournamentRepository)
            grondona.init()
        }

        @Test
        @Order(1)
        fun `should create a new user, a new group and join it`() {
            user1Id = grondona.adminId
            user1Token = grondona.adminToken

            val (newId, newToken) = grondona.createUser()
            user2Id = newId
            user2Token = newToken

            groupId = grondona.createGroup()
            grondona.joinGroup(user2Token, groupId)
        }

        @Test
        @Order(2)
        fun `should submit predictions for every match, for both users`() {
            grondona.submitMatchPredictionsToGroup(user1Token, groupId, grondona.matches.map {
                SubmitMatchPredictionRequest(matchId = it.id, homeGoals = 0, awayGoals = 0)
            }, withAssertions = true)

            grondona.submitMatchPredictionsToGroup(user2Token, groupId, grondona.matches.map {
                SubmitMatchPredictionRequest(matchId = it.id, homeGoals = 1, awayGoals = 1)
            }, withAssertions = true)
        }

        @Test
        @Order(3)
        fun `should submit predictions for every award, for both users`() {
            champion = grondona.teams.random().id
            topScorer = grondona.playerIds.random()
            bestPlayer = grondona.playerIds.random()
            bestGoalkeeper = grondona.goalkeeperIds.random()
            bestYoungPlayer = grondona.youngsterIds.random()

            grondona.submitAwardPredictionsToGroup(
                user1Token, groupId, SubmitAwardPredictionRequest(
                    champions = listOf(champion),
                    topScorers = listOf(topScorer),
                    bestPlayers = listOf(bestPlayer),
                    bestGoalkeepers = listOf(bestGoalkeeper),
                    bestYoungPlayers = listOf(bestYoungPlayer),
                )
            )

            val extraChampion = grondona.teams.map { team -> team.id }.otherRandom(champion)
            val extraTopScorer = grondona.playerIds.otherRandom(topScorer)
            val extraBestPlayer = grondona.playerIds.otherRandom(bestPlayer)
            val extraBestGoalkeeper = grondona.goalkeeperIds.otherRandom(bestGoalkeeper)
            val extraBestYoungPlayer = grondona.youngsterIds.otherRandom(bestYoungPlayer)
            grondona.submitAwardPredictionsToGroup(
                user2Token, groupId, SubmitAwardPredictionRequest(
                    champions = listOf(champion, extraChampion),
                    topScorers = listOf(topScorer, extraTopScorer),
                    bestPlayers = listOf(bestPlayer, extraBestPlayer),
                    bestGoalkeepers = listOf(bestGoalkeeper, extraBestGoalkeeper),
                    bestYoungPlayers = listOf(bestYoungPlayer, extraBestYoungPlayer),
                )
            )
        }

        @Test
        @Order(4)
        fun `should receive updates for every existing match in group stage`() {
            val matchesToUpdate = grondona.matches

            val externalMatchesChunks = matchesToUpdate.chunked(12).map { matches ->
                matches.map {
                    ExternalMatch(
                        home = it.homeCode, away = it.awayCode, stage = it.stage, group = it.group,
                        homeGoals = 0, awayGoals = 0, status = MatchStatus.FINISHED, substatus = "FIN",
                        startedAt = ZonedDateTime.now().minusMinutes(118), finishedAt = ZonedDateTime.now()
                    )
                }
            }

            every { matchClient.getMatches(any()) } returnsMany externalMatchesChunks

            externalMatchesChunks.forEach { _ -> matchScheduler.updateMatches() }
        }

        @Test
        @Order(5)
        fun `should check standings after group stage, for both users`() {
            val standings1 = grondona.fetchGroup(user1Token, groupId).standings
            val standings2 = grondona.fetchGroup(user2Token, groupId).standings

            listOf(standings1, standings2).forEach { standings ->
                assertEquals(2, standings.size)
                assertEquals(1, standings[0].rank)
                assertEquals(user1Id, standings[0].user.id)
                assertEquals(216f, standings[0].points)
                assertEquals(List(5) { PredictionStatus.CORRECT }, standings[0].lastPredictions)
                assertEquals(2, standings[1].rank)
                assertEquals(user2Id, standings[1].user.id)
                assertEquals(72f, standings[1].points)
                assertEquals(List(5) { PredictionStatus.PARTIAL }, standings[1].lastPredictions)
            }
        }

        @Test
        @Order(6)
        fun `should create new matches, for round of 32`() {
            qualifiedTeams = mutableListOf()
            val newMatchesRequests = codesForLast32.map {
                val homeTeam: UUID = grondona.teams.map { team -> team.id }.otherRandom(*qualifiedTeams.toTypedArray())
                qualifiedTeams.add(homeTeam)
                val awayTeam = grondona.teams.map { team -> team.id }.otherRandom(*qualifiedTeams.toTypedArray())
                qualifiedTeams.add(awayTeam)
                CreateMatchRequest(
                    code = it, homeTeam = homeTeam, awayTeam = awayTeam,
                    stage = MatchStage.ROUND_OF_32, startedAt = ZonedDateTime.now().plusDays(1),
                )
            }

            grondona.createMatches(request = CreateMatchesRequest(matches = newMatchesRequests))
        }

        @Test
        @Order(7)
        fun `should submit predictions for every match in the round of 32, for both users`() {
            grondona.submitMatchPredictionsToGroup(user1Token, groupId, grondona.matches.filter { it.code in codesForLast32 }.map {
                SubmitMatchPredictionRequest(matchId = it.id, homeGoals = 0, awayGoals = 0)
            }, withAssertions = true)

            grondona.submitMatchPredictionsToGroup(user2Token, groupId, grondona.matches.filter { it.code in codesForLast32 }.map {
                SubmitMatchPredictionRequest(matchId = it.id, homeGoals = 1, awayGoals = 1)
            }, withAssertions = true)
        }

        @Test
        @Order(8)
        fun `should receive updates for every existing match in round of 32`() {
            val matchesToUpdate = grondona.matches.filter { it.code in codesForLast32 }
            val middle = (matchesToUpdate.size / 2) + 1

            val externalMatchesResponse1 = matchesToUpdate.take(middle)
            val externalMatchesResponse2 = matchesToUpdate.drop(middle)

            val externalMatchesResponses = listOf(externalMatchesResponse1, externalMatchesResponse2).map { matches ->
                matches.map {
                    ExternalMatch(
                        home = it.homeCode, away = it.awayCode, stage = it.stage, group = it.group,
                        homeGoals = 0, awayGoals = 0, status = MatchStatus.FINISHED, substatus = "FIN",
                        startedAt = ZonedDateTime.now().minusMinutes(118), finishedAt = ZonedDateTime.now()
                    )
                }
            }

            matchesToUpdate[0]
            every { matchClient.getMatches(any()) } returnsMany externalMatchesResponses

            matchScheduler.updateMatches()
            matchScheduler.updateMatches()
        }

        @Test
        @Order(9)
        fun `should check standings after round of 32, for both users`() {
            val standings1 = grondona.fetchGroup(user1Token, groupId).standings
            val standings2 = grondona.fetchGroup(user2Token, groupId).standings

            listOf(standings1, standings2).forEach { standings ->
                assertEquals(2, standings.size)
                assertEquals(1, standings[0].rank)
                assertEquals(user1Id, standings[0].user.id)
                assertEquals(264f, standings[0].points)
                assertEquals(List(5) { PredictionStatus.CORRECT }, standings[0].lastPredictions)
                assertEquals(2, standings[1].rank)
                assertEquals(user2Id, standings[1].user.id)
                assertEquals(88f, standings[1].points)
                assertEquals(List(5) { PredictionStatus.PARTIAL }, standings[1].lastPredictions)
            }
        }

        @Test
        @Order(10)
        fun `should create new matches, for round of 16`() {
            val newQualifiedTeams = mutableListOf<UUID>()
            val newMatchesRequests = codesForLast16.map {
                val homeTeam: UUID = qualifiedTeams.otherRandom(*newQualifiedTeams.toTypedArray())
                newQualifiedTeams.add(homeTeam)
                val awayTeam = qualifiedTeams.otherRandom(*newQualifiedTeams.toTypedArray())
                newQualifiedTeams.add(awayTeam)
                CreateMatchRequest(
                    code = it, homeTeam = homeTeam, awayTeam = awayTeam,
                    stage = MatchStage.ROUND_OF_16, startedAt = ZonedDateTime.now().plusDays(1),
                )
            }

            qualifiedTeams = newQualifiedTeams
            grondona.createMatches(request = CreateMatchesRequest(matches = newMatchesRequests))
        }

        @Test
        @Order(11)
        fun `should submit predictions for every match in the round of 16, for both users`() {
            grondona.submitMatchPredictionsToGroup(user1Token, groupId, grondona.matches.filter { it.code in codesForLast16 }.map {
                SubmitMatchPredictionRequest(matchId = it.id, homeGoals = 0, awayGoals = 0)
            }, withAssertions = true)

            grondona.submitMatchPredictionsToGroup(user2Token, groupId, grondona.matches.filter { it.code in codesForLast16 }.map {
                SubmitMatchPredictionRequest(matchId = it.id, homeGoals = 1, awayGoals = 1)
            }, withAssertions = true)
        }

        @Test
        @Order(12)
        fun `should receive updates for every existing match in round of 16`() {
            every { matchClient.getMatches(any()) } returns grondona.matches.filter { it.code in codesForLast16 }.map {
                ExternalMatch(
                    home = it.homeCode, away = it.awayCode, stage = it.stage, group = it.group,
                    homeGoals = 0, awayGoals = 0, status = MatchStatus.FINISHED, substatus = "FIN",
                    startedAt = ZonedDateTime.now().minusMinutes(118), finishedAt = ZonedDateTime.now()
                )
            }

            matchScheduler.updateMatches()
        }

        @Test
        @Order(13)
        fun `should check standings after round of 16, for both users`() {
            val standings1 = grondona.fetchGroup(user1Token, groupId).standings
            val standings2 = grondona.fetchGroup(user2Token, groupId).standings

            listOf(standings1, standings2).forEach { standings ->
                assertEquals(2, standings.size)
                assertEquals(1, standings[0].rank)
                assertEquals(user1Id, standings[0].user.id)
                assertEquals(288f, standings[0].points)
                assertEquals(List(5) { PredictionStatus.CORRECT }, standings[0].lastPredictions)
                assertEquals(2, standings[1].rank)
                assertEquals(user2Id, standings[1].user.id)
                assertEquals(96f, standings[1].points)
                assertEquals(List(5) { PredictionStatus.PARTIAL }, standings[1].lastPredictions)
            }
        }

        @Test
        @Order(14)
        fun `should create new matches, for quarterfinals`() {
            val newQualifiedTeams = mutableListOf<UUID>()
            val newMatchesRequests = codesForQuarterfinals.map {
                val homeTeam: UUID = qualifiedTeams.otherRandom(*newQualifiedTeams.toTypedArray())
                newQualifiedTeams.add(homeTeam)
                val awayTeam = qualifiedTeams.otherRandom(*newQualifiedTeams.toTypedArray())
                newQualifiedTeams.add(awayTeam)
                CreateMatchRequest(
                    code = it, homeTeam = homeTeam, awayTeam = awayTeam,
                    stage = MatchStage.QUARTERFINALS, startedAt = ZonedDateTime.now().plusDays(1),
                )
            }

            qualifiedTeams = newQualifiedTeams
            grondona.createMatches(request = CreateMatchesRequest(matches = newMatchesRequests))
        }

        @Test
        @Order(15)
        fun `should submit predictions for every match in the quarterfinals, for both users`() {
            grondona.submitMatchPredictionsToGroup(
                user1Token,
                groupId,
                grondona.matches.filter { it.code in codesForQuarterfinals }.map {
                    SubmitMatchPredictionRequest(matchId = it.id, homeGoals = 0, awayGoals = 0)
                },
                withAssertions = true
            )

            grondona.submitMatchPredictionsToGroup(
                user2Token,
                groupId,
                grondona.matches.filter { it.code in codesForQuarterfinals }.map {
                    SubmitMatchPredictionRequest(matchId = it.id, homeGoals = 1, awayGoals = 1)
                },
                withAssertions = true
            )
        }

        @Test
        @Order(16)
        fun `should receive updates for every existing match in the quarterfinals`() {
            every { matchClient.getMatches(any()) } returns grondona.matches.filter { it.code in codesForQuarterfinals }.map {
                ExternalMatch(
                    home = it.homeCode, away = it.awayCode, stage = it.stage, group = it.group,
                    homeGoals = 1, awayGoals = 0, status = MatchStatus.FINISHED, substatus = "FIN",
                    startedAt = ZonedDateTime.now().minusMinutes(118), finishedAt = ZonedDateTime.now()
                )
            }

            matchScheduler.updateMatches()
        }

        @Test
        @Order(17)
        fun `should check standings after the quarterfinals, for both users`() {
            val standings1 = grondona.fetchGroup(user1Token, groupId).standings
            val standings2 = grondona.fetchGroup(user2Token, groupId).standings

            listOf(standings1, standings2).forEach { standings ->
                assertEquals(2, standings.size)
                assertEquals(1, standings[0].rank)
                assertEquals(user1Id, standings[0].user.id)
                assertEquals(288f, standings[0].points)
                assertEquals(listOf(PredictionStatus.CORRECT) + List(4) { PredictionStatus.INCORRECT }, standings[0].lastPredictions)
                assertEquals(2, standings[1].rank)
                assertEquals(user2Id, standings[1].user.id)
                assertEquals(96f, standings[1].points)
                assertEquals(listOf(PredictionStatus.PARTIAL) + List(4) { PredictionStatus.INCORRECT }, standings[1].lastPredictions)
            }
        }

        @Test
        @Order(18)
        fun `should create new matches, for the semifinals`() {
            val newQualifiedTeams = mutableListOf<UUID>()
            val newMatchesRequests = codesForSemifinals.map {
                val homeTeam: UUID = qualifiedTeams.otherRandom(*newQualifiedTeams.toTypedArray())
                newQualifiedTeams.add(homeTeam)
                val awayTeam = qualifiedTeams.otherRandom(*newQualifiedTeams.toTypedArray())
                newQualifiedTeams.add(awayTeam)
                CreateMatchRequest(
                    code = it, homeTeam = homeTeam, awayTeam = awayTeam,
                    stage = MatchStage.SEMIFINALS, startedAt = ZonedDateTime.now().plusDays(1),
                )
            }

            qualifiedTeams = newQualifiedTeams
            grondona.createMatches(request = CreateMatchesRequest(matches = newMatchesRequests))
        }

        @Test
        @Order(19)
        fun `should submit predictions for every match in the semifinals, for both users`() {
            grondona.submitMatchPredictionsToGroup(
                user1Token,
                groupId,
                grondona.matches.filter { it.code in codesForSemifinals }.map {
                    SubmitMatchPredictionRequest(matchId = it.id, homeGoals = 5, awayGoals = 0)
                },
                withAssertions = true
            )

            grondona.submitMatchPredictionsToGroup(
                user2Token,
                groupId,
                grondona.matches.filter { it.code in codesForSemifinals }.map {
                    SubmitMatchPredictionRequest(matchId = it.id, homeGoals = 5, awayGoals = 1)
                },
                withAssertions = true
            )
        }

        @Test
        @Order(20)
        fun `should receive updates for every existing match in the semifinals`() {
            every { matchClient.getMatches(any()) } returns grondona.matches.filter { it.code in codesForSemifinals }.map {
                ExternalMatch(
                    home = it.homeCode, away = it.awayCode, stage = it.stage, group = it.group,
                    homeGoals = 5, awayGoals = 0, status = MatchStatus.FINISHED, substatus = "FIN",
                    startedAt = ZonedDateTime.now().minusMinutes(118), finishedAt = ZonedDateTime.now()
                )
            }

            matchScheduler.updateMatches()
        }

        @Test
        @Order(21)
        fun `should check standings after the semifinals, for both users`() {
            val standings1 = grondona.fetchGroup(user1Token, groupId).standings
            val standings2 = grondona.fetchGroup(user2Token, groupId).standings

            listOf(standings1, standings2).forEach { standings ->
                assertEquals(2, standings.size)
                assertEquals(1, standings[0].rank)
                assertEquals(user1Id, standings[0].user.id)
                assertEquals(298f, standings[0].points)
                assertEquals(List(3) { PredictionStatus.INCORRECT } + List(2) { PredictionStatus.BONUS }, standings[0].lastPredictions)
                assertEquals(2, standings[1].rank)
                assertEquals(user2Id, standings[1].user.id)
                assertEquals(98f, standings[1].points)
                assertEquals(List(3) { PredictionStatus.INCORRECT } + List(2) { PredictionStatus.PARTIAL }, standings[1].lastPredictions)
            }
        }

        @Test
        @Order(22)
        fun `should create new matches, for the last round`() {
            val newQualifiedTeams = mutableListOf<UUID>()
            val newMatchesRequests = codesForLastRound.mapIndexed { idx, code ->
                val homeTeam: UUID = qualifiedTeams.otherRandom(*newQualifiedTeams.toTypedArray())
                newQualifiedTeams.add(homeTeam)
                val awayTeam = qualifiedTeams.otherRandom(*newQualifiedTeams.toTypedArray())
                newQualifiedTeams.add(awayTeam)
                CreateMatchRequest(
                    code = code, homeTeam = homeTeam, awayTeam = awayTeam, stage = if (idx == 0) MatchStage.THIRD_PLACE else MatchStage.FINAL,
                    startedAt = ZonedDateTime.now().plusDays(1), hasMultiplier = true,
                )
            }

            qualifiedTeams = newQualifiedTeams
            grondona.createMatches(request = CreateMatchesRequest(matches = newMatchesRequests))
        }

        @Test
        @Order(23)
        fun `should submit predictions for every match in the last round, for both users`() {
            grondona.submitMatchPredictionsToGroup(
                user1Token,
                groupId,
                grondona.matches.filter { it.code in codesForLastRound }.map {
                    SubmitMatchPredictionRequest(matchId = it.id, homeGoals = 1, awayGoals = 0)
                },
                withAssertions = true
            )

            grondona.submitMatchPredictionsToGroup(
                user2Token,
                groupId,
                grondona.matches.filter { it.code in codesForLastRound }.map {
                    SubmitMatchPredictionRequest(matchId = it.id, homeGoals = 3, awayGoals = 1)
                },
                withAssertions = true
            )
        }

        @Test
        @Order(24)
        fun `should receive updates for every existing match in the last round`() {
            every { matchClient.getMatches(any()) } returns grondona.matches.filter { it.code in codesForLastRound }.map {
                ExternalMatch(
                    home = it.homeCode, away = it.awayCode, stage = it.stage, group = it.group,
                    homeGoals = 3, awayGoals = 1, status = MatchStatus.FINISHED, substatus = "FIN",
                    startedAt = ZonedDateTime.now().minusMinutes(118), finishedAt = ZonedDateTime.now()
                )
            }

            matchScheduler.updateMatches()
        }

        @Test
        @Order(25)
        fun `should check standings after the last round, for both users`() {

            val standings1 = grondona.fetchGroup(user1Token, groupId).standings
            val standings2 = grondona.fetchGroup(user2Token, groupId).standings

            listOf(standings1, standings2).forEach { standings ->
                val lastPredictions1 =
                    listOf(PredictionStatus.INCORRECT) + List(2) { PredictionStatus.BONUS } + List(2) { PredictionStatus.PARTIAL }
                assertEquals(2, standings.size)
                assertEquals(1, standings[0].rank)
                assertEquals(user1Id, standings[0].user.id)
                assertEquals(301f, standings[0].points)
                assertEquals(lastPredictions1, standings[0].lastPredictions)

                val lastPredictions2 =
                    listOf(PredictionStatus.INCORRECT) + List(2) { PredictionStatus.PARTIAL } + List(2) { PredictionStatus.CORRECT }
                assertEquals(2, standings[1].rank)
                assertEquals(user2Id, standings[1].user.id)
                assertEquals(107f, standings[1].points)
                assertEquals(lastPredictions2, standings[1].lastPredictions)
            }
        }

        @Test
        @Order(26)
        fun `should update tournament to set awards' winners`() {
            grondona.updateTournament(
                request = UpdateTournamentRequest(
                    awards = Awards(
                        champion = champion,
                        topScorer = topScorer,
                        bestPlayer = bestPlayer,
                        bestGoalkeeper = bestGoalkeeper,
                        bestYoungPlayer = bestYoungPlayer
                    )
                )
            )
        }

        @Test
        @Order(27)
        fun `should check standings after the awards, for both users`() {
            val standings1 = grondona.fetchGroup(user1Token, groupId).standings
            val standings2 = grondona.fetchGroup(user2Token, groupId).standings

            listOf(standings1, standings2).forEach { standings ->
                assertEquals(2, standings.size)
                assertEquals(1, standings[0].rank)
                assertEquals(user1Id, standings[0].user.id)
                assertEquals(351f, standings[0].points)
                assertEquals(
                    listOf(PredictionStatus.INCORRECT) + List(2) { PredictionStatus.BONUS } + List(2) { PredictionStatus.PARTIAL },
                    standings[0].lastPredictions
                )
                assertEquals(2, standings[1].rank)
                assertEquals(user2Id, standings[1].user.id)
                assertEquals(140f, standings[1].points)
                assertEquals(
                    listOf(PredictionStatus.INCORRECT) + List(2) { PredictionStatus.PARTIAL } + List(2) { PredictionStatus.CORRECT },
                    standings[1].lastPredictions
                )
            }
        }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    @TestMethodOrder(MethodOrderer.OrderAnnotation::class)
    inner class AutoWorldCupTests {

        private var qualifiedTeams = mutableListOf<String>()

        private val codesToDates = mapOf(
            "73" to ZonedDateTime.parse("2026-06-28T12:00:00-07:00"),
            "74" to ZonedDateTime.parse("2026-06-29T16:30:00-04:00"),
            "75" to ZonedDateTime.parse("2026-06-29T19:00:00-06:00"),
            "76" to ZonedDateTime.parse("2026-06-29T12:00:00-05:00"),
            "77" to ZonedDateTime.parse("2026-06-30T17:00:00-04:00"),
            "78" to ZonedDateTime.parse("2026-06-30T12:00:00-05:00"),
            "79" to ZonedDateTime.parse("2026-06-30T19:00:00-06:00"),
            "80" to ZonedDateTime.parse("2026-07-01T12:00:00-04:00"),
            "81" to ZonedDateTime.parse("2026-07-01T17:00:00-07:00"),
            "82" to ZonedDateTime.parse("2026-07-01T13:00:00-07:00"),
            "83" to ZonedDateTime.parse("2026-07-02T19:00:00-04:00"),
            "84" to ZonedDateTime.parse("2026-07-02T12:00:00-07:00"),
            "85" to ZonedDateTime.parse("2026-07-02T20:00:00-07:00"),
            "86" to ZonedDateTime.parse("2026-07-03T18:00:00-04:00"),
            "87" to ZonedDateTime.parse("2026-07-03T20:30:00-05:00"),
            "88" to ZonedDateTime.parse("2026-07-03T13:00:00-05:00"),
            "89" to ZonedDateTime.parse("2026-07-04T17:00:00-04:00"),
            "90" to ZonedDateTime.parse("2026-07-04T12:00:00-05:00"),
            "91" to ZonedDateTime.parse("2026-07-05T16:00:00-04:00"),
            "92" to ZonedDateTime.parse("2026-07-05T18:00:00-06:00"),
            "93" to ZonedDateTime.parse("2026-07-06T14:00:00-05:00"),
            "94" to ZonedDateTime.parse("2026-07-06T17:00:00-07:00"),
            "95" to ZonedDateTime.parse("2026-07-07T12:00:00-04:00"),
            "96" to ZonedDateTime.parse("2026-07-07T13:00:00-07:00"),
            "97" to ZonedDateTime.parse("2026-07-09T16:00:00-04:00"),
            "98" to ZonedDateTime.parse("2026-07-10T12:00:00-07:00"),
            "99" to ZonedDateTime.parse("2026-07-11T17:00:00-04:00"),
            "100" to ZonedDateTime.parse("2026-07-11T20:00:00-05:00"),
            "101" to ZonedDateTime.parse("2026-07-14T14:00:00-05:00"),
            "102" to ZonedDateTime.parse("2026-07-15T15:00:00-04:00"),
            "103" to ZonedDateTime.parse("2026-07-18T17:00:00-04:00"),
            "104" to ZonedDateTime.parse("2026-07-19T15:00:00-04:00"),
        )

        private val externalMatches = mutableListOf<ExternalMatch>()

        @BeforeAll
        fun setUp() {
            testDatabaseCleaner.cleanAll()
            clearMocks(matchClient)

            grondona = GrondonaClient(mockMvc, objectMapper).withRepositories(userRepository, tournamentRepository)
            grondona.init()
        }

        @Test
        @Order(1)
        fun `should create a new user, a new group and join it`() {
            user1Id = grondona.adminId
            user1Token = grondona.adminToken

            val (newId, newToken) = grondona.createUser()
            user2Id = newId
            user2Token = newToken

            groupId = grondona.createGroup()
            grondona.joinGroup(user2Token, groupId)
        }

        @Test
        @Order(2)
        fun `should submit predictions for every match, for both users`() {
            grondona.submitMatchPredictionsToGroup(user1Token, groupId, grondona.matches.map {
                SubmitMatchPredictionRequest(matchId = it.id, homeGoals = 0, awayGoals = 0)
            }, withAssertions = true)

            grondona.submitMatchPredictionsToGroup(user2Token, groupId, grondona.matches.map {
                SubmitMatchPredictionRequest(matchId = it.id, homeGoals = 1, awayGoals = 1)
            }, withAssertions = true)
        }

        @Test
        @Order(3)
        fun `should submit predictions for every award, for both users`() {
            champion = grondona.teams.random().id
            topScorer = grondona.playerIds.random()
            bestPlayer = grondona.playerIds.random()
            bestGoalkeeper = grondona.goalkeeperIds.random()
            bestYoungPlayer = grondona.youngsterIds.random()

            grondona.submitAwardPredictionsToGroup(
                user1Token, groupId, SubmitAwardPredictionRequest(
                    champions = listOf(champion),
                    topScorers = listOf(topScorer),
                    bestPlayers = listOf(bestPlayer),
                    bestGoalkeepers = listOf(bestGoalkeeper),
                    bestYoungPlayers = listOf(bestYoungPlayer),
                )
            )

            val extraChampion = grondona.teams.map { team -> team.id }.otherRandom(champion)
            val extraTopScorer = grondona.playerIds.otherRandom(topScorer)
            val extraBestPlayer = grondona.playerIds.otherRandom(bestPlayer)
            val extraBestGoalkeeper = grondona.goalkeeperIds.otherRandom(bestGoalkeeper)
            val extraBestYoungPlayer = grondona.youngsterIds.otherRandom(bestYoungPlayer)
            grondona.submitAwardPredictionsToGroup(
                user2Token, groupId, SubmitAwardPredictionRequest(
                    champions = listOf(champion, extraChampion),
                    topScorers = listOf(topScorer, extraTopScorer),
                    bestPlayers = listOf(bestPlayer, extraBestPlayer),
                    bestGoalkeepers = listOf(bestGoalkeeper, extraBestGoalkeeper),
                    bestYoungPlayers = listOf(bestYoungPlayer, extraBestYoungPlayer),
                )
            )
        }

        @Test
        @Order(4)
        fun `should receive updates for every existing match in group stage, including new matches for round of 32`() {
            val matchesToUpdate = grondona.matches
            qualifiedTeams = mutableListOf()

            val newExternalMatches = matchesToUpdate.map {
                ExternalMatch(
                    home = it.homeCode, away = it.awayCode, stage = it.stage, group = it.group,
                    homeGoals = 0, awayGoals = 0, status = MatchStatus.FINISHED, substatus = "FIN",
                    startedAt = ZonedDateTime.now().minusHours(2), finishedAt = ZonedDateTime.now()
                )
            } + codesForLast32.map { code ->
                val homeTeam = grondona.teams.map { team -> team.code }.otherRandom(*qualifiedTeams.toTypedArray())
                qualifiedTeams.add(homeTeam)
                val awayTeam = grondona.teams.map { team -> team.code }.otherRandom(*qualifiedTeams.toTypedArray())
                qualifiedTeams.add(awayTeam)

                ExternalMatch(
                    home = homeTeam, away = awayTeam, stage = MatchStage.ROUND_OF_32, homeGoals = 0, awayGoals = 0,
                    status = MatchStatus.NOT_STARTED, startedAt = codesToDates[code]!!, homeOdds = 0f, drawOdds = 0f, awayOdds = 0f
                )
            }

            externalMatches.addAll(newExternalMatches)
            val externalMatchesChunks = externalMatches.chunked(12)

            every { matchClient.getMatches(any()) } returnsMany externalMatchesChunks
            externalMatchesChunks.forEach { _ -> matchScheduler.updateMatches() }
        }

        @Test
        @Order(5)
        fun `should check standings after group stage, for both users`() {
            val standings1 = grondona.fetchGroup(user1Token, groupId).standings
            val standings2 = grondona.fetchGroup(user2Token, groupId).standings

            listOf(standings1, standings2).forEach { standings ->
                assertEquals(2, standings.size)
                assertEquals(1, standings[0].rank)
                assertEquals(user1Id, standings[0].user.id)
                assertEquals(216f, standings[0].points)
                assertEquals(List(5) { PredictionStatus.CORRECT }, standings[0].lastPredictions)
                assertEquals(2, standings[1].rank)
                assertEquals(user2Id, standings[1].user.id)
                assertEquals(72f, standings[1].points)
                assertEquals(List(5) { PredictionStatus.PARTIAL }, standings[1].lastPredictions)
            }
        }

        @Test
        @Order(6)
        fun `should submit predictions for every match in the round of 32, for both users`() {
            grondona.submitMatchPredictionsToGroup(user1Token, groupId, grondona.matches.filter { it.code in codesForLast32 }.map {
                SubmitMatchPredictionRequest(matchId = it.id, homeGoals = 0, awayGoals = 0)
            }, withAssertions = true)

            grondona.submitMatchPredictionsToGroup(user2Token, groupId, grondona.matches.filter { it.code in codesForLast32 }.map {
                SubmitMatchPredictionRequest(matchId = it.id, homeGoals = 1, awayGoals = 1)
            }, withAssertions = true)
        }

        @Test
        @Order(7)
        fun `should receive updates for every existing match in round of 32, including new matches for round of 16`() {
            val matchesToUpdate = grondona.matches.filter { it.code in codesForLast32 }
            val newQualifiedTeams = mutableListOf<String>()
            qualifiedTeams = mutableListOf()

            val newExternalMatches = matchesToUpdate.map {
                ExternalMatch(
                    home = it.homeCode, away = it.awayCode, stage = it.stage, group = it.group,
                    homeGoals = 0, awayGoals = 0, status = MatchStatus.FINISHED, substatus = "FIN",
                    startedAt = ZonedDateTime.now().minusHours(2), finishedAt = ZonedDateTime.now()
                )
            } + codesForLast16.map { code ->
                val homeTeam = grondona.teams.map { team -> team.code }.otherRandom(*qualifiedTeams.toTypedArray())
                newQualifiedTeams.add(homeTeam)
                val awayTeam = grondona.teams.map { team -> team.code }.otherRandom(*qualifiedTeams.toTypedArray())
                newQualifiedTeams.add(awayTeam)

                ExternalMatch(
                    home = homeTeam, away = awayTeam, stage = MatchStage.ROUND_OF_16, homeGoals = 0, awayGoals = 0,
                    status = MatchStatus.NOT_STARTED, startedAt = codesToDates[code]!!, homeOdds = 0f, drawOdds = 0f, awayOdds = 0f
                )
            }

            qualifiedTeams = newQualifiedTeams
            externalMatches.addAll(newExternalMatches)

            every { matchClient.getMatches(any()) } returns externalMatches
            matchScheduler.updateMatches()
        }

        @Test
        @Order(8)
        fun `should check standings after round of 32, for both users`() {
            val standings1 = grondona.fetchGroup(user1Token, groupId).standings
            val standings2 = grondona.fetchGroup(user2Token, groupId).standings

            listOf(standings1, standings2).forEach { standings ->
                assertEquals(2, standings.size)
                assertEquals(1, standings[0].rank)
                assertEquals(user1Id, standings[0].user.id)
                assertEquals(264f, standings[0].points)
                assertEquals(List(5) { PredictionStatus.CORRECT }, standings[0].lastPredictions)
                assertEquals(2, standings[1].rank)
                assertEquals(user2Id, standings[1].user.id)
                assertEquals(88f, standings[1].points)
                assertEquals(List(5) { PredictionStatus.PARTIAL }, standings[1].lastPredictions)
            }
        }

        @Test
        @Order(9)
        fun `should submit predictions for every match in the round of 16, for both users`() {
            grondona.submitMatchPredictionsToGroup(user1Token, groupId, grondona.matches.filter { it.code in codesForLast16 }.map {
                SubmitMatchPredictionRequest(matchId = it.id, homeGoals = 0, awayGoals = 0)
            }, withAssertions = true)

            grondona.submitMatchPredictionsToGroup(user2Token, groupId, grondona.matches.filter { it.code in codesForLast16 }.map {
                SubmitMatchPredictionRequest(matchId = it.id, homeGoals = 1, awayGoals = 1)
            }, withAssertions = true)
        }

        @Test
        @Order(10)
        fun `should receive updates for every existing match in round of 16, including new matches for quarterfinals`() {
            val matchesToUpdate = grondona.matches.filter { it.code in codesForLast16 }
            val newQualifiedTeams = mutableListOf<String>()
            qualifiedTeams = mutableListOf()

            val newExternalMatches = matchesToUpdate.map {
                ExternalMatch(
                    home = it.homeCode, away = it.awayCode, stage = it.stage, group = it.group,
                    homeGoals = 0, awayGoals = 0, status = MatchStatus.FINISHED, substatus = "FIN",
                    startedAt = ZonedDateTime.now().minusHours(2), finishedAt = ZonedDateTime.now()
                )
            } + codesForQuarterfinals.map { code ->
                val homeTeam = grondona.teams.map { team -> team.code }.otherRandom(*qualifiedTeams.toTypedArray())
                newQualifiedTeams.add(homeTeam)
                val awayTeam = grondona.teams.map { team -> team.code }.otherRandom(*qualifiedTeams.toTypedArray())
                newQualifiedTeams.add(awayTeam)

                ExternalMatch(
                    home = homeTeam, away = awayTeam, stage = MatchStage.QUARTERFINALS, homeGoals = 0, awayGoals = 0,
                    status = MatchStatus.NOT_STARTED, startedAt = codesToDates[code]!!, homeOdds = 0f, drawOdds = 0f, awayOdds = 0f
                )
            }

            qualifiedTeams = newQualifiedTeams
            externalMatches.addAll(newExternalMatches)

            every { matchClient.getMatches(any()) } returns externalMatches
            matchScheduler.updateMatches()
        }

        @Test
        @Order(11)
        fun `should check standings after round of 16, for both users`() {
            val standings1 = grondona.fetchGroup(user1Token, groupId).standings
            val standings2 = grondona.fetchGroup(user2Token, groupId).standings

            listOf(standings1, standings2).forEach { standings ->
                assertEquals(2, standings.size)
                assertEquals(1, standings[0].rank)
                assertEquals(user1Id, standings[0].user.id)
                assertEquals(288f, standings[0].points)
                assertEquals(List(5) { PredictionStatus.CORRECT }, standings[0].lastPredictions)
                assertEquals(2, standings[1].rank)
                assertEquals(user2Id, standings[1].user.id)
                assertEquals(96f, standings[1].points)
                assertEquals(List(5) { PredictionStatus.PARTIAL }, standings[1].lastPredictions)
            }
        }

        @Test
        @Order(12)
        fun `should submit predictions for every match in the quarterfinals, for both users`() {
            grondona.submitMatchPredictionsToGroup(
                user1Token,
                groupId,
                grondona.matches.filter { it.code in codesForQuarterfinals }.map {
                    SubmitMatchPredictionRequest(matchId = it.id, homeGoals = 0, awayGoals = 0)
                },
                withAssertions = true
            )

            grondona.submitMatchPredictionsToGroup(
                user2Token,
                groupId,
                grondona.matches.filter { it.code in codesForQuarterfinals }.map {
                    SubmitMatchPredictionRequest(matchId = it.id, homeGoals = 1, awayGoals = 1)
                },
                withAssertions = true
            )
        }

        @Test
        @Order(13)
        fun `should receive updates for every existing match in the quarterfinals, including new matches for semifinals`() {
            val matchesToUpdate = grondona.matches.filter { it.code in codesForQuarterfinals }
            val newQualifiedTeams = mutableListOf<String>()
            qualifiedTeams = mutableListOf()

            val newExternalMatches = matchesToUpdate.map {
                ExternalMatch(
                    home = it.homeCode, away = it.awayCode, stage = it.stage, group = it.group,
                    homeGoals = 1, awayGoals = 0, status = MatchStatus.FINISHED, substatus = "FIN",
                    startedAt = ZonedDateTime.now().minusHours(2), finishedAt = ZonedDateTime.now()
                )
            } + codesForSemifinals.map { code ->
                val homeTeam = grondona.teams.map { team -> team.code }.otherRandom(*qualifiedTeams.toTypedArray())
                newQualifiedTeams.add(homeTeam)
                val awayTeam = grondona.teams.map { team -> team.code }.otherRandom(*qualifiedTeams.toTypedArray())
                newQualifiedTeams.add(awayTeam)

                ExternalMatch(
                    home = homeTeam, away = awayTeam, stage = MatchStage.SEMIFINALS, homeGoals = 0, awayGoals = 0,
                    status = MatchStatus.NOT_STARTED, startedAt = codesToDates[code]!!, homeOdds = 0f, drawOdds = 0f, awayOdds = 0f
                )
            }

            qualifiedTeams = newQualifiedTeams
            externalMatches.addAll(newExternalMatches)

            every { matchClient.getMatches(any()) } returns externalMatches
            matchScheduler.updateMatches()
        }

        @Test
        @Order(14)
        fun `should check standings after the quarterfinals, for both users`() {
            val standings1 = grondona.fetchGroup(user1Token, groupId).standings
            val standings2 = grondona.fetchGroup(user2Token, groupId).standings

            listOf(standings1, standings2).forEach { standings ->
                assertEquals(2, standings.size)
                assertEquals(1, standings[0].rank)
                assertEquals(user1Id, standings[0].user.id)
                assertEquals(288f, standings[0].points)
                assertEquals(listOf(PredictionStatus.CORRECT) + List(4) { PredictionStatus.INCORRECT }, standings[0].lastPredictions)
                assertEquals(2, standings[1].rank)
                assertEquals(user2Id, standings[1].user.id)
                assertEquals(96f, standings[1].points)
                assertEquals(listOf(PredictionStatus.PARTIAL) + List(4) { PredictionStatus.INCORRECT }, standings[1].lastPredictions)
            }
        }

        @Test
        @Order(15)
        fun `should submit predictions for every match in the semifinals, for both users`() {
            grondona.submitMatchPredictionsToGroup(
                user1Token,
                groupId,
                grondona.matches.filter { it.code in codesForSemifinals }.map {
                    SubmitMatchPredictionRequest(matchId = it.id, homeGoals = 5, awayGoals = 0)
                },
                withAssertions = true
            )

            grondona.submitMatchPredictionsToGroup(
                user2Token,
                groupId,
                grondona.matches.filter { it.code in codesForSemifinals }.map {
                    SubmitMatchPredictionRequest(matchId = it.id, homeGoals = 5, awayGoals = 1)
                },
                withAssertions = true
            )
        }

        @Test
        @Order(16)
        fun `should receive updates for every existing match in the semifinals, including new matches for the last round`() {
            val matchesToUpdate = grondona.matches.filter { it.code in codesForQuarterfinals }
            val newQualifiedTeams = mutableListOf<String>()
            qualifiedTeams = mutableListOf()

            val newExternalMatches = matchesToUpdate.map {
                ExternalMatch(
                    home = it.homeCode, away = it.awayCode, stage = it.stage, group = it.group,
                    homeGoals = 5, awayGoals = 0, status = MatchStatus.FINISHED, substatus = "FIN",
                    startedAt = ZonedDateTime.now().minusHours(2), finishedAt = ZonedDateTime.now()
                )
            } + codesForSemifinals.map { code ->
                val homeTeam = grondona.teams.map { team -> team.code }.otherRandom(*qualifiedTeams.toTypedArray())
                newQualifiedTeams.add(homeTeam)
                val awayTeam = grondona.teams.map { team -> team.code }.otherRandom(*qualifiedTeams.toTypedArray())
                newQualifiedTeams.add(awayTeam)

                ExternalMatch(
                    home = homeTeam, away = awayTeam, stage = MatchStage.SEMIFINALS, homeGoals = 0, awayGoals = 0,
                    status = MatchStatus.NOT_STARTED, startedAt = codesToDates[code]!!, homeOdds = 0f, drawOdds = 0f, awayOdds = 0f
                )
            }

            qualifiedTeams = newQualifiedTeams
            externalMatches.addAll(newExternalMatches)
            every { matchClient.getMatches(any()) } returns externalMatches
            matchScheduler.updateMatches()
        }

        @Test
        @Order(17)
        fun `should check standings after the semifinals, for both users`() {
            val standings1 = grondona.fetchGroup(user1Token, groupId).standings
            val standings2 = grondona.fetchGroup(user2Token, groupId).standings

            listOf(standings1, standings2).forEach { standings ->
                assertEquals(2, standings.size)
                assertEquals(1, standings[0].rank)
                assertEquals(user1Id, standings[0].user.id)
                assertEquals(298f, standings[0].points)
                assertEquals(List(3) { PredictionStatus.INCORRECT } + List(2) { PredictionStatus.BONUS }, standings[0].lastPredictions)
                assertEquals(2, standings[1].rank)
                assertEquals(user2Id, standings[1].user.id)
                assertEquals(98f, standings[1].points)
                assertEquals(List(3) { PredictionStatus.INCORRECT } + List(2) { PredictionStatus.PARTIAL }, standings[1].lastPredictions)
            }
        }

        @Test
        @Order(18)
        fun `should submit predictions for every match in the last round, for both users`() {
            grondona.submitMatchPredictionsToGroup(
                user1Token,
                groupId,
                grondona.matches.filter { it.code in codesForLastRound }.map {
                    SubmitMatchPredictionRequest(matchId = it.id, homeGoals = 1, awayGoals = 0)
                },
                withAssertions = true
            )

            grondona.submitMatchPredictionsToGroup(
                user2Token,
                groupId,
                grondona.matches.filter { it.code in codesForLastRound }.map {
                    SubmitMatchPredictionRequest(matchId = it.id, homeGoals = 3, awayGoals = 1)
                },
                withAssertions = true
            )
        }

        @Test
        @Order(19)
        fun `should receive updates for every existing match in the last round`() {
            val newExternalMatches = grondona.matches.filter { it.code in codesForLastRound }.map {
                ExternalMatch(
                    home = it.homeCode, away = it.awayCode, stage = it.stage, group = it.group,
                    homeGoals = 3, awayGoals = 1, status = MatchStatus.FINISHED, substatus = "FIN",
                    startedAt = ZonedDateTime.now().minusMinutes(118), finishedAt = ZonedDateTime.now()
                )
            }

            externalMatches.addAll(newExternalMatches)
            every { matchClient.getMatches(any()) } returns externalMatches
            matchScheduler.updateMatches()
        }

        @Test
        @Order(20)
        fun `should check standings after the last round, for both users`() {

            val standings1 = grondona.fetchGroup(user1Token, groupId).standings
            val standings2 = grondona.fetchGroup(user2Token, groupId).standings

            listOf(standings1, standings2).forEach { standings ->
                val lastPredictions1 =
                    listOf(PredictionStatus.INCORRECT) + List(2) { PredictionStatus.BONUS } + List(2) { PredictionStatus.PARTIAL }
                assertEquals(2, standings.size)
                assertEquals(1, standings[0].rank)
                assertEquals(user1Id, standings[0].user.id)
                assertEquals(301f, standings[0].points)
                assertEquals(lastPredictions1, standings[0].lastPredictions)

                val lastPredictions2 =
                    listOf(PredictionStatus.INCORRECT) + List(2) { PredictionStatus.PARTIAL } + List(2) { PredictionStatus.CORRECT }
                assertEquals(2, standings[1].rank)
                assertEquals(user2Id, standings[1].user.id)
                assertEquals(107f, standings[1].points)
                assertEquals(lastPredictions2, standings[1].lastPredictions)
            }
        }

        @Test
        @Order(21)
        fun `should update tournament to set awards' winners`() {
            grondona.updateTournament(
                request = UpdateTournamentRequest(
                    awards = Awards(
                        champion = champion,
                        topScorer = topScorer,
                        bestPlayer = bestPlayer,
                        bestGoalkeeper = bestGoalkeeper,
                        bestYoungPlayer = bestYoungPlayer
                    )
                )
            )
        }

        @Test
        @Order(22)
        fun `should check standings after the awards, for both users`() {
            val standings1 = grondona.fetchGroup(user1Token, groupId).standings
            val standings2 = grondona.fetchGroup(user2Token, groupId).standings

            listOf(standings1, standings2).forEach { standings ->
                assertEquals(2, standings.size)
                assertEquals(1, standings[0].rank)
                assertEquals(user1Id, standings[0].user.id)
                assertEquals(351f, standings[0].points)
                assertEquals(
                    listOf(PredictionStatus.INCORRECT) + List(2) { PredictionStatus.BONUS } + List(2) { PredictionStatus.PARTIAL },
                    standings[0].lastPredictions
                )
                assertEquals(2, standings[1].rank)
                assertEquals(user2Id, standings[1].user.id)
                assertEquals(140f, standings[1].points)
                assertEquals(
                    listOf(PredictionStatus.INCORRECT) + List(2) { PredictionStatus.PARTIAL } + List(2) { PredictionStatus.CORRECT },
                    standings[1].lastPredictions
                )
            }
        }
    }
}
