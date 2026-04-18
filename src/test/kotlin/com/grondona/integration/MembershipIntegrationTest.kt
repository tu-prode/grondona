package com.grondona.integration

import com.fasterxml.jackson.databind.ObjectMapper
import com.grondona.createTestingTournamentRequest
import com.grondona.createTestingUserRequest
import com.grondona.model.UserPermissions
import com.grondona.model.dto.request.CreateGroupRequest
import com.grondona.model.dto.response.AuthenticatedUserResponse
import com.grondona.model.dto.response.GroupResponse
import com.grondona.model.dto.response.TournamentResponse
import com.grondona.repository.GroupRepository
import com.grondona.repository.MembershipRepository
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
class MembershipIntegrationTest {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var objectMapper: ObjectMapper
    @Autowired private lateinit var groupRepository: GroupRepository
    @Autowired private lateinit var membershipRepository: MembershipRepository
    @Autowired private lateinit var userRepository: UserRepository

    private var authToken: String? = null
    private var secondUserToken: String? = null
    private var testGroupId: String? = null
    private var testTournamentId: String? = null

    @BeforeAll
    fun setUp() {
        membershipRepository.deleteAll()
        groupRepository.deleteAll()
        userRepository.deleteAll()

        // Create primary test user
        val userResult = mockMvc.perform(
            post("/api/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createTestingUserRequest()))
        ).andReturn()
        authToken = objectMapper.readValue(userResult.response.contentAsString, AuthenticatedUserResponse::class.java).token

        // Create second test user
        val secondUserResult = mockMvc.perform(
            post("/api/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createTestingUserRequest()))
        ).andReturn()
        secondUserToken = objectMapper.readValue(secondUserResult.response.contentAsString, AuthenticatedUserResponse::class.java).token

        // Create admin user
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

        // Create test group
        val groupResult = mockMvc.perform(
            post("/api/tournaments/{tournamentId}/groups", testTournamentId)
                .header("Authorization", "Bearer $adminToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    CreateGroupRequest(name = "Membership Test Group", isPrivate = false, maxMembers = 5)
                ))
        ).andReturn()
        testGroupId = objectMapper.readValue(groupResult.response.contentAsString, GroupResponse::class.java).id.toString()
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
    inner class MembershipLifecycleTests {

        @Test
        @Order(1)
        fun `should return empty groups list before joining any group`() {
            mockMvc.perform(
                get("/api/users/me/groups")
                    .header("Authorization", "Bearer $authToken")
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.length()").value(0))
        }

        @Test
        @Order(2)
        fun `should join group successfully`() {
            mockMvc.perform(
                post("/api/tournaments/{tournamentId}/groups/{groupId}/join", testTournamentId, testGroupId)
                    .header("Authorization", "Bearer $authToken")
            )
                .andExpect(status().isCreated)
        }

        @Test
        @Order(3)
        fun `should return group in my groups list after joining`() {
            mockMvc.perform(
                get("/api/users/me/groups")
                    .header("Authorization", "Bearer $authToken")
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].group.id").value(testGroupId))
                .andExpect(jsonPath("$[0].group.name").value("Membership Test Group"))
                .andExpect(jsonPath("$[0].member_count").value(2))
                .andExpect(jsonPath("$[0].points").value(0.0))
                .andExpect(jsonPath("$[0].rank").doesNotExist())
        }

        @Test
        @Order(4)
        fun `should fail to join the same group twice`() {
            mockMvc.perform(
                post("/api/tournaments/{tournamentId}/groups/{groupId}/join", testTournamentId, testGroupId)
                    .header("Authorization", "Bearer $authToken")
            )
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.message").value("You are already member of this group"))
        }

        @Test
        @Order(5)
        fun `second user joins and member count increases`() {
            mockMvc.perform(
                post("/api/tournaments/{tournamentId}/groups/{groupId}/join", testTournamentId, testGroupId)
                    .header("Authorization", "Bearer $secondUserToken")
            )
                .andExpect(status().isCreated)

            // Verify member count is now 2 from first user's perspective
            mockMvc.perform(
                get("/api/users/me/groups")
                    .header("Authorization", "Bearer $authToken")
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$[0].member_count").value(3))
        }

        @Test
        @Order(6)
        fun `should leave group successfully`() {
            mockMvc.perform(
                delete("/api/tournaments/{tournamentId}/groups/{groupId}/leave", testTournamentId, testGroupId)
                    .header("Authorization", "Bearer $authToken")
            )
                .andExpect(status().isNoContent)
        }

        @Test
        @Order(7)
        fun `should return empty groups list after leaving`() {
            mockMvc.perform(
                get("/api/users/me/groups")
                    .header("Authorization", "Bearer $authToken")
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.length()").value(0))
        }

        @Test
        @Order(8)
        fun `should fail to leave a group not joined`() {
            mockMvc.perform(
                delete("/api/tournaments/{tournamentId}/groups/{groupId}/leave", testTournamentId, testGroupId)
                    .header("Authorization", "Bearer $authToken")
            )
                .andExpect(status().isNotFound)
                .andExpect(jsonPath("$.message").value("You are not member of this group"))
        }

        @Test
        @Order(9)
        fun `should automatically join a group after creating it`() {
            val groupResult = mockMvc.perform(
                post("/api/tournaments/{tournamentId}/groups", testTournamentId)
                    .header("Authorization", "Bearer $authToken")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(
                        CreateGroupRequest(name = "New Group to Join", isPrivate = false, maxMembers = 5)
                    ))
            ).andReturn()
            val newGroupId = objectMapper.readValue(groupResult.response.contentAsString, GroupResponse::class.java).id.toString()

            mockMvc.perform(
                get("/api/users/me/groups")
                    .header("Authorization", "Bearer $authToken")
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].group.id").value(newGroupId))
                .andExpect(jsonPath("$[0].group.name").value("New Group to Join"))
                .andExpect(jsonPath("$[0].member_count").value(1))
                .andExpect(jsonPath("$[0].points").value(0.0))
                .andExpect(jsonPath("$[0].role").value("OWNER"))
                .andExpect(jsonPath("$[0].rank").doesNotExist())
        }
    }

    @Nested
    inner class MembershipValidationTests {

        @Test
        fun `should return 404 when joining non existent group`() {
            val nonExistentId = UUID.randomUUID()

            mockMvc.perform(
                post("/api/tournaments/{tournamentId}/groups/{groupId}/join", testTournamentId, nonExistentId)
                    .header("Authorization", "Bearer $authToken")
            )
                .andExpect(status().isNotFound)
                .andExpect(jsonPath("$.message").value("Group not found"))
        }

        @Test
        fun `should return 404 when leaving non existent group`() {
            val nonExistentId = UUID.randomUUID()

            mockMvc.perform(
                delete("/api/tournaments/{tournamentId}/groups/{groupId}/leave", testTournamentId, nonExistentId)
                    .header("Authorization", "Bearer $authToken")
            )
                .andExpect(status().isNotFound)
                .andExpect(jsonPath("$.message").value("Group not found"))
        }

        @Test
        fun `should return 400 when joining a full group`() {
            // First user creates a group and joins it
            val tinyGroupResult = mockMvc.perform(
                post("/api/tournaments/{tournamentId}/groups", testTournamentId)
                    .header("Authorization", "Bearer $authToken")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(
                        CreateGroupRequest(name = "Tiny Group", isPrivate = false, maxMembers = 1)
                    ))
            ).andReturn()
            val tinyGroupId = objectMapper.readValue(tinyGroupResult.response.contentAsString, GroupResponse::class.java).id

            // Second user tries to join → full
            mockMvc.perform(
                post("/api/tournaments/{tournamentId}/groups/{groupId}/join", testTournamentId, tinyGroupId)
                    .header("Authorization", "Bearer $secondUserToken")
            )
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.message").value("Group is full"))
        }
    }

    @Nested
    inner class MembershipAuthenticationTests {

        @Test
        fun `should reject join without authentication`() {
            mockMvc.perform(post("/api/tournaments/{tournamentId}/groups/{groupId}/join", testTournamentId, testGroupId))
                .andExpect(status().isUnauthorized)
        }

        @Test
        fun `should reject leave without authentication`() {
            mockMvc.perform(delete("/api/tournaments/{tournamentId}/groups/{groupId}/leave", testTournamentId, testGroupId))
                .andExpect(status().isUnauthorized)
        }

        @Test
        fun `should reject my groups without authentication`() {
            mockMvc.perform(get("/api/users/me/groups"))
                .andExpect(status().isForbidden)
        }
    }
}
