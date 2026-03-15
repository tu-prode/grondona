package com.grondona.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.grondona.model.dto.*
import com.grondona.repository.GroupRepository
import com.grondona.repository.UserRepository
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
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
class GroupControllerIntegrationTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var groupRepository: GroupRepository

    @Autowired
    private lateinit var userRepository: UserRepository

    private var authToken: String? = null
    private var createdGroupId: String? = null

    @BeforeAll
    fun setUp() {
        groupRepository.deleteAll()
        userRepository.deleteAll()

        // Create a user and get auth token for all group tests
        val createUserRequest = CreateUserRequest(
            fullname = "Group Test User",
            username = "grouptestuser",
            email = "grouptest@test.com",
            password = "password123"
        )
        val result = mockMvc.perform(
            post("/api/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createUserRequest))
        ).andReturn()

        val authResponse = objectMapper.readValue(result.response.contentAsString, AuthResponse::class.java)
        authToken = authResponse.token
    }

    @AfterAll
    fun tearDown() {
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
                post("/api/groups")
                    .header("Authorization", "Bearer $authToken")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isCreated)
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.name").value("Integration Group"))
                .andExpect(jsonPath("$.private").value(false))
                .andExpect(jsonPath("$.maxMembers").value(30))
                .andReturn()

            val response = objectMapper.readValue(result.response.contentAsString, GroupResponse::class.java)
            createdGroupId = response.id.toString()
        }

        @Test
        @Order(2)
        fun `should fail to create group with duplicate name`() {
            val request = CreateGroupRequest(name = "Integration Group", isPrivate = false, maxMembers = 10)

            mockMvc.perform(
                post("/api/groups")
                    .header("Authorization", "Bearer $authToken")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isConflict)
                .andExpect(jsonPath("$.field").value("name"))
                .andExpect(jsonPath("$.rejectedValue").value("Integration Group"))
        }

        @Test
        @Order(3)
        fun `should fetch the created group by id`() {
            mockMvc.perform(
                get("/api/groups/{groupId}", createdGroupId)
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
                patch("/api/groups/{groupId}", createdGroupId)
                    .header("Authorization", "Bearer $authToken")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.name").value("Updated Integration Group"))
                .andExpect(jsonPath("$.maxMembers").value(50))
        }

        @Test
        @Order(5)
        fun `should update group privacy`() {
            val request = UpdateGroupRequest(isPrivate = true)

            mockMvc.perform(
                patch("/api/groups/{groupId}", createdGroupId)
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
                get("/api/groups")
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
                post("/api/groups")
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
                get("/api/groups")
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
                get("/api/groups")
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
                get("/api/groups")
                    .header("Authorization", "Bearer $authToken")
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.length()").value(2))
        }

        @Test
        @Order(11)
        fun `should delete the group successfully`() {
            mockMvc.perform(
                delete("/api/groups/{groupId}", createdGroupId)
                    .header("Authorization", "Bearer $authToken")
            )
                .andExpect(status().isNoContent)

            // Verify it's gone
            mockMvc.perform(
                get("/api/groups/{groupId}", createdGroupId)
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
                post("/api/groups")
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
                post("/api/groups")
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
                get("/api/groups/{groupId}", nonExistentId)
                    .header("Authorization", "Bearer $authToken")
            )
                .andExpect(status().isNotFound)
                .andExpect(jsonPath("$.message").value("Grupo no encontrado"))
        }

        @Test
        fun `should return 404 when deleting non existent group`() {
            val nonExistentId = UUID.randomUUID()

            mockMvc.perform(
                delete("/api/groups/{groupId}", nonExistentId)
                    .header("Authorization", "Bearer $authToken")
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
                post("/api/groups")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isForbidden)
        }

        @Test
        fun `should reject get all groups without authentication`() {
            mockMvc.perform(get("/api/groups"))
                .andExpect(status().isForbidden)
        }

        @Test
        fun `should reject get group by id without authentication`() {
            mockMvc.perform(get("/api/groups/{groupId}", UUID.randomUUID()))
                .andExpect(status().isForbidden)
        }

        @Test
        fun `should reject group update without authentication`() {
            val request = UpdateGroupRequest(name = "New Name")

            mockMvc.perform(
                patch("/api/groups/{groupId}", UUID.randomUUID())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isForbidden)
        }

        @Test
        fun `should reject group deletion without authentication`() {
            mockMvc.perform(delete("/api/groups/{groupId}", UUID.randomUUID()))
                .andExpect(status().isForbidden)
        }
    }
}
