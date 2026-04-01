package com.grondona.service

import com.grondona.exception.BadRequestException
import com.grondona.exception.ForbiddenException
import com.grondona.exception.NotFoundException
import com.grondona.model.Group
import com.grondona.model.GroupRole
import com.grondona.model.GroupUser
import com.grondona.model.Match
import com.grondona.model.MatchStatus
import com.grondona.model.Prediction
import com.grondona.model.PredictionStatus
import com.grondona.model.PredictionView
import com.grondona.model.Standing
import com.grondona.model.Team
import com.grondona.model.Tournament
import com.grondona.model.TournamentStatus
import com.grondona.model.User
import com.grondona.model.dto.request.SubmitBulkPredictionsRequest
import com.grondona.model.dto.request.SubmitPredictionRequest
import com.grondona.repository.GroupRepository
import com.grondona.repository.MatchRepository
import com.grondona.repository.MembershipRepository
import com.grondona.repository.PredictionRepository
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

class PredictionServiceTest {

    @MockK
    private lateinit var userRepository: UserRepository

    @MockK
    private lateinit var groupRepository: GroupRepository

    @MockK
    private lateinit var matchRepository: MatchRepository

    @MockK
    private lateinit var membershipRepository: MembershipRepository

    @MockK
    private lateinit var predictionRepository: PredictionRepository

    @InjectMockKs
    private lateinit var predictionService: PredictionService

    private val testUserId = UUID.randomUUID()
    private val testGroupId = UUID.randomUUID()
    private val testMatchId = UUID.randomUUID()
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

    private val testTeam = Team(
        id = UUID.randomUUID(),
        tournament = testTournament,
        name = "Team A",
        code = "A",
        icon = "a.png"
    )

    private val testGroup = Group(
        id = testGroupId,
        name = "Test Group",
        isPrivate = false,
        maxMembers = 10,
        tournament = testTournament,
        createdAt = LocalDateTime.now(),
        updatedAt = LocalDateTime.now()
    )

    // Open match: startedAt > now + 15min → canSubmit = true
    private val testMatchOpen = Match(
        id = testMatchId,
        tournament = testTournament,
        code = "OPEN-01",
        homeTeam = testTeam,
        awayTeam = testTeam,
        startedAt = LocalDateTime.now().plusHours(2)
    )

    // Locked match: startedAt in the past → canSubmit = false
    private val testMatchLocked = Match(
        id = testMatchId,
        tournament = testTournament,
        code = "LOCKED-01",
        homeTeam = testTeam,
        awayTeam = testTeam,
        status = MatchStatus.FINISHED,
        startedAt = LocalDateTime.now().minusHours(2)
    )

    private val testPrediction = Prediction(
        id = UUID.randomUUID(),
        user = testUser,
        group = testGroup,
        match = testMatchOpen,
        homeGoals = 1,
        awayGoals = 0
    )

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
    }

    @Nested
    inner class CanSubmitTests {

        @Test
        fun `canSubmit returns true when match starts more than 15 minutes from now`() {
            val match = testMatchOpen.copy(startedAt = LocalDateTime.now().plusHours(1))
            assertTrue(PredictionService.canSubmit(match))
        }

        @Test
        fun `canSubmit returns false when match starts in exactly 15 minutes`() {
            val match = testMatchOpen.copy(startedAt = LocalDateTime.now().plusMinutes(15))
            assertFalse(PredictionService.canSubmit(match))
        }

        @Test
        fun `canSubmit returns false when match starts in less than 15 minutes`() {
            val match = testMatchOpen.copy(startedAt = LocalDateTime.now().plusMinutes(5))
            assertFalse(PredictionService.canSubmit(match))
        }

        @Test
        fun `canSubmit returns false when match has already started`() {
            assertFalse(PredictionService.canSubmit(testMatchLocked))
        }

        @Test
        fun `canSubmit returns false when startedAt is null`() {
            val match = testMatchOpen.copy(startedAt = null)
            assertFalse(PredictionService.canSubmit(match))
        }
    }

    @Nested
    inner class SubmitPredictionTests {

        private val request = SubmitPredictionRequest(matchId = testMatchId, homeGoals = 2, awayGoals = 1)

        @Test
        fun `submitPrediction should succeed for an open match`() {
            every { userRepository.findById(testUserId) } returns Optional.of(testUser)
            every { groupRepository.findById(testGroupId) } returns Optional.of(testGroup)
            every { membershipRepository.existsByUserIdAndGroupId(testUserId, testGroupId) } returns true
            every { matchRepository.findById(testMatchId) } returns Optional.of(testMatchOpen)
            every { predictionRepository.upsert(any()) } returns testPrediction

            val result = predictionService.submitPrediction(testUserId, testGroupId, request)

            assertEquals(testUserId, result.user.id)
            assertEquals(testMatchId, result.match.id)
            verify { predictionRepository.upsert(any()) }
        }

        @Test
        fun `submitPrediction should throw NotFoundException when user not found`() {
            every { userRepository.findById(testUserId) } returns Optional.empty()

            assertThrows<NotFoundException> {
                predictionService.submitPrediction(testUserId, testGroupId, request)
            }
            verify(exactly = 0) { predictionRepository.upsert(any()) }
        }

        @Test
        fun `submitPrediction should throw NotFoundException when group not found`() {
            every { userRepository.findById(testUserId) } returns Optional.of(testUser)
            every { groupRepository.findById(testGroupId) } returns Optional.empty()

            assertThrows<NotFoundException> {
                predictionService.submitPrediction(testUserId, testGroupId, request)
            }
            verify(exactly = 0) { predictionRepository.upsert(any()) }
        }

        @Test
        fun `submitPrediction should throw ForbiddenException when user is not a member`() {
            every { userRepository.findById(testUserId) } returns Optional.of(testUser)
            every { groupRepository.findById(testGroupId) } returns Optional.of(testGroup)
            every { membershipRepository.existsByUserIdAndGroupId(testUserId, testGroupId) } returns false

            val exception = assertThrows<ForbiddenException> {
                predictionService.submitPrediction(testUserId, testGroupId, request)
            }
            assertEquals("User doesn't belong to the group", exception.message)
            verify(exactly = 0) { predictionRepository.upsert(any()) }
        }

        @Test
        fun `submitPrediction should throw NotFoundException when match not found`() {
            every { userRepository.findById(testUserId) } returns Optional.of(testUser)
            every { groupRepository.findById(testGroupId) } returns Optional.of(testGroup)
            every { membershipRepository.existsByUserIdAndGroupId(testUserId, testGroupId) } returns true
            every { matchRepository.findById(testMatchId) } returns Optional.empty()

            assertThrows<NotFoundException> {
                predictionService.submitPrediction(testUserId, testGroupId, request)
            }
            verify(exactly = 0) { predictionRepository.upsert(any()) }
        }

        @Test
        fun `submitPrediction should throw BadRequestException when match is locked`() {
            every { userRepository.findById(testUserId) } returns Optional.of(testUser)
            every { groupRepository.findById(testGroupId) } returns Optional.of(testGroup)
            every { membershipRepository.existsByUserIdAndGroupId(testUserId, testGroupId) } returns true
            every { matchRepository.findById(testMatchId) } returns Optional.of(testMatchLocked)

            val exception = assertThrows<BadRequestException> {
                predictionService.submitPrediction(testUserId, testGroupId, request)
            }
            assertEquals("Cannot submit predictions for this match", exception.message)
            verify(exactly = 0) { predictionRepository.upsert(any()) }
        }
    }

    @Nested
    inner class SubmitBulkPredictionsTests {

        private val matchId2 = UUID.randomUUID()

        private val openMatch2 = testMatchOpen.copy(id = matchId2, code = "OPEN-02")
        private val lockedMatch2 = testMatchLocked.copy(id = matchId2, code = "LOCKED-02")

        @Test
        fun `submitBulkPredictions should submit all open matches`() {
            val request = SubmitBulkPredictionsRequest(
                predictions = listOf(
                    SubmitPredictionRequest(matchId = testMatchId, homeGoals = 1, awayGoals = 0),
                    SubmitPredictionRequest(matchId = matchId2, homeGoals = 2, awayGoals = 2)
                )
            )
            val savedPredictions = listOf(testPrediction, testPrediction.copy(match = openMatch2))

            every { userRepository.findById(testUserId) } returns Optional.of(testUser)
            every { groupRepository.findById(testGroupId) } returns Optional.of(testGroup)
            every { membershipRepository.existsByUserIdAndGroupId(testUserId, testGroupId) } returns true
            every { matchRepository.findById(testMatchId) } returns Optional.of(testMatchOpen)
            every { matchRepository.findById(matchId2) } returns Optional.of(openMatch2)
            every { predictionRepository.upsertAll(any()) } returns savedPredictions

            val result = predictionService.submitBulkPredictions(testUserId, testGroupId, request)

            assertEquals(testGroupId, result.groupId)
            assertEquals(2, result.predictions.size)
            verify { predictionRepository.upsertAll(match { it.size == 2 }) }
        }

        @Test
        fun `submitBulkPredictions should silently skip locked matches`() {
            val request = SubmitBulkPredictionsRequest(
                predictions = listOf(
                    SubmitPredictionRequest(matchId = testMatchId, homeGoals = 1, awayGoals = 0),
                    SubmitPredictionRequest(matchId = matchId2, homeGoals = 0, awayGoals = 0)
                )
            )

            every { userRepository.findById(testUserId) } returns Optional.of(testUser)
            every { groupRepository.findById(testGroupId) } returns Optional.of(testGroup)
            every { membershipRepository.existsByUserIdAndGroupId(testUserId, testGroupId) } returns true
            every { matchRepository.findById(testMatchId) } returns Optional.of(testMatchOpen)
            every { matchRepository.findById(matchId2) } returns Optional.of(lockedMatch2)
            every { predictionRepository.upsertAll(any()) } returns listOf(testPrediction)

            val result = predictionService.submitBulkPredictions(testUserId, testGroupId, request)

            assertEquals(1, result.predictions.size)
            verify { predictionRepository.upsertAll(match { it.size == 1 }) }
        }

        @Test
        fun `submitBulkPredictions should return empty when all matches are locked`() {
            val request = SubmitBulkPredictionsRequest(
                predictions = listOf(SubmitPredictionRequest(matchId = testMatchId, homeGoals = 1, awayGoals = 0))
            )

            every { userRepository.findById(testUserId) } returns Optional.of(testUser)
            every { groupRepository.findById(testGroupId) } returns Optional.of(testGroup)
            every { membershipRepository.existsByUserIdAndGroupId(testUserId, testGroupId) } returns true
            every { matchRepository.findById(testMatchId) } returns Optional.of(testMatchLocked)
            every { predictionRepository.upsertAll(emptyList()) } returns emptyList()

            val result = predictionService.submitBulkPredictions(testUserId, testGroupId, request)

            assertEquals(0, result.predictions.size)
        }

        @Test
        fun `submitBulkPredictions should throw ForbiddenException when user is not a member`() {
            val request = SubmitBulkPredictionsRequest(
                predictions = listOf(SubmitPredictionRequest(matchId = testMatchId, homeGoals = 1, awayGoals = 0))
            )
            every { userRepository.findById(testUserId) } returns Optional.of(testUser)
            every { groupRepository.findById(testGroupId) } returns Optional.of(testGroup)
            every { membershipRepository.existsByUserIdAndGroupId(testUserId, testGroupId) } returns false

            val exception = assertThrows<ForbiddenException> {
                predictionService.submitBulkPredictions(testUserId, testGroupId, request)
            }
            assertEquals("User doesn't belong to the group", exception.message)
        }

        @Test
        fun `submitBulkPredictions should throw NotFoundException when group not found`() {
            val request = SubmitBulkPredictionsRequest(
                predictions = listOf(SubmitPredictionRequest(matchId = testMatchId, homeGoals = 1, awayGoals = 0))
            )
            every { userRepository.findById(testUserId) } returns Optional.of(testUser)
            every { groupRepository.findById(testGroupId) } returns Optional.empty()

            assertThrows<NotFoundException> {
                predictionService.submitBulkPredictions(testUserId, testGroupId, request)
            }
        }
    }

    @Nested
    inner class GetGroupUserPredictionsTests {

        @Test
        fun `getGroupUserPredictions should return predictions for member`() {
            val predictionView = PredictionView(
                id = UUID.randomUUID(), user = testUser, rank = 1, match = testMatchOpen, prediction = testPrediction
            )
            every { userRepository.findById(testUserId) } returns Optional.of(testUser)
            every { groupRepository.findById(testGroupId) } returns Optional.of(testGroup)
            every { membershipRepository.existsByUserIdAndGroupId(testUserId, testGroupId) } returns true
            every { predictionRepository.findGroupPredictionsForUser(testGroupId, testUserId) } returns listOf(predictionView)

            val result = predictionService.getGroupUserPredictions(testUserId, testGroupId)

            assertEquals(testGroupId, result.groupId)
            assertEquals(1, result.predictions.size)
        }

        @Test
        fun `getGroupUserPredictions should throw ForbiddenException when not a member`() {
            every { userRepository.findById(testUserId) } returns Optional.of(testUser)
            every { groupRepository.findById(testGroupId) } returns Optional.of(testGroup)
            every { membershipRepository.existsByUserIdAndGroupId(testUserId, testGroupId) } returns false

            val exception = assertThrows<ForbiddenException> {
                predictionService.getGroupUserPredictions(testUserId, testGroupId)
            }
            assertEquals("User doesn't belong to the group", exception.message)
        }

        @Test
        fun `getGroupUserPredictions should throw NotFoundException when group not found`() {
            every { userRepository.findById(testUserId) } returns Optional.of(testUser)
            every { groupRepository.findById(testGroupId) } returns Optional.empty()

            assertThrows<NotFoundException> {
                predictionService.getGroupUserPredictions(testUserId, testGroupId)
            }
        }

        @Test
        fun `getGroupUserPredictions should throw NotFoundException when user not found`() {
            every { userRepository.findById(testUserId) } returns Optional.empty()

            assertThrows<NotFoundException> {
                predictionService.getGroupUserPredictions(testUserId, testGroupId)
            }
        }
    }

    @Nested
    inner class GetGroupMatchPredictionsTests {

        @Test
        fun `getGroupMatchPredictions should return predictions for locked match`() {
            val predictionView = PredictionView(
                id = UUID.randomUUID(), user = testUser, rank = 1, match = testMatchLocked, prediction = testPrediction
            )
            every { groupRepository.findById(testGroupId) } returns Optional.of(testGroup)
            every { membershipRepository.existsByUserIdAndGroupId(testUserId, testGroupId) } returns true
            every { matchRepository.findById(testMatchId) } returns Optional.of(testMatchLocked)
            every { predictionRepository.findGroupPredictionsForMatch(testGroupId, testMatchId) } returns listOf(predictionView)

            val result = predictionService.getGroupMatchPredictions(testUserId, testGroupId, testMatchId)

            assertEquals(testGroupId, result.groupId)
            assertEquals(1, result.predictions.size)
        }

        @Test
        fun `getGroupMatchPredictions should throw BadRequestException when match is still open`() {
            every { groupRepository.findById(testGroupId) } returns Optional.of(testGroup)
            every { membershipRepository.existsByUserIdAndGroupId(testUserId, testGroupId) } returns true
            every { matchRepository.findById(testMatchId) } returns Optional.of(testMatchOpen)

            val exception = assertThrows<BadRequestException> {
                predictionService.getGroupMatchPredictions(testUserId, testGroupId, testMatchId)
            }
            assertEquals("Match is still open", exception.message)
        }

        @Test
        fun `getGroupMatchPredictions should throw ForbiddenException when not a member`() {
            every { groupRepository.findById(testGroupId) } returns Optional.of(testGroup)
            every { membershipRepository.existsByUserIdAndGroupId(testUserId, testGroupId) } returns false

            val exception = assertThrows<ForbiddenException> {
                predictionService.getGroupMatchPredictions(testUserId, testGroupId, testMatchId)
            }
            assertEquals("User doesn't belong to the group", exception.message)
        }

        @Test
        fun `getGroupMatchPredictions should throw NotFoundException when group not found`() {
            every { groupRepository.findById(testGroupId) } returns Optional.empty()

            assertThrows<NotFoundException> {
                predictionService.getGroupMatchPredictions(testUserId, testGroupId, testMatchId)
            }
        }

        @Test
        fun `getGroupMatchPredictions should throw NotFoundException when match not found`() {
            every { groupRepository.findById(testGroupId) } returns Optional.of(testGroup)
            every { membershipRepository.existsByUserIdAndGroupId(testUserId, testGroupId) } returns true
            every { matchRepository.findById(testMatchId) } returns Optional.empty()

            assertThrows<NotFoundException> {
                predictionService.getGroupMatchPredictions(testUserId, testGroupId, testMatchId)
            }
        }
    }

    @Nested
    inner class CalculateStandingsTests {

        private val userId2 = UUID.randomUUID()
        private val testUser2 = testUser.copy(id = userId2, username = "user2", email = "user2@example.com")

        // A finished match with actual goals for scoring
        private val finishedMatch = testMatchLocked.copy(
            id = UUID.randomUUID(),
            homeGoals = 2,
            awayGoals = 1,
            homeQuota = 2.0f,
            finishedAt = LocalDateTime.now().minusHours(1),
        )

        private fun memberOf(user: User, joinedAt: LocalDateTime = LocalDateTime.now(), calculatedAt: LocalDateTime? = null) =
            GroupUser(
                id = UUID.randomUUID(), user = user, group = testGroup,
                role = GroupRole.MEMBER, points = 0f, rank = null,
                joinedAt = joinedAt, calculatedAt = calculatedAt,
            )

        @Test
        fun `calculateStandings returns empty standings ranked by join date when no finished matches`() {
            val member1 = memberOf(testUser, joinedAt = LocalDateTime.now().minusDays(2))
            val member2 = memberOf(testUser2, joinedAt = LocalDateTime.now().minusDays(1))

            every { matchRepository.findByTournamentIdAndStatusOrderByStartedAt(testTournamentId, MatchStatus.FINISHED) } returns emptyList()
            every { membershipRepository.findByGroupId(testGroupId) } returns listOf(member1, member2)
            every { membershipRepository.saveAll(any<Iterable<GroupUser>>()) } returns mutableListOf()

            val result = predictionService.calculateStandings(testGroup)

            assertEquals(2, result.size)
            assertEquals(1, result[0].rank)
            assertEquals(testUser.id, result[0].user.id)   // joined earlier → rank 1
            assertEquals(2, result[1].rank)
            assertEquals(userId2, result[1].user.id)
            result.forEach { assertEquals(0f, it.points) }
            verify(exactly = 0) { predictionRepository.findByGroupIdAndMatchIdIn(any(), any()) }
        }

        @Test
        fun `calculateStandings returns empty list when group has no members`() {
            every { matchRepository.findByTournamentIdAndStatusOrderByStartedAt(testTournamentId, MatchStatus.FINISHED) } returns emptyList()
            every { membershipRepository.findByGroupId(testGroupId) } returns emptyList()
            every { membershipRepository.saveAll(any<Iterable<GroupUser>>()) } returns mutableListOf()

            val result = predictionService.calculateStandings(testGroup)

            assertTrue(result.isEmpty())
        }

        @Test
        fun `calculateStandings computes new standings when calculatedAt is null`() {
            val member1 = memberOf(testUser, calculatedAt = null)
            val member2 = memberOf(testUser2, calculatedAt = null)

            val prediction1 = Prediction(
                id = UUID.randomUUID(), user = testUser, group = testGroup,
                match = finishedMatch, homeGoals = 2, awayGoals = 1, // exact match → CORRECT
            )
            val prediction2 = Prediction(
                id = UUID.randomUUID(), user = testUser2, group = testGroup,
                match = finishedMatch, homeGoals = 0, awayGoals = 1, // wrong outcome → INCORRECT
            )

            every { matchRepository.findByTournamentIdAndStatusOrderByStartedAt(testTournamentId, MatchStatus.FINISHED) } returns listOf(finishedMatch)
            every { membershipRepository.findByGroupId(testGroupId) } returns listOf(member1, member2)
            every { predictionRepository.findByGroupIdAndMatchIdIn(testGroupId, listOf(finishedMatch.id!!)) } returns listOf(prediction1, prediction2)
            every { predictionRepository.saveAll(any<List<Prediction>>()) } answers { firstArg() }
            every { membershipRepository.saveAll(any<List<GroupUser>>()) } answers { firstArg() }

            val result = predictionService.calculateStandings(testGroup)

            assertEquals(2, result.size)
            // testUser predicted correctly → more points → rank 1
            val user1Standing = result.find { it.user.id == testUser.id }!!
            val user2Standing = result.find { it.user.id == userId2 }!!
            assertTrue(user1Standing.points > user2Standing.points)
            assertEquals(1, user1Standing.rank)
            assertEquals(2, user2Standing.rank)
            verify { predictionRepository.saveAll(any<List<Prediction>>()) }
            verify { membershipRepository.saveAll(any<List<GroupUser>>()) }
        }

        @Test
        fun `calculateStandings computes new standings when calculatedAt is before last match finishedAt`() {
            val staleTime = LocalDateTime.now().minusHours(3)
            val member = memberOf(testUser, calculatedAt = staleTime)

            val prediction = Prediction(
                id = UUID.randomUUID(), user = testUser, group = testGroup,
                match = finishedMatch, homeGoals = 2, awayGoals = 1,
            )

            every { matchRepository.findByTournamentIdAndStatusOrderByStartedAt(testTournamentId, MatchStatus.FINISHED) } returns listOf(finishedMatch)
            every { membershipRepository.findByGroupId(testGroupId) } returns listOf(member)
            every { predictionRepository.findByGroupIdAndMatchIdIn(testGroupId, listOf(finishedMatch.id!!)) } returns listOf(prediction)
            every { predictionRepository.saveAll(any<List<Prediction>>()) } answers { firstArg() }
            every { membershipRepository.saveAll(any<List<GroupUser>>()) } answers { firstArg() }

            val result = predictionService.calculateStandings(testGroup)

            assertEquals(1, result.size)
            assertEquals(1, result[0].rank)
            verify { predictionRepository.findByGroupIdAndMatchIdIn(any(), any()) }
        }

        @Test
        fun `calculateStandings returns cached standings when all members are up to date`() {
            val freshTime = LocalDateTime.now().minusMinutes(10)
            val member = memberOf(testUser, calculatedAt = freshTime).copy(
                points = 8.5f, rank = 1,
                lastPredictions = listOf(PredictionStatus.CORRECT, PredictionStatus.PARTIAL)
            )

            every { matchRepository.findByTournamentIdAndStatusOrderByStartedAt(testTournamentId, MatchStatus.FINISHED) } returns listOf(finishedMatch)
            every { membershipRepository.findByGroupId(testGroupId) } returns listOf(member)

            val result = predictionService.calculateStandings(testGroup)

            assertEquals(1, result.size)
            assertEquals(1, result[0].rank)
            assertEquals(8.5f, result[0].points)
            assertEquals(listOf(PredictionStatus.CORRECT, PredictionStatus.PARTIAL), result[0].lastPredictions)
            verify(exactly = 0) { predictionRepository.findByGroupIdAndMatchIdIn(any(), any()) }
            verify(exactly = 0) { membershipRepository.saveAll(any<List<GroupUser>>()) }
        }

        @Test
        fun `calculateStandings assigns zero points to members with no predictions`() {
            val member1 = memberOf(testUser, calculatedAt = null)
            val member2 = memberOf(testUser2, calculatedAt = null)

            every { matchRepository.findByTournamentIdAndStatusOrderByStartedAt(testTournamentId, MatchStatus.FINISHED) } returns listOf(finishedMatch)
            every { membershipRepository.findByGroupId(testGroupId) } returns listOf(member1, member2)
            // Neither member has a prediction for this match
            every { predictionRepository.findByGroupIdAndMatchIdIn(testGroupId, listOf(finishedMatch.id!!)) } returns emptyList()
            every { predictionRepository.saveAll(any<Iterable<Prediction>>()) } returns mutableListOf()
            every { membershipRepository.saveAll(any<Iterable<GroupUser>>()) } returns mutableListOf()

            val result = predictionService.calculateStandings(testGroup)

            assertEquals(2, result.size)
            result.forEach { assertEquals(0f, it.points) }
            // With 1 finished match and no predictions, each member gets 1 MISSING entry
            result.forEach {
                assertEquals(1, it.lastPredictions.size)
                assertEquals(PredictionStatus.MISSING, it.lastPredictions[0])
            }
        }

        @Test
        fun `calculateStandings ranks members correctly by points descending`() {
            val member1 = memberOf(testUser, calculatedAt = null)
            val member2 = memberOf(testUser2, calculatedAt = null)

            // testUser2 predicts exactly right, testUser predicts incorrectly
            val pred1 = Prediction(
                id = UUID.randomUUID(), user = testUser, group = testGroup,
                match = finishedMatch, homeGoals = 0, awayGoals = 3, // wrong outcome
            )
            val pred2 = Prediction(
                id = UUID.randomUUID(), user = testUser2, group = testGroup,
                match = finishedMatch, homeGoals = 2, awayGoals = 1, // exact → higher score
            )

            every { matchRepository.findByTournamentIdAndStatusOrderByStartedAt(testTournamentId, MatchStatus.FINISHED) } returns listOf(finishedMatch)
            every { membershipRepository.findByGroupId(testGroupId) } returns listOf(member1, member2)
            every { predictionRepository.findByGroupIdAndMatchIdIn(testGroupId, listOf(finishedMatch.id!!)) } returns listOf(pred1, pred2)
            every { predictionRepository.saveAll(any<List<Prediction>>()) } answers { firstArg() }
            every { membershipRepository.saveAll(any<List<GroupUser>>()) } answers { firstArg() }

            val result = predictionService.calculateStandings(testGroup)

            assertEquals(2, result.size)
            assertEquals(userId2, result[0].user.id)   // testUser2 predicted correctly → rank 1
            assertEquals(testUserId, result[1].user.id)
            assertEquals(1, result[0].rank)
            assertEquals(2, result[1].rank)
        }
    }
}
