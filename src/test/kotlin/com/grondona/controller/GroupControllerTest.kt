package com.grondona.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.grondona.exception.BadRequestException
import com.grondona.exception.ConflictException
import com.grondona.exception.GlobalExceptionHandler
import com.grondona.exception.NotFoundException
import com.grondona.model.dto.request.CreateGroupRequest
import com.grondona.model.dto.request.UpdateGroupRequest
import com.grondona.model.dto.response.GroupResponse
import com.grondona.security.JwtUserPrincipal
import com.grondona.service.MembershipService
import com.grondona.service.GroupService
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

class GroupControllerTest {

    private lateinit var mockMvc: MockMvc
    private lateinit var groupService: GroupService
    private lateinit var membershipService: MembershipService
    private lateinit var objectMapper: ObjectMapper

    private val testUserId = UUID.randomUUID()
    private val testTournamentId: UUID = UUID.randomUUID()
    private val testGroupId = UUID.randomUUID()
    private val testGroupResponse = GroupResponse(
        id = testGroupId,
        name = "Test Group",
        isPrivate = false,
        maxMembers = 20,
        totalMembers = 10,
        tournamentId = testTournamentId,
        hasStarted = true,
        standings = emptyList()
    )

    private inner class TestPrincipalArgumentResolver : HandlerMethodArgumentResolver {
        override fun supportsParameter(parameter: MethodParameter): Boolean =
            parameter.parameterType == JwtUserPrincipal::class.java

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
        groupService = mockk()
        membershipService = mockk()
        objectMapper = ObjectMapper().findAndRegisterModules()
        mockMvc = MockMvcBuilders
            .standaloneSetup(GroupController(groupService, membershipService))
            .setControllerAdvice(GlobalExceptionHandler())
            .setCustomArgumentResolvers(TestPrincipalArgumentResolver())
            .build()
    }

    private fun setAuthenticatedUser(userId: UUID) {
        val principal = JwtUserPrincipal(userId, "testuser")
        val auth = UsernamePasswordAuthenticationToken(principal, null, emptyList())
        SecurityContextHolder.getContext().authentication = auth
    }

    private fun clearAuthentication() = SecurityContextHolder.clearContext()

    @Nested
    inner class CreateGroupEndpointTests {
        @Test
        fun `POST api groups should return 201 when group created successfully`() {
            every { groupService.createGroup(any(), any(), any()) } returns testGroupResponse.copy(name = "New Group")
            every { membershipService.joinGroup(any(), any()) } just Runs

            setAuthenticatedUser(testUserId)
            val request = CreateGroupRequest(name = "New Group", isPrivate = false, maxMembers = 10)
            mockMvc.perform(
                post("/api/tournaments/{tournamentId}/groups", testTournamentId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isCreated)
                .andExpect(jsonPath("$.id").value(testGroupId.toString()))
                .andExpect(jsonPath("$.name").value("New Group"))
                .andExpect(jsonPath("$.private").value(false))
                .andExpect(jsonPath("$.maxMembers").value(20))
        }

        @Test
        fun `POST api groups should return 409 when group name already exists`() {
            every { groupService.createGroup(any(), any(), any()) } throws ConflictException(
                message = "Group name already exists",
                field = "name",
                rejectedValue = "Duplicate"
            )

            setAuthenticatedUser(testUserId)
            val request = CreateGroupRequest(name = "Duplicate", isPrivate = false, maxMembers = 10)
            mockMvc.perform(
                post("/api/tournaments/{tournamentId}/groups", testTournamentId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isConflict)
                .andExpect(jsonPath("$.data.field").value("name"))
                .andExpect(jsonPath("$.data.rejectedValue").value("Duplicate"))
        }

        @Test
        fun `POST api groups should return 400 when name is blank`() {
            val body = mapOf("name" to "", "private" to false, "maxMembers" to 10)

            setAuthenticatedUser(testUserId)
            mockMvc.perform(
                post("/api/tournaments/{ournamentId}/groups", testTournamentId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(body))
            )
                .andExpect(status().isBadRequest)
        }

        @Test
        fun `POST api groups should return 400 when maxMembers is less than 1`() {
            val body = mapOf("name" to "Valid Name", "private" to false, "maxMembers" to 0)

            setAuthenticatedUser(testUserId)
            mockMvc.perform(
                post("/api/tournaments/{tournamentId}/groups", testTournamentId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(body))
            )
                .andExpect(status().isBadRequest)
        }
    }

    @Nested
    inner class UpdateGroupEndpointTests {
        @Test
        fun `PATCH api groups groupId should return 200 when group updated successfully`() {
            every { membershipService.isAdmin(testUserId, testGroupId) } returns true
            val updated = testGroupResponse.copy(name = "Updated Name", maxMembers = 30)
            every { groupService.updateGroup(testGroupId, any()) } returns updated

            setAuthenticatedUser(testUserId)
            val request = UpdateGroupRequest(name = "Updated Name", maxMembers = 30)
            mockMvc.perform(
                patch("/api/tournaments/{tournamentId}/groups/{groupId}", testTournamentId, testGroupId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.name").value("Updated Name"))
                .andExpect(jsonPath("$.maxMembers").value(30))
        }

        @Test
        fun `PATCH api groups groupId should return 404 when group not found`() {
            every { membershipService.isAdmin(testUserId, testGroupId) } returns true
            every { groupService.updateGroup(testGroupId, any()) } throws NotFoundException("Group not found")

            setAuthenticatedUser(testUserId)
            val request = UpdateGroupRequest(name = "New Name")
            mockMvc.perform(
                patch("/api/tournaments/{tournamentId}/groups/{groupId}", testTournamentId, testGroupId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isNotFound)
                .andExpect(jsonPath("$.message").value("Group not found"))
        }

        @Test
        fun `PATCH api groups groupId should return 409 when new name already exists`() {
            every { membershipService.isAdmin(testUserId, testGroupId) } returns true
            every { groupService.updateGroup(testGroupId, any()) } throws ConflictException(
                message = "Nombre de grupo 'Taken Name' ya registrado",
                field = "name",
                rejectedValue = "Taken Name"
            )

            setAuthenticatedUser(testUserId)
            val request = UpdateGroupRequest(name = "Taken Name")
            mockMvc.perform(
                patch("/api/tournaments/{tournamentId}/groups/{groupId}", testTournamentId, testGroupId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isConflict)
                .andExpect(jsonPath("$.data.field").value("name"))
        }
    }

    @Nested
    inner class DeleteGroupEndpointTests {

        @Test
        fun `DELETE api groups groupId should return 403 when user is not group admin`() {
            every { groupService.getGroupById(testGroupId, omitStandings = true) } returns testGroupResponse
            every { membershipService.isAdmin(testUserId, testGroupId) } returns false

            setAuthenticatedUser(testUserId)
            mockMvc.perform(delete("/api/tournaments/{tournamentId}/groups/{groupId}", testTournamentId, testGroupId))
                .andExpect(status().isForbidden)
        }

        @Test
        fun `DELETE api groups groupId should return 204 when group deleted successfully`() {
            every { groupService.getGroupById(testGroupId, omitStandings = true) } returns testGroupResponse
            every { membershipService.isAdmin(testUserId, testGroupId) } returns true
            every { groupService.deleteGroup(testGroupId) } just Runs

            setAuthenticatedUser(testUserId)
            mockMvc.perform(delete("/api/tournaments/{tournamentId}/groups/{groupId}", testTournamentId, testGroupId))
                .andExpect(status().isNoContent)
        }

        @Test
        fun `DELETE api groups groupId should return 404 when group not found`() {
            every { groupService.getGroupById(testGroupId, omitStandings = true) } throws NotFoundException("Group not found")

            setAuthenticatedUser(testUserId)
            mockMvc.perform(delete("/api/tournaments/{tournamentId}/groups/{groupId}", testTournamentId, testGroupId))
                .andExpect(status().isNotFound)
        }
    }

    @Nested
    inner class GetGroupEndpointTests {

        @Test
        fun `GET api groups groupId should return 200 with group details`() {
            every { membershipService.isMember(testUserId, testGroupId) } returns true
            every { groupService.getGroupById(testGroupId) } returns testGroupResponse

            setAuthenticatedUser(testUserId)
            mockMvc.perform(get("/api/tournaments/{tournamentId}/groups/{groupId}", testTournamentId, testGroupId))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.id").value(testGroupId.toString()))
                .andExpect(jsonPath("$.name").value("Test Group"))
                .andExpect(jsonPath("$.private").value(false))
                .andExpect(jsonPath("$.maxMembers").value(20))
        }

        @Test
        fun `GET api groups groupId should return 404 when group not found`() {
            every { membershipService.isMember(testUserId, testGroupId) } returns true
            every { groupService.getGroupById(testGroupId) } throws NotFoundException("Group not found")

            setAuthenticatedUser(testUserId)
            mockMvc.perform(get("/api/tournaments/{tournamentId}/groups/{groupId}", testTournamentId, testGroupId))
                .andExpect(status().isNotFound)
                .andExpect(jsonPath("$.message").value("Group not found"))
        }
    }

    @Nested
    inner class FindGroupsEndpointTests {

        @Test
        fun `GET api groups should return 200 with all groups when no search param`() {
            val groups = listOf(
                testGroupResponse,
                testGroupResponse.copy(id = UUID.randomUUID(), name = "Second Group")
            )
            every { groupService.searchGroups(testUserId, testTournamentId, null, null) } returns groups

            setAuthenticatedUser(testUserId)
            mockMvc.perform(get("/api/tournaments/{tournamentId}/groups", testTournamentId))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("Test Group"))
                .andExpect(jsonPath("$[1].name").value("Second Group"))
        }

        @Test
        fun `GET api groups should return filtered groups when search and joined params provided`() {
            val groups = listOf(testGroupResponse)
            every { groupService.searchGroups(testUserId, testTournamentId, "test", false) } returns groups

            setAuthenticatedUser(testUserId)
            mockMvc.perform(get("/api/tournaments/{tournamentId}/groups", testTournamentId).param("search", "test").param( "joined", "false"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Test Group"))
        }

        @Test
        fun `GET api groups should return empty list when no groups match search`() {
            every { groupService.searchGroups(testUserId, testTournamentId, "xyz", false) } returns emptyList()

            setAuthenticatedUser(testUserId)
            mockMvc.perform(get("/api/tournaments/{tournamentId}/groups", testTournamentId).param("search", "xyz").param( "joined", "false"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.length()").value(0))
        }
    }

    @Nested
    inner class JoinGroupEndpointTests {

        @Test
        fun `POST api groups groupId join should return 201 when join succeeds`() {
            setAuthenticatedUser(testUserId)
            every { membershipService.joinGroup(testUserId, testGroupId) } just Runs

            mockMvc.perform(post("/api/tournaments/{tournamentId}/groups/{groupId}/join", testTournamentId, testGroupId))
                .andExpect(status().isCreated)

            clearAuthentication()
        }

        @Test
        fun `POST api groups groupId join should return 404 when group not found`() {
            setAuthenticatedUser(testUserId)
            every { membershipService.joinGroup(testUserId, testGroupId) } throws NotFoundException("Group not found")

            mockMvc.perform(post("/api/tournaments/{tournamentId}/groups/{groupId}/join", testTournamentId, testGroupId))
                .andExpect(status().isNotFound)
                .andExpect(jsonPath("$.message").value("Group not found"))

            clearAuthentication()
        }

        @Test
        fun `POST api groups groupId join should return 400 when already a member`() {
            setAuthenticatedUser(testUserId)
            every { membershipService.joinGroup(testUserId, testGroupId) } throws BadRequestException("You are already member of this group")

            mockMvc.perform(post("/api/tournaments/{tournamentId}/groups/{groupId}/join", testTournamentId, testGroupId))
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.message").value("You are already member of this group"))

            clearAuthentication()
        }

        @Test
        fun `POST api groups groupId join should return 400 when group is full`() {
            setAuthenticatedUser(testUserId)
            every { membershipService.joinGroup(testUserId, testGroupId) } throws BadRequestException("Group is full")

            mockMvc.perform(post("/api/tournaments/{tournamentId}/groups/{groupId}/join", testTournamentId, testGroupId))
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.message").value("Group is full"))

            clearAuthentication()
        }
    }

    @Nested
    inner class LeaveGroupEndpointTests {

        @Test
        fun `DELETE api groups groupId leave should return 204 when leave succeeds`() {
            setAuthenticatedUser(testUserId)
            every { membershipService.leaveGroup(testUserId, testGroupId) } just Runs

            mockMvc.perform(delete("/api/tournaments/{tournamentId}/groups/{groupId}/leave", testTournamentId, testGroupId))
                .andExpect(status().isNoContent)

            clearAuthentication()
        }

        @Test
        fun `DELETE api groups groupId leave should return 404 when group not found`() {
            setAuthenticatedUser(testUserId)
            every { membershipService.leaveGroup(testUserId, testGroupId) } throws NotFoundException("Group not found")

            mockMvc.perform(delete("/api/tournaments/{tournamentId}/groups/{groupId}/leave", testTournamentId, testGroupId))
                .andExpect(status().isNotFound)

            clearAuthentication()
        }

        @Test
        fun `DELETE api groups groupId leave should return 404 when not a member`() {
            setAuthenticatedUser(testUserId)
            every { membershipService.leaveGroup(testUserId, testGroupId) } throws NotFoundException("You are not member of this group")

            mockMvc.perform(delete("/api/tournaments/{tournamentId}/groups/{groupId}/leave", testTournamentId, testGroupId))
                .andExpect(status().isNotFound)
                .andExpect(jsonPath("$.message").value("You are not member of this group"))

            clearAuthentication()
        }
    }
}
