package com.grondona.service

import com.grondona.exception.BadRequestException
import com.grondona.exception.NotFoundException
import com.grondona.model.Group
import com.grondona.model.GroupRole
import com.grondona.model.GroupUser
import com.grondona.model.Tournament
import com.grondona.model.TournamentStatus
import com.grondona.model.User
import com.grondona.model.dto.UserGroupResponse
import com.grondona.repository.GroupRepository
import com.grondona.repository.GroupUserRepository
import com.grondona.repository.UserRepository
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

class GroupMembershipServiceTest {

    @MockK
    private lateinit var groupRepository: GroupRepository

    @MockK
    private lateinit var userRepository: UserRepository

    @MockK
    private lateinit var groupUserRepository: GroupUserRepository

    @InjectMockKs
    private lateinit var groupMembershipService: GroupMembershipService

    private val testUserId = UUID.randomUUID()
    private val testGroupId = UUID.randomUUID()
    private val testTournamentId = UUID.randomUUID()

    private val testUser = User(
        id = testUserId,
        fullname = "Test User",
        username = "testuser",
        email = "test@example.com",
        passwordHash = "hash",
        createdAt = LocalDateTime.now(),
        updatedAt = LocalDateTime.now()
    )

    private val testGroup = Group(
        id = testGroupId,
        name = "Test Group",
        isPrivate = false,
        maxMembers = 10,
        createdAt = LocalDateTime.now(),
        updatedAt = LocalDateTime.now(),
        tournament = Tournament(
            id = testTournamentId,
            name = "Test Tournament",
            status = TournamentStatus.NOT_STARTED,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )
    )

    private val testMembership = GroupUser(
        id = UUID.randomUUID(),
        user = testUser,
        group = testGroup,
        joinedAt = LocalDateTime.now()
    )

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
    }

    @Nested
    inner class JoinGroupTests {

        @Test
        fun `joinGroup should succeed when all conditions are met`() {
            every { groupRepository.findById(testGroupId) } returns Optional.of(testGroup)
            every { userRepository.findById(testUserId) } returns Optional.of(testUser)
            every { groupUserRepository.existsByUserIdAndGroupId(testUserId, testGroupId) } returns false
            every { groupUserRepository.countByGroupId(testGroupId) } returns 5L
            every { groupUserRepository.save(any()) } returns testMembership

            groupMembershipService.joinGroup(testUserId, testGroupId)

            verify { groupUserRepository.save(any()) }
        }

        @Test
        fun `joinGroup should throw NotFoundException when group does not exist`() {
            every { groupRepository.findById(testGroupId) } returns Optional.empty()

            val exception = assertThrows<NotFoundException> {
                groupMembershipService.joinGroup(testUserId, testGroupId)
            }
            assertEquals("Group not found", exception.message)
            verify(exactly = 0) { groupUserRepository.save(any()) }
        }

        @Test
        fun `joinGroup should throw NotFoundException when user does not exist`() {
            every { groupRepository.findById(testGroupId) } returns Optional.of(testGroup)
            every { userRepository.findById(testUserId) } returns Optional.empty()

            val exception = assertThrows<NotFoundException> {
                groupMembershipService.joinGroup(testUserId, testGroupId)
            }
            assertEquals("User not found", exception.message)
            verify(exactly = 0) { groupUserRepository.save(any()) }
        }

        @Test
        fun `joinGroup should throw BadRequestException when user is already a member`() {
            every { groupRepository.findById(testGroupId) } returns Optional.of(testGroup)
            every { userRepository.findById(testUserId) } returns Optional.of(testUser)
            every { groupUserRepository.existsByUserIdAndGroupId(testUserId, testGroupId) } returns true

            val exception = assertThrows<BadRequestException> {
                groupMembershipService.joinGroup(testUserId, testGroupId)
            }
            assertEquals("You are already member of this group", exception.message)
            verify(exactly = 0) { groupUserRepository.save(any()) }
        }

        @Test
        fun `joinGroup should throw BadRequestException when group is full`() {
            // testGroup.maxMembers = 10, count = 10 → full
            every { groupRepository.findById(testGroupId) } returns Optional.of(testGroup)
            every { userRepository.findById(testUserId) } returns Optional.of(testUser)
            every { groupUserRepository.existsByUserIdAndGroupId(testUserId, testGroupId) } returns false
            every { groupUserRepository.countByGroupId(testGroupId) } returns 10L

            val exception = assertThrows<BadRequestException> {
                groupMembershipService.joinGroup(testUserId, testGroupId)
            }
            assertEquals("Group is full", exception.message)
            verify(exactly = 0) { groupUserRepository.save(any()) }
        }

        @Test
        fun `joinGroup should allow joining when group has exactly one slot left`() {
            // maxMembers = 10, current = 9 → one slot left
            every { groupRepository.findById(testGroupId) } returns Optional.of(testGroup)
            every { userRepository.findById(testUserId) } returns Optional.of(testUser)
            every { groupUserRepository.existsByUserIdAndGroupId(testUserId, testGroupId) } returns false
            every { groupUserRepository.countByGroupId(testGroupId) } returns 9L
            every { groupUserRepository.save(any()) } returns testMembership

            groupMembershipService.joinGroup(testUserId, testGroupId)

            verify { groupUserRepository.save(any()) }
        }
    }

    @Nested
    inner class LeaveGroupTests {

        @Test
        fun `leaveGroup should succeed when user is a member`() {
            every { groupRepository.findById(testGroupId) } returns Optional.of(testGroup)
            every { groupUserRepository.findByUserIdAndGroupId(testUserId, testGroupId) } returns Optional.of(testMembership)
            every { groupUserRepository.delete(testMembership) } just Runs

            groupMembershipService.leaveGroup(testUserId, testGroupId)

            verify { groupUserRepository.delete(testMembership) }
        }

        @Test
        fun `leaveGroup should throw NotFoundException when group does not exist`() {
            every { groupRepository.findById(testGroupId) } returns Optional.empty()

            val exception = assertThrows<NotFoundException> {
                groupMembershipService.leaveGroup(testUserId, testGroupId)
            }
            assertEquals("Group not found", exception.message)
            verify(exactly = 0) { groupUserRepository.delete(any()) }
        }

        @Test
        fun `leaveGroup should throw NotFoundException when user is not a member`() {
            every { groupRepository.findById(testGroupId) } returns Optional.of(testGroup)
            every { groupUserRepository.findByUserIdAndGroupId(testUserId, testGroupId) } returns Optional.empty()

            val exception = assertThrows<NotFoundException> {
                groupMembershipService.leaveGroup(testUserId, testGroupId)
            }
            assertEquals("You are not member of this group", exception.message)
            verify(exactly = 0) { groupUserRepository.delete(any()) }
        }
    }

    @Nested
    inner class GetMyGroupsTests {

        @Test
        fun `getMyGroups should return groups with member count`() {
            val group1 = testGroup.copy(id = UUID.randomUUID(), name = "Group A")
            val group2 = testGroup.copy(id = UUID.randomUUID(), name = "Group B")
            val memberships = listOf(
                UserGroupResponse(group1.id!!, group1.name, 3L, 10.5f, GroupRole.ADMIN),
                UserGroupResponse(group2.id!!, group2.name, 7L, 5.0f, GroupRole.MEMBER)
            )

            every { groupUserRepository.findUserGroups(testUserId) } returns memberships

            val result = groupMembershipService.getMyGroups(testUserId)

            assertEquals(2, result.size)
            assertEquals("Group A", result[0].name)
            assertEquals(3, result[0].memberCount)
            assertEquals(10.5f, result[0].points)
            assertEquals(GroupRole.ADMIN, result[0].role)
            assertEquals(group1.id, result[0].groupId)
            assertEquals("Group B", result[1].name)
            assertEquals(7, result[1].memberCount)
            assertEquals(5.0f, result[1].points)
            assertEquals(GroupRole.MEMBER, result[1].role)
            assertEquals(group2.id, result[1].groupId)
        }

        @Test
        fun `getMyGroups should return empty list when user is not in any group`() {
            every { groupUserRepository.findUserGroups(testUserId) } returns emptyList()

            val result = groupMembershipService.getMyGroups(testUserId)

            assertTrue(result.isEmpty())
        }
    }
}
