package com.grondona.integration

import com.fasterxml.jackson.databind.ObjectMapper
import com.grondona.createTestingTournamentRequest
import com.grondona.createTestingUserRequest
import com.grondona.model.UserPermissions
import com.grondona.model.dto.request.CreateGroupRequest
import com.grondona.model.dto.request.SubmitMatchPredictionRequest
import com.grondona.model.dto.request.UpdateGroupRequest
import com.grondona.model.dto.response.AuthenticatedUserResponse
import com.grondona.model.dto.response.GroupResponse
import com.grondona.model.dto.response.TournamentResponse
import com.grondona.repository.GroupRepository
import com.grondona.repository.MembershipRepository
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

    private var authToken: String? = null
    private var createdGroupId: String? = null
    private var testTournamentId: String? = null

    @BeforeAll
    fun setUp() {
        groupRepository.deleteAll()
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
        adminUser.permissions = UserPermissions.SUPERUSER
        userRepository.save(adminUser)
        val adminToken = objectMapper.readValue(adminResult.response.contentAsString, AuthenticatedUserResponse::class.java).token

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
        authToken = authResponse.token
    }

    @AfterAll
    fun tearDown() {
        membershipRepository.deleteAll()
        groupRepository.deleteAll()
        userRepository.deleteAll()
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
                    .header("Authorization", "Bearer $authToken")
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
                    .header("Authorization", "Bearer $authToken")
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
                    .header("Authorization", "Bearer $authToken")
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
                    .header("Authorization", "Bearer $authToken")
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
                    .header("Authorization", "Bearer $authToken")
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
                    .header("Authorization", "Bearer $authToken")
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
                    .header("Authorization", "Bearer $authToken")
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
                    .header("Authorization", "Bearer $authToken")
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
                    .header("Authorization", "Bearer $authToken")
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
                    .header("Authorization", "Bearer $authToken")
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.length()").value(2))
        }

        @Test
        @Order(11)
        fun `should delete the group successfully`() {
            mockMvc.perform(
                delete("/api/tournaments/{tournamentId}/groups/{groupId}", testTournamentId, createdGroupId)
                    .header("Authorization", "Bearer $authToken")
            )
                .andExpect(status().isNoContent)

            // Verify it's gone
            mockMvc.perform(
                get("/api/tournaments/{tournamentId}/groups/{groupId}", testTournamentId, createdGroupId)
                    .header("Authorization", "Bearer $authToken")
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
                    .header("Authorization", "Bearer $authToken")
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
                    .header("Authorization", "Bearer $authToken")
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
                    .header("Authorization", "Bearer $authToken")
            )
                .andExpect(status().isNotFound)
                .andExpect(jsonPath("$.message").value("Group not found"))
        }

        @Test
        fun `should return 404 when deleting non existent group`() {
            val nonExistentId = UUID.randomUUID()

            mockMvc.perform(
                delete("/api/tournaments/{tournamentId}/groups/{groupId}", testTournamentId, nonExistentId)
                    .header("Authorization", "Bearer $authToken")
            )
                .andExpect(status().isNotFound)
        }
    }

    /**
     * Case 1: Private group flow where User 2's join request is accepted.
     *
     * 1. User 1 creates a private group and is assigned as owner.
     * 2. User 2 requests to join.
     * 3. User 2 requests to join a second time and fails (already requested).
     * 4. User 2 cannot access the group standings while pending.
     * 5. User 2 cannot submit predictions while pending.
     * 6. User 1 accepts User 2.
     * 7. User 2 can now access the group standings.
     * 8. User 2 can now submit predictions.
     */
    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    @TestMethodOrder(MethodOrderer.OrderAnnotation::class)
    inner class PrivateGroupAcceptanceFlowTests {

        private var user2Token: String? = null
        private var user2Id: String? = null
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
                    .header("Authorization", "Bearer $authToken")
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
                    .header("Authorization", "Bearer $authToken")
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
                .andExpect(jsonPath("$.message").value("You are already member of this group"))
        }

        @Test
        @Order(4)
        fun `user 2 cannot get group standings while join request is pending`() {
            mockMvc.perform(
                get("/api/tournaments/{tournamentId}/groups/{groupId}", testTournamentId, privateGroupId)
                    .header("Authorization", "Bearer $user2Token")
            )
                .andExpect(status().isForbidden)
        }

        @Test
        @Order(5)
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
        @Order(6)
        fun `user 1 accepts user 2 into the group`() {
            mockMvc.perform(
                put("/api/tournaments/{tournamentId}/groups/{groupId}/accept/{candidateId}", testTournamentId, privateGroupId, user2Id)
                    .header("Authorization", "Bearer $authToken")
            )
                .andExpect(status().isOk)
        }

        @Test
        @Order(7)
        fun `user 2 can get group standings after being accepted`() {
            mockMvc.perform(
                get("/api/tournaments/{tournamentId}/groups/{groupId}", testTournamentId, privateGroupId)
                    .header("Authorization", "Bearer $user2Token")
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.id").value(privateGroupId))
        }

        @Test
        @Order(8)
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

    /**
     * Case 2: Private group flow where User 2's join request is rejected, then re-requested.
     *
     * 1. User 1 creates a private group and is assigned as owner.
     * 2. User 2 requests to join.
     * 3. User 2 cannot access the group standings while pending.
     * 4. User 2 cannot submit predictions while pending.
     * 5. User 1 rejects User 2.
     * 6. User 2 still cannot access the group standings after rejection.
     * 7. User 2 still cannot submit predictions after rejection.
     * 8. User 2 requests to join again (succeeds, becomes CANDIDATE again).
     * 9. User 2 still cannot access the group standings (pending again, no access yet).
     * 10. User 2 still cannot submit predictions (pending again, no access yet).
     */
    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    @TestMethodOrder(MethodOrderer.OrderAnnotation::class)
    inner class PrivateGroupRejectionFlowTests {

        private var user2Token: String? = null
        private var user2Id: String? = null
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
                    .header("Authorization", "Bearer $authToken")
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
                    .header("Authorization", "Bearer $authToken")
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
                    "/api/tournaments/{tournamentId}/groups/{groupId}/reject/{candidateId}",
                    testTournamentId, privateGroupId, user2Id
                )
                    .header("Authorization", "Bearer $authToken")
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
}
