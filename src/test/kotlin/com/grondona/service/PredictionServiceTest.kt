package com.grondona.service

import com.grondona.exception.BadRequestException
import com.grondona.exception.ForbiddenException
import com.grondona.exception.NotFoundException
import com.grondona.model.AwardPrediction
import com.grondona.model.AwardPredictionView
import com.grondona.model.AwardType
import com.grondona.model.Group
import com.grondona.model.Match
import com.grondona.model.MatchStatus
import com.grondona.model.MatchPrediction
import com.grondona.model.MatchPredictionView
import com.grondona.model.Player
import com.grondona.model.PlayerPosition
import com.grondona.model.Team
import com.grondona.model.Tournament
import com.grondona.model.TournamentStatus
import com.grondona.model.User
import com.grondona.model.dto.request.SubmitAwardPredictionRequest
import com.grondona.model.dto.request.SubmitBulkMatchPredictionsRequest
import com.grondona.model.dto.request.SubmitMatchPredictionRequest
import com.grondona.repository.AwardPredictionRepository
import com.grondona.repository.GroupRepository
import com.grondona.repository.MatchRepository
import com.grondona.repository.MembershipRepository
import com.grondona.repository.MatchPredictionRepository
import com.grondona.repository.PlayerRepository
import com.grondona.repository.TeamRepository
import com.grondona.repository.TournamentRepository
import com.grondona.repository.UserRepository
import com.grondona.service.engine.WorldCupEngine
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
    private lateinit var teamRepository: TeamRepository

    @MockK
    private lateinit var groupRepository: GroupRepository

    @MockK
    private lateinit var matchRepository: MatchRepository

    @MockK
    private lateinit var playerRepository: PlayerRepository

    @MockK
    private lateinit var membershipRepository: MembershipRepository

    @MockK
    private lateinit var tournamentRepository: TournamentRepository

    @MockK
    private lateinit var matchPredictionRepository: MatchPredictionRepository

    @MockK
    private lateinit var awardPredictionRepository: AwardPredictionRepository

    @InjectMockKs
    private lateinit var awardPredictionService: PredictionService

    private val testUserId = UUID.randomUUID()
    private val testGroupId = UUID.randomUUID()
    private val testMatchId = UUID.randomUUID()
    private val testTournamentId = WorldCupEngine.SYSTEM_TOURNAMENT_ID

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

    private val testPlayer = Player(
        id = UUID.randomUUID(),
        team = testTeam,
        name = "Team A",
        position = PlayerPosition.MIDFIELDER,
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

    private fun mockMembership() {
        every { userRepository.findById(testUserId) } returns Optional.of(testUser)
        every { groupRepository.findById(testGroupId) } returns Optional.of(testGroup)
        every { membershipRepository.isMember(testUserId, testGroupId) } returns true
    }

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
    }

    @Nested
    inner class CanSubmitTests {

        @Test
        fun `canSubmit returns true when match starts more than 15 minutes from now`() {
            val match = testMatchOpen.copy(startedAt = LocalDateTime.now().plusHours(1))
            assertTrue(PredictionService.isMatchUnlocked(match))
        }

        @Test
        fun `canSubmit returns false when match starts in less than 15 minutes`() {
            val match = testMatchOpen.copy(startedAt = LocalDateTime.now().plusMinutes(5))
            assertFalse(PredictionService.isMatchUnlocked(match))
        }

        @Test
        fun `canSubmit returns false when match has already started`() {
            assertFalse(PredictionService.isMatchUnlocked(testMatchLocked))
        }

        @Test
        fun `canSubmit returns false when startedAt is null`() {
            val match = testMatchOpen.copy(startedAt = null)
            assertTrue(PredictionService.isMatchUnlocked(match))
        }
    }

    @Nested
    inner class CheckMembershipTests {

        @Test
        fun `checkMembership should throw NotFoundException when user not found`() {
            every { userRepository.findById(testUserId) } returns Optional.empty()
            assertThrows<NotFoundException> { awardPredictionService.checkMembership(testUserId, testGroupId) }
        }

        @Test
        fun `checkMembership should throw NotFoundException when group not found`() {
            every { userRepository.findById(testUserId) } returns Optional.of(testUser)
            every { groupRepository.findById(testGroupId) } returns Optional.empty()
            assertThrows<NotFoundException> { awardPredictionService.checkMembership(testUserId, testGroupId) }
        }

        @Test
        fun `checkMembership should throw ForbiddenException when not a member`() {
            every { userRepository.findById(testUserId) } returns Optional.of(testUser)
            every { groupRepository.findById(testGroupId) } returns Optional.of(testGroup)
            every { membershipRepository.isMember(testUserId, testGroupId) } returns false

            val exception = assertThrows<ForbiddenException> { awardPredictionService.getUserMatchPredictionsForGroup(testUserId, testGroupId) }
            assertEquals("User doesn't belong to the group", exception.message)
        }
    }

    @Nested
    inner class SubmitSingleMatchPredictionTests {

        private val request = SubmitMatchPredictionRequest(matchId = testMatchId, homeGoals = 2, awayGoals = 1)

        @Test
        fun `submitSingleMatchPrediction should succeed for an open match`() {
            mockMembership()
            every { matchRepository.findById(testMatchId) } returns Optional.of(testMatchOpen)
            every { matchPredictionRepository.upsert(any()) } returns testPrediction

            val result = awardPredictionService.submitSingleMatchPrediction(testUserId, testGroupId, request)

            assertEquals(testUserId, result.user.id)
            assertEquals(testMatchId, result.match.id)
            verify { matchPredictionRepository.upsert(any()) }
        }

        @Test
        fun `submitSingleMatchPrediction should throw NotFoundException when match not found`() {
            every { userRepository.findById(testUserId) } returns Optional.of(testUser)
            every { groupRepository.findById(testGroupId) } returns Optional.of(testGroup)
            every { membershipRepository.isMember(testUserId, testGroupId) } returns true
            every { matchRepository.findById(testMatchId) } returns Optional.empty()

            assertThrows<NotFoundException> {
                awardPredictionService.submitSingleMatchPrediction(testUserId, testGroupId, request)
            }
            verify(exactly = 0) { matchPredictionRepository.upsert(any()) }
        }

        @Test
        fun `submitSingleMatchPrediction should throw BadRequestException when match is locked`() {
            every { userRepository.findById(testUserId) } returns Optional.of(testUser)
            every { groupRepository.findById(testGroupId) } returns Optional.of(testGroup)
            every { membershipRepository.isMember(testUserId, testGroupId) } returns true
            every { matchRepository.findById(testMatchId) } returns Optional.of(testMatchLocked)

            val exception = assertThrows<BadRequestException> {
                awardPredictionService.submitSingleMatchPrediction(testUserId, testGroupId, request)
            }
            assertEquals("Cannot submit predictions for this match", exception.message)
            verify(exactly = 0) { matchPredictionRepository.upsert(any()) }
        }
    }

    @Nested
    inner class SubmitMatchPredictionsTests {

        private val matchId2 = UUID.randomUUID()

        private val openMatch2 = testMatchOpen.copy(id = matchId2, code = "OPEN-02")
        private val lockedMatch2 = testMatchLocked.copy(id = matchId2, code = "LOCKED-02")

        @Test
        fun `submitMatchPredictions should submit all open matches`() {
            val request = SubmitBulkMatchPredictionsRequest(
                predictions = listOf(
                    SubmitMatchPredictionRequest(matchId = testMatchId, homeGoals = 1, awayGoals = 0),
                    SubmitMatchPredictionRequest(matchId = matchId2, homeGoals = 2, awayGoals = 2)
                )
            )
            val savedPredictions = listOf(testPrediction, testPrediction.copy(match = openMatch2))

            mockMembership()
            every { matchRepository.findById(testMatchId) } returns Optional.of(testMatchOpen)
            every { matchRepository.findById(matchId2) } returns Optional.of(openMatch2)
            every { matchPredictionRepository.upsertAll(any()) } returns savedPredictions

            val result = awardPredictionService.submitMatchPredictions(testUserId, testGroupId, request)

            assertEquals(testGroupId, result.group.id)
            assertEquals(2, result.predictions.size)
            verify { matchPredictionRepository.upsertAll(match { it.size == 2 }) }
        }

        @Test
        fun `submitMatchPredictions should silently skip locked matches`() {
            val request = SubmitBulkMatchPredictionsRequest(
                predictions = listOf(
                    SubmitMatchPredictionRequest(matchId = testMatchId, homeGoals = 1, awayGoals = 0),
                    SubmitMatchPredictionRequest(matchId = matchId2, homeGoals = 0, awayGoals = 0)
                )
            )

            mockMembership()
            every { matchRepository.findById(testMatchId) } returns Optional.of(testMatchOpen)
            every { matchRepository.findById(matchId2) } returns Optional.of(lockedMatch2)
            every { matchPredictionRepository.upsertAll(any()) } returns listOf(testPrediction)

            val result = awardPredictionService.submitMatchPredictions(testUserId, testGroupId, request)

            assertEquals(1, result.predictions.size)
            verify { matchPredictionRepository.upsertAll(match { it.size == 1 }) }
        }

        @Test
        fun `submitMatchPredictions should return empty when all matches are locked`() {
            val request = SubmitBulkMatchPredictionsRequest(
                predictions = listOf(SubmitMatchPredictionRequest(matchId = testMatchId, homeGoals = 1, awayGoals = 0))
            )

            mockMembership()
            every { matchRepository.findById(testMatchId) } returns Optional.of(testMatchLocked)
            every { matchPredictionRepository.upsertAll(emptyList()) } returns emptyList()

            val result = awardPredictionService.submitMatchPredictions(testUserId, testGroupId, request)

            assertEquals(0, result.predictions.size)
        }
    }

    @Nested
    inner class GetUserMatchPredictionsForGroupTests {

        @Test
        fun `getUserMatchPredictionsForGroup should return predictions for member`() {
            val predictionView = MatchPredictionView(
                id = UUID.randomUUID(), user = testUser, rank = 1, match = testMatchOpen, prediction = testPrediction
            )

            mockMembership()
            every { matchPredictionRepository.findGroupPredictionsForUser(testGroupId, testUserId) } returns listOf(predictionView)

            val result = awardPredictionService.getUserMatchPredictionsForGroup(testUserId, testGroupId)
            assertEquals(testGroupId, result.group.id)
            assertEquals(1, result.predictions.size)
        }
    }

    @Nested
    inner class GetMatchPredictionsForGroupTests {

        @Test
        fun `getMatchPredictionsForGroup should return predictions for whole group`() {
            val testUser2 = testUser.copy(id = UUID.randomUUID())
            val predictionViews = listOf(
                MatchPredictionView(id = UUID.randomUUID(), user = testUser, rank = 1, match = testMatchOpen, prediction = testPrediction),
                MatchPredictionView(id = UUID.randomUUID(), user = testUser2, rank = 2, match = testMatchOpen, prediction = testPrediction),
            )

            mockMembership()
            every { matchPredictionRepository.findGroupPredictions(testGroupId) } returns predictionViews

            val result = awardPredictionService.getMatchPredictionsForGroup(testUserId, testGroupId)
            assertEquals(testGroupId, result.group.id)
            assertEquals(2, result.predictions.size)
        }
    }

    @Nested
    inner class GetSingleMatchPredictionsForGroupTests {

        @Test
        fun `getSingleMatchPredictionsForGroup should return predictions for locked match`() {
            val predictionView = MatchPredictionView(
                id = UUID.randomUUID(), user = testUser, rank = 1, match = testMatchLocked, prediction = testPrediction
            )

            mockMembership()
            every { matchRepository.findById(testMatchId) } returns Optional.of(testMatchLocked)
            every { matchPredictionRepository.findGroupPredictionsForMatch(testGroupId, testMatchId) } returns listOf(predictionView)

            val result = awardPredictionService.getSingleMatchPredictionsForGroup(testUserId, testGroupId, testMatchId)
            assertEquals(testGroupId, result.group.id)
            assertEquals(1, result.predictions.size)
        }

        @Test
        fun `getSingleMatchPredictionsForGroup should throw BadRequestException when match is still open`() {
            mockMembership()
            every { matchRepository.findById(testMatchId) } returns Optional.of(testMatchOpen)

            val exception = assertThrows<BadRequestException> {
                awardPredictionService.getSingleMatchPredictionsForGroup(testUserId, testGroupId, testMatchId)
            }
            assertEquals("Match is still open", exception.message)
        }

        @Test
        fun `getSingleMatchPredictionsForGroup should throw NotFoundException when match not found`() {
            mockMembership()
            every { matchRepository.findById(testMatchId) } returns Optional.empty()

            assertThrows<NotFoundException> { awardPredictionService.getSingleMatchPredictionsForGroup(testUserId, testGroupId, testMatchId) }
        }
    }

    @Nested
    inner class SubmitAwardsPredictionsTests {

        @Test
        fun `submitAwardPredictions should submit awards for a tournament (if they're all provided)`() {
            val request = SubmitAwardPredictionRequest(
                champions = listOf(UUID.randomUUID(), UUID.randomUUID()),
                topScorers = listOf(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()),
                bestPlayers = listOf(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()),
                bestGoalkeepers = listOf(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()),
                bestYoungPlayers = listOf(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()),
            )

            mockMembership()
            every { tournamentRepository.findById(testTournamentId) } returns Optional.of(testTournament)
            every { awardPredictionRepository.deleteByUserId(testUserId) } returns 0
            every { awardPredictionRepository.saveAll(any<List<AwardPrediction>>()) } answers { firstArg() }

            every { teamRepository.getReferenceById(request.champions[0]) } returns
                    testTeam.copy(id = request.champions[0], name = "Team 1")
            every { teamRepository.getReferenceById(request.champions[1]) } returns
                    testTeam.copy(id = request.champions[1], name = "Team 2")
            every { playerRepository.getReferenceById(request.topScorers[0]) } returns
                    testPlayer.copy(id = request.topScorers[0], name = "Player 1")
            every { playerRepository.getReferenceById(request.topScorers[1]) } returns
                    testPlayer.copy(id = request.topScorers[1], name = "Player 2")
            every { playerRepository.getReferenceById(request.topScorers[2]) } returns
                    testPlayer.copy(id = request.topScorers[2], name = "Player 3")
            every { playerRepository.getReferenceById(request.bestPlayers[0]) } returns
                    testPlayer.copy(id = request.bestPlayers[0], name = "Player 4")
            every { playerRepository.getReferenceById(request.bestPlayers[1]) } returns
                    testPlayer.copy(id = request.bestPlayers[1], name = "Player 5")
            every { playerRepository.getReferenceById(request.bestPlayers[2]) } returns
                    testPlayer.copy(id = request.bestPlayers[2], name = "Player 6")
            every { playerRepository.getReferenceById(request.bestGoalkeepers[0]) } returns
                    testPlayer.copy(id = request.bestGoalkeepers[0], name = "Player 7")
            every { playerRepository.getReferenceById(request.bestGoalkeepers[1]) } returns
                    testPlayer.copy(id = request.bestGoalkeepers[1], name = "Player 8")
            every { playerRepository.getReferenceById(request.bestGoalkeepers[2]) } returns
                    testPlayer.copy(id = request.bestGoalkeepers[2], name = "Player 9")
            every { playerRepository.getReferenceById(request.bestYoungPlayers[0]) } returns
                    testPlayer.copy(id = request.bestYoungPlayers[0], name = "Player 10")
            every { playerRepository.getReferenceById(request.bestYoungPlayers[1]) } returns
                    testPlayer.copy(id = request.bestYoungPlayers[1], name = "Player 11")
            every { playerRepository.getReferenceById(request.bestYoungPlayers[2]) } returns
                    testPlayer.copy(id = request.bestYoungPlayers[2], name = "Player 12")

            val result = awardPredictionService.submitAwardPredictions(testUserId, testGroupId, testTournamentId, request)
            assertEquals(request.champions[0], result.champions[0].id)
            assertEquals("Team 1", result.champions[0].name)
            assertEquals(request.champions[1], result.champions[1].id)
            assertEquals("Team 2", result.champions[1].name)
            assertEquals(request.topScorers[0], result.topScorers[0].id)
            assertEquals("Player 1", result.topScorers[0].name)
            assertEquals(request.topScorers[1], result.topScorers[1].id)
            assertEquals("Player 2", result.topScorers[1].name)
            assertEquals(request.topScorers[2], result.topScorers[2].id)
            assertEquals("Player 3", result.topScorers[2].name)
            assertEquals(request.bestPlayers[0], result.bestPlayers[0].id)
            assertEquals("Player 4", result.bestPlayers[0].name)
            assertEquals(request.bestPlayers[1], result.bestPlayers[1].id)
            assertEquals("Player 5", result.bestPlayers[1].name)
            assertEquals(request.bestPlayers[2], result.bestPlayers[2].id)
            assertEquals("Player 6", result.bestPlayers[2].name)
            assertEquals(request.bestGoalkeepers[0], result.bestGoalkeepers[0].id)
            assertEquals("Player 7", result.bestGoalkeepers[0].name)
            assertEquals(request.bestGoalkeepers[1], result.bestGoalkeepers[1].id)
            assertEquals("Player 8", result.bestGoalkeepers[1].name)
            assertEquals(request.bestGoalkeepers[2], result.bestGoalkeepers[2].id)
            assertEquals("Player 9", result.bestGoalkeepers[2].name)
            assertEquals(request.bestYoungPlayers[0], result.bestYoungPlayers[0].id)
            assertEquals("Player 10", result.bestYoungPlayers[0].name)
            assertEquals(request.bestYoungPlayers[1], result.bestYoungPlayers[1].id)
            assertEquals("Player 11", result.bestYoungPlayers[1].name)
            assertEquals(request.bestYoungPlayers[2], result.bestYoungPlayers[2].id)
            assertEquals("Player 12", result.bestYoungPlayers[2].name)

            val slot = slot<List<AwardPrediction>>()
            verify(exactly = 1) { awardPredictionRepository.saveAll(capture(slot)) }
            val savedPredictions = slot.captured
            assertEquals(14, savedPredictions.size)
            assertEquals(AwardType.CHAMPION, savedPredictions[0].awardType)
            assertEquals(request.champions[0], savedPredictions[0].team!!.id!!)
            assertEquals(AwardType.CHAMPION, savedPredictions[1].awardType)
            assertEquals(request.champions[1], savedPredictions[1].team!!.id!!)
            assertEquals(AwardType.TOP_SCORER, savedPredictions[2].awardType)
            assertEquals(request.topScorers[0], savedPredictions[2].player!!.id!!)
            assertEquals(AwardType.TOP_SCORER, savedPredictions[3].awardType)
            assertEquals(request.topScorers[1], savedPredictions[3].player!!.id!!)
            assertEquals(AwardType.TOP_SCORER, savedPredictions[4].awardType)
            assertEquals(request.topScorers[2], savedPredictions[4].player!!.id!!)
            assertEquals(AwardType.BEST_PLAYER, savedPredictions[5].awardType)
            assertEquals(request.bestPlayers[0], savedPredictions[5].player!!.id!!)
            assertEquals(AwardType.BEST_PLAYER, savedPredictions[6].awardType)
            assertEquals(request.bestPlayers[1], savedPredictions[6].player!!.id!!)
            assertEquals(AwardType.BEST_PLAYER, savedPredictions[7].awardType)
            assertEquals(request.bestPlayers[2], savedPredictions[7].player!!.id!!)
            assertEquals(AwardType.BEST_GOALKEEPER, savedPredictions[8].awardType)
            assertEquals(request.bestGoalkeepers[0], savedPredictions[8].player!!.id!!)
            assertEquals(AwardType.BEST_GOALKEEPER, savedPredictions[9].awardType)
            assertEquals(request.bestGoalkeepers[1], savedPredictions[9].player!!.id!!)
            assertEquals(AwardType.BEST_GOALKEEPER, savedPredictions[10].awardType)
            assertEquals(request.bestGoalkeepers[2], savedPredictions[10].player!!.id!!)
            assertEquals(AwardType.BEST_YOUNG_PLAYER, savedPredictions[11].awardType)
            assertEquals(request.bestYoungPlayers[0], savedPredictions[11].player!!.id!!)
            assertEquals(AwardType.BEST_YOUNG_PLAYER, savedPredictions[12].awardType)
            assertEquals(request.bestYoungPlayers[1], savedPredictions[12].player!!.id!!)
            assertEquals(AwardType.BEST_YOUNG_PLAYER, savedPredictions[13].awardType)
            assertEquals(request.bestYoungPlayers[2], savedPredictions[13].player!!.id!!)
        }

        @Test
        fun `submitAwardPredictions should submit awards for a tournament (if some are missing)`() {
            val request = SubmitAwardPredictionRequest(
                champions = listOf(UUID.randomUUID()),
                topScorers = emptyList(),
                bestPlayers = listOf(UUID.randomUUID(), UUID.randomUUID()),
                bestGoalkeepers = listOf(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()),
                bestYoungPlayers = emptyList(),
            )

            mockMembership()
            every { tournamentRepository.findById(testTournamentId) } returns Optional.of(testTournament)
            every { awardPredictionRepository.deleteByUserId(testUserId) } returns 0
            every { awardPredictionRepository.saveAll(any<List<AwardPrediction>>()) } answers { firstArg() }

            every { teamRepository.getReferenceById(request.champions[0]) } returns
                    testTeam.copy(id = request.champions[0], name = "Team 1")
            every { playerRepository.getReferenceById(request.bestPlayers[0]) } returns
                    testPlayer.copy(id = request.bestPlayers[0], name = "Player 1")
            every { playerRepository.getReferenceById(request.bestPlayers[1]) } returns
                    testPlayer.copy(id = request.bestPlayers[1], name = "Player 2")
            every { playerRepository.getReferenceById(request.bestGoalkeepers[0]) } returns
                    testPlayer.copy(id = request.bestGoalkeepers[0], name = "Player 3")
            every { playerRepository.getReferenceById(request.bestGoalkeepers[1]) } returns
                    testPlayer.copy(id = request.bestGoalkeepers[1], name = "Player 4")
            every { playerRepository.getReferenceById(request.bestGoalkeepers[2]) } returns
                    testPlayer.copy(id = request.bestGoalkeepers[2], name = "Player 5")

            val result = awardPredictionService.submitAwardPredictions(testUserId, testGroupId, testTournamentId, request)
            assertEquals(request.champions[0], result.champions[0].id)
            assertEquals("Team 1", result.champions[0].name)
            assertEquals(request.bestPlayers[0], result.bestPlayers[0].id)
            assertEquals("Player 1", result.bestPlayers[0].name)
            assertEquals(request.bestPlayers[1], result.bestPlayers[1].id)
            assertEquals("Player 2", result.bestPlayers[1].name)
            assertEquals(request.bestGoalkeepers[0], result.bestGoalkeepers[0].id)
            assertEquals("Player 3", result.bestGoalkeepers[0].name)
            assertEquals(request.bestGoalkeepers[1], result.bestGoalkeepers[1].id)
            assertEquals("Player 4", result.bestGoalkeepers[1].name)
            assertEquals(request.bestGoalkeepers[2], result.bestGoalkeepers[2].id)
            assertEquals("Player 5", result.bestGoalkeepers[2].name)

            val slot = slot<List<AwardPrediction>>()
            verify(exactly = 1) { awardPredictionRepository.saveAll(capture(slot)) }
            val savedPredictions = slot.captured
            assertEquals(6, savedPredictions.size)
            assertEquals(AwardType.CHAMPION, savedPredictions[0].awardType)
            assertEquals(request.champions[0], savedPredictions[0].team!!.id!!)
            assertEquals(AwardType.BEST_PLAYER, savedPredictions[1].awardType)
            assertEquals(request.bestPlayers[0], savedPredictions[1].player!!.id!!)
            assertEquals(AwardType.BEST_PLAYER, savedPredictions[2].awardType)
            assertEquals(request.bestPlayers[1], savedPredictions[2].player!!.id!!)
            assertEquals(AwardType.BEST_GOALKEEPER, savedPredictions[3].awardType)
            assertEquals(request.bestGoalkeepers[0], savedPredictions[3].player!!.id!!)
            assertEquals(AwardType.BEST_GOALKEEPER, savedPredictions[4].awardType)
            assertEquals(request.bestGoalkeepers[1], savedPredictions[4].player!!.id!!)
            assertEquals(AwardType.BEST_GOALKEEPER, savedPredictions[5].awardType)
            assertEquals(request.bestGoalkeepers[2], savedPredictions[5].player!!.id!!)
        }

        @Test
        fun `submitAwardPredictions should not allow submissions when tournament has started`() {
            val request = SubmitAwardPredictionRequest(
                champions = listOf(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()),
                topScorers = emptyList(),
                bestPlayers = emptyList(),
                bestGoalkeepers = emptyList(),
                bestYoungPlayers = emptyList(),
            )

            mockMembership()
            every { tournamentRepository.findById(testTournamentId) } returns Optional.of(testTournament.copy(status = TournamentStatus.IN_PROGRESS))
            val exception = assertThrows<BadRequestException> {
                awardPredictionService.submitAwardPredictions(testUserId, testGroupId, testTournamentId, request)
            }
            assertEquals("Tournament has already started", exception.message)
        }

        @Test
        fun `submitAwardPredictions should not allow more than 2 champions`() {
            val request = SubmitAwardPredictionRequest(
                champions = listOf(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()),
                topScorers = emptyList(),
                bestPlayers = emptyList(),
                bestGoalkeepers = emptyList(),
                bestYoungPlayers = emptyList(),
            )

            mockMembership()
            every { tournamentRepository.findById(testTournamentId) } returns Optional.of(testTournament)
            val exception = assertThrows<BadRequestException> {
                awardPredictionService.submitAwardPredictions(testUserId, testGroupId, testTournamentId, request)
            }
            assertEquals("Invalid amount of awards", exception.message)
        }

        @Test
        fun `submitAwardPredictions should not allow more than 3 top scorers`() {
            val request = SubmitAwardPredictionRequest(
                champions = emptyList(),
                topScorers = listOf(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()),
                bestPlayers = emptyList(),
                bestGoalkeepers = emptyList(),
                bestYoungPlayers = emptyList(),
            )

            mockMembership()
            every { tournamentRepository.findById(testTournamentId) } returns Optional.of(testTournament)
            val exception = assertThrows<BadRequestException> {
                awardPredictionService.submitAwardPredictions(testUserId, testGroupId, testTournamentId, request)
            }
            assertEquals("Invalid amount of awards", exception.message)
        }

        @Test
        fun `submitAwardPredictions should not allow more than 3 best players`() {
            val request = SubmitAwardPredictionRequest(
                champions = emptyList(),
                topScorers = emptyList(),
                bestPlayers = listOf(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()),
                bestGoalkeepers = emptyList(),
                bestYoungPlayers = emptyList(),
            )

            mockMembership()
            every { tournamentRepository.findById(testTournamentId) } returns Optional.of(testTournament)
            val exception = assertThrows<BadRequestException> {
                awardPredictionService.submitAwardPredictions(testUserId, testGroupId, testTournamentId, request)
            }
            assertEquals("Invalid amount of awards", exception.message)
        }

        @Test
        fun `submitAwardPredictions should not allow more than 3 best goalkeepers`() {
            val request = SubmitAwardPredictionRequest(
                champions = emptyList(),
                topScorers = emptyList(),
                bestPlayers = emptyList(),
                bestGoalkeepers = listOf(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()),
                bestYoungPlayers = emptyList(),
            )

            mockMembership()
            every { tournamentRepository.findById(testTournamentId) } returns Optional.of(testTournament)
            val exception = assertThrows<BadRequestException> {
                awardPredictionService.submitAwardPredictions(testUserId, testGroupId, testTournamentId, request)
            }
            assertEquals("Invalid amount of awards", exception.message)
        }

        @Test
        fun `submitAwardPredictions should not allow more than 3 best young players`() {
            val request = SubmitAwardPredictionRequest(
                champions = emptyList(),
                topScorers = emptyList(),
                bestPlayers = emptyList(),
                bestGoalkeepers = emptyList(),
                bestYoungPlayers = listOf(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()),
            )

            mockMembership()
            every { tournamentRepository.findById(testTournamentId) } returns Optional.of(testTournament)
            val exception = assertThrows<BadRequestException> {
                awardPredictionService.submitAwardPredictions(testUserId, testGroupId, testTournamentId, request)
            }
            assertEquals("Invalid amount of awards", exception.message)
        }
    }

    @Nested
    inner class GetUserAwardsPredictionsForGroupTests {

        @Test
        fun `getUserAwardPredictionsForGroup should return awards predictions for whole group`() {
            val predictions = listOf(
                AwardPrediction(user = testUser, group = testGroup, awardType = AwardType.CHAMPION, team = testTeam),
                AwardPrediction(user = testUser, group = testGroup, awardType = AwardType.CHAMPION, team = testTeam.copy(id = UUID.randomUUID())),
                AwardPrediction(user = testUser, group = testGroup, awardType = AwardType.BEST_PLAYER, player = testPlayer),
            )

            mockMembership()
            every { tournamentRepository.findById(testTournamentId) } returns Optional.of(testTournament)
            every { awardPredictionRepository.findByUserIdAndGroupId(testUserId, testGroupId) } returns predictions

            val result = awardPredictionService.getUserAwardPredictionsForGroup(testUserId, testGroupId)
            assertEquals(2, result.champions.size)
            assertEquals(predictions[0].team!!.id, result.champions[0].id)
            assertEquals(predictions[1].team!!.id, result.champions[1].id)
            assertEquals(1, result.bestPlayers.size)
            assertEquals(predictions[2].player!!.id, result.bestPlayers[0].id)
            assertEquals(0, result.topScorers.size)
            assertEquals(0, result.bestGoalkeepers.size)
            assertEquals(0, result.bestYoungPlayers.size)
        }
    }

    @Nested
    inner class GetAwardsPredictionsForGroupTests {

        @Test
        fun `getAwardPredictionsForGroup should not fail when tournament hasn't started`() {
            mockMembership()
            every { tournamentRepository.findById(testTournamentId) } returns Optional.of(testTournament)

            val exception = assertThrows<BadRequestException> {
                awardPredictionService.getAwardPredictionsForGroup(testUserId, testGroupId, testTournamentId)
            }
            assertEquals("Tournament hasn't started yet", exception.message)
        }

        @Test
        fun `getAwardPredictionsForGroup should return awards predictions for member`() {
            val testUser2 = testUser.copy(id = UUID.randomUUID())
            val predictions = listOf(
                AwardPredictionView(id = UUID.randomUUID(), user = testUser,
                    awardPrediction = AwardPrediction(user = testUser, group = testGroup, awardType = AwardType.CHAMPION, team = testTeam)),
                AwardPredictionView(id = UUID.randomUUID(), user = testUser2,
                    awardPrediction = AwardPrediction(user = testUser2, group = testGroup, awardType = AwardType.BEST_PLAYER, player = testPlayer)),
            )

            mockMembership()
            every { tournamentRepository.findById(testTournamentId) } returns Optional.of(testTournament.copy(status = TournamentStatus.IN_PROGRESS))
            every { awardPredictionRepository.findGroupAwardPredictions(testGroupId) } returns predictions

            val result = awardPredictionService.getAwardPredictionsForGroup(testUserId, testGroupId, testTournamentId)
            assertEquals(testGroupId, result.group.id)
            assertEquals(2, result.predictions.size)
            assertEquals(testUser.id, result.predictions[0].user.id)
            assertEquals(1, result.predictions[0].champions.size)
            assertEquals(testTeam.id, result.predictions[0].champions[0].id)
            assertEquals(testUser2.id, result.predictions[1].user.id)
            assertEquals(1, result.predictions[1].bestPlayers.size)
            assertEquals(testPlayer.id, result.predictions[1].bestPlayers[0].id)
        }
    }
}
