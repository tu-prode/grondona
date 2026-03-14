package com.grondona.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.grondona.exception.ConflictException
import com.grondona.exception.GlobalExceptionHandler
import com.grondona.exception.NotFoundException
import com.grondona.model.dto.*
import com.grondona.service.GroupService
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.Runs
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.time.LocalDateTime
import java.util.*

class GroupControllerTest {

    private lateinit var mockMvc: MockMvc
    private lateinit var groupService: GroupService
    private lateinit var objectMapper: ObjectMapper

    private val testGroupId = UUID.randomUUID()
    private val testGroupResponse = GroupResponse(
        id = testGroupId,
        name = "Test Group",
        isPrivate = false,
        maxMembers = 20,
        createdAt = LocalDateTime.now(),
        updatedAt = LocalDateTime.now()
    )

    @BeforeEach
    fun setUp() {
        groupService = mockk()
        objectMapper = ObjectMapper().findAndRegisterModules()
        mockMvc = MockMvcBuilders
            .standaloneSetup(GroupController(groupService))
            .setControllerAdvice(GlobalExceptionHandler())
            .build()
    }

    @Nested
    inner class CreateGroupEndpointTests {

        @Test
        fun `POST api groups should return 201 when group created successfully`() {
            val request = CreateGroupRequest(name = "New Group", isPrivate = false, maxMembers = 10)
            every { groupService.createGroup(any()) } returns testGroupResponse.copy(name = "New Group")

            mockMvc.perform(
                post("/api/groups")
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
            val request = CreateGroupRequest(name = "Duplicate", isPrivate = false, maxMembers = 10)
            every { groupService.createGroup(any()) } throws ConflictException(
                message = "Nombre de grupo ya registrado",
                field = "name",
                rejectedValue = "Duplicate"
            )

            mockMvc.perform(
                post("/api/groups")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isConflict)
                .andExpect(jsonPath("$.field").value("name"))
                .andExpect(jsonPath("$.rejectedValue").value("Duplicate"))
        }

        @Test
        fun `POST api groups should return 400 when name is blank`() {
            val body = mapOf("name" to "", "private" to false, "maxMembers" to 10)

            mockMvc.perform(
                post("/api/groups")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(body))
            )
                .andExpect(status().isBadRequest)
        }

        @Test
        fun `POST api groups should return 400 when maxMembers is less than 1`() {
            val body = mapOf("name" to "Valid Name", "private" to false, "maxMembers" to 0)

            mockMvc.perform(
                post("/api/groups")
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
            val request = UpdateGroupRequest(name = "Updated Name", maxMembers = 30)
            val updated = testGroupResponse.copy(name = "Updated Name", maxMembers = 30)
            every { groupService.updateGroup(testGroupId, any()) } returns updated

            mockMvc.perform(
                patch("/api/groups/{groupId}", testGroupId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.name").value("Updated Name"))
                .andExpect(jsonPath("$.maxMembers").value(30))
        }

        @Test
        fun `PATCH api groups groupId should return 404 when group not found`() {
            val request = UpdateGroupRequest(name = "New Name")
            every { groupService.updateGroup(testGroupId, any()) } throws NotFoundException("Grupo no encontrado")

            mockMvc.perform(
                patch("/api/groups/{groupId}", testGroupId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isNotFound)
                .andExpect(jsonPath("$.message").value("Grupo no encontrado"))
        }

        @Test
        fun `PATCH api groups groupId should return 409 when new name already exists`() {
            val request = UpdateGroupRequest(name = "Taken Name")
            every { groupService.updateGroup(testGroupId, any()) } throws ConflictException(
                message = "Nombre de grupo 'Taken Name' ya registrado",
                field = "name",
                rejectedValue = "Taken Name"
            )

            mockMvc.perform(
                patch("/api/groups/{groupId}", testGroupId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isConflict)
                .andExpect(jsonPath("$.field").value("name"))
        }
    }

    @Nested
    inner class DeleteGroupEndpointTests {

        @Test
        fun `DELETE api groups groupId should return 204 when group deleted successfully`() {
            every { groupService.deleteGroup(testGroupId) } just Runs

            mockMvc.perform(delete("/api/groups/{groupId}", testGroupId))
                .andExpect(status().isNoContent)
        }

        @Test
        fun `DELETE api groups groupId should return 404 when group not found`() {
            every { groupService.deleteGroup(testGroupId) } throws NotFoundException("Grupo no encontrado")

            mockMvc.perform(delete("/api/groups/{groupId}", testGroupId))
                .andExpect(status().isNotFound)
        }
    }

    @Nested
    inner class GetGroupEndpointTests {

        @Test
        fun `GET api groups groupId should return 200 with group details`() {
            every { groupService.getGroupById(testGroupId) } returns testGroupResponse

            mockMvc.perform(get("/api/groups/{groupId}", testGroupId))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.id").value(testGroupId.toString()))
                .andExpect(jsonPath("$.name").value("Test Group"))
                .andExpect(jsonPath("$.private").value(false))
                .andExpect(jsonPath("$.maxMembers").value(20))
        }

        @Test
        fun `GET api groups groupId should return 404 when group not found`() {
            every { groupService.getGroupById(testGroupId) } throws NotFoundException("Grupo no encontrado")

            mockMvc.perform(get("/api/groups/{groupId}", testGroupId))
                .andExpect(status().isNotFound)
                .andExpect(jsonPath("$.message").value("Grupo no encontrado"))
        }
    }

    @Nested
    inner class GetAllGroupsEndpointTests {

        @Test
        fun `GET api groups should return 200 with all groups when no search param`() {
            val groups = listOf(
                testGroupResponse,
                testGroupResponse.copy(id = UUID.randomUUID(), name = "Second Group")
            )
            every { groupService.getAllGroups() } returns groups

            mockMvc.perform(get("/api/groups"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("Test Group"))
                .andExpect(jsonPath("$[1].name").value("Second Group"))
        }

        @Test
        fun `GET api groups should return filtered groups when search param provided`() {
            val groups = listOf(testGroupResponse)
            every { groupService.searchGroups("test") } returns groups

            mockMvc.perform(get("/api/groups").param("search", "test"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Test Group"))
        }

        @Test
        fun `GET api groups should return empty list when no groups match search`() {
            every { groupService.searchGroups("xyz") } returns emptyList()

            mockMvc.perform(get("/api/groups").param("search", "xyz"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.length()").value(0))
        }
    }
}
