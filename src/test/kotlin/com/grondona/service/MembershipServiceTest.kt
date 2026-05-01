package com.grondona.service

import com.grondona.exception.BadRequestException
import com.grondona.exception.NotFoundException
import com.grondona.model.Group
import com.grondona.model.GroupRole
import com.grondona.model.GroupUser
import com.grondona.model.MembershipView
import com.grondona.model.Tournament
import com.grondona.model.TournamentStatus
import com.grondona.model.User
import com.grondona.repository.GroupRepository
import com.grondona.repository.MembershipRepository
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

class MembershipServiceTest {

    @MockK
    private lateinit var groupRepository: GroupRepository

    @MockK
    private lateinit var userRepository: UserRepository

    @MockK
    private lateinit var membershipRepository: MembershipRepository

    @InjectMockKs
    private lateinit var membershipService: MembershipService

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

    private val testTournament = Tournament(
        id = testTournamentId,
        name = "Test Tournament",
        status = TournamentStatus.NOT_STARTED,
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
        tournament = testTournament
    )

    private val testMembership = GroupUser(
        id = UUID.randomUUID(),
        user = testUser,
        group = testGroup,
        joinedAt = LocalDateTime.now()
    )

    private val testMembershipView = MembershipView(
        group = testGroup,
        points = 12f,
        rank = 1,
        role = GroupRole.MEMBER,
        membersCount = 1L,
        candidatesCount = 1L,
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
            every { membershipRepository.isMember(testUserId, testGroupId) } returns false
            every { membershipRepository.countMembers(testGroupId) } returns 5L
            every { membershipRepository.save(any()) } returns testMembership

            membershipService.joinGroup(testUserId, testGroupId)

            verify { membershipRepository.save(any()) }
        }

        @Test
        fun `joinGroup should register new user as candidate when group is private`() {
            every { groupRepository.findById(testGroupId) } returns Optional.of(testGroup.copy(isPrivate = true))
            every { userRepository.findById(testUserId) } returns Optional.of(testUser)
            every { membershipRepository.isMember(testUserId, testGroupId) } returns false
            every { membershipRepository.countMembers(testGroupId) } returns 0L
            every { membershipRepository.save(any()) } answers { firstArg() }

            membershipService.joinGroup(testUserId, testGroupId)

            verify { membershipRepository.save(match { it.role == GroupRole.CANDIDATE }) }
        }

        @Test
        fun `joinGroup should immediately register new user as member when group is public`() {
            every { groupRepository.findById(testGroupId) } returns Optional.of(testGroup.copy(isPrivate = false))
            every { userRepository.findById(testUserId) } returns Optional.of(testUser)
            every { membershipRepository.isMember(testUserId, testGroupId) } returns false
            every { membershipRepository.countMembers(testGroupId) } returns 0L
            every { membershipRepository.save(any()) } answers { firstArg() }

            membershipService.joinGroup(testUserId, testGroupId)

            verify { membershipRepository.save(match { it.role == GroupRole.MEMBER }) }
        }

        @Test
        fun `joinGroup should throw NotFoundException when group does not exist`() {
            every { groupRepository.findById(testGroupId) } returns Optional.empty()

            val exception = assertThrows<NotFoundException> {
                membershipService.joinGroup(testUserId, testGroupId)
            }
            assertEquals("Group not found", exception.message)
            verify(exactly = 0) { membershipRepository.save(any()) }
        }

        @Test
        fun `joinGroup should throw NotFoundException when user does not exist`() {
            every { groupRepository.findById(testGroupId) } returns Optional.of(testGroup)
            every { userRepository.findById(testUserId) } returns Optional.empty()

            val exception = assertThrows<NotFoundException> {
                membershipService.joinGroup(testUserId, testGroupId)
            }
            assertEquals("User not found", exception.message)
            verify(exactly = 0) { membershipRepository.save(any()) }
        }

        @Test
        fun `joinGroup should throw BadRequestException when user is already a member`() {
            every { groupRepository.findById(testGroupId) } returns Optional.of(testGroup)
            every { userRepository.findById(testUserId) } returns Optional.of(testUser)
            every { membershipRepository.isMember(testUserId, testGroupId) } returns true

            val exception = assertThrows<BadRequestException> {
                membershipService.joinGroup(testUserId, testGroupId)
            }
            assertEquals("You are already member of this group", exception.message)
            verify(exactly = 0) { membershipRepository.save(any()) }
        }

        @Test
        fun `joinGroup should throw BadRequestException when group is full`() {
            every { groupRepository.findById(testGroupId) } returns Optional.of(testGroup)
            every { userRepository.findById(testUserId) } returns Optional.of(testUser)
            every { membershipRepository.isMember(testUserId, testGroupId) } returns false
            every { membershipRepository.countMembers(testGroupId) } returns 10L

            val exception = assertThrows<BadRequestException> {
                membershipService.joinGroup(testUserId, testGroupId)
            }
            assertEquals("Group is full", exception.message)
            verify(exactly = 0) { membershipRepository.save(any()) }
        }

        @Test
        fun `joinGroup should allow joining when group has exactly one slot left`() {
            every { groupRepository.findById(testGroupId) } returns Optional.of(testGroup)
            every { userRepository.findById(testUserId) } returns Optional.of(testUser)
            every { membershipRepository.isMember(testUserId, testGroupId) } returns false
            every { membershipRepository.countMembers(testGroupId) } returns 9L
            every { membershipRepository.save(any()) } returns testMembership

            membershipService.joinGroup(testUserId, testGroupId)

            verify { membershipRepository.save(any()) }
        }
    }

    @Nested
    inner class LeaveGroupTests {

        @Test
        fun `leaveGroup should succeed when user is a member`() {
            every { groupRepository.findById(testGroupId) } returns Optional.of(testGroup)
            every { membershipRepository.findMember(testUserId, testGroupId) } returns Optional.of(testMembership)
            every { membershipRepository.delete(testMembership) } just Runs

            membershipService.leaveGroup(testUserId, testGroupId)

            verify { membershipRepository.delete(testMembership) }
        }

        @Test
        fun `leaveGroup should throw NotFoundException when group does not exist`() {
            every { groupRepository.findById(testGroupId) } returns Optional.empty()

            val exception = assertThrows<NotFoundException> {
                membershipService.leaveGroup(testUserId, testGroupId)
            }
            assertEquals("Group not found", exception.message)
            verify(exactly = 0) { membershipRepository.delete(any()) }
        }

        @Test
        fun `leaveGroup should throw NotFoundException when user is not a member`() {
            every { groupRepository.findById(testGroupId) } returns Optional.of(testGroup)
            every { membershipRepository.findMember(testUserId, testGroupId) } returns Optional.empty()

            val exception = assertThrows<NotFoundException> {
                membershipService.leaveGroup(testUserId, testGroupId)
            }
            assertEquals("You are not member of this group", exception.message)
            verify(exactly = 0) { membershipRepository.delete(any()) }
        }
    }

    @Nested
    inner class GetMyGroupsTests {

        @Test
        fun `getMyGroups should return empty list when user has no memberships`() {
            every { membershipRepository.findUserGroups(testUserId) } returns emptyList()
            val result = membershipService.getMyGroups(testUserId)
            assertTrue(result.isEmpty())
        }

        @Test
        fun `getMyGroups should return membership response with standing data`() {
            every { membershipRepository.findUserGroups(testUserId) } returns listOf(testMembershipView)

            val result = membershipService.getMyGroups(testUserId)
            assertEquals(1, result.size)
            assertEquals(testGroupId, result[0].group.id)
            assertEquals("Test Group", result[0].group.name)
            assertEquals(1, result[0].memberCount)
            assertEquals(12f, result[0].points)
            assertEquals(1, result[0].rank)
            assertEquals(GroupRole.MEMBER, result[0].role)
        }

        @Test
        fun `getMyGroups should return multiple memberships`() {
            val secondGroupId = UUID.randomUUID()
            val secondGroup = testGroup.copy(id = secondGroupId, name = "Second Group")
            val membership1 = testMembershipView.copy(rank = 1, points = 7.5f, membersCount = 6)
            val membership2 = testMembershipView.copy(group = secondGroup, role = GroupRole.ADMIN, rank = 2, points = 13.4f)

            every { membershipRepository.findUserGroups(testUserId) } returns listOf(membership1, membership2)

            val result = membershipService.getMyGroups(testUserId)

            assertEquals(2, result.size)
            assertEquals(testGroupId, result[0].group.id)
            assertEquals(testGroup.name, result[0].group.name)
            assertEquals(6, result[0].memberCount)
            assertEquals(7.5f, result[0].points)
            assertEquals(1, result[0].rank)
            assertEquals(GroupRole.MEMBER, result[0].role)
            assertEquals(secondGroup.id, result[1].group.id)
            assertEquals(secondGroup.name, result[1].group.name)
            assertEquals(1, result[1].memberCount)
            assertEquals(13.4f, result[1].points)
            assertEquals(2, result[1].rank)
            assertEquals(GroupRole.ADMIN, result[1].role)
        }
    }

    @Nested
    inner class AcceptCandidateTests {

        val candidateId: UUID = UUID.randomUUID()
        val candidate = GroupUser(group = testGroup, user = testUser.copy(id = candidateId, username = "candidate"))

        @Test
        fun `acceptCandidate should throw NotFoundException when group does not exist`() {
            every { groupRepository.findById(testGroupId) } returns Optional.empty()

            val exception = assertThrows<NotFoundException> {
                membershipService.acceptCandidate(testUserId, testGroupId, candidateId)
            }
            assertEquals("Group not found", exception.message)
            verify(exactly = 0) { membershipRepository.save(any()) }
        }

        @Test
        fun `acceptCandidate should throw NotFoundException when user does not exist`() {
            every { groupRepository.findById(testGroupId) } returns Optional.of(testGroup)
            every { userRepository.existsById(testUserId) } returns false

            val exception = assertThrows<NotFoundException> {
                membershipService.acceptCandidate(testUserId, testGroupId, candidateId)
            }
            assertEquals("User not found", exception.message)
            verify(exactly = 0) { membershipRepository.save(any()) }
        }

        @Test
        fun `acceptCandidate should throw NotFoundException when candidate does not exist`() {
            every { groupRepository.findById(testGroupId) } returns Optional.of(testGroup)
            every { userRepository.existsById(testUserId) } returns true
            every { userRepository.existsById(candidateId) } returns false

            val exception = assertThrows<NotFoundException> {
                membershipService.acceptCandidate(testUserId, testGroupId, candidateId)
            }
            assertEquals("Candidate not found", exception.message)
            verify(exactly = 0) { membershipRepository.save(any()) }
        }

        @Test
        fun `acceptCandidate should throw BadRequestException when user is not member of the group`() {
            every { groupRepository.findById(testGroupId) } returns Optional.of(testGroup)
            every { userRepository.existsById(testUserId) } returns true
            every { userRepository.existsById(candidateId) } returns true
            every { membershipRepository.findMember(testUserId, testGroupId) } returns Optional.empty()

            val exception = assertThrows<BadRequestException> {
                membershipService.acceptCandidate(testUserId, testGroupId, candidateId)
            }
            assertEquals("User does not belong to the group", exception.message)
            verify(exactly = 0) { membershipRepository.save(any()) }
        }

        @Test
        fun `acceptCandidate should throw BadRequestException when user is not admin of the group`() {
            every { groupRepository.findById(testGroupId) } returns Optional.of(testGroup)
            every { userRepository.existsById(testUserId) } returns true
            every { userRepository.existsById(candidateId) } returns true
            every { membershipRepository.findMember(testUserId, testGroupId) } returns Optional.of(testMembership.copy(role = GroupRole.MEMBER))

            val exception = assertThrows<BadRequestException> {
                membershipService.acceptCandidate(testUserId, testGroupId, candidateId)
            }
            assertEquals("User is not a group admin", exception.message)
            verify(exactly = 0) { membershipRepository.save(any()) }
        }

        @Test
        fun `acceptCandidate should throw BadRequestException when candidate has not requested access`() {
            every { groupRepository.findById(testGroupId) } returns Optional.of(testGroup)
            every { userRepository.existsById(testUserId) } returns true
            every { userRepository.existsById(candidateId) } returns true
            every { membershipRepository.findMember(testUserId, testGroupId) } returns Optional.of(testMembership.copy(role = GroupRole.ADMIN))
            every { membershipRepository.findCandidate(candidateId, testGroupId) } returns Optional.empty()

            val exception = assertThrows<BadRequestException> {
                membershipService.acceptCandidate(testUserId, testGroupId, candidateId)
            }
            assertEquals("The user is not a candidate for the group", exception.message)
            verify(exactly = 0) { membershipRepository.save(any()) }
        }

        @Test
        fun `acceptCandidate should throw BadRequestException when group is already full`() {
            every { groupRepository.findById(testGroupId) } returns Optional.of(testGroup)
            every { userRepository.existsById(testUserId) } returns true
            every { userRepository.existsById(candidateId) } returns true
            every { membershipRepository.findMember(testUserId, testGroupId) } returns Optional.of(testMembership.copy(role = GroupRole.ADMIN))
            every { membershipRepository.findCandidate(candidateId, testGroupId) } returns Optional.of(candidate)
            every { membershipRepository.countMembers(testGroupId) } returns testGroup.maxMembers.toLong()

            val exception = assertThrows<BadRequestException> {
                membershipService.acceptCandidate(testUserId, testGroupId, candidateId)
            }
            assertEquals("Group is full", exception.message)
            verify(exactly = 0) { membershipRepository.save(any()) }
        }

        @Test
        fun `acceptCandidate should succeed with the proper request`() {
            every { groupRepository.findById(testGroupId) } returns Optional.of(testGroup)
            every { userRepository.existsById(testUserId) } returns true
            every { userRepository.existsById(candidateId) } returns true
            every { membershipRepository.findMember(testUserId, testGroupId) } returns Optional.of(testMembership.copy(role = GroupRole.ADMIN))
            every { membershipRepository.findCandidate(candidateId, testGroupId) } returns Optional.of(candidate)
            every { membershipRepository.countMembers(testGroupId) } returns 1L
            every { membershipRepository.save(any()) } answers { firstArg() }

            membershipService.acceptCandidate(testUserId, testGroupId, candidateId)
            verify(exactly = 1) { membershipRepository.save(any()) }
        }
    }

    @Nested
    inner class RejectCandidateTests {

        val candidateId: UUID = UUID.randomUUID()
        val candidate = GroupUser(group = testGroup, user = testUser.copy(id = candidateId, username = "candidate"))

        @Test
        fun `rejectCandidate should throw NotFoundException when group does not exist`() {
            every { groupRepository.findById(testGroupId) } returns Optional.empty()

            val exception = assertThrows<NotFoundException> {
                membershipService.rejectCandidate(testUserId, testGroupId, candidateId)
            }
            assertEquals("Group not found", exception.message)
            verify(exactly = 0) { membershipRepository.save(any()) }
        }

        @Test
        fun `rejectCandidate should throw NotFoundException when user does not exist`() {
            every { groupRepository.findById(testGroupId) } returns Optional.of(testGroup)
            every { userRepository.existsById(testUserId) } returns false

            val exception = assertThrows<NotFoundException> {
                membershipService.rejectCandidate(testUserId, testGroupId, candidateId)
            }
            assertEquals("User not found", exception.message)
            verify(exactly = 0) { membershipRepository.save(any()) }
        }

        @Test
        fun `rejectCandidate should throw NotFoundException when candidate does not exist`() {
            every { groupRepository.findById(testGroupId) } returns Optional.of(testGroup)
            every { userRepository.existsById(testUserId) } returns true
            every { userRepository.existsById(candidateId) } returns false

            val exception = assertThrows<NotFoundException> {
                membershipService.rejectCandidate(testUserId, testGroupId, candidateId)
            }
            assertEquals("Candidate not found", exception.message)
            verify(exactly = 0) { membershipRepository.save(any()) }
        }

        @Test
        fun `rejectCandidate should throw BadRequestException when user is not member of the group`() {
            every { groupRepository.findById(testGroupId) } returns Optional.of(testGroup)
            every { userRepository.existsById(testUserId) } returns true
            every { userRepository.existsById(candidateId) } returns true
            every { membershipRepository.findMember(testUserId, testGroupId) } returns Optional.empty()

            val exception = assertThrows<BadRequestException> {
                membershipService.rejectCandidate(testUserId, testGroupId, candidateId)
            }
            assertEquals("User does not belong to the group", exception.message)
            verify(exactly = 0) { membershipRepository.save(any()) }
        }

        @Test
        fun `rejectCandidate should throw BadRequestException when user is not admin of the group`() {
            every { groupRepository.findById(testGroupId) } returns Optional.of(testGroup)
            every { userRepository.existsById(testUserId) } returns true
            every { userRepository.existsById(candidateId) } returns true
            every { membershipRepository.findMember(testUserId, testGroupId) } returns Optional.of(testMembership.copy(role = GroupRole.MEMBER))

            val exception = assertThrows<BadRequestException> {
                membershipService.rejectCandidate(testUserId, testGroupId, candidateId)
            }
            assertEquals("User is not a group admin", exception.message)
            verify(exactly = 0) { membershipRepository.save(any()) }
        }

        @Test
        fun `rejectCandidate should throw BadRequestException when candidate has not requested access`() {
            every { groupRepository.findById(testGroupId) } returns Optional.of(testGroup)
            every { userRepository.existsById(testUserId) } returns true
            every { userRepository.existsById(candidateId) } returns true
            every { membershipRepository.findMember(testUserId, testGroupId) } returns Optional.of(testMembership.copy(role = GroupRole.ADMIN))
            every { membershipRepository.countMembers(testGroupId) } returns 1L
            every { membershipRepository.findCandidate(candidateId, testGroupId) } returns Optional.empty()

            val exception = assertThrows<BadRequestException> {
                membershipService.rejectCandidate(testUserId, testGroupId, candidateId)
            }
            assertEquals("The user is not a candidate for the group", exception.message)
            verify(exactly = 0) { membershipRepository.save(any()) }
        }

        @Test
        fun `rejectCandidate should succeed with the proper request`() {
            every { groupRepository.findById(testGroupId) } returns Optional.of(testGroup)
            every { userRepository.existsById(testUserId) } returns true
            every { userRepository.existsById(candidateId) } returns true
            every { membershipRepository.findMember(testUserId, testGroupId) } returns Optional.of(testMembership.copy(role = GroupRole.ADMIN))
            every { membershipRepository.countMembers(testGroupId) } returns 1L
            every { membershipRepository.findCandidate(candidateId, testGroupId) } returns Optional.of(candidate)
            every { membershipRepository.delete(any()) } just Runs

            membershipService.rejectCandidate(testUserId, testGroupId, candidateId)
            verify(exactly = 1) { membershipRepository.delete(any()) }
        }
    }

    @Nested
    inner class IsMemberTests {

        @Test
        fun `isMember should return true when user is a member`() {
            every { membershipRepository.findMember(testUserId, testGroupId) } returns Optional.of(testMembership)
            assertTrue(membershipService.isMember(testUserId, testGroupId))
        }

        @Test
        fun `isMember should return false when user is not a member`() {
            every { membershipRepository.findMember(testUserId, testGroupId) } returns Optional.empty()
            assertFalse(membershipService.isMember(testUserId, testGroupId))
        }
    }

    @Nested
    inner class IsAdminTests {

        @Test
        fun `isAdmin should return true for ADMIN role`() {
            val adminMembership = testMembership.copy(role = GroupRole.ADMIN)
            every { membershipRepository.findMember(testUserId, testGroupId) } returns Optional.of(adminMembership)
            assertTrue(membershipService.isAdmin(testUserId, testGroupId))
        }

        @Test
        fun `isAdmin should return true for OWNER role`() {
            val ownerMembership = testMembership.copy(role = GroupRole.OWNER)
            every { membershipRepository.findMember(testUserId, testGroupId) } returns Optional.of(ownerMembership)
            assertTrue(membershipService.isAdmin(testUserId, testGroupId))
        }

        @Test
        fun `isAdmin should return false for MEMBER role`() {
            val memberMembership = testMembership.copy(role = GroupRole.MEMBER)
            every { membershipRepository.findMember(testUserId, testGroupId) } returns Optional.of(memberMembership)
            assertFalse(membershipService.isAdmin(testUserId, testGroupId))
        }

        @Test
        fun `isAdmin should return false when user is not a member`() {
            every { membershipRepository.findMember(testUserId, testGroupId) } returns Optional.empty()
            assertFalse(membershipService.isAdmin(testUserId, testGroupId))
        }
    }
}
