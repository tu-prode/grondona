package com.grondona.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.grondona.exception.ConflictException
import com.grondona.exception.GlobalExceptionHandler
import com.grondona.exception.NotFoundException
import com.grondona.model.dto.request.CreateUserRequest
import com.grondona.model.dto.request.LoginUserRequest
import com.grondona.model.dto.request.UpdateUserRequest
import com.grondona.model.dto.response.AuthenticatedUserResponse
import com.grondona.model.dto.response.MembershipResponse
import com.grondona.model.dto.response.UserResponse
import com.grondona.security.JwtUserPrincipal
import com.grondona.service.MembershipService
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
import java.util.*

class UserControllerTest {

    private lateinit var mockMvc: MockMvc
    private lateinit var userService: UserService
    private lateinit var groupMembershipService: MembershipService
    private lateinit var userController: UserController
    private lateinit var objectMapper: ObjectMapper

    private val testUserId = UUID.randomUUID()
    private val testToken = "test.jwt.token"

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
        groupMembershipService = mockk()
        userController = UserController(userService, groupMembershipService)
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
            val request = CreateUserRequest(
                fullname = "Test User",
                username = "testuser",
                email = "test@example.com",
                password = "password123"
            )
            val response = AuthenticatedUserResponse(
                token = testToken,
                userId = testUserId,
                username = request.username,
                email = request.email,
                fullname = request.fullname
            )
            every { userService.createUser(any()) } returns response

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
            val request = CreateUserRequest(
                fullname = "Test User",
                username = "existinguser",
                email = "test@example.com",
                password = "password123"
            )
            every { userService.createUser(any()) } throws ConflictException(
                message = "Username 'existinguser' already exists",
                field = "username",
                rejectedValue = "existinguser"
            )

            mockMvc.perform(
                post("/api/users")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isConflict)
                .andExpect(jsonPath("$.data.field").value("username"))
                .andExpect(jsonPath("$.data.rejectedValue").value("existinguser"))
        }
    }

    @Nested
    inner class LoginEndpointTests {

        @Test
        fun `POST api users login should return 200 with token on successful login`() {
            val request = LoginUserRequest(user = "testuser", password = "password123")
            val response = AuthenticatedUserResponse(
                token = testToken,
                userId = testUserId,
                username = request.user,
                email = "test@example.com",
                fullname = "Test User"
            )
            every { userService.login(any()) } returns response

            mockMvc.perform(
                post("/api/users/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.token").value(testToken))
                .andExpect(jsonPath("$.username").value(request.user))
        }
    }

    @Nested
    inner class UpdateUserEndpointTests {

        @Test
        fun `PATCH api users should return 200 when user updated successfully`() {
            setAuthenticatedUser(testUserId, "testuser")
            val request = UpdateUserRequest(fullname = "Updated Name")
            val response = UserResponse(
                id = testUserId,
                fullname = "Updated Name",
                username = "testuser",
                email = "test@example.com",
            )
            every { userService.updateUser(testUserId, any()) } returns response

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
            setAuthenticatedUser(testUserId, "testuser")
            every { userService.deleteUser(testUserId, testUserId) } just Runs

            mockMvc.perform(delete("/api/users/{userId}", testUserId))
                .andExpect(status().isNoContent)

            clearAuthentication()
        }
    }

    @Nested
    inner class GetCurrentUserEndpointTests {

        @Test
        fun `GET api users me should return 200 with user details`() {
            setAuthenticatedUser(testUserId, "testuser")
            val response = UserResponse(
                id = testUserId,
                fullname = "Test User",
                username = "testuser",
                email = "test@example.com",
            )
            every { userService.getUserById(testUserId) } returns response

            mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.id").value(testUserId.toString()))
                .andExpect(jsonPath("$.username").value("testuser"))

            clearAuthentication()
        }

        @Test
        fun `GET api users me should return 404 when user not found`() {
            setAuthenticatedUser(testUserId, "testuser")
            every { userService.getUserById(testUserId) } throws NotFoundException("User not found")

            mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isNotFound)

            clearAuthentication()
        }
    }

    @Nested
    inner class GetMyGroupsEndpointTests {

        @Test
        fun `GET api users me groups should return 200 with list of groups`() {
            setAuthenticatedUser(testUserId, "testuser")
            val groupId = UUID.randomUUID()
            val groups = listOf(
                MembershipResponse(
                    groupId = groupId,
                    groupName = "My Group",
                    memberCount = 5,
                )
            )
            every { groupMembershipService.getMyGroups(testUserId) } returns groups

            mockMvc.perform(get("/api/users/me/groups"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].groupId").value(groupId.toString()))
                .andExpect(jsonPath("$[0].name").value("My Group"))
                .andExpect(jsonPath("$[0].memberCount").value(5))

            clearAuthentication()
        }

        @Test
        fun `GET api users me groups should return empty list when not in any group`() {
            setAuthenticatedUser(testUserId, "testuser")
            every { groupMembershipService.getMyGroups(testUserId) } returns emptyList()

            mockMvc.perform(get("/api/users/me/groups"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.length()").value(0))

            clearAuthentication()
        }
    }
}
