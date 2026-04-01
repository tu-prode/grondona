package com.grondona.service

import com.grondona.exception.BadRequestException
import com.grondona.exception.NotFoundException
import com.grondona.model.Group
import com.grondona.model.GroupRole
import com.grondona.model.GroupUser
import com.grondona.model.Standing
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
    private lateinit var groupUserRepository: MembershipRepository

    @MockK
    private lateinit var predictionService: PredictionService

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

            membershipService.joinGroup(testUserId, testGroupId)

            verify { groupUserRepository.save(any()) }
        }

        @Test
        fun `joinGroup should succeed with custom role`() {
            every { groupRepository.findById(testGroupId) } returns Optional.of(testGroup)
            every { userRepository.findById(testUserId) } returns Optional.of(testUser)
            every { groupUserRepository.existsByUserIdAndGroupId(testUserId, testGroupId) } returns false
            every { groupUserRepository.countByGroupId(testGroupId) } returns 0L
            every { groupUserRepository.save(any()) } returns testMembership.copy(role = GroupRole.ADMIN)

            membershipService.joinGroup(testUserId, testGroupId, GroupRole.ADMIN)

            verify { groupUserRepository.save(match { it.role == GroupRole.ADMIN }) }
        }

        @Test
        fun `joinGroup should throw NotFoundException when group does not exist`() {
            every { groupRepository.findById(testGroupId) } returns Optional.empty()

            val exception = assertThrows<NotFoundException> {
                membershipService.joinGroup(testUserId, testGroupId)
            }
            assertEquals("Group not found", exception.message)
            verify(exactly = 0) { groupUserRepository.save(any()) }
        }

        @Test
        fun `joinGroup should throw NotFoundException when user does not exist`() {
            every { groupRepository.findById(testGroupId) } returns Optional.of(testGroup)
            every { userRepository.findById(testUserId) } returns Optional.empty()

            val exception = assertThrows<NotFoundException> {
                membershipService.joinGroup(testUserId, testGroupId)
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
                membershipService.joinGroup(testUserId, testGroupId)
            }
            assertEquals("You are already member of this group", exception.message)
            verify(exactly = 0) { groupUserRepository.save(any()) }
        }

        @Test
        fun `joinGroup should throw BadRequestException when group is full`() {
            every { groupRepository.findById(testGroupId) } returns Optional.of(testGroup)
            every { userRepository.findById(testUserId) } returns Optional.of(testUser)
            every { groupUserRepository.existsByUserIdAndGroupId(testUserId, testGroupId) } returns false
            every { groupUserRepository.countByGroupId(testGroupId) } returns 10L

            val exception = assertThrows<BadRequestException> {
                membershipService.joinGroup(testUserId, testGroupId)
            }
            assertEquals("Group is full", exception.message)
            verify(exactly = 0) { groupUserRepository.save(any()) }
        }

        @Test
        fun `joinGroup should allow joining when group has exactly one slot left`() {
            every { groupRepository.findById(testGroupId) } returns Optional.of(testGroup)
            every { userRepository.findById(testUserId) } returns Optional.of(testUser)
            every { groupUserRepository.existsByUserIdAndGroupId(testUserId, testGroupId) } returns false
            every { groupUserRepository.countByGroupId(testGroupId) } returns 9L
            every { groupUserRepository.save(any()) } returns testMembership

            membershipService.joinGroup(testUserId, testGroupId)

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

            membershipService.leaveGroup(testUserId, testGroupId)

            verify { groupUserRepository.delete(testMembership) }
        }

        @Test
        fun `leaveGroup should throw NotFoundException when group does not exist`() {
            every { groupRepository.findById(testGroupId) } returns Optional.empty()

            val exception = assertThrows<NotFoundException> {
                membershipService.leaveGroup(testUserId, testGroupId)
            }
            assertEquals("Group not found", exception.message)
            verify(exactly = 0) { groupUserRepository.delete(any()) }
        }

        @Test
        fun `leaveGroup should throw NotFoundException when user is not a member`() {
            every { groupRepository.findById(testGroupId) } returns Optional.of(testGroup)
            every { groupUserRepository.findByUserIdAndGroupId(testUserId, testGroupId) } returns Optional.empty()

            val exception = assertThrows<NotFoundException> {
                membershipService.leaveGroup(testUserId, testGroupId)
            }
            assertEquals("You are not member of this group", exception.message)
            verify(exactly = 0) { groupUserRepository.delete(any()) }
        }
    }

    @Nested
    inner class GetMyGroupsTests {

        @Test
        fun `getMyGroups should return empty list when user has no memberships`() {
            every { groupUserRepository.findByUserId(testUserId) } returns emptyList()

            val result = membershipService.getMyGroups(testUserId)

            assertTrue(result.isEmpty())
            verify(exactly = 0) { predictionService.calculateStandings(any()) }
        }

        @Test
        fun `getMyGroups should return membership response with standing data`() {
            val standing = Standing(rank = 1, user = testUser, points = 15.5f, lastPredictions = emptyList())
            every { groupUserRepository.findByUserId(testUserId) } returns listOf(testMembership)
            every { predictionService.calculateStandings(testGroup) } returns listOf(standing)

            val result = membershipService.getMyGroups(testUserId)

            assertEquals(1, result.size)
            assertEquals(testGroupId, result[0].groupId)
            assertEquals("Test Group", result[0].groupName)
            assertEquals(1, result[0].memberCount)
            assertEquals(15.5f, result[0].points)
            assertEquals(1, result[0].rank)
            assertEquals(GroupRole.MEMBER, result[0].role)
        }

        @Test
        fun `getMyGroups should aggregate member count from standings`() {
            val secondUser = testUser.copy(id = UUID.randomUUID(), username = "other")
            val standings = listOf(
                Standing(rank = 1, user = testUser, points = 20f, lastPredictions = emptyList()),
                Standing(rank = 2, user = secondUser, points = 10f, lastPredictions = emptyList())
            )
            every { groupUserRepository.findByUserId(testUserId) } returns listOf(testMembership)
            every { predictionService.calculateStandings(testGroup) } returns standings

            val result = membershipService.getMyGroups(testUserId)

            assertEquals(1, result.size)
            assertEquals(2, result[0].memberCount)
            assertEquals(20f, result[0].points)
            assertEquals(1, result[0].rank)
        }

        @Test
        fun `getMyGroups should return multiple memberships`() {
            val secondGroupId = UUID.randomUUID()
            val secondGroup = testGroup.copy(id = secondGroupId, name = "Second Group")
            val secondMembership = testMembership.copy(group = secondGroup, role = GroupRole.ADMIN)
            val standing1 = Standing(rank = 1, user = testUser, points = 10f, lastPredictions = emptyList())
            val standing2 = Standing(rank = 2, user = testUser, points = 5f, lastPredictions = emptyList())

            every { groupUserRepository.findByUserId(testUserId) } returns listOf(testMembership, secondMembership)
            every { predictionService.calculateStandings(testGroup) } returns listOf(standing1)
            every { predictionService.calculateStandings(secondGroup) } returns listOf(standing2)

            val result = membershipService.getMyGroups(testUserId)

            assertEquals(2, result.size)
            assertEquals("Test Group", result[0].groupName)
            assertEquals(GroupRole.MEMBER, result[0].role)
            assertEquals("Second Group", result[1].groupName)
            assertEquals(GroupRole.ADMIN, result[1].role)
        }
    }

    @Nested
    inner class IsMemberTests {

        @Test
        fun `isMember should return true when user is a member`() {
            every { groupUserRepository.findByUserIdAndGroupId(testUserId, testGroupId) } returns Optional.of(testMembership)
            assertTrue(membershipService.isMember(testUserId, testGroupId))
        }

        @Test
        fun `isMember should return false when user is not a member`() {
            every { groupUserRepository.findByUserIdAndGroupId(testUserId, testGroupId) } returns Optional.empty()
            assertFalse(membershipService.isMember(testUserId, testGroupId))
        }
    }

    @Nested
    inner class IsAdminTests {

        @Test
        fun `isAdmin should return true for ADMIN role`() {
            val adminMembership = testMembership.copy(role = GroupRole.ADMIN)
            every { groupUserRepository.findByUserIdAndGroupId(testUserId, testGroupId) } returns Optional.of(adminMembership)
            assertTrue(membershipService.isAdmin(testUserId, testGroupId))
        }

        @Test
        fun `isAdmin should return true for OWNER role`() {
            val ownerMembership = testMembership.copy(role = GroupRole.OWNER)
            every { groupUserRepository.findByUserIdAndGroupId(testUserId, testGroupId) } returns Optional.of(ownerMembership)
            assertTrue(membershipService.isAdmin(testUserId, testGroupId))
        }

        @Test
        fun `isAdmin should return false for MEMBER role`() {
            val memberMembership = testMembership.copy(role = GroupRole.MEMBER)
            every { groupUserRepository.findByUserIdAndGroupId(testUserId, testGroupId) } returns Optional.of(memberMembership)
            assertFalse(membershipService.isAdmin(testUserId, testGroupId))
        }

        @Test
        fun `isAdmin should return false when user is not a member`() {
            every { groupUserRepository.findByUserIdAndGroupId(testUserId, testGroupId) } returns Optional.empty()
            assertFalse(membershipService.isAdmin(testUserId, testGroupId))
        }
    }
}
