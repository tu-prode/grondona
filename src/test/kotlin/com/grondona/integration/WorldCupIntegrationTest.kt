package com.grondona.integration

import com.fasterxml.jackson.databind.ObjectMapper
import com.grondona.client.MatchClient
import com.grondona.integration.utils.GrondonaClient
import com.grondona.model.Awards
import com.grondona.model.ExternalMatch
import com.grondona.model.PredictionStatus
import com.grondona.model.dto.request.CreateMatchRequest
import com.grondona.model.dto.request.CreateMatchesRequest
import com.grondona.model.dto.request.SubmitAwardPredictionRequest
import com.grondona.model.dto.request.SubmitMatchPredictionRequest
import com.grondona.model.dto.request.UpdateTournamentRequest
import com.grondona.otherRandom
import com.grondona.repository.TournamentRepository
import com.grondona.repository.UserRepository
import com.grondona.scheduler.MatchScheduler
import com.grondona.service.engine.WorldCupEngine
import com.ninjasquad.springmockk.MockkBean
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
@ActiveProfiles("test")
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
    lateinit var matchScheduler: MatchScheduler

    @MockkBean
    lateinit var matchClient: MatchClient

    lateinit var grondona: GrondonaClient

    @BeforeAll
    fun setUp() {
        userRepository.deleteAll()
        tournamentRepository.deleteAll()

        grondona = GrondonaClient(mockMvc, objectMapper).withRepositories(userRepository, tournamentRepository)
        grondona.init()
    }

    @AfterAll
    fun tearDown() {
        userRepository.deleteAll()
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    @TestMethodOrder(MethodOrderer.OrderAnnotation::class)
    inner class FullWorldcupTests {

        private lateinit var user1Id: UUID
        private lateinit var user2Id: UUID
        private lateinit var user1Token: String
        private lateinit var user2Token: String

        private lateinit var groupId: UUID

        private var qualifiedTeams = mutableListOf<UUID>()

        private lateinit var champion: UUID
        private lateinit var topScorer: UUID
        private lateinit var bestPlayer: UUID
        private lateinit var bestGoalkeeper: UUID
        private lateinit var bestYoungPlayer: UUID


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
            champion = grondona.teamIds.random()
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

            val extraChampion = grondona.teamIds.otherRandom(champion)
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
                        code = it.code, home = it.homeCode, away = it.awayCode, status = "COMPLETED",
                        homeGoals = 0, awayGoals = 0, half = 2, minutes = 93,
                        startedAt = ZonedDateTime.now().minusMinutes(118), endedAt = ZonedDateTime.now()
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
            val newMatchesRequests = WorldCupEngine.RO32_MATCHES_CODE.map {
                val homeTeam: UUID = grondona.teamIds.otherRandom(*qualifiedTeams.toTypedArray())
                qualifiedTeams.add(homeTeam)
                val awayTeam = grondona.teamIds.otherRandom(*qualifiedTeams.toTypedArray())
                qualifiedTeams.add(awayTeam)
                CreateMatchRequest(code = it, homeTeam = homeTeam, awayTeam = awayTeam, startedAt = ZonedDateTime.now().plusDays(1))
            }

            grondona.createMatches(request = CreateMatchesRequest(matches = newMatchesRequests))
        }

        @Test
        @Order(7)
        fun `should submit predictions for every match in the round of 32, for both users`() {
            grondona.submitMatchPredictionsToGroup(user1Token, groupId, grondona.matches.filter { it.code in WorldCupEngine.RO32_MATCHES_CODE }.map {
                SubmitMatchPredictionRequest(matchId = it.id, homeGoals = 0, awayGoals = 0)
            }, withAssertions = true)

            grondona.submitMatchPredictionsToGroup(user2Token, groupId, grondona.matches.filter { it.code in WorldCupEngine.RO32_MATCHES_CODE }.map {
                SubmitMatchPredictionRequest(matchId = it.id, homeGoals = 1, awayGoals = 1)
            }, withAssertions = true)
        }

        @Test
        @Order(8)
        fun `should receive updates for every existing match in round of 32`() {
            val matchesToUpdate = grondona.matches.filter { it.code in WorldCupEngine.RO32_MATCHES_CODE }
            val middle = (matchesToUpdate.size / 2) + 1

            val externalMatchesResponse1 = matchesToUpdate.take(middle)
            val externalMatchesResponse2 = matchesToUpdate.drop(middle)

            val externalMatchesResponses = listOf(externalMatchesResponse1, externalMatchesResponse2).map { matches ->
                matches.map {
                    ExternalMatch(
                        code = it.code, home = it.homeCode, away = it.awayCode, status = "COMPLETED",
                        homeGoals = 0, awayGoals = 0, half = 2, minutes = 93,
                        startedAt = ZonedDateTime.now().minusMinutes(118), endedAt = ZonedDateTime.now()
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
            val newMatchesRequests = WorldCupEngine.RO16_MATCHES_CODE.map {
                val homeTeam: UUID = qualifiedTeams.otherRandom(*newQualifiedTeams.toTypedArray())
                newQualifiedTeams.add(homeTeam)
                val awayTeam = qualifiedTeams.otherRandom(*newQualifiedTeams.toTypedArray())
                newQualifiedTeams.add(awayTeam)
                CreateMatchRequest(code = it, homeTeam = homeTeam, awayTeam = awayTeam, startedAt = ZonedDateTime.now().plusDays(1))
            }

            qualifiedTeams = newQualifiedTeams
            grondona.createMatches(request = CreateMatchesRequest(matches = newMatchesRequests))
        }

        @Test
        @Order(11)
        fun `should submit predictions for every match in the round of 16, for both users`() {
            grondona.submitMatchPredictionsToGroup(user1Token, groupId, grondona.matches.filter { it.code in WorldCupEngine.RO16_MATCHES_CODE }.map {
                SubmitMatchPredictionRequest(matchId = it.id, homeGoals = 0, awayGoals = 0)
            }, withAssertions = true)

            grondona.submitMatchPredictionsToGroup(user2Token, groupId, grondona.matches.filter { it.code in WorldCupEngine.RO16_MATCHES_CODE }.map {
                SubmitMatchPredictionRequest(matchId = it.id, homeGoals = 1, awayGoals = 1)
            }, withAssertions = true)
        }

        @Test
        @Order(12)
        fun `should receive updates for every existing match in round of 16`() {
            every { matchClient.getMatches(any()) } returns grondona.matches.filter { it.code in WorldCupEngine.RO16_MATCHES_CODE }.map {
                ExternalMatch(
                    code = it.code, home = it.homeCode, away = it.awayCode, status = "COMPLETED",
                    homeGoals = 0, awayGoals = 0, half = 2, minutes = 93,
                    startedAt = ZonedDateTime.now().minusMinutes(118), endedAt = ZonedDateTime.now()
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
            val newMatchesRequests = WorldCupEngine.QUARTERFINALS_MATCHES_CODE.map {
                val homeTeam: UUID = qualifiedTeams.otherRandom(*newQualifiedTeams.toTypedArray())
                newQualifiedTeams.add(homeTeam)
                val awayTeam = qualifiedTeams.otherRandom(*newQualifiedTeams.toTypedArray())
                newQualifiedTeams.add(awayTeam)
                CreateMatchRequest(code = it, homeTeam = homeTeam, awayTeam = awayTeam, startedAt = ZonedDateTime.now().plusDays(1))
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
                grondona.matches.filter { it.code in WorldCupEngine.QUARTERFINALS_MATCHES_CODE }.map {
                    SubmitMatchPredictionRequest(matchId = it.id, homeGoals = 0, awayGoals = 0)
                },
                withAssertions = true
            )

            grondona.submitMatchPredictionsToGroup(
                user2Token,
                groupId,
                grondona.matches.filter { it.code in WorldCupEngine.QUARTERFINALS_MATCHES_CODE }.map {
                    SubmitMatchPredictionRequest(matchId = it.id, homeGoals = 1, awayGoals = 1)
                },
                withAssertions = true
            )
        }

        @Test
        @Order(16)
        fun `should receive updates for every existing match in the quarterfinals`() {
            every { matchClient.getMatches(any()) } returns grondona.matches.filter { it.code in WorldCupEngine.QUARTERFINALS_MATCHES_CODE }.map {
                ExternalMatch(
                    code = it.code, home = it.homeCode, away = it.awayCode, status = "COMPLETED",
                    homeGoals = 1, awayGoals = 0, half = 2, minutes = 93,
                    startedAt = ZonedDateTime.now().minusMinutes(118), endedAt = ZonedDateTime.now()
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
            val newMatchesRequests = WorldCupEngine.SEMIFINALS_MATCHES_CODE.map {
                val homeTeam: UUID = qualifiedTeams.otherRandom(*newQualifiedTeams.toTypedArray())
                newQualifiedTeams.add(homeTeam)
                val awayTeam = qualifiedTeams.otherRandom(*newQualifiedTeams.toTypedArray())
                newQualifiedTeams.add(awayTeam)
                CreateMatchRequest(code = it, homeTeam = homeTeam, awayTeam = awayTeam, startedAt = ZonedDateTime.now().plusDays(1))
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
                grondona.matches.filter { it.code in WorldCupEngine.SEMIFINALS_MATCHES_CODE }.map {
                    SubmitMatchPredictionRequest(matchId = it.id, homeGoals = 5, awayGoals = 0)
                },
                withAssertions = true
            )

            grondona.submitMatchPredictionsToGroup(
                user2Token,
                groupId,
                grondona.matches.filter { it.code in WorldCupEngine.SEMIFINALS_MATCHES_CODE }.map {
                    SubmitMatchPredictionRequest(matchId = it.id, homeGoals = 5, awayGoals = 1)
                },
                withAssertions = true
            )
        }

        @Test
        @Order(20)
        fun `should receive updates for every existing match in the semifinals`() {
            every { matchClient.getMatches(any()) } returns grondona.matches.filter { it.code in WorldCupEngine.SEMIFINALS_MATCHES_CODE }.map {
                ExternalMatch(
                    code = it.code, home = it.homeCode, away = it.awayCode, status = "COMPLETED",
                    homeGoals = 5, awayGoals = 0, half = 2, minutes = 93,
                    startedAt = ZonedDateTime.now().minusMinutes(118), endedAt = ZonedDateTime.now()
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
            val newMatchesRequests = WorldCupEngine.LAST_ROUND_MATCHES_CODE.map {
                val homeTeam: UUID = qualifiedTeams.otherRandom(*newQualifiedTeams.toTypedArray())
                newQualifiedTeams.add(homeTeam)
                val awayTeam = qualifiedTeams.otherRandom(*newQualifiedTeams.toTypedArray())
                newQualifiedTeams.add(awayTeam)
                CreateMatchRequest(code = it, homeTeam = homeTeam, awayTeam = awayTeam, startedAt = ZonedDateTime.now().plusDays(1), hasMultiplier = true)
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
                grondona.matches.filter { it.code in WorldCupEngine.LAST_ROUND_MATCHES_CODE }.map {
                    SubmitMatchPredictionRequest(matchId = it.id, homeGoals = 1, awayGoals = 0)
                },
                withAssertions = true
            )

            grondona.submitMatchPredictionsToGroup(
                user2Token,
                groupId,
                grondona.matches.filter { it.code in WorldCupEngine.LAST_ROUND_MATCHES_CODE }.map {
                    SubmitMatchPredictionRequest(matchId = it.id, homeGoals = 3, awayGoals = 1)
                },
                withAssertions = true
            )
        }

        @Test
        @Order(24)
        fun `should receive updates for every existing match in the last round`() {
            every { matchClient.getMatches(any()) } returns grondona.matches.filter { it.code in WorldCupEngine.LAST_ROUND_MATCHES_CODE }.map {
                ExternalMatch(
                    code = it.code, home = it.homeCode, away = it.awayCode, status = "COMPLETED",
                    homeGoals = 3, awayGoals = 1, half = 2, minutes = 93,
                    startedAt = ZonedDateTime.now().minusMinutes(118), endedAt = ZonedDateTime.now()
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
}
