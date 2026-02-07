package com.grondona.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.grondona.exception.ConflictException
import com.grondona.exception.GlobalExceptionHandler
import com.grondona.exception.NotFoundException
import com.grondona.model.dto.*
import com.grondona.security.JwtUserPrincipal
import com.grondona.service.UserService
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.Runs
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.core.MethodParameter
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.bind.support.WebDataBinderFactory
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.method.support.ModelAndViewContainer
import java.time.LocalDateTime
import java.util.*

class UserControllerTest {

    private lateinit var mockMvc: MockMvc
    private lateinit var userService: UserService
    private lateinit var userController: UserController
    private lateinit var objectMapper: ObjectMapper

    private val testUserId = UUID.randomUUID()
    private val testToken = "test.jwt.token"

    // Custom argument resolver to inject JwtUserPrincipal in tests
    private inner class TestPrincipalArgumentResolver : HandlerMethodArgumentResolver {
        override fun supportsParameter(parameter: MethodParameter): Boolean {
            return parameter.parameterType == JwtUserPrincipal::class.java
        }

        override fun resolveArgument(
            parameter: MethodParameter,
            mavContainer: ModelAndViewContainer?,
            webRequest: NativeWebRequest,
            binderFactory: WebDataBinderFactory?
        ): Any? {
            val auth = SecurityContextHolder.getContext().authentication
            return auth?.principal as? JwtUserPrincipal
        }
    }

    @BeforeEach
    fun setUp() {
        userService = mockk()
        userController = UserController(userService)
        objectMapper = ObjectMapper()
        mockMvc = MockMvcBuilders
            .standaloneSetup(userController)
            .setControllerAdvice(GlobalExceptionHandler())
            .setCustomArgumentResolvers(TestPrincipalArgumentResolver())
            .build()
    }

    private fun setAuthenticatedUser(userId: UUID, username: String) {
        val principal = JwtUserPrincipal(userId, username)
        val auth = UsernamePasswordAuthenticationToken(principal, null, emptyList())
        SecurityContextHolder.getContext().authentication = auth
    }

    private fun clearAuthentication() {
        SecurityContextHolder.clearContext()
    }

    @Nested
    inner class CreateUserEndpointTests {

        @Test
        fun `POST api users should return 201 when user created successfully`() {
            // Given
            val request = CreateUserRequest(
                fullname = "Test User",
                username = "testuser",
                email = "test@example.com",
                password = "password123"
            )
            val response = AuthResponse(
                token = testToken,
                userId = testUserId,
                username = request.username,
                email = request.email,
                fullname = request.fullname
            )
            every { userService.createUser(any()) } returns response

            // When/Then
            mockMvc.perform(
                post("/api/users")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isCreated)
                .andExpect(jsonPath("$.token").value(testToken))
                .andExpect(jsonPath("$.userId").value(testUserId.toString()))
                .andExpect(jsonPath("$.username").value(request.username))
        }

        @Test
        fun `POST api users should return 409 when username exists`() {
            // Given
            val request = CreateUserRequest(
                fullname = "Test User",
                username = "existinguser",
                email = "test@example.com",
                password = "password123"
            )
            every { userService.createUser(any()) } throws ConflictException("Username 'existinguser' already exists")

            // When/Then
            mockMvc.perform(
                post("/api/users")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isConflict)
        }
    }

    @Nested
    inner class LoginEndpointTests {

        @Test
        fun `POST api users login should return 200 with token on successful login`() {
            // Given
            val request = LoginRequest(username = "testuser", password = "password123")
            val response = AuthResponse(
                token = testToken,
                userId = testUserId,
                username = request.username,
                email = "test@example.com",
                fullname = "Test User"
            )
            every { userService.login(any()) } returns response

            // When/Then
            mockMvc.perform(
                post("/api/users/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.token").value(testToken))
                .andExpect(jsonPath("$.username").value(request.username))
        }
    }

    @Nested
    inner class UpdateUserEndpointTests {

        @Test
        fun `PATCH api users should return 200 when user updated successfully`() {
            // Given
            setAuthenticatedUser(testUserId, "testuser")
            val request = UpdateUserRequest(fullname = "Updated Name")
            val response = UserResponse(
                id = testUserId,
                fullname = "Updated Name",
                username = "testuser",
                email = "test@example.com",
                createdAt = LocalDateTime.now(),
                updatedAt = LocalDateTime.now()
            )
            every { userService.updateUser(testUserId, any()) } returns response

            // When/Then
            mockMvc.perform(
                patch("/api/users")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.fullname").value("Updated Name"))

            clearAuthentication()
        }
    }

    @Nested
    inner class DeleteUserEndpointTests {

        @Test
        fun `DELETE api users userId should return 204 when user deleted successfully`() {
            // Given
            setAuthenticatedUser(testUserId, "testuser")
            every { userService.deleteUser(testUserId, testUserId) } just Runs

            // When/Then
            mockMvc.perform(
                delete("/api/users/{userId}", testUserId)
            )
                .andExpect(status().isNoContent)

            clearAuthentication()
        }
    }

    @Nested
    inner class GetCurrentUserEndpointTests {

        @Test
        fun `GET api users me should return 200 with user details`() {
            // Given
            setAuthenticatedUser(testUserId, "testuser")
            val response = UserResponse(
                id = testUserId,
                fullname = "Test User",
                username = "testuser",
                email = "test@example.com",
                createdAt = LocalDateTime.now(),
                updatedAt = LocalDateTime.now()
            )
            every { userService.getUserById(testUserId) } returns response

            // When/Then
            mockMvc.perform(
                get("/api/users/me")
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.id").value(testUserId.toString()))
                .andExpect(jsonPath("$.username").value("testuser"))

            clearAuthentication()
        }

        @Test
        fun `GET api users me should return 404 when user not found`() {
            // Given
            setAuthenticatedUser(testUserId, "testuser")
            every { userService.getUserById(testUserId) } throws NotFoundException("User not found")

            // When/Then
            mockMvc.perform(
                get("/api/users/me")
            )
                .andExpect(status().isNotFound)

            clearAuthentication()
        }
    }
}
