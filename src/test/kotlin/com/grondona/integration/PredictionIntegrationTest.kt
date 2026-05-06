package com.grondona.integration

import com.fasterxml.jackson.databind.ObjectMapper
import com.grondona.createTestingTournamentRequest
import com.grondona.createTestingUserRequest
import com.grondona.model.PlayerPosition
import com.grondona.model.UserPermissions
import com.grondona.model.dto.request.CreateGroupRequest
import com.grondona.model.dto.request.CreateMatchRequest
import com.grondona.model.dto.request.CreatePlayerRequest
import com.grondona.model.dto.request.CreateTeamRequest
import com.grondona.model.dto.request.SubmitAwardPredictionRequest
import com.grondona.model.dto.request.SubmitBulkMatchPredictionsRequest
import com.grondona.model.dto.request.SubmitMatchPredictionRequest
import com.grondona.model.dto.request.UpdateUserRequest
import com.grondona.model.dto.response.AuthenticatedUserResponse
import com.grondona.model.dto.response.AwardPredictionsResponse
import com.grondona.model.dto.response.GroupMatchPredictionsResponse
import com.grondona.model.dto.response.GroupResponse
import com.grondona.model.dto.response.MatchPredictionResponse
import com.grondona.model.dto.response.MatchResponse
import com.grondona.model.dto.response.PlayerResponse
import com.grondona.model.dto.response.TeamResponse
import com.grondona.model.dto.response.TournamentResponse
import com.grondona.repository.AwardPredictionRepository
import com.grondona.repository.GroupRepository
import com.grondona.repository.MatchPredictionRepository
import com.grondona.repository.MatchRepository
import com.grondona.repository.MembershipRepository
import com.grondona.repository.PlayerRepository
import com.grondona.repository.TeamRepository
import com.grondona.repository.TournamentRepository
import com.grondona.repository.UserRepository
import org.junit.jupiter.api.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PredictionIntegrationTest {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var objectMapper: ObjectMapper
    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var groupRepository: GroupRepository
    @Autowired private lateinit var playerRepository: PlayerRepository
    @Autowired private lateinit var teamRepository: TeamRepository
    @Autowired private lateinit var matchRepository: MatchRepository
    @Autowired private lateinit var tournamentRepository: TournamentRepository
    @Autowired private lateinit var membershipRepository: MembershipRepository
    @Autowired private lateinit var matchPredictionRepository: MatchPredictionRepository
    @Autowired private lateinit var awardPredictionRepository: AwardPredictionRepository

    private lateinit var adminToken: String
    private lateinit var memberToken: String
    private lateinit var nonMemberToken: String
    private lateinit var memberId: UUID
    private lateinit var tournamentId: UUID
    private lateinit var groupId: UUID
    private lateinit var teamOneId: UUID
    private lateinit var teamTwoId: UUID
    private lateinit var forwardPlayerId: UUID
    private lateinit var midfielderPlayerId: UUID
    private lateinit var goalkeeperPlayerId: UUID
    private lateinit var youngPlayerId: UUID
    private lateinit var firstMatchId: UUID
    private lateinit var secondMatchId: UUID

    @BeforeAll
    fun setUp() {
        awardPredictionRepository.deleteAll()
        matchPredictionRepository.deleteAll()
        matchRepository.deleteAll()
        membershipRepository.deleteAll()
        groupRepository.deleteAll()
        playerRepository.deleteAll()
        teamRepository.deleteAll()
        tournamentRepository.deleteAll()
        userRepository.deleteAll()

        val adminResponse = createUser("admin")
        val adminUser = userRepository.findById(adminResponse.userId).get()
        userRepository.save(adminUser.copy(permissions = UserPermissions.SUPERUSER))
        adminToken = adminResponse.token

        tournamentId = createTournament()

        teamOneId = createTeam("Argentina", "ARG").id
        teamTwoId = createTeam("Brazil", "BRA").id

        forwardPlayerId = createPlayer("Forward Player", PlayerPosition.FORWARD, teamOneId, LocalDate.of(1998, 5, 10)).id
        midfielderPlayerId = createPlayer("Midfielder Player", PlayerPosition.MIDFIELDER, teamOneId, LocalDate.of(1999, 3, 4)).id
        goalkeeperPlayerId = createPlayer("Goalkeeper Player", PlayerPosition.GOALKEEPER, teamTwoId, LocalDate.of(1997, 7, 20)).id
        youngPlayerId = createPlayer("Young Player", PlayerPosition.FORWARD, teamTwoId, LocalDate.of(2006, 8, 15)).id

        firstMatchId = createMatch("ARG-BRA-1", teamOneId, teamTwoId, LocalDateTime.now().plusDays(10)).id
        secondMatchId = createMatch("BRA-ARG-2", teamTwoId, teamOneId, LocalDateTime.now().plusDays(11)).id

        groupId = createGroup(adminToken, "Prediction Group")

        val memberResponse = createUser("member")
        memberToken = memberResponse.token
        memberId = memberResponse.userId
        joinGroup(memberToken, groupId)

        nonMemberToken = createUser("nonmember").token
    }

    @AfterAll
    fun tearDown() {
        awardPredictionRepository.deleteAll()
        matchPredictionRepository.deleteAll()
        matchRepository.deleteAll()
        membershipRepository.deleteAll()
        groupRepository.deleteAll()
        playerRepository.deleteAll()
        teamRepository.deleteAll()
        tournamentRepository.deleteAll()
        userRepository.deleteAll()
    }

    @Nested
    inner class SubmitGroupSingleMatchPredictionsTests {

        @Test
        fun `should submit a single match prediction successfully`() {
            val request = SubmitMatchPredictionRequest(matchId = firstMatchId, homeGoals = 2, awayGoals = 1)

            val result = mockMvc.perform(
                post(
                    "/api/tournaments/{tournamentId}/groups/{groupId}/predictions/matches/{matchId}",
                    tournamentId, groupId, firstMatchId
                )
                    .header("Authorization", "Bearer $memberToken")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isCreated)
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.user.id").value(memberId.toString()))
                .andExpect(jsonPath("$.match.id").value(firstMatchId.toString()))
                .andExpect(jsonPath("$.prediction.home_goals").value(2))
                .andExpect(jsonPath("$.prediction.away_goals").value(1))
                .andReturn()

            val response = objectMapper.readValue(result.response.contentAsString, MatchPredictionResponse::class.java)
            Assertions.assertEquals(firstMatchId, response.match.id)
            Assertions.assertEquals(2, response.prediction?.homeGoals)
            Assertions.assertEquals(1, response.prediction?.awayGoals)
        }

        @Test
        fun `should return 401 when submitting prediction without authentication`() {
            val request = SubmitMatchPredictionRequest(matchId = firstMatchId, homeGoals = 1, awayGoals = 0)

            mockMvc.perform(
                post(
                    "/api/tournaments/{tournamentId}/groups/{groupId}/predictions/matches/{matchId}",
                    tournamentId, groupId, firstMatchId
                )
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isUnauthorized)
        }

        @Test
        fun `should return 403 when submitting prediction as non-member`() {
            val request = SubmitMatchPredictionRequest(matchId = firstMatchId, homeGoals = 1, awayGoals = 0)

            mockMvc.perform(
                post(
                    "/api/tournaments/{tournamentId}/groups/{groupId}/predictions/matches/{matchId}",
                    tournamentId, groupId, firstMatchId
                )
                    .header("Authorization", "Bearer $nonMemberToken")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isForbidden)
                .andExpect(jsonPath("$.message").value("User doesn't belong to the group"))
        }

        @Test
        fun `should return 404 when group does not exist`() {
            val nonExistentGroupId = UUID.randomUUID()
            val request = SubmitMatchPredictionRequest(matchId = firstMatchId, homeGoals = 1, awayGoals = 0)

            mockMvc.perform(
                post(
                    "/api/tournaments/{tournamentId}/groups/{groupId}/predictions/matches/{matchId}",
                    tournamentId, nonExistentGroupId, firstMatchId
                )
                    .header("Authorization", "Bearer $memberToken")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isNotFound)
                .andExpect(jsonPath("$.message").value("Group not found"))
        }

        @Test
        fun `should return 404 when match does not exist`() {
            val nonExistentMatchId = UUID.randomUUID()
            val request = SubmitMatchPredictionRequest(matchId = nonExistentMatchId, homeGoals = 1, awayGoals = 0)

            mockMvc.perform(
                post(
                    "/api/tournaments/{tournamentId}/groups/{groupId}/predictions/matches/{matchId}",
                    tournamentId, groupId, nonExistentMatchId
                )
                    .header("Authorization", "Bearer $memberToken")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isNotFound)
                .andExpect(jsonPath("$.message").value("Match not found"))
        }

        @Test
        fun `should return 400 when homeGoals is negative`() {
            val invalidRequest = SubmitMatchPredictionRequest(matchId = firstMatchId, homeGoals = -1, awayGoals = 0)

            mockMvc.perform(
                post(
                    "/api/tournaments/{tournamentId}/groups/{groupId}/predictions/matches/{matchId}",
                    tournamentId, groupId, firstMatchId
                )
                    .header("Authorization", "Bearer $memberToken")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(invalidRequest))
            )
                .andExpect(status().isBadRequest)
        }
    }

    @Nested
    inner class SubmitGroupBulkMatchPredictionsTests {

        @Test
        fun `should submit bulk match predictions successfully`() {
            val request = SubmitBulkMatchPredictionsRequest(
                predictions = listOf(
                    SubmitMatchPredictionRequest(matchId = firstMatchId, homeGoals = 2, awayGoals = 0),
                    SubmitMatchPredictionRequest(matchId = secondMatchId, homeGoals = 1, awayGoals = 1)
                )
            )

            val response = submitBulkMatchPredictions(memberToken, groupId, request)

            assertMatchPrediction(response, memberId, firstMatchId, 2, 0)
            assertMatchPrediction(response, memberId, secondMatchId, 1, 1)
        }

        @Test
        fun `should return 401 when submitting bulk predictions without authentication`() {
            val request = SubmitBulkMatchPredictionsRequest(
                predictions = listOf(SubmitMatchPredictionRequest(matchId = firstMatchId, homeGoals = 1, awayGoals = 0))
            )

            mockMvc.perform(
                post("/api/tournaments/{tournamentId}/groups/{groupId}/predictions/matches", tournamentId, groupId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isUnauthorized)
        }

        @Test
        fun `should return 403 when submitting bulk predictions as non-member`() {
            val request = SubmitBulkMatchPredictionsRequest(
                predictions = listOf(SubmitMatchPredictionRequest(matchId = firstMatchId, homeGoals = 1, awayGoals = 0))
            )

            mockMvc.perform(
                post("/api/tournaments/{tournamentId}/groups/{groupId}/predictions/matches", tournamentId, groupId)
                    .header("Authorization", "Bearer $nonMemberToken")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isForbidden)
                .andExpect(jsonPath("$.message").value("User doesn't belong to the group"))
        }

        @Test
        fun `should return 404 when group does not exist for bulk submission`() {
            val nonExistentGroupId = UUID.randomUUID()
            val request = SubmitBulkMatchPredictionsRequest(
                predictions = listOf(SubmitMatchPredictionRequest(matchId = firstMatchId, homeGoals = 1, awayGoals = 0))
            )

            mockMvc.perform(
                post("/api/tournaments/{tournamentId}/groups/{groupId}/predictions/matches", tournamentId, nonExistentGroupId)
                    .header("Authorization", "Bearer $memberToken")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isNotFound)
                .andExpect(jsonPath("$.message").value("Group not found"))
        }

        @Test
        fun `should return 400 when bulk predictions list is empty`() {
            val emptyRequest = SubmitBulkMatchPredictionsRequest(predictions = emptyList())

            mockMvc.perform(
                post("/api/tournaments/{tournamentId}/groups/{groupId}/predictions/matches", tournamentId, groupId)
                    .header("Authorization", "Bearer $memberToken")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(emptyRequest))
            )
                .andExpect(status().isBadRequest)
        }
    }

    @Nested
    inner class SubmitAwardPredictionsTests {

        @Test
        fun `should submit award predictions successfully`() {
            val response = submitAwardPredictions(memberToken, groupId, validAwardPredictionRequest())

            assertAwardPredictions(response, memberId, listOf(teamOneId, teamTwoId), listOf(forwardPlayerId), listOf(midfielderPlayerId), listOf(goalkeeperPlayerId), listOf(youngPlayerId))
        }

        @Test
        fun `should return 401 when submitting award predictions without authentication`() {
            mockMvc.perform(
                post("/api/tournaments/{tournamentId}/groups/{groupId}/predictions/awards", tournamentId, groupId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(validAwardPredictionRequest()))
            )
                .andExpect(status().isUnauthorized)
        }

        @Test
        fun `should return 403 when submitting award predictions as non-member`() {
            mockMvc.perform(
                post("/api/tournaments/{tournamentId}/groups/{groupId}/predictions/awards", tournamentId, groupId)
                    .header("Authorization", "Bearer $nonMemberToken")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(validAwardPredictionRequest()))
            )
                .andExpect(status().isForbidden)
                .andExpect(jsonPath("$.message").value("User doesn't belong to the group"))
        }

        @Test
        fun `should return 404 when group does not exist for award predictions`() {
            mockMvc.perform(
                post("/api/tournaments/{tournamentId}/groups/{groupId}/predictions/awards", tournamentId, UUID.randomUUID())
                    .header("Authorization", "Bearer $memberToken")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(validAwardPredictionRequest()))
            )
                .andExpect(status().isNotFound)
                .andExpect(jsonPath("$.message").value("Group not found"))
        }

        @Test
        fun `should return 400 when too many champions are submitted`() {
            val request = validAwardPredictionRequest().copy(
                champions = listOf(teamOneId, teamTwoId, UUID.randomUUID())
            )

            mockMvc.perform(
                post("/api/tournaments/{tournamentId}/groups/{groupId}/predictions/awards", tournamentId, groupId)
                    .header("Authorization", "Bearer $memberToken")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.message").value("Invalid amount of awards"))
        }
    }

    @Nested
    inner class GetMyGroupMatchPredictionsTests {

        @Test
        fun `should return 401 when getting predictions without authentication`() {
            mockMvc.perform(
                get("/api/tournaments/{tournamentId}/groups/{groupId}/predictions/matches/me", tournamentId, groupId)
            )
                .andExpect(status().isUnauthorized)
        }

        @Test
        fun `should return 403 when getting predictions as non-member`() {
            mockMvc.perform(
                get("/api/tournaments/{tournamentId}/groups/{groupId}/predictions/matches/me", tournamentId, groupId)
                    .header("Authorization", "Bearer $nonMemberToken")
            )
                .andExpect(status().isForbidden)
                .andExpect(jsonPath("$.message").value("User doesn't belong to the group"))
        }

        @Test
        fun `should return 404 when group does not exist`() {
            val nonExistentGroupId = UUID.randomUUID()

            mockMvc.perform(
                get("/api/tournaments/{tournamentId}/groups/{groupId}/predictions/matches/me", tournamentId, nonExistentGroupId)
                    .header("Authorization", "Bearer $memberToken")
            )
                .andExpect(status().isNotFound)
                .andExpect(jsonPath("$.message").value("Group not found"))
        }
    }

    @Nested
    inner class GetSingleGroupMatchPredictionsTests {

        @Test
        fun `should return 401 when getting match predictions without authentication`() {
            mockMvc.perform(
                get(
                    "/api/tournaments/{tournamentId}/groups/{groupId}/predictions/matches/{matchId}",
                    tournamentId, groupId, firstMatchId
                )
            )
                .andExpect(status().isUnauthorized)
        }

        @Test
        fun `should return 403 when getting match predictions as non-member`() {
            mockMvc.perform(
                get(
                    "/api/tournaments/{tournamentId}/groups/{groupId}/predictions/matches/{matchId}",
                    tournamentId, groupId, firstMatchId
                )
                    .header("Authorization", "Bearer $nonMemberToken")
            )
                .andExpect(status().isForbidden)
                .andExpect(jsonPath("$.message").value("User doesn't belong to the group"))
        }

        @Test
        fun `should return 404 when group does not exist`() {
            val nonExistentGroupId = UUID.randomUUID()

            mockMvc.perform(
                get(
                    "/api/tournaments/{tournamentId}/groups/{groupId}/predictions/matches/{matchId}",
                    tournamentId, nonExistentGroupId, firstMatchId
                )
                    .header("Authorization", "Bearer $memberToken")
            )
                .andExpect(status().isNotFound)
                .andExpect(jsonPath("$.message").value("Group not found"))
        }

        @Test
        fun `should return 404 when match does not exist`() {
            val nonExistentMatchId = UUID.randomUUID()

            mockMvc.perform(
                get(
                    "/api/tournaments/{tournamentId}/groups/{groupId}/predictions/matches/{matchId}",
                    tournamentId, groupId, nonExistentMatchId
                )
                    .header("Authorization", "Bearer $memberToken")
            )
                .andExpect(status().isNotFound)
                .andExpect(jsonPath("$.message").value("Match not found"))
        }
    }

    @Nested
    inner class UniquePredictionsAcrossGroupsTests {

        @Test
        fun `should clone and keep match and award predictions synced across user groups`() {
            val user1 = createUser("scenario-user-1")
            val user2 = createUser("scenario-user-2")

            val group1Id = createGroup(user1.token, "Scenario Group 1")
            val group2Id = createGroup(user1.token, "Scenario Group 2")

            joinGroup(user2.token, group1Id)
            joinGroup(user2.token, group2Id)

            submitBulkMatchPredictions(
                user1.token,
                group1Id,
                SubmitBulkMatchPredictionsRequest(listOf(SubmitMatchPredictionRequest(firstMatchId, 1, 0)))
            )
            submitBulkMatchPredictions(
                user1.token,
                group2Id,
                SubmitBulkMatchPredictionsRequest(listOf(SubmitMatchPredictionRequest(secondMatchId, 2, 2)))
            )

            assertMatchPrediction(getMyMatchPredictions(user1.token, group1Id), user1.userId, firstMatchId, 1, 0)
            assertMatchPrediction(getMyMatchPredictions(user1.token, group2Id), user1.userId, secondMatchId, 2, 2)

            val user1Group1Awards = validAwardPredictionRequest(champions = listOf(teamOneId), topScorers = listOf(forwardPlayerId))
            val user1Group2Awards = validAwardPredictionRequest(champions = listOf(teamTwoId), topScorers = listOf(youngPlayerId))
            submitAwardPredictions(user1.token, group1Id, user1Group1Awards)
            submitAwardPredictions(user1.token, group2Id, user1Group2Awards)

            assertAwardPredictions(getMyAwardPredictions(user1.token, group1Id), user1.userId, listOf(teamOneId), listOf(forwardPlayerId), listOf(midfielderPlayerId), listOf(goalkeeperPlayerId), listOf(youngPlayerId))
            assertAwardPredictions(getMyAwardPredictions(user1.token, group2Id), user1.userId, listOf(teamTwoId), listOf(youngPlayerId), listOf(midfielderPlayerId), listOf(goalkeeperPlayerId), listOf(youngPlayerId))

            submitBulkMatchPredictions(
                user2.token,
                group1Id,
                SubmitBulkMatchPredictionsRequest(listOf(SubmitMatchPredictionRequest(firstMatchId, 3, 1)))
            )
            submitAwardPredictions(user2.token, group1Id, validAwardPredictionRequest(champions = listOf(teamOneId), topScorers = listOf(forwardPlayerId)))

            assertMatchPrediction(getMyMatchPredictions(user2.token, group1Id), user2.userId, firstMatchId, 3, 1)
            assertAwardPredictions(getMyAwardPredictions(user2.token, group1Id), user2.userId, listOf(teamOneId), listOf(forwardPlayerId), listOf(midfielderPlayerId), listOf(goalkeeperPlayerId), listOf(youngPlayerId))

            mockMvc.perform(
                patch("/api/users")
                    .header("Authorization", "Bearer ${user2.token}")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(UpdateUserRequest(uniquePredictions = true, uniquePredictionsMaster = group1Id)))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.unique_predictions").value(true))

            assertMatchPrediction(getMyMatchPredictions(user2.token, group2Id), user2.userId, firstMatchId, 3, 1)
            assertAwardPredictions(getMyAwardPredictions(user2.token, group2Id), user2.userId, listOf(teamOneId), listOf(forwardPlayerId), listOf(midfielderPlayerId), listOf(goalkeeperPlayerId), listOf(youngPlayerId))

            submitSingleMatchPrediction(user2.token, group1Id, SubmitMatchPredictionRequest(secondMatchId, 4, 2))
            assertMatchPrediction(getMyMatchPredictions(user2.token, group2Id), user2.userId, secondMatchId, 4, 2)

            submitAwardPredictions(user2.token, group2Id, validAwardPredictionRequest(champions = listOf(teamTwoId), topScorers = listOf(youngPlayerId)))
            assertAwardPredictions(getMyAwardPredictions(user2.token, group1Id), user2.userId, listOf(teamTwoId), listOf(youngPlayerId), listOf(midfielderPlayerId), listOf(goalkeeperPlayerId), listOf(youngPlayerId))
        }
    }

    private fun createUser(username: String): AuthenticatedUserResponse {
        val result = mockMvc.perform(
            post("/api/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createTestingUserRequest(username = username)))
        )
            .andExpect(status().isCreated)
            .andReturn()

        return objectMapper.readValue(result.response.contentAsString, AuthenticatedUserResponse::class.java)
    }

    private fun createTournament(): UUID {
        val result = mockMvc.perform(
            post("/api/tournaments")
                .header("Authorization", "Bearer $adminToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createTestingTournamentRequest(name = "PredictionTest Tournament")))
        )
            .andExpect(status().isCreated)
            .andReturn()

        return objectMapper.readValue(result.response.contentAsString, TournamentResponse::class.java).id
    }

    private fun createTeam(name: String, code: String): TeamResponse {
        val request = CreateTeamRequest(name = name, code = code, icon = code.lowercase())
        val result = mockMvc.perform(
            post("/api/tournaments/{tournamentId}/teams", tournamentId)
                .header("Authorization", "Bearer $adminToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isCreated)
            .andReturn()

        return objectMapper.readValue(result.response.contentAsString, TeamResponse::class.java)
    }

    private fun createPlayer(name: String, position: PlayerPosition, teamId: UUID, birthdate: LocalDate): PlayerResponse {
        val request = CreatePlayerRequest(name = name, position = position, team = teamId, birthdate = birthdate)
        val result = mockMvc.perform(
            post("/api/tournaments/{tournamentId}/players", tournamentId)
                .header("Authorization", "Bearer $adminToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isCreated)
            .andReturn()

        return objectMapper.readValue(result.response.contentAsString, PlayerResponse::class.java)
    }

    private fun createMatch(code: String, homeTeamId: UUID, awayTeamId: UUID, startedAt: LocalDateTime): MatchResponse {
        val request = CreateMatchRequest(code = code, homeTeam = homeTeamId, awayTeam = awayTeamId, startedAt = startedAt)
        val result = mockMvc.perform(
            post("/api/tournaments/{tournamentId}/matches", tournamentId)
                .header("Authorization", "Bearer $adminToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isCreated)
            .andReturn()

        return objectMapper.readValue(result.response.contentAsString, MatchResponse::class.java)
    }

    private fun createGroup(token: String, name: String): UUID {
        val result = mockMvc.perform(
            post("/api/tournaments/{tournamentId}/groups", tournamentId)
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(CreateGroupRequest(name = name, isPrivate = false, maxMembers = 10)))
        )
            .andExpect(status().isCreated)
            .andReturn()

        return objectMapper.readValue(result.response.contentAsString, GroupResponse::class.java).id
    }

    private fun joinGroup(token: String, groupId: UUID) {
        mockMvc.perform(
            post("/api/tournaments/{tournamentId}/groups/{groupId}/join", tournamentId, groupId)
                .header("Authorization", "Bearer $token")
        )
            .andExpect(status().isCreated)
    }

    private fun submitSingleMatchPrediction(
        token: String,
        groupId: UUID,
        request: SubmitMatchPredictionRequest
    ): MatchPredictionResponse {
        val result = mockMvc.perform(
            post(
                "/api/tournaments/{tournamentId}/groups/{groupId}/predictions/matches/{matchId}",
                tournamentId, groupId, request.matchId
            )
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isCreated)
            .andReturn()

        return objectMapper.readValue(result.response.contentAsString, MatchPredictionResponse::class.java)
    }

    private fun submitBulkMatchPredictions(
        token: String,
        groupId: UUID,
        request: SubmitBulkMatchPredictionsRequest
    ): GroupMatchPredictionsResponse {
        val result = mockMvc.perform(
            post("/api/tournaments/{tournamentId}/groups/{groupId}/predictions/matches", tournamentId, groupId)
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isCreated)
            .andReturn()

        return objectMapper.readValue(result.response.contentAsString, GroupMatchPredictionsResponse::class.java)
    }

    private fun getMyMatchPredictions(token: String, groupId: UUID): GroupMatchPredictionsResponse {
        val result = mockMvc.perform(
            get("/api/tournaments/{tournamentId}/groups/{groupId}/predictions/matches/me", tournamentId, groupId)
                .header("Authorization", "Bearer $token")
        )
            .andExpect(status().isOk)
            .andReturn()

        return objectMapper.readValue(result.response.contentAsString, GroupMatchPredictionsResponse::class.java)
    }

    private fun submitAwardPredictions(
        token: String,
        groupId: UUID,
        request: SubmitAwardPredictionRequest
    ): AwardPredictionsResponse {
        val result = mockMvc.perform(
            post("/api/tournaments/{tournamentId}/groups/{groupId}/predictions/awards", tournamentId, groupId)
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isCreated)
            .andReturn()

        return objectMapper.readValue(result.response.contentAsString, AwardPredictionsResponse::class.java)
    }

    private fun getMyAwardPredictions(token: String, groupId: UUID): AwardPredictionsResponse {
        val result = mockMvc.perform(
            get("/api/tournaments/{tournamentId}/groups/{groupId}/predictions/awards/me", tournamentId, groupId)
                .header("Authorization", "Bearer $token")
        )
            .andExpect(status().isOk)
            .andReturn()

        return objectMapper.readValue(result.response.contentAsString, AwardPredictionsResponse::class.java)
    }

    private fun validAwardPredictionRequest(
        champions: List<UUID> = listOf(teamOneId, teamTwoId),
        topScorers: List<UUID> = listOf(forwardPlayerId),
        bestPlayers: List<UUID> = listOf(midfielderPlayerId),
        bestGoalkeepers: List<UUID> = listOf(goalkeeperPlayerId),
        bestYoungPlayers: List<UUID> = listOf(youngPlayerId),
    ): SubmitAwardPredictionRequest =
        SubmitAwardPredictionRequest(
            champions = champions,
            topScorers = topScorers,
            bestPlayers = bestPlayers,
            bestGoalkeepers = bestGoalkeepers,
            bestYoungPlayers = bestYoungPlayers
        )

    private fun assertMatchPrediction(
        response: GroupMatchPredictionsResponse,
        expectedUserId: UUID,
        expectedMatchId: UUID,
        expectedHomeGoals: Int,
        expectedAwayGoals: Int
    ) {
        val prediction = response.predictions.firstOrNull {
            it.user.id == expectedUserId && it.match.id == expectedMatchId
        }

        Assertions.assertNotNull(prediction, "Expected match prediction was not found")
        Assertions.assertEquals(expectedHomeGoals, prediction?.prediction?.homeGoals)
        Assertions.assertEquals(expectedAwayGoals, prediction?.prediction?.awayGoals)
    }

    private fun assertAwardPredictions(
        response: AwardPredictionsResponse,
        expectedUserId: UUID,
        expectedChampions: List<UUID>,
        expectedTopScorers: List<UUID>,
        expectedBestPlayers: List<UUID>,
        expectedBestGoalkeepers: List<UUID>,
        expectedBestYoungPlayers: List<UUID>
    ) {
        Assertions.assertEquals(expectedUserId, response.user.id)
        Assertions.assertEquals(expectedChampions.toSet(), response.champions.map { it.id }.toSet())
        Assertions.assertEquals(expectedTopScorers.toSet(), response.topScorers.map { it.id }.toSet())
        Assertions.assertEquals(expectedBestPlayers.toSet(), response.bestPlayers.map { it.id }.toSet())
        Assertions.assertEquals(expectedBestGoalkeepers.toSet(), response.bestGoalkeepers.map { it.id }.toSet())
        Assertions.assertEquals(expectedBestYoungPlayers.toSet(), response.bestYoungPlayers.map { it.id }.toSet())
    }
}
