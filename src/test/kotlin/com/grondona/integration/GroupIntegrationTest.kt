package com.grondona.integration

import com.fasterxml.jackson.databind.ObjectMapper
import com.grondona.createTestingTournamentRequest
import com.grondona.createTestingUserRequest
import com.grondona.model.GroupRole
import com.grondona.model.UserPermissions
import com.grondona.model.dto.request.CreateGroupRequest
import com.grondona.model.dto.request.CreateMatchRequest
import com.grondona.model.dto.request.CreateTeamRequest
import com.grondona.model.dto.request.SubmitBulkMatchPredictionsRequest
import com.grondona.model.dto.request.SubmitMatchPredictionRequest
import com.grondona.model.dto.request.UpdateGroupRequest
import com.grondona.model.dto.request.UpdateMemberRequest
import com.grondona.model.dto.response.AuthenticatedUserResponse
import com.grondona.model.dto.response.GroupResponse
import com.grondona.model.dto.response.TournamentResponse
import com.grondona.repository.GroupRepository
import com.grondona.repository.MatchPredictionRepository
import com.grondona.repository.MatchRepository
import com.grondona.repository.MembershipRepository
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
import java.time.LocalDateTime
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class GroupIntegrationTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var groupRepository: GroupRepository

    @Autowired
    private lateinit var membershipRepository: MembershipRepository

    @Autowired
    private lateinit var tournamentRepository: TournamentRepository

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var teamRepository: TeamRepository

    @Autowired
    private lateinit var matchRepository: MatchRepository

    @Autowired
    private lateinit var matchPredictionRepository: MatchPredictionRepository

    private var adminToken: String? = null

    private var user1Id: String? = null
    private var user1Token: String? = null
    private var createdGroupId: String? = null
    private var testTournamentId: String? = null

    @BeforeAll
    fun setUp() {
        matchPredictionRepository.deleteAll()
        membershipRepository.deleteAll()
        groupRepository.deleteAll()
        matchRepository.deleteAll()
        teamRepository.deleteAll()
        userRepository.deleteAll()
        tournamentRepository.deleteAll()

        // Create an admin user to create the first tournament
        val adminResult = mockMvc.perform(
            post("/api/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createTestingUserRequest()))
        ).andReturn()
        val adminId = objectMapper.readValue(adminResult.response.contentAsString, AuthenticatedUserResponse::class.java).userId
        val adminUser = userRepository.findById(adminId).get()
        userRepository.save(adminUser.copy(permissions = UserPermissions.SUPERUSER))
        adminToken = objectMapper.readValue(adminResult.response.contentAsString, AuthenticatedUserResponse::class.java).token

        // Create tournament
        val tournamentResult = mockMvc.perform(
            post("/api/tournaments")
                .header("Authorization", "Bearer $adminToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createTestingTournamentRequest()))
        ).andReturn()
        testTournamentId = objectMapper.readValue(tournamentResult.response.contentAsString, TournamentResponse::class.java).id.toString()

        // Create a user and get auth token for all group tests
        val result = mockMvc.perform(
            post("/api/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createTestingUserRequest()))
        ).andReturn()

        val authResponse = objectMapper.readValue(result.response.contentAsString, AuthenticatedUserResponse::class.java)
        user1Id = authResponse.userId.toString()
        user1Token = authResponse.token
    }

    @AfterAll
    fun tearDown() {
        matchPredictionRepository.deleteAll()
        membershipRepository.deleteAll()
        groupRepository.deleteAll()
        matchRepository.deleteAll()
        teamRepository.deleteAll()
        userRepository.deleteAll()
        tournamentRepository.deleteAll()
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    @TestMethodOrder(MethodOrderer.OrderAnnotation::class)
    inner class GroupLifecycleTests {

        @Test
        @Order(1)
        fun `should create a new group successfully`() {
            val request = CreateGroupRequest(name = "Integration Group", isPrivate = false, maxMembers = 30)

            val result = mockMvc.perform(
                post("/api/tournaments/{tournamentId}/groups", testTournamentId)
                    .header("Authorization", "Bearer $user1Token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isCreated)
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.name").value("Integration Group"))
                .andExpect(jsonPath("$.private").value(false))
                .andExpect(jsonPath("$.max_members").value(30))
                .andReturn()

            val response = objectMapper.readValue(result.response.contentAsString, GroupResponse::class.java)
            createdGroupId = response.id.toString()
        }

        @Test
        @Order(2)
        fun `should fail to create group with duplicate name`() {
            val request = CreateGroupRequest(name = "Integration Group", isPrivate = false, maxMembers = 10)

            mockMvc.perform(
                post("/api/tournaments/{tournamentId}/groups", testTournamentId)
                    .header("Authorization", "Bearer $user1Token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isConflict)
                .andExpect(jsonPath("$.data.field").value("name"))
                .andExpect(jsonPath("$.data.rejected_value").value("Integration Group"))
        }

        @Test
        @Order(3)
        fun `should fetch the created group by id`() {
            mockMvc.perform(
                get("/api/tournaments/{tournamentId}/groups/{groupId}", testTournamentId, createdGroupId)
                    .header("Authorization", "Bearer $user1Token")
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.id").value(createdGroupId))
                .andExpect(jsonPath("$.name").value("Integration Group"))
        }

        @Test
        @Order(4)
        fun `should update the group successfully`() {
            val request = UpdateGroupRequest(name = "Updated Integration Group", maxMembers = 50)

            mockMvc.perform(
                patch("/api/tournaments/{tournamentId}/groups/{groupId}", testTournamentId, createdGroupId)
                    .header("Authorization", "Bearer $user1Token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.name").value("Updated Integration Group"))
                .andExpect(jsonPath("$.max_members").value(50))
        }

        @Test
        @Order(5)
        fun `should update group privacy`() {
            val request = UpdateGroupRequest(isPrivate = true)

            mockMvc.perform(
                patch("/api/tournaments/{tournamentId}/groups/{groupId}", testTournamentId, createdGroupId)
                    .header("Authorization", "Bearer $user1Token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.private").value(true))
        }

        @Test
        @Order(6)
        fun `should fetch all groups`() {
            mockMvc.perform(
                get("/api/tournaments/{tournamentId}/groups", testTournamentId)
                    .header("Authorization", "Bearer $user1Token")
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.length()").value(1))
        }

        @Test
        @Order(7)
        fun `should create second group for search tests`() {
            val request = CreateGroupRequest(name = "Another Group", isPrivate = false, maxMembers = 15)

            mockMvc.perform(
                post("/api/tournaments/{tournamentId}/groups", testTournamentId)
                    .header("Authorization", "Bearer $user1Token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isCreated)
        }

        @Test
        @Order(8)
        fun `should search groups by name substring case insensitively`() {
            mockMvc.perform(
                get("/api/tournaments/{tournamentId}/groups", testTournamentId)
                    .header("Authorization", "Bearer $user1Token")
                    .param("search", "updated")
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Updated Integration Group"))
        }

        @Test
        @Order(9)
        fun `should return empty list when search has no matches`() {
            mockMvc.perform(
                get("/api/tournaments/{tournamentId}/groups", testTournamentId)
                    .header("Authorization", "Bearer $user1Token")
                    .param("search", "zzznomatch")
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.length()").value(0))
        }

        @Test
        @Order(10)
        fun `should fetch all groups without search`() {
            mockMvc.perform(
                get("/api/tournaments/{tournamentId}/groups", testTournamentId)
                    .header("Authorization", "Bearer $user1Token")
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.length()").value(2))
        }

        @Test
        @Order(11)
        fun `should delete the group successfully`() {
            mockMvc.perform(
                delete("/api/tournaments/{tournamentId}/groups/{groupId}", testTournamentId, createdGroupId)
                    .header("Authorization", "Bearer $user1Token")
            )
                .andExpect(status().isNoContent)

            // Verify it's gone
            mockMvc.perform(
                get("/api/tournaments/{tournamentId}/groups/{groupId}", testTournamentId, createdGroupId)
                    .header("Authorization", "Bearer $user1Token")
            )
                .andExpect(status().isNotFound)
        }
    }

    @Nested
    inner class GroupValidationTests {

        @Test
        fun `should fail to create group with blank name`() {
            val body = mapOf("name" to "", "private" to false, "maxMembers" to 10)

            mockMvc.perform(
                post("/api/tournaments/{tournamentId}/groups", testTournamentId)
                    .header("Authorization", "Bearer $user1Token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(body))
            )
                .andExpect(status().isBadRequest)
        }

        @Test
        fun `should fail to create group with maxMembers less than 1`() {
            val body = mapOf("name" to "Valid Name", "private" to false, "maxMembers" to 0)

            mockMvc.perform(
                post("/api/tournaments/{tournamentId}/groups", testTournamentId)
                    .header("Authorization", "Bearer $user1Token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(body))
            )
                .andExpect(status().isBadRequest)
        }

        @Test
        fun `should return 404 for non existent group`() {
            val nonExistentId = UUID.randomUUID()

            mockMvc.perform(
                get("/api/tournaments/{tournamentId}/groups/{groupId}", testTournamentId, nonExistentId)
                    .header("Authorization", "Bearer $user1Token")
            )
                .andExpect(status().isNotFound)
                .andExpect(jsonPath("$.message").value("Group not found"))
        }

        @Test
        fun `should return 404 when deleting non existent group`() {
            val nonExistentId = UUID.randomUUID()

            mockMvc.perform(
                delete("/api/tournaments/{tournamentId}/groups/{groupId}", testTournamentId, nonExistentId)
                    .header("Authorization", "Bearer $user1Token")
            )
                .andExpect(status().isNotFound)
        }
    }

    @Nested
    inner class GroupAuthenticationTests {

        @Test
        fun `should reject group creation without authentication`() {
            val request = CreateGroupRequest(name = "Auth Test Group", isPrivate = false, maxMembers = 10)

            mockMvc.perform(
                post("/api/tournaments/{tournamentId}/groups", testTournamentId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isUnauthorized)
        }

        @Test
        fun `should reject get all groups without authentication`() {
            mockMvc.perform(get("/api/tournaments/{tournamentId}/groups", testTournamentId))
                .andExpect(status().isUnauthorized)
        }

        @Test
        fun `should reject get group by id without authentication`() {
            mockMvc.perform(get("/api/tournaments/{tournamentId}/groups/{groupId}", testTournamentId, UUID.randomUUID()))
                .andExpect(status().isUnauthorized)
        }

        @Test
        fun `should reject group update without authentication`() {
            val request = UpdateGroupRequest(name = "New Name")

            mockMvc.perform(
                patch("/api/tournaments/{tournamentId}/groups/{groupId}", testTournamentId, UUID.randomUUID())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isUnauthorized)
        }

        @Test
        fun `should reject group deletion without authentication`() {
            mockMvc.perform(delete("/api/tournaments/{tournamentId}/groups/{groupId}", testTournamentId, UUID.randomUUID()))
                .andExpect(status().isUnauthorized)
        }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    @TestMethodOrder(MethodOrderer.OrderAnnotation::class)
    inner class PrivateGroupAcceptanceFlowTests {

        private var user2Id: String? = null
        private var user2Token: String? = null
        private var privateGroupId: String? = null
        private val matchId: UUID = UUID.randomUUID()

        @BeforeAll
        fun setUp() {
            val user2Result = mockMvc.perform(
                post("/api/users")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(createTestingUserRequest()))
            ).andReturn()
            val user2Response = objectMapper.readValue(user2Result.response.contentAsString, AuthenticatedUserResponse::class.java)
            user2Token = user2Response.token
            user2Id = user2Response.userId.toString()

            val groupResult = mockMvc.perform(
                post("/api/tournaments/{tournamentId}/groups", testTournamentId)
                    .header("Authorization", "Bearer $user1Token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(CreateGroupRequest(name = "Private Group Case 1", isPrivate = true, maxMembers = 10)))
            ).andReturn()
            privateGroupId = objectMapper.readValue(groupResult.response.contentAsString, GroupResponse::class.java).id.toString()
        }

        @Test
        @Order(1)
        fun `user 1 is assigned as group owner upon creation`() {
            mockMvc.perform(
                get("/api/users/me/groups")
                    .header("Authorization", "Bearer $user1Token")
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$[?(@.group.id == '$privateGroupId')].role").value("OWNER"))
        }

        @Test
        @Order(2)
        fun `user 2 requests to join the private group`() {
            mockMvc.perform(
                post("/api/tournaments/{tournamentId}/groups/{groupId}/join", testTournamentId, privateGroupId)
                    .header("Authorization", "Bearer $user2Token")
            )
                .andExpect(status().isCreated)
        }

        @Test
        @Order(3)
        fun `user 2 requests to join again and fails because already requested`() {
            mockMvc.perform(
                post("/api/tournaments/{tournamentId}/groups/{groupId}/join", testTournamentId, privateGroupId)
                    .header("Authorization", "Bearer $user2Token")
            )
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.message").value("You are already candidate to this group"))
        }

        @Test
        @Order(4)
        fun `user 2 request its groups and check its role in the private group is CANDIDATE`() {
            mockMvc.perform(
                get("/api/users/me/groups")
                    .header("Authorization", "Bearer $user2Token")
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$[?(@.group.id == '$privateGroupId')].role").value("CANDIDATE"))
        }

        @Test
        @Order(5)
        fun `user 2 cannot get group standings while join request is pending`() {
            mockMvc.perform(
                get("/api/tournaments/{tournamentId}/groups/{groupId}", testTournamentId, privateGroupId)
                    .header("Authorization", "Bearer $user2Token")
            )
                .andExpect(status().isForbidden)
        }

        @Test
        @Order(6)
        fun `user 2 cannot submit predictions while join request is pending`() {
            val request = SubmitMatchPredictionRequest(matchId = matchId, homeGoals = 1, awayGoals = 0)
            mockMvc.perform(
                post(
                    "/api/tournaments/{tournamentId}/groups/{groupId}/predictions/matches/{matchId}",
                    testTournamentId, privateGroupId, matchId
                )
                    .header("Authorization", "Bearer $user2Token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isForbidden)
                .andExpect(jsonPath("$.message").value("User doesn't belong to the group"))
        }

        @Test
        @Order(7)
        fun `user 1 accepts user 2 into the group`() {
            mockMvc.perform(
                put("/api/tournaments/{tournamentId}/groups/{groupId}/members/{candidateId}/accept", testTournamentId, privateGroupId, user2Id)
                    .header("Authorization", "Bearer $user1Token")
            )
                .andExpect(status().isOk)
        }

        @Test
        @Order(8)
        fun `user 2 can get group standings after being accepted`() {
            mockMvc.perform(
                get("/api/tournaments/{tournamentId}/groups/{groupId}", testTournamentId, privateGroupId)
                    .header("Authorization", "Bearer $user2Token")
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.id").value(privateGroupId))
        }

        @Test
        @Order(9)
        fun `user 2 can submit predictions after being accepted`() {
            val request = SubmitMatchPredictionRequest(matchId = matchId, homeGoals = 1, awayGoals = 0)
            mockMvc.perform(
                post(
                    "/api/tournaments/{tournamentId}/groups/{groupId}/predictions/matches/{matchId}",
                    testTournamentId, privateGroupId, matchId
                )
                    .header("Authorization", "Bearer $user2Token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isNotFound)
                .andExpect(jsonPath("$.message").value("Match not found"))
        }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    @TestMethodOrder(MethodOrderer.OrderAnnotation::class)
    inner class PrivateGroupRejectionFlowTests {

        private var user2Id: String? = null
        private var user2Token: String? = null
        private var privateGroupId: String? = null
        private val matchId: UUID = UUID.randomUUID()

        @BeforeAll
        fun setUp() {
            val user2Result = mockMvc.perform(
                post("/api/users")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(createTestingUserRequest()))
            ).andReturn()
            val user2Response = objectMapper.readValue(user2Result.response.contentAsString, AuthenticatedUserResponse::class.java)
            user2Token = user2Response.token
            user2Id = user2Response.userId.toString()

            val groupResult = mockMvc.perform(
                post("/api/tournaments/{tournamentId}/groups", testTournamentId)
                    .header("Authorization", "Bearer $user1Token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(CreateGroupRequest(name = "Private Group Case 2", isPrivate = true, maxMembers = 10)))
            ).andReturn()
            privateGroupId = objectMapper.readValue(groupResult.response.contentAsString, GroupResponse::class.java).id.toString()
        }

        @Test
        @Order(1)
        fun `user 1 is assigned as group owner upon creation`() {
            mockMvc.perform(
                get("/api/users/me/groups")
                    .header("Authorization", "Bearer $user1Token")
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$[?(@.group.id == '$privateGroupId')].role").value("OWNER"))
        }

        @Test
        @Order(2)
        fun `user 2 requests to join the private group`() {
            mockMvc.perform(
                post("/api/tournaments/{tournamentId}/groups/{groupId}/join", testTournamentId, privateGroupId)
                    .header("Authorization", "Bearer $user2Token")
            )
                .andExpect(status().isCreated)
        }

        @Test
        @Order(3)
        fun `user 2 cannot get group standings while join request is pending`() {
            mockMvc.perform(
                get("/api/tournaments/{tournamentId}/groups/{groupId}", testTournamentId, privateGroupId)
                    .header("Authorization", "Bearer $user2Token")
            )
                .andExpect(status().isForbidden)
        }

        @Test
        @Order(4)
        fun `user 2 cannot submit predictions while join request is pending`() {
            val request = SubmitMatchPredictionRequest(matchId = matchId, homeGoals = 1, awayGoals = 0)
            mockMvc.perform(
                post(
                    "/api/tournaments/{tournamentId}/groups/{groupId}/predictions/matches/{matchId}",
                    testTournamentId, privateGroupId, matchId
                )
                    .header("Authorization", "Bearer $user2Token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isForbidden)
                .andExpect(jsonPath("$.message").value("User doesn't belong to the group"))
        }

        @Test
        @Order(5)
        fun `user 1 rejects user 2 from the group`() {
            mockMvc.perform(
                delete(
                    "/api/tournaments/{tournamentId}/groups/{groupId}/members/{candidateId}/reject",
                    testTournamentId, privateGroupId, user2Id
                )
                    .header("Authorization", "Bearer $user1Token")
            )
                .andExpect(status().isNoContent)
        }

        @Test
        @Order(6)
        fun `user 2 cannot get group standings after being rejected`() {
            mockMvc.perform(
                get("/api/tournaments/{tournamentId}/groups/{groupId}", testTournamentId, privateGroupId)
                    .header("Authorization", "Bearer $user2Token")
            )
                .andExpect(status().isForbidden)
        }

        @Test
        @Order(7)
        fun `user 2 cannot submit predictions after being rejected`() {
            val request = SubmitMatchPredictionRequest(matchId = matchId, homeGoals = 1, awayGoals = 0)
            mockMvc.perform(
                post(
                    "/api/tournaments/{tournamentId}/groups/{groupId}/predictions/matches/{matchId}",
                    testTournamentId, privateGroupId, matchId
                )
                    .header("Authorization", "Bearer $user2Token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isForbidden)
                .andExpect(jsonPath("$.message").value("User doesn't belong to the group"))
        }

        @Test
        @Order(8)
        fun `user 2 can request to join again after being rejected`() {
            mockMvc.perform(
                post("/api/tournaments/{tournamentId}/groups/{groupId}/join", testTournamentId, privateGroupId)
                    .header("Authorization", "Bearer $user2Token")
            )
                .andExpect(status().isCreated)
        }

        @Test
        @Order(9)
        fun `user 2 still cannot get group standings after re-requesting`() {
            mockMvc.perform(
                get("/api/tournaments/{tournamentId}/groups/{groupId}", testTournamentId, privateGroupId)
                    .header("Authorization", "Bearer $user2Token")
            )
                .andExpect(status().isForbidden)
        }

        @Test
        @Order(10)
        fun `user 2 still cannot submit predictions after re-requesting`() {
            val request = SubmitMatchPredictionRequest(matchId = matchId, homeGoals = 1, awayGoals = 0)
            mockMvc.perform(
                post(
                    "/api/tournaments/{tournamentId}/groups/{groupId}/predictions/matches/{matchId}",
                    testTournamentId, privateGroupId, matchId
                )
                    .header("Authorization", "Bearer $user2Token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isForbidden)
                .andExpect(jsonPath("$.message").value("User doesn't belong to the group"))
        }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    @TestMethodOrder(MethodOrderer.OrderAnnotation::class)
    inner class GroupMembersManagementFlowTests {

        private var user2Id: String? = null
        private var user2Token: String? = null
        private var user3Id: String? = null
        private var user3Token: String? = null
        private var user4Id: String? = null
        private var user4Token: String? = null
        private var user5Id: String? = null
        private var user5Token: String? = null
        private var privateGroupId: String? = null
        private lateinit var match1Id: UUID
        private lateinit var match2Id: UUID

        private fun createTeam(name: String, code: String): UUID {
            val request = CreateTeamRequest(name = name, code = code, icon = code.lowercase())
            val result = mockMvc.perform(
                post("/api/tournaments/{tournamentId}/teams", testTournamentId)
                    .header("Authorization", "Bearer $adminToken")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isCreated)
                .andReturn()

            return UUID.fromString(objectMapper.readTree(result.response.contentAsString).get("id").asText())
        }

        private fun createMatch(code: String, homeTeamId: String, awayTeamId: String, startedAt: LocalDateTime): UUID {
            val request = CreateMatchRequest(
                code = code,
                homeTeam = UUID.fromString(homeTeamId),
                awayTeam = UUID.fromString(awayTeamId),
                startedAt = startedAt
            )
            val result = mockMvc.perform(
                post("/api/tournaments/{tournamentId}/matches", testTournamentId)
                    .header("Authorization", "Bearer $adminToken")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isCreated)
                .andReturn()

            return UUID.fromString(objectMapper.readTree(result.response.contentAsString).get("id").asText())
        }

        private fun submitGroupBulkMatchPredictions(
            token: String?,
            match1HomeGoals: Int,
            match1AwayGoals: Int,
            match2HomeGoals: Int,
            match2AwayGoals: Int
        ) {
            val request = SubmitBulkMatchPredictionsRequest(
                predictions = listOf(
                    SubmitMatchPredictionRequest(matchId = match1Id, homeGoals = match1HomeGoals, awayGoals = match1AwayGoals),
                    SubmitMatchPredictionRequest(matchId = match2Id, homeGoals = match2HomeGoals, awayGoals = match2AwayGoals)
                )
            )

            mockMvc.perform(
                post("/api/tournaments/{tournamentId}/groups/{groupId}/predictions/matches", testTournamentId, privateGroupId)
                    .header("Authorization", "Bearer $token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isCreated)
                .andExpect(jsonPath("$.predictions[?(@.match.id == '$match1Id')].prediction.home_goals").value(match1HomeGoals))
                .andExpect(jsonPath("$.predictions[?(@.match.id == '$match1Id')].prediction.away_goals").value(match1AwayGoals))
                .andExpect(jsonPath("$.predictions[?(@.match.id == '$match2Id')].prediction.home_goals").value(match2HomeGoals))
                .andExpect(jsonPath("$.predictions[?(@.match.id == '$match2Id')].prediction.away_goals").value(match2AwayGoals))
        }

        private fun getMyGroupMatchPredictions(
            token: String?,
            match1HomeGoals: Int,
            match1AwayGoals: Int,
            match2HomeGoals: Int,
            match2AwayGoals: Int
        ) {
            mockMvc.perform(
                get("/api/tournaments/{tournamentId}/groups/{groupId}/predictions/matches/me", testTournamentId, privateGroupId)
                    .header("Authorization", "Bearer $token")
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.predictions.length()").value(2))
                .andExpect(jsonPath("$.predictions[?(@.match.id == '$match1Id')].prediction.home_goals").value(match1HomeGoals))
                .andExpect(jsonPath("$.predictions[?(@.match.id == '$match1Id')].prediction.away_goals").value(match1AwayGoals))
                .andExpect(jsonPath("$.predictions[?(@.match.id == '$match2Id')].prediction.home_goals").value(match2HomeGoals))
                .andExpect(jsonPath("$.predictions[?(@.match.id == '$match2Id')].prediction.away_goals").value(match2AwayGoals))
        }

        private fun assertGroupStandings(token: String?, vararg expectedUserIds: String?) {
            var resultActions = mockMvc.perform(
                get("/api/tournaments/{tournamentId}/groups/{groupId}", testTournamentId, privateGroupId)
                    .header("Authorization", "Bearer $token")
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.id").value(privateGroupId))
                .andExpect(jsonPath("$.standings.length()").value(expectedUserIds.size))

            expectedUserIds.forEach { userId ->
                resultActions = resultActions
                    .andExpect(jsonPath("$.standings[?(@.user.id == '$userId')].points").value(0.0))
            }
        }

        @BeforeAll
        fun setUp() {
            val user2Result = mockMvc.perform(
                post("/api/users")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(createTestingUserRequest()))
            ).andReturn()
            val user2Response = objectMapper.readValue(user2Result.response.contentAsString, AuthenticatedUserResponse::class.java)
            user2Token = user2Response.token
            user2Id = user2Response.userId.toString()

            val user3Result = mockMvc.perform(
                post("/api/users")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(createTestingUserRequest()))
            ).andReturn()
            val user3Response = objectMapper.readValue(user3Result.response.contentAsString, AuthenticatedUserResponse::class.java)
            user3Token = user3Response.token
            user3Id = user3Response.userId.toString()

            val user4Result = mockMvc.perform(
                post("/api/users")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(createTestingUserRequest()))
            ).andReturn()
            val user4Response = objectMapper.readValue(user4Result.response.contentAsString, AuthenticatedUserResponse::class.java)
            user4Token = user4Response.token
            user4Id = user4Response.userId.toString()

            val user5Result = mockMvc.perform(
                post("/api/users")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(createTestingUserRequest()))
            ).andReturn()
            val user5Response = objectMapper.readValue(user5Result.response.contentAsString, AuthenticatedUserResponse::class.java)
            user5Token = user5Response.token
            user5Id = user5Response.userId.toString()

            val groupResult = mockMvc.perform(
                post("/api/tournaments/{tournamentId}/groups", testTournamentId)
                    .header("Authorization", "Bearer $user1Token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(CreateGroupRequest(name = "Public Group", maxMembers = 10)))
            ).andReturn()
            privateGroupId = objectMapper.readValue(groupResult.response.contentAsString, GroupResponse::class.java).id.toString()

            val team1Id = createTeam("Flow Team One", "FLOW1").toString()
            val team2Id = createTeam("Flow Team Two", "FLOW2").toString()
            match1Id = createMatch("FLOW-1", team1Id, team2Id, LocalDateTime.now().plusDays(10))
            match2Id = createMatch("FLOW-2", team2Id, team1Id, LocalDateTime.now().plusDays(11))
        }

        @Test
        @Order(1)
        fun `user 1 is assigned as group owner upon creation`() {
            mockMvc.perform(
                get("/api/users/me/groups")
                    .header("Authorization", "Bearer $user1Token")
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$[?(@.group.id == '$privateGroupId')].role").value("OWNER"))
        }

        @Test
        @Order(2)
        fun `user 2 joins the public group`() {
            mockMvc.perform(
                post("/api/tournaments/{tournamentId}/groups/{groupId}/join", testTournamentId, privateGroupId)
                    .header("Authorization", "Bearer $user2Token")
            )
                .andExpect(status().isCreated)
        }

        @Test
        @Order(3)
        fun `user 2 can submit predictions`() {
            submitGroupBulkMatchPredictions(user2Token, 1, 0, 2, 2)
        }

        @Test
        @Order(4)
        fun `user 2 can check group standings`() {
            assertGroupStandings(user2Token, user1Id, user2Id)
        }

        @Test
        @Order(5)
        fun `user 3 joins the public group`() {
            mockMvc.perform(
                post("/api/tournaments/{tournamentId}/groups/{groupId}/join", testTournamentId, privateGroupId)
                    .header("Authorization", "Bearer $user3Token")
            )
                .andExpect(status().isCreated)
        }

        @Test
        @Order(6)
        fun `user 3 can submit predictions`() {
            submitGroupBulkMatchPredictions(user3Token, 0, 1, 3, 1)
        }

        @Test
        @Order(7)
        fun `user 3 can check group standings`() {
            assertGroupStandings(user3Token, user1Id, user2Id, user3Id)
        }

        @Test
        @Order(8)
        fun `user 4 joins the public group`() {
            mockMvc.perform(
                post("/api/tournaments/{tournamentId}/groups/{groupId}/join", testTournamentId, privateGroupId)
                    .header("Authorization", "Bearer $user4Token")
            )
                .andExpect(status().isCreated)
        }

        @Test
        @Order(9)
        fun `user 4 can submit predictions`() {
            submitGroupBulkMatchPredictions(user4Token, 2, 1, 0, 0)
        }

        @Test
        @Order(10)
        fun `user 4 can check group standings`() {
            assertGroupStandings(user4Token, user1Id, user2Id, user3Id, user4Id)
        }

        @Test
        @Order(11)
        fun `user 1 updates user 2 to group admin`() {
            mockMvc.perform(
                patch("/api/tournaments/{tournamentId}/groups/{groupId}/members/{memberId}", testTournamentId, privateGroupId, user2Id)
                    .header("Authorization", "Bearer $user1Token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(UpdateMemberRequest(role = GroupRole.ADMIN)))
            )
                .andExpect(status().isOk)
        }

        @Test
        @Order(12)
        fun `user 2 checks its status for the public group and its now admin`() {
            mockMvc.perform(
                get("/api/users/me/groups")
                    .header("Authorization", "Bearer $user2Token")
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$[?(@.group.id == '$privateGroupId')].role").value("ADMIN"))
        }

        @Test
        @Order(13)
        fun `user 1 tries to change user 2 role to owner and fails`() {
            mockMvc.perform(
                patch("/api/tournaments/{tournamentId}/groups/{groupId}/members/{memberId}", testTournamentId, privateGroupId, user2Id)
                    .header("Authorization", "Bearer $user1Token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(UpdateMemberRequest(role = GroupRole.OWNER)))
            )
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.message").value("Cannot change role to OWNER or CANDIDATE"))
        }

        @Test
        @Order(14)
        fun `user 1 tries to change user 2 role to candidate and fails`() {
            mockMvc.perform(
                patch("/api/tournaments/{tournamentId}/groups/{groupId}/members/{memberId}", testTournamentId, privateGroupId, user2Id)
                    .header("Authorization", "Bearer $user1Token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(UpdateMemberRequest(role = GroupRole.CANDIDATE)))
            )
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.message").value("Cannot change role to OWNER or CANDIDATE"))
        }

        @Test
        @Order(15)
        fun `user 2 updates user 3 to group admin`() {
            mockMvc.perform(
                patch("/api/tournaments/{tournamentId}/groups/{groupId}/members/{memberId}", testTournamentId, privateGroupId, user3Id)
                    .header("Authorization", "Bearer $user2Token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(UpdateMemberRequest(role = GroupRole.ADMIN)))
            )
                .andExpect(status().isOk)
        }

        @Test
        @Order(16)
        fun `user 2 tries to kicks user 3 from the group and fails`() {
            mockMvc.perform(
                delete("/api/tournaments/{tournamentId}/groups/{groupId}/members/{memberId}", testTournamentId, privateGroupId, user3Id)
                    .header("Authorization", "Bearer $user2Token")
            )
                .andExpect(status().isForbidden)
                .andExpect(jsonPath("$.message").value("You have no access to perform this action"))
        }

        @Test
        @Order(17)
        fun `user 2 kicks user 4 from the group`() {
            mockMvc.perform(
                delete("/api/tournaments/{tournamentId}/groups/{groupId}/members/{memberId}", testTournamentId, privateGroupId, user4Id)
                    .header("Authorization", "Bearer $user2Token")
            )
                .andExpect(status().isNoContent)
        }

        @Test
        @Order(18)
        fun `user 2 checks standings table and there are now 3 users`() {
            assertGroupStandings(user1Token, user1Id, user2Id, user3Id)
        }

        @Test
        @Order(19)
        fun `user 4 cannot get group since is no longer member`() {
            mockMvc.perform(
                get("/api/tournaments/{tournamentId}/groups/{groupId}", testTournamentId, privateGroupId)
                    .header("Authorization", "Bearer $user4Token")
            )
                .andExpect(status().isForbidden)
                .andExpect(jsonPath("$.message").value("User not allowed"))
        }

        @Test
        @Order(20)
        fun `user 2 tries to kick user 1 from the group and fails`() {
            mockMvc.perform(
                delete("/api/tournaments/{tournamentId}/groups/{groupId}/members/{memberId}", testTournamentId, privateGroupId, user1Id)
                    .header("Authorization", "Bearer $user2Token")
            )
                .andExpect(status().isForbidden)
                .andExpect(jsonPath("$.message").value("You have no access to perform this action"))
        }

        @Test
        @Order(21)
        fun `user 1 kicks user 3 from the group`() {
            mockMvc.perform(
                delete("/api/tournaments/{tournamentId}/groups/{groupId}/members/{memberId}", testTournamentId, privateGroupId, user3Id)
                    .header("Authorization", "Bearer $user1Token")
            )
                .andExpect(status().isNoContent)
        }

        @Test
        @Order(22)
        fun `user 3 cannot get group since is no longer member`() {
            mockMvc.perform(
                get("/api/tournaments/{tournamentId}/groups/{groupId}", testTournamentId, privateGroupId)
                    .header("Authorization", "Bearer $user3Token")
            )
                .andExpect(status().isForbidden)
                .andExpect(jsonPath("$.message").value("User not allowed"))
        }

        @Test
        @Order(23)
        fun `user 1 checks standings table and there are only 2 users`() {
            assertGroupStandings(user1Token, user1Id, user2Id)
        }

        @Test
        @Order(24)
        fun `user 1 kicks user 2 from the group`() {
            mockMvc.perform(
                delete("/api/tournaments/{tournamentId}/groups/{groupId}/members/{memberId}", testTournamentId, privateGroupId, user2Id)
                    .header("Authorization", "Bearer $user1Token")
            )
                .andExpect(status().isNoContent)
        }

        @Test
        @Order(25)
        fun `user 2 cannot get group since is no longer member`() {
            mockMvc.perform(
                get("/api/tournaments/{tournamentId}/groups/{groupId}", testTournamentId, privateGroupId)
                    .header("Authorization", "Bearer $user2Token")
            )
                .andExpect(status().isForbidden)
                .andExpect(jsonPath("$.message").value("User not allowed"))
        }

        @Test
        @Order(26)
        fun `user 1 cannot update its own data`() {
            mockMvc.perform(
                patch("/api/tournaments/{tournamentId}/groups/{groupId}/members/{memberId}", testTournamentId, privateGroupId, user1Id)
                    .header("Authorization", "Bearer $user1Token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(UpdateMemberRequest(role = GroupRole.ADMIN)))
            )
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.message").value("You cannot update your own role or data"))
        }

        @Test
        @Order(27)
        fun `user 2 rejoins the group`() {
            mockMvc.perform(
                post("/api/tournaments/{tournamentId}/groups/{groupId}/join", testTournamentId, privateGroupId)
                    .header("Authorization", "Bearer $user2Token")
            )
                .andExpect(status().isCreated)
        }

        @Test
        @Order(28)
        fun `user 2 checks its old predictions are still there`() {
            getMyGroupMatchPredictions(user2Token, 1, 0, 2, 2)
        }

        @Test
        @Order(29)
        fun `user 3 rejoins the group`() {
            mockMvc.perform(
                post("/api/tournaments/{tournamentId}/groups/{groupId}/join", testTournamentId, privateGroupId)
                    .header("Authorization", "Bearer $user3Token")
            )
                .andExpect(status().isCreated)
        }

        @Test
        @Order(30)
        fun `user 3 checks its old predictions are still there`() {
            getMyGroupMatchPredictions(user3Token, 0, 1, 3, 1)
        }

        @Test
        @Order(31)
        fun `user 4 rejoins the group`() {
            mockMvc.perform(
                post("/api/tournaments/{tournamentId}/groups/{groupId}/join", testTournamentId, privateGroupId)
                    .header("Authorization", "Bearer $user4Token")
            )
                .andExpect(status().isCreated)
        }

        @Test
        @Order(32)
        fun `user 4 checks its old predictions are still there`() {
            getMyGroupMatchPredictions(user4Token, 2, 1, 0, 0)
        }

        @Test
        @Order(33)
        fun `user 1 updates user 4 to group admin`() {
            mockMvc.perform(
                patch("/api/tournaments/{tournamentId}/groups/{groupId}/members/{memberId}", testTournamentId, privateGroupId, user4Id)
                    .header("Authorization", "Bearer $user1Token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(UpdateMemberRequest(role = GroupRole.ADMIN)))
            )
                .andExpect(status().isOk)
        }

        @Test
        @Order(34)
        fun `user 4 is now assigned as group admin`() {
            mockMvc.perform(
                get("/api/users/me/groups")
                    .header("Authorization", "Bearer $user4Token")
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$[?(@.group.id == '$privateGroupId')].role").value("ADMIN"))
        }

        @Test
        @Order(35)
        fun `user 1 leaves the group`() {
            mockMvc.perform(
                delete("/api/tournaments/{tournamentId}/groups/{groupId}/leave", testTournamentId, privateGroupId)
                    .header("Authorization", "Bearer $user1Token")
            )
                .andExpect(status().isNoContent)
        }

        @Test
        @Order(36)
        fun `user 4 is now assigned as group owner`() {
            mockMvc.perform(
                get("/api/users/me/groups")
                    .header("Authorization", "Bearer $user4Token")
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$[?(@.group.id == '$privateGroupId')].role").value("OWNER"))
        }

        @Test
        @Order(37)
        fun `user 4 leaves the group`() {
            mockMvc.perform(
                delete("/api/tournaments/{tournamentId}/groups/{groupId}/leave", testTournamentId, privateGroupId)
                    .header("Authorization", "Bearer $user4Token")
            )
                .andExpect(status().isNoContent)
        }

        @Test
        @Order(38)
        fun `user 2 is now assigned as group owner`() {
            mockMvc.perform(
                get("/api/users/me/groups")
                    .header("Authorization", "Bearer $user2Token")
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$[?(@.group.id == '$privateGroupId')].role").value("OWNER"))
        }
    }
}
