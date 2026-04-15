package com.grondona.service

import com.grondona.exception.BadRequestException
import com.grondona.exception.ForbiddenException
import com.grondona.exception.NotFoundException
import com.grondona.model.Group
import com.grondona.model.Match
import com.grondona.model.MatchStatus
import com.grondona.model.MatchPrediction
import com.grondona.model.MatchPredictionView
import com.grondona.model.Team
import com.grondona.model.Tournament
import com.grondona.model.TournamentStatus
import com.grondona.model.User
import com.grondona.model.dto.request.SubmitBulkMatchPredictionsRequest
import com.grondona.model.dto.request.SubmitMatchPredictionRequest
import com.grondona.repository.GroupRepository
import com.grondona.repository.MatchRepository
import com.grondona.repository.MembershipRepository
import com.grondona.repository.MatchPredictionRepository
import com.grondona.repository.UserRepository
import com.grondona.utils.WorldCupEngine
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
    private lateinit var predictionRepository: MatchPredictionRepository

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
        id = WorldCupEngine.SYSTEM_TOURNAMENT_ID,
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

    private val testPrediction = MatchPrediction(
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
            assertTrue(PredictionService.canSubmit(match))
        }
    }

    @Nested
    inner class SubmitPredictionTests {

        private val request = SubmitMatchPredictionRequest(matchId = testMatchId, homeGoals = 2, awayGoals = 1)

        @Test
        fun `submitPrediction should succeed for an open match`() {
            every { userRepository.findById(testUserId) } returns Optional.of(testUser)
            every { groupRepository.findById(testGroupId) } returns Optional.of(testGroup)
            every { membershipRepository.existsByUserIdAndGroupId(testUserId, testGroupId) } returns true
            every { matchRepository.findById(testMatchId) } returns Optional.of(testMatchOpen)
            every { predictionRepository.upsert(any()) } returns testPrediction

            val result = predictionService.submitSingleMatchPrediction(testUserId, testGroupId, request)

            assertEquals(testUserId, result.user.id)
            assertEquals(testMatchId, result.match.id)
            verify { predictionRepository.upsert(any()) }
        }

        @Test
        fun `submitPrediction should throw NotFoundException when user not found`() {
            every { userRepository.findById(testUserId) } returns Optional.empty()

            assertThrows<NotFoundException> {
                predictionService.submitSingleMatchPrediction(testUserId, testGroupId, request)
            }
            verify(exactly = 0) { predictionRepository.upsert(any()) }
        }

        @Test
        fun `submitPrediction should throw NotFoundException when group not found`() {
            every { userRepository.findById(testUserId) } returns Optional.of(testUser)
            every { groupRepository.findById(testGroupId) } returns Optional.empty()

            assertThrows<NotFoundException> {
                predictionService.submitSingleMatchPrediction(testUserId, testGroupId, request)
            }
            verify(exactly = 0) { predictionRepository.upsert(any()) }
        }

        @Test
        fun `submitPrediction should throw ForbiddenException when user is not a member`() {
            every { userRepository.findById(testUserId) } returns Optional.of(testUser)
            every { groupRepository.findById(testGroupId) } returns Optional.of(testGroup)
            every { membershipRepository.existsByUserIdAndGroupId(testUserId, testGroupId) } returns false

            val exception = assertThrows<ForbiddenException> {
                predictionService.submitSingleMatchPrediction(testUserId, testGroupId, request)
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
                predictionService.submitSingleMatchPrediction(testUserId, testGroupId, request)
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
                predictionService.submitSingleMatchPrediction(testUserId, testGroupId, request)
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
            val request = SubmitBulkMatchPredictionsRequest(
                predictions = listOf(
                    SubmitMatchPredictionRequest(matchId = testMatchId, homeGoals = 1, awayGoals = 0),
                    SubmitMatchPredictionRequest(matchId = matchId2, homeGoals = 2, awayGoals = 2)
                )
            )
            val savedPredictions = listOf(testPrediction, testPrediction.copy(match = openMatch2))

            every { userRepository.findById(testUserId) } returns Optional.of(testUser)
            every { groupRepository.findById(testGroupId) } returns Optional.of(testGroup)
            every { membershipRepository.existsByUserIdAndGroupId(testUserId, testGroupId) } returns true
            every { matchRepository.findById(testMatchId) } returns Optional.of(testMatchOpen)
            every { matchRepository.findById(matchId2) } returns Optional.of(openMatch2)
            every { predictionRepository.upsertAll(any()) } returns savedPredictions

            val result = predictionService.submitMatchPredictions(testUserId, testGroupId, request)

            assertEquals(testGroupId, result.groupId)
            assertEquals(2, result.predictions.size)
            verify { predictionRepository.upsertAll(match { it.size == 2 }) }
        }

        @Test
        fun `submitBulkPredictions should silently skip locked matches`() {
            val request = SubmitBulkMatchPredictionsRequest(
                predictions = listOf(
                    SubmitMatchPredictionRequest(matchId = testMatchId, homeGoals = 1, awayGoals = 0),
                    SubmitMatchPredictionRequest(matchId = matchId2, homeGoals = 0, awayGoals = 0)
                )
            )

            every { userRepository.findById(testUserId) } returns Optional.of(testUser)
            every { groupRepository.findById(testGroupId) } returns Optional.of(testGroup)
            every { membershipRepository.existsByUserIdAndGroupId(testUserId, testGroupId) } returns true
            every { matchRepository.findById(testMatchId) } returns Optional.of(testMatchOpen)
            every { matchRepository.findById(matchId2) } returns Optional.of(lockedMatch2)
            every { predictionRepository.upsertAll(any()) } returns listOf(testPrediction)

            val result = predictionService.submitMatchPredictions(testUserId, testGroupId, request)

            assertEquals(1, result.predictions.size)
            verify { predictionRepository.upsertAll(match { it.size == 1 }) }
        }

        @Test
        fun `submitBulkPredictions should return empty when all matches are locked`() {
            val request = SubmitBulkMatchPredictionsRequest(
                predictions = listOf(SubmitMatchPredictionRequest(matchId = testMatchId, homeGoals = 1, awayGoals = 0))
            )

            every { userRepository.findById(testUserId) } returns Optional.of(testUser)
            every { groupRepository.findById(testGroupId) } returns Optional.of(testGroup)
            every { membershipRepository.existsByUserIdAndGroupId(testUserId, testGroupId) } returns true
            every { matchRepository.findById(testMatchId) } returns Optional.of(testMatchLocked)
            every { predictionRepository.upsertAll(emptyList()) } returns emptyList()

            val result = predictionService.submitMatchPredictions(testUserId, testGroupId, request)

            assertEquals(0, result.predictions.size)
        }

        @Test
        fun `submitBulkPredictions should throw ForbiddenException when user is not a member`() {
            val request = SubmitBulkMatchPredictionsRequest(
                predictions = listOf(SubmitMatchPredictionRequest(matchId = testMatchId, homeGoals = 1, awayGoals = 0))
            )
            every { userRepository.findById(testUserId) } returns Optional.of(testUser)
            every { groupRepository.findById(testGroupId) } returns Optional.of(testGroup)
            every { membershipRepository.existsByUserIdAndGroupId(testUserId, testGroupId) } returns false

            val exception = assertThrows<ForbiddenException> {
                predictionService.submitMatchPredictions(testUserId, testGroupId, request)
            }
            assertEquals("User doesn't belong to the group", exception.message)
        }

        @Test
        fun `submitBulkPredictions should throw NotFoundException when group not found`() {
            val request = SubmitBulkMatchPredictionsRequest(
                predictions = listOf(SubmitMatchPredictionRequest(matchId = testMatchId, homeGoals = 1, awayGoals = 0))
            )
            every { userRepository.findById(testUserId) } returns Optional.of(testUser)
            every { groupRepository.findById(testGroupId) } returns Optional.empty()

            assertThrows<NotFoundException> {
                predictionService.submitMatchPredictions(testUserId, testGroupId, request)
            }
        }
    }

    @Nested
    inner class GetGroupUserPredictionsTests {

        @Test
        fun `getGroupUserPredictions should return predictions for member`() {
            val predictionView = MatchPredictionView(
                id = UUID.randomUUID(), user = testUser, rank = 1, match = testMatchOpen, prediction = testPrediction
            )
            every { userRepository.findById(testUserId) } returns Optional.of(testUser)
            every { groupRepository.findById(testGroupId) } returns Optional.of(testGroup)
            every { membershipRepository.existsByUserIdAndGroupId(testUserId, testGroupId) } returns true
            every { predictionRepository.findGroupPredictionsForUser(testGroupId, testUserId) } returns listOf(
                predictionView
            )

            val result = predictionService.getUserMatchPredictionsForGroup(testUserId, testGroupId)

            assertEquals(testGroupId, result.groupId)
            assertEquals(1, result.predictions.size)
        }

        @Test
        fun `getGroupUserPredictions should throw ForbiddenException when not a member`() {
            every { userRepository.findById(testUserId) } returns Optional.of(testUser)
            every { groupRepository.findById(testGroupId) } returns Optional.of(testGroup)
            every { membershipRepository.existsByUserIdAndGroupId(testUserId, testGroupId) } returns false

            val exception = assertThrows<ForbiddenException> {
                predictionService.getUserMatchPredictionsForGroup(testUserId, testGroupId)
            }
            assertEquals("User doesn't belong to the group", exception.message)
        }

        @Test
        fun `getGroupUserPredictions should throw NotFoundException when group not found`() {
            every { userRepository.findById(testUserId) } returns Optional.of(testUser)
            every { groupRepository.findById(testGroupId) } returns Optional.empty()

            assertThrows<NotFoundException> {
                predictionService.getUserMatchPredictionsForGroup(testUserId, testGroupId)
            }
        }

        @Test
        fun `getGroupUserPredictions should throw NotFoundException when user not found`() {
            every { userRepository.findById(testUserId) } returns Optional.empty()

            assertThrows<NotFoundException> {
                predictionService.getUserMatchPredictionsForGroup(testUserId, testGroupId)
            }
        }
    }

    @Nested
    inner class GetGroupMatchPredictionsTests {

        @Test
        fun `getGroupMatchPredictions should return predictions for locked match`() {
            val predictionView = MatchPredictionView(
                id = UUID.randomUUID(), user = testUser, rank = 1, match = testMatchLocked, prediction = testPrediction
            )
            every { groupRepository.findById(testGroupId) } returns Optional.of(testGroup)
            every { membershipRepository.existsByUserIdAndGroupId(testUserId, testGroupId) } returns true
            every { matchRepository.findById(testMatchId) } returns Optional.of(testMatchLocked)
            every { predictionRepository.findGroupPredictionsForMatch(testGroupId, testMatchId) } returns listOf(
                predictionView
            )

            val result = predictionService.getSingleMatchPredictionsForGroup(testUserId, testGroupId, testMatchId)

            assertEquals(testGroupId, result.groupId)
            assertEquals(1, result.predictions.size)
        }

        @Test
        fun `getGroupMatchPredictions should throw BadRequestException when match is still open`() {
            every { groupRepository.findById(testGroupId) } returns Optional.of(testGroup)
            every { membershipRepository.existsByUserIdAndGroupId(testUserId, testGroupId) } returns true
            every { matchRepository.findById(testMatchId) } returns Optional.of(testMatchOpen)

            val exception = assertThrows<BadRequestException> {
                predictionService.getSingleMatchPredictionsForGroup(testUserId, testGroupId, testMatchId)
            }
            assertEquals("Match is still open", exception.message)
        }

        @Test
        fun `getGroupMatchPredictions should throw ForbiddenException when not a member`() {
            every { groupRepository.findById(testGroupId) } returns Optional.of(testGroup)
            every { membershipRepository.existsByUserIdAndGroupId(testUserId, testGroupId) } returns false

            val exception = assertThrows<ForbiddenException> {
                predictionService.getSingleMatchPredictionsForGroup(testUserId, testGroupId, testMatchId)
            }
            assertEquals("User doesn't belong to the group", exception.message)
        }

        @Test
        fun `getGroupMatchPredictions should throw NotFoundException when group not found`() {
            every { groupRepository.findById(testGroupId) } returns Optional.empty()

            assertThrows<NotFoundException> {
                predictionService.getSingleMatchPredictionsForGroup(testUserId, testGroupId, testMatchId)
            }
        }

        @Test
        fun `getGroupMatchPredictions should throw NotFoundException when match not found`() {
            every { groupRepository.findById(testGroupId) } returns Optional.of(testGroup)
            every { membershipRepository.existsByUserIdAndGroupId(testUserId, testGroupId) } returns true
            every { matchRepository.findById(testMatchId) } returns Optional.empty()

            assertThrows<NotFoundException> {
                predictionService.getSingleMatchPredictionsForGroup(testUserId, testGroupId, testMatchId)
            }
        }
    }
}
