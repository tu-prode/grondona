package com.grondona.service

import com.grondona.exception.ConflictException
import com.grondona.exception.NotFoundException
import com.grondona.model.Group
import com.grondona.model.Tournament
import com.grondona.model.TournamentStatus
import com.grondona.model.dto.request.CreateGroupRequest
import com.grondona.model.dto.request.UpdateGroupRequest
import com.grondona.repository.GroupRepository
import com.grondona.repository.TournamentRepository
import io.mockk.*
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDateTime
import java.util.*

class GroupServiceTest {

    @MockK
    private lateinit var groupRepository: GroupRepository

    @MockK
    private lateinit var tournamentRepository: TournamentRepository

    @InjectMockKs
    private lateinit var groupService: GroupService

    private val testTournamentId = UUID.randomUUID()
    private val testTournament: Tournament = Tournament(
        id = testTournamentId,
        name = "Test Tournament",
        status = TournamentStatus.NOT_STARTED,
        createdAt = LocalDateTime.now(),
        updatedAt = LocalDateTime.now()
    )

    private val testGroupId = UUID.randomUUID()
    private val testGroup = Group(
        id = testGroupId,
        tournament = testTournament,
        name = "Test Group",
        isPrivate = false,
        maxMembers = 20,
        createdAt = LocalDateTime.now(),
        updatedAt = LocalDateTime.now()
    )

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
    }

    @Nested
    inner class CreateGroupTests {

        @Test
        fun `createGroup should return GroupResponse when successful`() {
            val request = CreateGroupRequest(name = "New Group", isPrivate = false, maxMembers = 15)
            val savedGroup = testGroup.copy(name = "New Group", maxMembers = 15)

            every { tournamentRepository.findById(testTournamentId) } returns Optional.of(testTournament)
            every { groupRepository.existsByName(request.name) } returns false
            every { groupRepository.save(any()) } returns savedGroup

            val result = groupService.createGroup(testTournamentId, request)

            assertEquals("New Group", result.name)
            assertEquals(false, result.isPrivate)
            assertEquals(15, result.maxMembers)
            verify { groupRepository.save(any()) }
        }

        @Test
        fun `createGroup should create private group when isPrivate is true`() {
            val request = CreateGroupRequest(name = "Private Group", isPrivate = true, maxMembers = 5)
            val savedGroup = testGroup.copy(name = "Private Group", isPrivate = true, maxMembers = 5)

            every { tournamentRepository.findById(testTournamentId) } returns Optional.of(testTournament)
            every { groupRepository.existsByName(request.name) } returns false
            every { groupRepository.save(any()) } returns savedGroup

            val result = groupService.createGroup(testTournamentId, request)

            assertTrue(result.isPrivate)
        }

        @Test
        fun `createGroup should throw ConflictException when name already exists`() {
            val request = CreateGroupRequest(name = "Existing Group", isPrivate = false, maxMembers = 10)
            every { groupRepository.existsByName(request.name) } returns true

            val exception = assertThrows<ConflictException> {
                groupService.createGroup(testTournamentId, request)
            }
            assertEquals("Group name already exists", exception.message)
            assertEquals("name", exception.field)
            assertEquals("Existing Group", exception.rejectedValue)
            verify(exactly = 0) { groupRepository.save(any()) }
        }
    }

    @Nested
    inner class UpdateGroupTests {

        @Test
        fun `updateGroup should update name when provided and not taken`() {
            val request = UpdateGroupRequest(name = "Updated Name")
            val groupCopy = testGroup.copy()
            every { groupRepository.findById(testGroupId) } returns Optional.of(groupCopy)
            every { groupRepository.existsByName("Updated Name") } returns false
            every { groupRepository.save(any()) } answers { firstArg() }

            val result = groupService.updateGroup(testGroupId, request)

            assertEquals("Updated Name", result.name)
        }

        @Test
        fun `updateGroup should update isPrivate when provided`() {
            val request = UpdateGroupRequest(isPrivate = true)
            val groupCopy = testGroup.copy()
            every { groupRepository.findById(testGroupId) } returns Optional.of(groupCopy)
            every { groupRepository.save(any()) } answers { firstArg() }

            val result = groupService.updateGroup(testGroupId, request)

            assertTrue(result.isPrivate)
        }

        @Test
        fun `updateGroup should update maxMembers when provided`() {
            val request = UpdateGroupRequest(maxMembers = 50)
            val groupCopy = testGroup.copy()
            every { groupRepository.findById(testGroupId) } returns Optional.of(groupCopy)
            every { groupRepository.save(any()) } answers { firstArg() }

            val result = groupService.updateGroup(testGroupId, request)

            assertEquals(50, result.maxMembers)
        }

        @Test
        fun `updateGroup should allow same name without conflict check`() {
            val request = UpdateGroupRequest(name = testGroup.name)
            val groupCopy = testGroup.copy()
            every { groupRepository.findById(testGroupId) } returns Optional.of(groupCopy)
            every { groupRepository.save(any()) } answers { firstArg() }

            // Should not throw even though the name is technically "taken" by itself
            val result = groupService.updateGroup(testGroupId, request)

            assertEquals(testGroup.name, result.name)
            verify(exactly = 0) { groupRepository.existsByName(any()) }
        }

        @Test
        fun `updateGroup should throw ConflictException when new name already taken`() {
            val request = UpdateGroupRequest(name = "Taken Name")
            every { groupRepository.findById(testGroupId) } returns Optional.of(testGroup.copy())
            every { groupRepository.existsByName("Taken Name") } returns true

            val exception = assertThrows<ConflictException> {
                groupService.updateGroup(testGroupId, request)
            }
            assertEquals("name", exception.field)
            assertEquals("Taken Name", exception.rejectedValue)
        }

        @Test
        fun `updateGroup should throw NotFoundException when group not found`() {
            val request = UpdateGroupRequest(name = "Any Name")
            every { groupRepository.findById(testGroupId) } returns Optional.empty()

            val exception = assertThrows<NotFoundException> {
                groupService.updateGroup(testGroupId, request)
            }
            assertEquals("Group not found", exception.message)
        }
    }

    @Nested
    inner class DeleteGroupTests {

        @Test
        fun `deleteGroup should delete group when it exists`() {
            every { groupRepository.findById(testGroupId) } returns Optional.of(testGroup)
            every { groupRepository.delete(testGroup) } just Runs

            groupService.deleteGroup(testGroupId)

            verify { groupRepository.delete(testGroup) }
        }

        @Test
        fun `deleteGroup should throw NotFoundException when group not found`() {
            every { groupRepository.findById(testGroupId) } returns Optional.empty()

            val exception = assertThrows<NotFoundException> {
                groupService.deleteGroup(testGroupId)
            }
            assertEquals("Group not found", exception.message)
        }
    }

    @Nested
    inner class GetGroupByIdTests {

        @Test
        fun `getGroupById should return GroupResponse when group exists`() {
            every { groupRepository.findById(testGroupId) } returns Optional.of(testGroup)

            val result = groupService.getGroupById(testGroupId)

            assertEquals(testGroupId, result.id)
            assertEquals(testGroup.name, result.name)
            assertEquals(testGroup.isPrivate, result.isPrivate)
            assertEquals(testGroup.maxMembers, result.maxMembers)
        }

        @Test
        fun `getGroupById should throw NotFoundException when group not found`() {
            every { groupRepository.findById(testGroupId) } returns Optional.empty()

            val exception = assertThrows<NotFoundException> {
                groupService.getGroupById(testGroupId)
            }
            assertEquals("Group not found", exception.message)
        }
    }
}
