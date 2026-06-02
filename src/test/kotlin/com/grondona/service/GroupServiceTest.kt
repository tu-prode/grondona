package com.grondona.service

import com.grondona.exception.ConflictException
import com.grondona.exception.NotFoundException
import com.grondona.model.GroupRole
import com.grondona.model.GroupUser
import com.grondona.model.Match
import com.grondona.model.MatchPrediction
import com.grondona.model.MatchPredictionView
import com.grondona.model.MatchStage
import com.grondona.model.MatchStatus
import com.grondona.model.PredictionStatus
import com.grondona.model.dto.request.CreateGroupRequest
import com.grondona.model.dto.request.UpdateGroupRequest
import com.grondona.repository.GroupRepository
import com.grondona.repository.MatchPredictionRepository
import com.grondona.repository.MembershipRepository
import com.grondona.repository.TournamentRepository
import com.grondona.repository.UserRepository
import com.grondona.testGroup
import com.grondona.testTeam
import com.grondona.testTournament
import com.grondona.testUser
import io.mockk.*
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDateTime
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.*

class GroupServiceTest {

    @MockK
    private lateinit var userRepository: UserRepository

    @MockK
    private lateinit var groupRepository: GroupRepository

    @MockK
    private lateinit var membershipRepository: MembershipRepository

    @MockK
    private lateinit var tournamentRepository: TournamentRepository

    @MockK
    private lateinit var matchPredictionRepository: MatchPredictionRepository

    @InjectMockKs
    private lateinit var groupService: GroupService

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

            every { userRepository.findById(testUser.id!!) } returns Optional.of(testUser)
            every { tournamentRepository.findById(testTournament.id!!) } returns Optional.of(testTournament)
            every { groupRepository.existsByName(request.name) } returns false
            every { groupRepository.save(any()) } returns savedGroup
            every { membershipRepository.save(any()) } answers { firstArg() }

            val result = groupService.createGroup(testUser.id!!, testTournament.id!!, request)

            assertEquals("New Group", result.name)
            assertEquals(false, result.isPrivate)
            assertEquals(15, result.maxMembers)
            verify { groupRepository.save(any()) }
        }

        @Test
        fun `createGroup should create private group when isPrivate is true`() {
            val request = CreateGroupRequest(name = "Private Group", isPrivate = true, maxMembers = 5)
            val savedGroup = testGroup.copy(name = "Private Group", isPrivate = true, maxMembers = 5)

            every { userRepository.findById(testUser.id!!) } returns Optional.of(testUser)
            every { tournamentRepository.findById(testTournament.id!!) } returns Optional.of(testTournament)
            every { groupRepository.existsByName(request.name) } returns false
            every { groupRepository.save(any()) } returns savedGroup
            every { membershipRepository.save(any()) } answers { firstArg() }

            val result = groupService.createGroup(testUser.id!!, testTournament.id!!, request)

            assertTrue(result.isPrivate)
        }

        @Test
        fun `createGroup should throw ConflictException when name already exists`() {
            val request = CreateGroupRequest(name = "Existing Group", isPrivate = false, maxMembers = 10)
            every { userRepository.findById(testUser.id!!) } returns Optional.of(testUser)
            every { groupRepository.existsByName(request.name) } returns true

            val exception = assertThrows<ConflictException> {
                groupService.createGroup(testUser.id!!, testTournament.id!!, request)
            }
            assertEquals("Group name already exists", exception.message)
            assertEquals("name", exception.field)
            assertEquals("Existing Group", exception.rejectedValue)
            verify(exactly = 0) { groupRepository.save(any()) }
        }

        @Test
        fun `createGroup should throw NotFoundException when tournament not found`() {
            val request = CreateGroupRequest(name = "New Group", isPrivate = false, maxMembers = 10)
            every { userRepository.findById(testUser.id!!) } returns Optional.of(testUser)
            every { groupRepository.existsByName(request.name) } returns false
            every { tournamentRepository.findById(testTournament.id!!) } returns Optional.empty()

            val exception = assertThrows<NotFoundException> {
                groupService.createGroup(testUser.id!!, testTournament.id!!, request)
            }
            assertEquals("Tournament not found", exception.message)
            verify(exactly = 0) { groupRepository.save(any()) }
        }

        @Test
        fun `createGroup should set current user as group owner`() {
            val request = CreateGroupRequest(name = "New Group", isPrivate = false, maxMembers = 15)
            val savedGroup = testGroup.copy(name = "New Group", maxMembers = 15)

            every { userRepository.findById(testUser.id!!) } returns Optional.of(testUser)
            every { tournamentRepository.findById(testTournament.id!!) } returns Optional.of(testTournament)
            every { groupRepository.existsByName(request.name) } returns false
            every { groupRepository.save(any()) } returns savedGroup
            every { membershipRepository.save(any()) } answers { firstArg() }

            val result = groupService.createGroup(testUser.id!!, testTournament.id!!, request)

            assertEquals("New Group", result.name)
            assertEquals(false, result.isPrivate)
            assertEquals(15, result.maxMembers)
            verify { groupRepository.save(any()) }

            val slot = slot<GroupUser>()
            verify(exactly = 1) { membershipRepository.save(capture(slot)) }
            val memberSaved = slot.captured
            assertEquals(testUser.id, memberSaved.user.id)
            assertEquals(GroupRole.OWNER, memberSaved.role)
        }
    }

    @Nested
    inner class UpdateGroupTests {

        @Test
        fun `updateGroup should update name when provided and not taken`() {
            val request = UpdateGroupRequest(name = "Updated Name")
            val groupCopy = testGroup.copy()
            every { groupRepository.findById(testGroup.id!!) } returns Optional.of(groupCopy)
            every { groupRepository.existsByName("Updated Name") } returns false
            every { groupRepository.save(any()) } answers { firstArg() }

            val result = groupService.updateGroup(testGroup.id!!, request)

            assertEquals("Updated Name", result.name)
        }

        @Test
        fun `updateGroup should update isPrivate when provided`() {
            val request = UpdateGroupRequest(isPrivate = true)
            val groupCopy = testGroup.copy()
            every { groupRepository.findById(testGroup.id!!) } returns Optional.of(groupCopy)
            every { groupRepository.save(any()) } answers { firstArg() }

            val result = groupService.updateGroup(testGroup.id!!, request)

            assertTrue(result.isPrivate)
        }

        @Test
        fun `updateGroup should update maxMembers when provided`() {
            val request = UpdateGroupRequest(maxMembers = 50)
            val groupCopy = testGroup.copy()
            every { groupRepository.findById(testGroup.id!!) } returns Optional.of(groupCopy)
            every { groupRepository.save(any()) } answers { firstArg() }

            val result = groupService.updateGroup(testGroup.id!!, request)

            assertEquals(50, result.maxMembers)
        }

        @Test
        fun `updateGroup should allow same name without conflict check`() {
            val request = UpdateGroupRequest(name = testGroup.name)
            val groupCopy = testGroup.copy()
            every { groupRepository.findById(testGroup.id!!) } returns Optional.of(groupCopy)
            every { groupRepository.save(any()) } answers { firstArg() }

            // Should not throw even though the name is technically "taken" by itself
            val result = groupService.updateGroup(testGroup.id!!, request)

            assertEquals(testGroup.name, result.name)
            verify(exactly = 0) { groupRepository.existsByName(any()) }
        }

        @Test
        fun `updateGroup should throw ConflictException when new name already taken`() {
            val request = UpdateGroupRequest(name = "Taken Name")
            every { groupRepository.findById(testGroup.id!!) } returns Optional.of(testGroup.copy())
            every { groupRepository.existsByName("Taken Name") } returns true

            val exception = assertThrows<ConflictException> {
                groupService.updateGroup(testGroup.id!!, request)
            }
            assertEquals("name", exception.field)
            assertEquals("Taken Name", exception.rejectedValue)
        }

        @Test
        fun `updateGroup should throw NotFoundException when group not found`() {
            val request = UpdateGroupRequest(name = "Any Name")
            every { groupRepository.findById(testGroup.id!!) } returns Optional.empty()

            val exception = assertThrows<NotFoundException> {
                groupService.updateGroup(testGroup.id!!, request)
            }
            assertEquals("Group not found", exception.message)
        }
    }

    @Nested
    inner class DeleteGroupTests {

        @Test
        fun `deleteGroup should delete group when it exists`() {
            every { groupRepository.findById(testGroup.id!!) } returns Optional.of(testGroup)
            every { membershipRepository.clearGroup(testGroup.id!!) } just Runs
            every { groupRepository.delete(testGroup) } just Runs

            groupService.deleteGroup(testGroup.id!!)

            verify { groupRepository.delete(testGroup) }
        }

        @Test
        fun `deleteGroup should throw NotFoundException when group not found`() {
            every { groupRepository.findById(testGroup.id!!) } returns Optional.empty()

            val exception = assertThrows<NotFoundException> {
                groupService.deleteGroup(testGroup.id!!)
            }
            assertEquals("Group not found", exception.message)
        }
    }

    @Nested
    inner class GetGroupByIdTests {

        @Test
        fun `getGroupById should return GroupResponse when group exists`() {
            every { groupRepository.findById(testGroup.id!!) } returns Optional.of(testGroup)
            every { membershipRepository.findEveryGroupUser(testGroup.id!!) } returns emptyList()

            val result = groupService.getGroupById(testGroup.id!!)

            assertEquals(testGroup.id, result.id)
            assertEquals(testGroup.name, result.name)
            assertEquals(testGroup.isPrivate, result.isPrivate)
            assertEquals(testGroup.maxMembers, result.maxMembers)
        }

        @Test
        fun `getGroupById should throw NotFoundException when group not found`() {
            every { groupRepository.findById(testGroup.id!!) } returns Optional.empty()

            val exception = assertThrows<NotFoundException> {
                groupService.getGroupById(testGroup.id!!)
            }
            assertEquals("Group not found", exception.message)
        }

        @Test
        fun `getGroupById should return list of candidates when there are some`() {
            val member1 = GroupUser(
                user = testUser.copy(id = UUID.randomUUID()), role = GroupRole.MEMBER,
                group = testGroup, joinedAt = LocalDateTime.now()
            )
            val member2 = GroupUser(
                user = testUser.copy(id = UUID.randomUUID()), role = GroupRole.MEMBER,
                group = testGroup, joinedAt = LocalDateTime.now().minus(1, ChronoUnit.DAYS)
            )
            val candidate1 = GroupUser(
                user = testUser.copy(id = UUID.randomUUID()), role = GroupRole.CANDIDATE,
                group = testGroup, joinedAt = LocalDateTime.now()
            )
            every { groupRepository.findById(testGroup.id!!) } returns Optional.of(testGroup)
            every { membershipRepository.findEveryGroupUser(testGroup.id!!) } returns listOf(member1, member2, candidate1)

            val result = groupService.getGroupById(testGroup.id!!)

            assertEquals(testGroup.id, result.id)
            assertEquals(testGroup.name, result.name)
            assertEquals(testGroup.isPrivate, result.isPrivate)
            assertEquals(testGroup.maxMembers, result.maxMembers)
            assertEquals(1, result.candidates.size)
            assertEquals(candidate1.user.id, result.candidates[0].id)
        }

        @Test
        fun `getGroupById should return empty standings when no user has rank yet`() {
            val member1 = GroupUser(
                user = testUser.copy(id = UUID.randomUUID()),
                group = testGroup, joinedAt = LocalDateTime.now()
            )
            val member2 = GroupUser(
                user = testUser.copy(id = UUID.randomUUID()),
                group = testGroup, joinedAt = LocalDateTime.now().minus(1, ChronoUnit.DAYS)
            )

            every { groupRepository.findById(testGroup.id!!) } returns Optional.of(testGroup)
            every { membershipRepository.findEveryGroupUser(testGroup.id!!) } returns listOf(member1, member2)

            val result = groupService.getGroupById(testGroup.id!!)

            assertEquals(testGroup.id, result.id)
            assertEquals(2, result.standings.size)
            assertEquals(member2.user.id, result.standings[0].user.id)
            assertEquals(1, result.standings[0].rank)
            assertEquals(0f, result.standings[0].points)
            assertEquals(emptyList<PredictionStatus>(), result.standings[0].lastPredictions)
            assertEquals(member1.user.id, result.standings[1].user.id)
            assertEquals(2, result.standings[1].rank)
            assertEquals(0f, result.standings[1].points)
            assertEquals(emptyList<PredictionStatus>(), result.standings[1].lastPredictions)
        }

        @Test
        fun `getGroupById should return real standings when no users are already ranked`() {
            val member1 = GroupUser(
                user = testUser.copy(id = UUID.randomUUID()), rank = 1, points = 2f,
                group = testGroup, joinedAt = LocalDateTime.now()
            )
            val member2 = GroupUser(
                user = testUser.copy(id = UUID.randomUUID()), rank = 2, points = 1.5f,
                group = testGroup, joinedAt = LocalDateTime.now().minus(1, ChronoUnit.DAYS)
            )

            every { groupRepository.findById(testGroup.id!!) } returns Optional.of(testGroup)
            every { membershipRepository.findEveryGroupUser(testGroup.id!!) } returns listOf(member1, member2)

            val result = groupService.getGroupById(testGroup.id!!)

            assertEquals(testGroup.id, result.id)
            assertEquals(2, result.standings.size)
            assertEquals(member1.user.id, result.standings[0].user.id)
            assertEquals(1, result.standings[0].rank)
            assertEquals(2f, result.standings[0].points)
            assertEquals(emptyList<PredictionStatus>(), result.standings[0].lastPredictions)
            assertEquals(member2.user.id, result.standings[1].user.id)
            assertEquals(2, result.standings[1].rank)
            assertEquals(1.5f, result.standings[1].points)
            assertEquals(emptyList<PredictionStatus>(), result.standings[1].lastPredictions)
        }

        @Test
        fun `getGroupById should return live standings when the live flag is set to true`() {
            val member1 = GroupUser(
                user = testUser.copy(id = UUID.randomUUID()), rank = 1, points = 2f,
                group = testGroup, joinedAt = LocalDateTime.now()
            )
            val member2 = GroupUser(
                user = testUser.copy(id = UUID.randomUUID()), rank = 2, points = 1.5f,
                group = testGroup, joinedAt = LocalDateTime.now().minus(1, ChronoUnit.DAYS)
            )

            val testMatch = Match(
                id = UUID.randomUUID(), code = "XX", tournament = testTournament, homeTeam = testTeam, awayTeam = testTeam,
                homeGoals = 1, awayGoals = 1, status = MatchStatus.IN_PROGRESS, stage = MatchStage.GROUP_STAGE,
                startedAt = ZonedDateTime.now().minusMinutes(60)
            )
            val prediction1 = MatchPredictionView(
                id = UUID.randomUUID(),
                user = member1.user,
                rank = member1.rank,
                match = testMatch,
                prediction = MatchPrediction(user = member1.user, group = member1.group, homeGoals = 0, awayGoals = 0, match = testMatch)
            )
            val prediction2 = MatchPredictionView(
                id = UUID.randomUUID(),
                user = member2.user,
                rank = member2.rank,
                match = testMatch,
                prediction = MatchPrediction(user = member2.user, group = member2.group, homeGoals = 1, awayGoals = 1, match = testMatch)
            )

            every { groupRepository.findById(testGroup.id!!) } returns Optional.of(testGroup)
            every { membershipRepository.findEveryGroupUser(testGroup.id!!) } returns listOf(member1, member2)
            every { matchPredictionRepository.findGroupPredictions(testGroup.id!!) } returns listOf(prediction1, prediction2)

            val result = groupService.getGroupById(testGroup.id!!, liveStandings = true)

            assertEquals(testGroup.id, result.id)
            assertEquals(2, result.standings.size)
            assertEquals(member2.user.id, result.standings[0].user.id)
            assertEquals(1, result.standings[0].rank)
            assertEquals(4.5f, result.standings[0].points)
            assertEquals(listOf(PredictionStatus.CORRECT), result.standings[0].lastPredictions)
            assertEquals(member1.user.id, result.standings[1].user.id)
            assertEquals(2, result.standings[1].rank)
            assertEquals(3f, result.standings[1].points)
            assertEquals(listOf(PredictionStatus.PARTIAL), result.standings[1].lastPredictions)
        }

        @Test
        fun `getGroupById should not return standings for non-members`() {
            val member = GroupUser(
                user = testUser.copy(id = UUID.randomUUID()), rank = 1, points = 2f,
                group = testGroup, joinedAt = LocalDateTime.now()
            )
            val candidate = GroupUser(
                user = testUser.copy(id = UUID.randomUUID()), role = GroupRole.CANDIDATE,
                group = testGroup, joinedAt = LocalDateTime.now().minus(1, ChronoUnit.DAYS)
            )

            every { groupRepository.findById(testGroup.id!!) } returns Optional.of(testGroup)
            every { membershipRepository.findEveryGroupUser(testGroup.id!!) } returns listOf(member, candidate)

            val result = groupService.getGroupById(testGroup.id!!)

            assertEquals(testGroup.id, result.id)
            assertEquals(1, result.standings.size)
            assertEquals(member.user.id, result.standings[0].user.id)
            assertEquals(1, result.standings[0].rank)
            assertEquals(1, result.candidates.size)
            assertEquals(candidate.user.id, result.candidates[0].id)
        }
    }
}
