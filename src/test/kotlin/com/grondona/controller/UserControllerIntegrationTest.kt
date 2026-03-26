package com.grondona.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.grondona.model.dto.response.AuthResponse
import com.grondona.model.dto.request.CreateUserRequest
import com.grondona.model.dto.request.LoginRequest
import com.grondona.model.dto.request.UpdateUserRequest
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

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class UserControllerIntegrationTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var userRepository: UserRepository

    private var authToken: String? = null
    private var userId: String? = null

    @BeforeAll
    fun setUp() {
        userRepository.deleteAll()
    }

    @AfterAll
    fun tearDown() {
        userRepository.deleteAll()
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    @TestMethodOrder(MethodOrderer.OrderAnnotation::class)
    inner class UserLifecycleTests {

        @Test
        @Order(1)
        fun `should create a new user successfully`() {
            // Given
            val request = CreateUserRequest(
                fullname = "Integration Test User",
                username = "integrationuser",
                email = "integration@test.com",
                password = "password123"
            )

            // When/Then
            val result = mockMvc.perform(
                post("/api/users")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isCreated)
                .andExpect(jsonPath("$.token").exists())
                .andExpect(jsonPath("$.user_id").exists())
                .andExpect(jsonPath("$.username").value("integrationuser"))
                .andExpect(jsonPath("$.email").value("integration@test.com"))
                .andExpect(jsonPath("$.fullname").value("Integration Test User"))
                .andReturn()

            // Store token and userId for subsequent tests
            val response = objectMapper.readValue(
                result.response.contentAsString,
                AuthResponse::class.java
            )
            authToken = response.token
            userId = response.userId.toString()
        }

        @Test
        @Order(2)
        fun `should fail to create user with duplicate username`() {
            // Given
            val request = CreateUserRequest(
                fullname = "Another User",
                username = "integrationuser", // Same username
                email = "another@test.com",
                password = "password123"
            )

            // When/Then
            mockMvc.perform(
                post("/api/users")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isConflict)
        }

        @Test
        @Order(3)
        fun `should fail to create user with duplicate email`() {
            // Given
            val request = CreateUserRequest(
                fullname = "Another User",
                username = "anotheruser",
                email = "integration@test.com", // Same email
                password = "password123"
            )

            // When/Then
            mockMvc.perform(
                post("/api/users")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isConflict)
        }

        @Test
        @Order(4)
        fun `should login successfully with valid credentials`() {
            // Given
            val request = LoginRequest(
                user = "integrationuser",
                password = "password123"
            )

            // When/Then
            val result = mockMvc.perform(
                post("/api/users/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.token").exists())
                .andExpect(jsonPath("$.username").value("integrationuser"))
                .andReturn()

            // Update token for subsequent tests
            val response = objectMapper.readValue(
                result.response.contentAsString,
                AuthResponse::class.java
            )
            authToken = response.token
        }

        @Test
        @Order(5)
        fun `should fail login with invalid password`() {
            // Given
            val request = LoginRequest(
                user = "integrationuser",
                password = "wrongpassword"
            )

            // When/Then
            mockMvc.perform(
                post("/api/users/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isBadRequest)
        }

        @Test
        @Order(6)
        fun `should get current user profile with valid token`() {
            // When/Then
            mockMvc.perform(
                get("/api/users/me")
                    .header("Authorization", "Bearer $authToken")
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.username").value("integrationuser"))
                .andExpect(jsonPath("$.email").value("integration@test.com"))
        }

        @Test
        @Order(7)
        fun `should fail to get profile without authentication`() {
            // When/Then - Spring Security returns 403 Forbidden for missing authentication
            mockMvc.perform(
                get("/api/users/me")
            )
                .andExpect(status().isForbidden)
        }

        @Test
        @Order(8)
        fun `should update user profile successfully`() {
            // Given
            val request = UpdateUserRequest(
                fullname = "Updated Integration User"
            )

            // When/Then
            mockMvc.perform(
                patch("/api/users")
                    .header("Authorization", "Bearer $authToken")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.fullname").value("Updated Integration User"))
        }

        @Test
        @Order(9)
        fun `should fail to update user without authentication`() {
            // Given
            val request = UpdateUserRequest(fullname = "Should Fail")

            // When/Then - Spring Security returns 403 Forbidden for missing authentication
            mockMvc.perform(
                patch("/api/users")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isForbidden)
        }

        @Test
        @Order(10)
        fun `should fail to delete another user account`() {
            // Given - Create another user first
            val anotherUserRequest = CreateUserRequest(
                fullname = "Another User",
                username = "anotheruser2",
                email = "another2@test.com",
                password = "password123"
            )
            val result = mockMvc.perform(
                post("/api/users")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(anotherUserRequest))
            ).andReturn()

            val anotherUserResponse = objectMapper.readValue(
                result.response.contentAsString,
                AuthResponse::class.java
            )

            // When/Then - Try to delete another user with our token
            mockMvc.perform(
                delete("/api/users/{userId}", anotherUserResponse.userId)
                    .header("Authorization", "Bearer $authToken")
            )
                .andExpect(status().isForbidden)
        }

        @Test
        @Order(11)
        fun `should delete own account successfully`() {
            // When/Then
            mockMvc.perform(
                delete("/api/users/{userId}", userId)
                    .header("Authorization", "Bearer $authToken")
            )
                .andExpect(status().isNoContent)

            // Verify user is deleted
            mockMvc.perform(
                get("/api/users/me")
                    .header("Authorization", "Bearer $authToken")
            )
                .andExpect(status().isNotFound)
        }
    }

    @Nested
    inner class ValidationTests {

        @Test
        fun `should fail to create user with invalid email format`() {
            val request = CreateUserRequest(
                fullname = "Test User",
                username = "validuser",
                email = "invalid-email",
                password = "password123"
            )

            mockMvc.perform(
                post("/api/users")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isBadRequest)
        }

        @Test
        fun `should fail to create user with short password`() {
            val request = CreateUserRequest(
                fullname = "Test User",
                username = "validuser",
                email = "valid@email.com",
                password = "short"
            )

            mockMvc.perform(
                post("/api/users")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isBadRequest)
        }

        @Test
        fun `should fail to create user with short username`() {
            val request = CreateUserRequest(
                fullname = "Test User",
                username = "ab", // Too short
                email = "valid@email.com",
                password = "password123"
            )

            mockMvc.perform(
                post("/api/users")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isBadRequest)
        }

        @Test
        fun `should fail to create user with blank fullname`() {
            val request = mapOf(
                "fullname" to "",
                "username" to "validuser",
                "email" to "valid@email.com",
                "password" to "password123"
            )

            mockMvc.perform(
                post("/api/users")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isBadRequest)
        }
    }

    @Nested
    inner class AuthenticationTests {

        @Test
        fun `should reject request with invalid token`() {
            // Spring Security returns 403 when token is invalid (authentication fails)
            mockMvc.perform(
                get("/api/users/me")
                    .header("Authorization", "Bearer invalid.token.here")
            )
                .andExpect(status().isForbidden)
        }

        @Test
        fun `should reject request with malformed authorization header`() {
            // Spring Security returns 403 when no valid Bearer token is provided
            mockMvc.perform(
                get("/api/users/me")
                    .header("Authorization", "NotBearer sometoken")
            )
                .andExpect(status().isForbidden)
        }
    }
}
