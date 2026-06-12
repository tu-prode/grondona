package com.grondona.service

import com.grondona.exception.BadRequestException
import com.grondona.now
import com.grondona.exception.ForbiddenException
import com.grondona.exception.NotFoundException
import com.grondona.model.AwardPrediction
import com.grondona.model.AwardPredictionView
import com.grondona.model.AwardType
import com.grondona.model.Awards
import com.grondona.model.Group
import com.grondona.model.GroupUser
import com.grondona.model.Match
import com.grondona.model.MatchGroup
import com.grondona.model.MatchStatus
import com.grondona.model.MatchPrediction
import com.grondona.model.MatchPredictionView
import com.grondona.model.MatchStage
import com.grondona.model.Player
import com.grondona.model.PlayerPosition
import com.grondona.model.PredictionStatus
import com.grondona.model.Score
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
import com.grondona.service.engine.PredictionsEngine
import com.grondona.service.engine.WorldCupEngine
import io.mockk.*
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
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
    private lateinit var predictionService: PredictionService

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
        hasUniquePredictions = false,
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
        code = "A",
        name = "Team A",
        englishKey = "A-en",
        icon = "a.png",
    )

    private val testPlayer = Player(
        id = UUID.randomUUID(),
        team = testTeam,
        name = "Player A",
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
        stage = MatchStage.GROUP_STAGE,
        group = MatchGroup.GROUP_A,
        startedAt = ZonedDateTime.now().plusHours(2)
    )

    // Locked match: startedAt in the past → canSubmit = false
    private val testMatchLocked = Match(
        id = testMatchId,
        tournament = testTournament,
        code = "LOCKED-01",
        homeTeam = testTeam,
        awayTeam = testTeam,
        stage = MatchStage.GROUP_STAGE,
        group = MatchGroup.GROUP_A,
        status = MatchStatus.FINISHED,
        startedAt = ZonedDateTime.now().minusHours(2)
    )

    private val testPrediction = MatchPrediction(
        id = UUID.randomUUID(),
        user = testUser,
        group = testGroup,
        match = testMatchOpen,
        homeGoals = 1,
        awayGoals = 0
    )

    private fun mockMembership(user: User = testUser, group: Group = testGroup, points: Float = 0f) {
        every { userRepository.findById(testUserId) } returns Optional.of(user)
        every { groupRepository.findById(testGroupId) } returns Optional.of(group)
        every { membershipRepository.findMember(testUserId, testGroupId) } returns Optional.of(
            GroupUser(user = user, group = group, points = points)
        )
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @BeforeEach
    fun setUp() {
        now = LocalDateTime.now()
        MockKAnnotations.init(this)
    }

    @Nested
    inner class CanSubmitTests {

        @Test
        fun `canSubmit returns true when match starts more than 15 minutes from now`() {
            val match = testMatchOpen.copy(startedAt = ZonedDateTime.now().plusHours(1))
            assertTrue(PredictionService.isMatchUnlocked(match))
        }

        @Test
        fun `canSubmit returns false when match starts in less than 15 minutes`() {
            val match = testMatchOpen.copy(startedAt = ZonedDateTime.now().plusMinutes(5))
            assertFalse(PredictionService.isMatchUnlocked(match))
        }

        @Test
        fun `canSubmit returns false when match has already started`() {
            assertFalse(PredictionService.isMatchUnlocked(testMatchLocked))
        }
    }

    @Nested
    inner class CheckMembershipTests {

        @Test
        fun `checkMembership should throw NotFoundException when user not found`() {
            every { userRepository.findById(testUserId) } returns Optional.empty()
            assertThrows<NotFoundException> { predictionService.checkMembership(testUserId, testGroupId) }
        }

        @Test
        fun `checkMembership should throw NotFoundException when group not found`() {
            every { userRepository.findById(testUserId) } returns Optional.of(testUser)
            every { groupRepository.findById(testGroupId) } returns Optional.empty()
            assertThrows<NotFoundException> { predictionService.checkMembership(testUserId, testGroupId) }
        }

        @Test
        fun `checkMembership should throw ForbiddenException when not a member`() {
            every { userRepository.findById(testUserId) } returns Optional.of(testUser)
            every { groupRepository.findById(testGroupId) } returns Optional.of(testGroup)
            every { membershipRepository.findMember(testUserId, testGroupId) } returns Optional.empty()

            val exception = assertThrows<ForbiddenException> { predictionService.getUserMatchPredictionsForGroup(testUserId, testGroupId) }
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

            val result = predictionService.submitSingleMatchPrediction(testUserId, testGroupId, request)

            assertEquals(testUserId, result.user.id)
            assertEquals(testMatchId, result.match.id)
            verify { matchPredictionRepository.upsert(any()) }
        }

        @Test
        fun `submitSingleMatchPrediction should succeed for a user with the unique-predictions flag set as true`() {
            val user = testUser.copy(hasUniquePredictions = true)
            mockMembership(user = user)
            every { matchRepository.findById(testMatchId) } returns Optional.of(testMatchOpen)

            val userGroups = listOf(
                GroupUser(user = user, group = testGroup),
                GroupUser(user = user, group = testGroup.copy(id = UUID.randomUUID(), name = "Another Group")),
            )
            every { membershipRepository.findUserGroups(testUserId) } returns userGroups
            every { matchPredictionRepository.upsertAll(any()) } answers { firstArg() }

            val result = predictionService.submitSingleMatchPrediction(testUserId, testGroupId, request)

            assertEquals(testUserId, result.user.id)
            assertEquals(testMatchId, result.match.id)

            val slot = slot<List<MatchPrediction>>()
            verify(exactly = 1) { matchPredictionRepository.upsertAll(capture(slot)) }
            val savedPredictions = slot.captured
            assertEquals(2, savedPredictions.size)
            assertEquals(testMatchId, savedPredictions[0].match.id)
            assertEquals(testUserId, savedPredictions[0].user.id)
            assertEquals(userGroups[0].group.id, savedPredictions[0].group.id)
            assertEquals(testMatchId, savedPredictions[1].match.id)
            assertEquals(testUserId, savedPredictions[1].user.id)
            assertEquals(userGroups[1].group.id, savedPredictions[1].group.id)

        }

        @Test
        fun `submitSingleMatchPrediction should throw NotFoundException when match not found`() {
            every { userRepository.findById(testUserId) } returns Optional.of(testUser)
            every { groupRepository.findById(testGroupId) } returns Optional.of(testGroup)
            every { membershipRepository.findMember(testUserId, testGroupId) } returns Optional.of(
                GroupUser(user = testUser, group = testGroup)
            )
            every { matchRepository.findById(testMatchId) } returns Optional.empty()

            assertThrows<NotFoundException> {
                predictionService.submitSingleMatchPrediction(testUserId, testGroupId, request)
            }
            verify(exactly = 0) { matchPredictionRepository.upsert(any()) }
        }

        @Test
        fun `submitSingleMatchPrediction should throw BadRequestException when match is locked`() {
            every { userRepository.findById(testUserId) } returns Optional.of(testUser)
            every { groupRepository.findById(testGroupId) } returns Optional.of(testGroup)
            every { membershipRepository.findMember(testUserId, testGroupId) } returns Optional.of(
                GroupUser(user = testUser, group = testGroup)
            )
            every { matchRepository.findById(testMatchId) } returns Optional.of(testMatchLocked)

            val exception = assertThrows<BadRequestException> {
                predictionService.submitSingleMatchPrediction(testUserId, testGroupId, request)
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
            every { matchRepository.findAllById(listOf(testMatchId, matchId2)) } returns listOf(testMatchOpen, openMatch2)
            every { matchPredictionRepository.upsertAll(any()) } returns savedPredictions

            val result = predictionService.submitMatchPredictions(testUserId, testGroupId, request)

            assertEquals(testGroupId, result.group.id)
            assertEquals(2, result.predictions.size)
            verify { matchPredictionRepository.upsertAll(match { it.size == 2 }) }
        }

        @Test
        fun `submitMatchPredictions should submit all open matches for a user with the unique-predictions flag set as trues`() {
            val request = SubmitBulkMatchPredictionsRequest(
                predictions = listOf(
                    SubmitMatchPredictionRequest(matchId = testMatchId, homeGoals = 1, awayGoals = 0),
                    SubmitMatchPredictionRequest(matchId = matchId2, homeGoals = 2, awayGoals = 2)
                )
            )

            val user = testUser.copy(hasUniquePredictions = true)
            mockMembership(user = user)
            every { matchRepository.findAllById(listOf(testMatchId, matchId2)) } returns listOf(testMatchOpen, openMatch2)

            val userGroups = listOf(
                GroupUser(user = user, group = testGroup),
                GroupUser(user = user, group = testGroup.copy(id = UUID.randomUUID(), name = "Another Group")),
            )
            every { membershipRepository.findUserGroups(testUserId) } returns userGroups
            every { matchPredictionRepository.upsertAll(any()) } answers { firstArg() }

            val result = predictionService.submitMatchPredictions(testUserId, testGroupId, request)

            assertEquals(testGroupId, result.group.id)
            assertEquals(2, result.predictions.size)

            val slot = slot<List<MatchPrediction>>()
            verify(exactly = 1) { matchPredictionRepository.upsertAll(capture(slot)) }
            val savedPredictions = slot.captured
            assertEquals(4, savedPredictions.size)
            assertEquals(testMatchId, savedPredictions[0].match.id)
            assertEquals(testUserId, savedPredictions[0].user.id)
            assertEquals(userGroups[0].group.id, savedPredictions[0].group.id)
            assertEquals(matchId2, savedPredictions[1].match.id)
            assertEquals(testUserId, savedPredictions[1].user.id)
            assertEquals(userGroups[0].group.id, savedPredictions[1].group.id)
            assertEquals(testMatchId, savedPredictions[2].match.id)
            assertEquals(testUserId, savedPredictions[2].user.id)
            assertEquals(userGroups[1].group.id, savedPredictions[2].group.id)
            assertEquals(matchId2, savedPredictions[3].match.id)
            assertEquals(testUserId, savedPredictions[3].user.id)
            assertEquals(userGroups[1].group.id, savedPredictions[3].group.id)
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
            every { matchRepository.findAllById(listOf(testMatchId, matchId2)) } returns listOf(testMatchOpen, lockedMatch2)
            every { matchPredictionRepository.upsertAll(any()) } returns listOf(testPrediction)

            val result = predictionService.submitMatchPredictions(testUserId, testGroupId, request)

            assertEquals(1, result.predictions.size)
            verify { matchPredictionRepository.upsertAll(match { it.size == 1 }) }
        }

        @Test
        fun `submitMatchPredictions should return empty when all matches are locked`() {
            val request = SubmitBulkMatchPredictionsRequest(
                predictions = listOf(SubmitMatchPredictionRequest(matchId = testMatchId, homeGoals = 1, awayGoals = 0))
            )

            mockMembership()
            every { matchRepository.findAllById(listOf(testMatchId)) } returns listOf(testMatchLocked)
            every { matchPredictionRepository.upsertAll(emptyList()) } returns emptyList()

            val result = predictionService.submitMatchPredictions(testUserId, testGroupId, request)

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

            val result = predictionService.getUserMatchPredictionsForGroup(testUserId, testGroupId)
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

            val result = predictionService.getMatchPredictionsForGroup(testUserId, testGroupId)
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

            val result = predictionService.getSingleMatchPredictionsForGroup(testUserId, testGroupId, testMatchId)
            assertEquals(testGroupId, result.group.id)
            assertEquals(1, result.predictions.size)
        }

        @Test
        fun `getSingleMatchPredictionsForGroup should return predictions for a non-started locked match`() {
            val nonStartedLockedMatch = testMatchLocked.copy(
                status = MatchStatus.NOT_STARTED,
                startedAt = now!!.atZone(ZoneId.systemDefault()).plusMinutes(15),
            )
            val predictionView = MatchPredictionView(
                id = UUID.randomUUID(), user = testUser, rank = 1, match = nonStartedLockedMatch, prediction = testPrediction
            )

            mockMembership()
            every { matchRepository.findById(testMatchId) } returns Optional.of(nonStartedLockedMatch)
            every { matchPredictionRepository.findGroupPredictionsForMatch(testGroupId, testMatchId) } returns listOf(predictionView)

            val result = predictionService.getSingleMatchPredictionsForGroup(testUserId, testGroupId, testMatchId)
            assertEquals(testGroupId, result.group.id)
            assertEquals(1, result.predictions.size)
        }

        @Test
        fun `getSingleMatchPredictionsForGroup should throw BadRequestException when match is still open`() {
            mockMembership()
            every { matchRepository.findById(testMatchId) } returns Optional.of(testMatchOpen)

            val exception = assertThrows<BadRequestException> {
                predictionService.getSingleMatchPredictionsForGroup(testUserId, testGroupId, testMatchId)
            }
            assertEquals("Match is still open", exception.message)
        }

        @Test
        fun `getSingleMatchPredictionsForGroup should throw NotFoundException when match not found`() {
            mockMembership()
            every { matchRepository.findById(testMatchId) } returns Optional.empty()

            assertThrows<NotFoundException> { predictionService.getSingleMatchPredictionsForGroup(testUserId, testGroupId, testMatchId) }
        }
    }

    @Nested
    inner class SubmitAwardsPredictionsTests {

        @Test
        fun `submitAwardPredictions should submit awards for a tournament (when they're all provided)`() {
            val request = SubmitAwardPredictionRequest(
                champions = listOf(UUID.randomUUID(), UUID.randomUUID()),
                topScorers = listOf(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()),
                bestPlayers = listOf(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()),
                bestGoalkeepers = listOf(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()),
                bestYoungPlayers = listOf(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()),
            )

            mockMembership()
            every { tournamentRepository.findById(testTournamentId) } returns Optional.of(testTournament)
            every { awardPredictionRepository.deleteAwardPredictionsForGroup(testUserId, testGroupId) } returns 0
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
            every { playerRepository.findById(request.bestGoalkeepers[0]) } returns
                    Optional.of(testPlayer.copy(id = request.bestGoalkeepers[0], name = "Player 7", position = PlayerPosition.GOALKEEPER))
            every { playerRepository.findById(request.bestGoalkeepers[1]) } returns
                    Optional.of(testPlayer.copy(id = request.bestGoalkeepers[1], name = "Player 8", position = PlayerPosition.GOALKEEPER))
            every { playerRepository.findById(request.bestGoalkeepers[2]) } returns
                    Optional.of(testPlayer.copy(id = request.bestGoalkeepers[2], name = "Player 9", position = PlayerPosition.GOALKEEPER))
            every { playerRepository.findById(request.bestYoungPlayers[0]) } returns
                    Optional.of(testPlayer.copy(id = request.bestYoungPlayers[0], name = "Player 10", birthdate = LocalDate.now()))
            every { playerRepository.findById(request.bestYoungPlayers[1]) } returns
                    Optional.of(testPlayer.copy(id = request.bestYoungPlayers[1], name = "Player 11", birthdate = LocalDate.now()))
            every { playerRepository.findById(request.bestYoungPlayers[2]) } returns
                    Optional.of(testPlayer.copy(id = request.bestYoungPlayers[2], name = "Player 12", birthdate = LocalDate.now()))

            val result = predictionService.submitAwardPredictions(testUserId, testGroupId, testTournamentId, request)
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
        fun `submitAwardPredictions should submit awards for a tournament (when some are missing)`() {
            val request = SubmitAwardPredictionRequest(
                champions = listOf(UUID.randomUUID()),
                bestPlayers = listOf(UUID.randomUUID(), UUID.randomUUID()),
                topScorers = listOf(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()),
                bestGoalkeepers = emptyList(),
                bestYoungPlayers = emptyList(),
            )

            mockMembership()
            every { tournamentRepository.findById(testTournamentId) } returns Optional.of(testTournament)
            every { awardPredictionRepository.deleteAwardPredictionsForGroup(testUserId, testGroupId) } returns 0
            every { awardPredictionRepository.saveAll(any<List<AwardPrediction>>()) } answers { firstArg() }

            every { teamRepository.getReferenceById(request.champions[0]) } returns
                    testTeam.copy(id = request.champions[0], name = "Team 1")
            every { playerRepository.getReferenceById(request.bestPlayers[0]) } returns
                    testPlayer.copy(id = request.bestPlayers[0], name = "Player 1")
            every { playerRepository.getReferenceById(request.bestPlayers[1]) } returns
                    testPlayer.copy(id = request.bestPlayers[1], name = "Player 2")
            every { playerRepository.getReferenceById(request.topScorers[0]) } returns
                    testPlayer.copy(id = request.topScorers[0], name = "Player 3")
            every { playerRepository.getReferenceById(request.topScorers[1]) } returns
                    testPlayer.copy(id = request.topScorers[1], name = "Player 4")
            every { playerRepository.getReferenceById(request.topScorers[2]) } returns
                    testPlayer.copy(id = request.topScorers[2], name = "Player 5")

            val result = predictionService.submitAwardPredictions(testUserId, testGroupId, testTournamentId, request)
            assertEquals(request.champions[0], result.champions[0].id)
            assertEquals("Team 1", result.champions[0].name)
            assertEquals(request.bestPlayers[0], result.bestPlayers[0].id)
            assertEquals("Player 1", result.bestPlayers[0].name)
            assertEquals(request.bestPlayers[1], result.bestPlayers[1].id)
            assertEquals("Player 2", result.bestPlayers[1].name)
            assertEquals(request.topScorers[0], result.topScorers[0].id)
            assertEquals("Player 3", result.topScorers[0].name)
            assertEquals(request.topScorers[1], result.topScorers[1].id)
            assertEquals("Player 4", result.topScorers[1].name)
            assertEquals(request.topScorers[2], result.topScorers[2].id)
            assertEquals("Player 5", result.topScorers[2].name)

            val slot = slot<List<AwardPrediction>>()
            verify(exactly = 1) { awardPredictionRepository.saveAll(capture(slot)) }
            val savedPredictions = slot.captured
            assertEquals(6, savedPredictions.size)
            assertEquals(AwardType.CHAMPION, savedPredictions[0].awardType)
            assertEquals(request.champions[0], savedPredictions[0].team!!.id!!)
            assertEquals(AwardType.TOP_SCORER, savedPredictions[1].awardType)
            assertEquals(request.topScorers[0], savedPredictions[1].player!!.id!!)
            assertEquals(AwardType.TOP_SCORER, savedPredictions[2].awardType)
            assertEquals(request.topScorers[1], savedPredictions[2].player!!.id!!)
            assertEquals(AwardType.TOP_SCORER, savedPredictions[3].awardType)
            assertEquals(request.topScorers[2], savedPredictions[3].player!!.id!!)
            assertEquals(AwardType.BEST_PLAYER, savedPredictions[4].awardType)
            assertEquals(request.bestPlayers[0], savedPredictions[4].player!!.id!!)
            assertEquals(AwardType.BEST_PLAYER, savedPredictions[5].awardType)
            assertEquals(request.bestPlayers[1], savedPredictions[5].player!!.id!!)
        }

        @Test
        fun `submitAwardPredictions should submit awards for multiple groups (when unique-predictions flag is true)`() {
            val request = SubmitAwardPredictionRequest(
                champions = listOf(UUID.randomUUID()),
                topScorers = listOf(UUID.randomUUID()),
                bestPlayers = listOf(UUID.randomUUID()),
                bestGoalkeepers = emptyList(),
                bestYoungPlayers = emptyList(),
            )

            val user = testUser.copy(hasUniquePredictions = true)
            mockMembership(user = user)
            every { tournamentRepository.findById(testTournamentId) } returns Optional.of(testTournament)

            val userGroups = listOf(
                GroupUser(user = user, group = testGroup),
                GroupUser(user = user, group = testGroup.copy(id = UUID.randomUUID(), name = "Another Group")),
            )
            every { membershipRepository.findUserGroups(testUserId) } returns userGroups
            every { awardPredictionRepository.deleteAwardPredictionsForMultipleGroups(testUserId, userGroups.map { it.group.id!! }) } returns 0
            every { awardPredictionRepository.saveAll(any<List<AwardPrediction>>()) } answers { firstArg() }

            every { teamRepository.getReferenceById(request.champions[0]) } returns
                    testTeam.copy(id = request.champions[0], name = "Team 1")
            every { playerRepository.getReferenceById(request.topScorers[0]) } returns
                    testPlayer.copy(id = request.topScorers[0], name = "Player 1")
            every { playerRepository.getReferenceById(request.bestPlayers[0]) } returns
                    testPlayer.copy(id = request.bestPlayers[0], name = "Player 2")

            val result = predictionService.submitAwardPredictions(testUserId, testGroupId, testTournamentId, request)
            assertEquals(request.champions[0], result.champions[0].id)
            assertEquals("Team 1", result.champions[0].name)
            assertEquals(request.topScorers[0], result.topScorers[0].id)
            assertEquals("Player 1", result.topScorers[0].name)
            assertEquals(request.bestPlayers[0], result.bestPlayers[0].id)
            assertEquals("Player 2", result.bestPlayers[0].name)

            val slot = slot<List<AwardPrediction>>()
            verify(exactly = 1) { awardPredictionRepository.saveAll(capture(slot)) }
            val savedPredictions = slot.captured
            assertEquals(6, savedPredictions.size)
            assertEquals(userGroups[0].group.id, savedPredictions[0].group.id)
            assertEquals(AwardType.CHAMPION, savedPredictions[0].awardType)
            assertEquals(request.champions[0], savedPredictions[0].team!!.id!!)
            assertEquals(userGroups[0].group.id, savedPredictions[1].group.id)
            assertEquals(AwardType.TOP_SCORER, savedPredictions[1].awardType)
            assertEquals(request.topScorers[0], savedPredictions[1].player!!.id!!)
            assertEquals(userGroups[0].group.id, savedPredictions[2].group.id)
            assertEquals(AwardType.BEST_PLAYER, savedPredictions[2].awardType)
            assertEquals(request.bestPlayers[0], savedPredictions[2].player!!.id!!)
            assertEquals(userGroups[1].group.id, savedPredictions[3].group.id)
            assertEquals(AwardType.CHAMPION, savedPredictions[3].awardType)
            assertEquals(request.champions[0], savedPredictions[3].team!!.id!!)
            assertEquals(userGroups[1].group.id, savedPredictions[4].group.id)
            assertEquals(AwardType.TOP_SCORER, savedPredictions[4].awardType)
            assertEquals(request.topScorers[0], savedPredictions[4].player!!.id!!)
            assertEquals(userGroups[1].group.id, savedPredictions[5].group.id)
            assertEquals(AwardType.BEST_PLAYER, savedPredictions[5].awardType)
            assertEquals(request.bestPlayers[0], savedPredictions[5].player!!.id!!)
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
                predictionService.submitAwardPredictions(testUserId, testGroupId, testTournamentId, request)
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
                predictionService.submitAwardPredictions(testUserId, testGroupId, testTournamentId, request)
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
                predictionService.submitAwardPredictions(testUserId, testGroupId, testTournamentId, request)
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
                predictionService.submitAwardPredictions(testUserId, testGroupId, testTournamentId, request)
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
                predictionService.submitAwardPredictions(testUserId, testGroupId, testTournamentId, request)
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
                predictionService.submitAwardPredictions(testUserId, testGroupId, testTournamentId, request)
            }
            assertEquals("Invalid amount of awards", exception.message)
        }

        @Test
        fun `submitAwardPredictions should not allow a non-goalkeeper as the best goalkeeper`() {
            val request = SubmitAwardPredictionRequest(
                champions = emptyList(),
                topScorers = emptyList(),
                bestPlayers = emptyList(),
                bestGoalkeepers = listOf(UUID.randomUUID()),
                bestYoungPlayers = emptyList(),
            )

            mockMembership()
            every { tournamentRepository.findById(testTournamentId) } returns Optional.of(testTournament)
            every { playerRepository.findById(request.bestGoalkeepers[0]) } returns
                    Optional.of(testPlayer.copy(position = PlayerPosition.MIDFIELDER))

            val exception = assertThrows<BadRequestException> {
                predictionService.submitAwardPredictions(testUserId, testGroupId, testTournamentId, request)
            }
            assertEquals("Player is not suitable for the best goalkeeper award", exception.message)
        }

        @Test
        fun `submitAwardPredictions should not allow an over-aged player as the best young player`() {
            val request = SubmitAwardPredictionRequest(
                champions = emptyList(),
                topScorers = emptyList(),
                bestPlayers = emptyList(),
                bestGoalkeepers = emptyList(),
                bestYoungPlayers = listOf(UUID.randomUUID()),
            )

            mockMembership()
            every { tournamentRepository.findById(testTournamentId) } returns Optional.of(testTournament)
            every { playerRepository.findById(request.bestYoungPlayers[0]) } returns
                    Optional.of(testPlayer.copy(birthdate = LocalDate.parse("1987-06-24")))

            val exception = assertThrows<BadRequestException> {
                predictionService.submitAwardPredictions(testUserId, testGroupId, testTournamentId, request)
            }
            assertEquals("Player is not suitable for the best young player award", exception.message)
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

            val result = predictionService.getUserAwardPredictionsForGroup(testUserId, testGroupId)
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
                predictionService.getAwardPredictionsForGroup(testUserId, testGroupId, testTournamentId)
            }
            assertEquals("Tournament hasn't started yet", exception.message)
        }

        @Test
        fun `getAwardPredictionsForGroup should return awards predictions for member`() {
            val testUser2 = testUser.copy(id = UUID.randomUUID())
            val predictions = listOf(
                AwardPredictionView(
                    id = UUID.randomUUID(), user = testUser,
                    awardPrediction = AwardPrediction(user = testUser, group = testGroup, awardType = AwardType.CHAMPION, team = testTeam)
                ),
                AwardPredictionView(
                    id = UUID.randomUUID(), user = testUser2,
                    awardPrediction = AwardPrediction(user = testUser2, group = testGroup, awardType = AwardType.BEST_PLAYER, player = testPlayer)
                ),
            )

            mockMembership()
            every { tournamentRepository.findById(testTournamentId) } returns Optional.of(testTournament.copy(status = TournamentStatus.IN_PROGRESS))
            every { awardPredictionRepository.findGroupAwardPredictions(testGroupId) } returns predictions

            val result = predictionService.getAwardPredictionsForGroup(testUserId, testGroupId, testTournamentId)
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

    @Nested
    inner class RecalculateTournamentPointsTests {

        private val testUser1 = testUser.copy(id = UUID.randomUUID(), username = "Test User 1")
        private val testUser2 = testUser.copy(id = UUID.randomUUID(), username = "Test User 2")
        private val testUser3 = testUser.copy(id = UUID.randomUUID(), username = "Test User 3")
        private val testGroup1 = testGroup.copy(
            id = UUID.randomUUID(), name = "Test Group 1",
            tournament = testTournament.copy(status = TournamentStatus.FINISHED)
        )
        private val testGroup2 = testGroup.copy(
            id = UUID.randomUUID(), name = "Test Group 2",
            tournament = testTournament.copy(status = TournamentStatus.FINISHED)
        )
        private val testGroup3 = testGroup.copy(
            id = UUID.randomUUID(), name = "Test Group 3",
            tournament = testTournament.copy(status = TournamentStatus.FINISHED)
        )

        private val winners = Awards(
            champion = UUID.randomUUID(), topScorer = UUID.randomUUID(), bestPlayer = UUID.randomUUID(),
            bestGoalkeeper = UUID.randomUUID(), bestYoungPlayer = UUID.randomUUID()
        )

        private fun awardPrediction(user: User, group: Group, awardType: AwardType, awardId: UUID) = AwardPrediction(
            group = group.copy(tournament = testTournament.copy(status = TournamentStatus.FINISHED, awards = winners)),
            user = user, awardType = awardType, team = testTeam.copy(id = awardId).takeIf { awardType == AwardType.CHAMPION },
            player = testPlayer.copy(id = awardId).takeIf { awardType != AwardType.CHAMPION }
        )

        @Test
        fun `recalculateTournamentPoints should properly recalculate the match-predictions points for every group and user`() {
            mockkObject(PredictionsEngine)

            val members = listOf(
                GroupUser(
                    user = testUser1, group = testGroup1, points = 150F, rank = 1, amountCorrect = 10, amountPartial = 10,
                    amountBonus = 10, lastPredictions = listOf(PredictionStatus.BONUS, PredictionStatus.BONUS, PredictionStatus.BONUS)
                ),
                GroupUser(
                    user = testUser2, group = testGroup1, points = 100F, rank = 2, amountCorrect = 5, amountPartial = 5,
                    amountBonus = 5, lastPredictions = listOf(PredictionStatus.CORRECT, PredictionStatus.CORRECT, PredictionStatus.CORRECT)
                ),
                GroupUser(
                    user = testUser3, group = testGroup1, points = 50F, rank = 3, amountCorrect = 1, amountPartial = 1,
                    amountBonus = 1, lastPredictions = listOf(PredictionStatus.PARTIAL, PredictionStatus.PARTIAL, PredictionStatus.PARTIAL)
                ),
                GroupUser(
                    user = testUser1, group = testGroup2, points = 1500F, rank = 2, amountCorrect = 100, amountPartial = 100,
                    amountBonus = 100, lastPredictions = listOf(PredictionStatus.INCORRECT, PredictionStatus.INCORRECT, PredictionStatus.INCORRECT)
                ),
                GroupUser(
                    user = testUser2, group = testGroup2, points = 1000F, rank = 1, amountCorrect = 50, amountPartial = 50,
                    amountBonus = 50, lastPredictions = listOf(PredictionStatus.INCORRECT, PredictionStatus.INCORRECT, PredictionStatus.INCORRECT)
                ),
                GroupUser(
                    user = testUser1, group = testGroup3, points = 15000F, rank = 2, amountCorrect = 0, amountPartial = 0,
                    amountBonus = 0, lastPredictions = listOf(PredictionStatus.BONUS, PredictionStatus.INCORRECT)
                ),
                GroupUser(
                    user = testUser3, group = testGroup3, points = 10000F, rank = 1, amountCorrect = 14, amountPartial = 15,
                    amountBonus = 16, lastPredictions = listOf(PredictionStatus.PARTIAL)
                ),
            )

            val testMatch1 = Match(
                id = UUID.randomUUID(), code = "X1", tournament = testTournament, homeTeam = testTeam, awayTeam = testTeam,
                homeGoals = 0, awayGoals = 0, stage = MatchStage.GROUP_STAGE, startedAt = ZonedDateTime.now().minusDays(7)
            )
            val testMatch2 = Match(
                id = UUID.randomUUID(), code = "X2", tournament = testTournament, homeTeam = testTeam, awayTeam = testTeam,
                homeGoals = 1, awayGoals = 0, stage = MatchStage.GROUP_STAGE, startedAt = ZonedDateTime.now().minusDays(6)
            )
            val testMatch3 = Match(
                id = UUID.randomUUID(), code = "X3", tournament = testTournament, homeTeam = testTeam, awayTeam = testTeam,
                homeGoals = 0, awayGoals = 1, stage = MatchStage.GROUP_STAGE, startedAt = ZonedDateTime.now().minusDays(5)
            )

            val matchPredictions = listOf(
                MatchPrediction(
                    user = testUser1, group = testGroup1, match = testMatch1,
                    homeGoals = 0, awayGoals = 0, status = PredictionStatus.BONUS
                ),
                MatchPrediction(
                    user = testUser1, group = testGroup1, match = testMatch2,
                    homeGoals = 1, awayGoals = 0, status = PredictionStatus.BONUS
                ),
                MatchPrediction(
                    user = testUser1, group = testGroup1, match = testMatch3,
                    homeGoals = 0, awayGoals = 1, status = PredictionStatus.BONUS
                ),
                MatchPrediction(
                    user = testUser2, group = testGroup1, match = testMatch1,
                    homeGoals = 0, awayGoals = 0, status = PredictionStatus.BONUS
                ),
                MatchPrediction(
                    user = testUser2, group = testGroup1, match = testMatch2,
                    homeGoals = 0, awayGoals = 0, status = PredictionStatus.BONUS
                ),
                MatchPrediction(
                    user = testUser3, group = testGroup1, match = testMatch2,
                    homeGoals = 0, awayGoals = 0, status = PredictionStatus.BONUS
                ),
                MatchPrediction(
                    user = testUser3, group = testGroup1, match = testMatch3,
                    homeGoals = 0, awayGoals = 2, status = PredictionStatus.BONUS
                ),
                MatchPrediction(
                    user = testUser1, group = testGroup2, match = testMatch1,
                    homeGoals = 0, awayGoals = 0, status = PredictionStatus.BONUS
                ),
                MatchPrediction(
                    user = testUser1, group = testGroup2, match = testMatch2,
                    homeGoals = 0, awayGoals = 0, status = PredictionStatus.BONUS
                ),
                MatchPrediction(
                    user = testUser2, group = testGroup2, match = testMatch1,
                    homeGoals = 1, awayGoals = 1, status = PredictionStatus.BONUS
                ),
                MatchPrediction(
                    user = testUser2, group = testGroup2, match = testMatch2,
                    homeGoals = 1, awayGoals = 1, status = PredictionStatus.BONUS
                ),
                MatchPrediction(
                    user = testUser1, group = testGroup3, match = testMatch1,
                    homeGoals = 0, awayGoals = 0, status = PredictionStatus.BONUS
                ),
                MatchPrediction(
                    user = testUser1, group = testGroup3, match = testMatch2,
                    homeGoals = 0, awayGoals = 0, status = PredictionStatus.BONUS
                ),
                MatchPrediction(
                    user = testUser3, group = testGroup3, match = testMatch1,
                    homeGoals = 1, awayGoals = 1, status = PredictionStatus.BONUS
                ),
                MatchPrediction(
                    user = testUser3, group = testGroup3, match = testMatch2,
                    homeGoals = 1, awayGoals = 1, status = PredictionStatus.BONUS
                ),
            )

            every { matchRepository.findByTournamentId(testTournamentId) } returns listOf(testMatch1, testMatch2, testMatch3)
            every { membershipRepository.findByTournamentId(testTournamentId) } returns members
            every { matchPredictionRepository.findByTournamentId(testTournamentId) } returns matchPredictions
            every { awardPredictionRepository.findByTournamentId(testTournamentId) } returns emptyList()

            every { membershipRepository.saveAll(any<List<GroupUser>>()) } answers { firstArg() }
            every { matchPredictionRepository.saveAll(any<List<MatchPrediction>>()) } answers { firstArg() }
            every { awardPredictionRepository.saveAll(emptyList<AwardPrediction>()) } answers { firstArg() }

            predictionService.recalculateTournamentPoints(testTournamentId)

            val slot1 = slot<List<GroupUser>>()
            verify(exactly = 1) { membershipRepository.saveAll(capture(slot1)) }
            val membersSaved = slot1.captured
            assertEquals(7, membersSaved.size)
            assertTrue {
                membersSaved.any {
                    it.user == testUser1 && it.group == testGroup1 &&
                            it.points == 9F && it.rank == 1 && it.amountBonus == 0 && it.amountCorrect == 3 && it.amountPartial == 0 &&
                            it.lastPredictions == listOf(PredictionStatus.CORRECT, PredictionStatus.CORRECT, PredictionStatus.CORRECT)
                }
            }
            assertTrue {
                membersSaved.any {
                    it.user == testUser2 && it.group == testGroup1 &&
                            it.points == 3F && it.rank == 2 && it.amountBonus == 0 && it.amountCorrect == 1 && it.amountPartial == 0 &&
                            it.lastPredictions == listOf(PredictionStatus.CORRECT, PredictionStatus.INCORRECT, PredictionStatus.MISSING)
                }
            }
            assertTrue {
                membersSaved.any {
                    it.user == testUser3 && it.group == testGroup1 &&
                            it.points == 1F && it.rank == 3 && it.amountBonus == 0 && it.amountCorrect == 0 && it.amountPartial == 1 &&
                            it.lastPredictions == listOf(PredictionStatus.MISSING, PredictionStatus.INCORRECT, PredictionStatus.PARTIAL)
                }
            }
            assertTrue {
                membersSaved.any {
                    it.user == testUser1 && it.group == testGroup2 &&
                            it.points == 3F && it.rank == 1 && it.amountBonus == 0 && it.amountCorrect == 1 && it.amountPartial == 0 &&
                            it.lastPredictions == listOf(PredictionStatus.CORRECT, PredictionStatus.INCORRECT, PredictionStatus.MISSING)
                }
            }
            assertTrue {
                membersSaved.any {
                    it.user == testUser2 && it.group == testGroup2 &&
                            it.points == 1F && it.rank == 2 && it.amountBonus == 0 && it.amountCorrect == 0 && it.amountPartial == 1 &&
                            it.lastPredictions == listOf(PredictionStatus.PARTIAL, PredictionStatus.INCORRECT, PredictionStatus.MISSING)
                }
            }
            assertTrue {
                membersSaved.any {
                    it.user == testUser1 && it.group == testGroup3 &&
                            it.points == 3F && it.rank == 1 && it.amountBonus == 0 && it.amountCorrect == 1 && it.amountPartial == 0 &&
                            it.lastPredictions == listOf(PredictionStatus.CORRECT, PredictionStatus.INCORRECT, PredictionStatus.MISSING)
                }
            }
            assertTrue {
                membersSaved.any {
                    it.user == testUser3 && it.group == testGroup3 &&
                            it.points == 1F && it.rank == 2 && it.amountBonus == 0 && it.amountCorrect == 0 && it.amountPartial == 1 &&
                            it.lastPredictions == listOf(PredictionStatus.PARTIAL, PredictionStatus.INCORRECT, PredictionStatus.MISSING)
                }
            }

            val slot2 = slot<List<MatchPrediction>>()
            verify(exactly = 1) { matchPredictionRepository.saveAll(capture(slot2)) }
            val matchPredictionsSaved = slot2.captured
            assertEquals(15, matchPredictionsSaved.size)
            assertTrue {
                matchPredictionsSaved.any {
                    it.user.id == testUser1.id && it.group.id == testGroup1.id
                            && it.match.id == testMatch1.id && it.status == PredictionStatus.CORRECT
                }
            }
            assertTrue {
                matchPredictionsSaved.any {
                    it.user.id == testUser1.id && it.group.id == testGroup1.id
                            && it.match.id == testMatch2.id && it.status == PredictionStatus.CORRECT
                }
            }
            assertTrue {
                matchPredictionsSaved.any {
                    it.user.id == testUser1.id && it.group.id == testGroup1.id
                            && it.match.id == testMatch3.id && it.status == PredictionStatus.CORRECT
                }
            }
            assertTrue {
                matchPredictionsSaved.any {
                    it.user.id == testUser2.id && it.group.id == testGroup1.id
                            && it.match.id == testMatch1.id && it.status == PredictionStatus.CORRECT
                }
            }
            assertTrue {
                matchPredictionsSaved.any {
                    it.user.id == testUser2.id && it.group.id == testGroup1.id
                            && it.match.id == testMatch2.id && it.status == PredictionStatus.INCORRECT
                }
            }
            assertTrue {
                matchPredictionsSaved.any {
                    it.user.id == testUser3.id && it.group.id == testGroup1.id
                            && it.match.id == testMatch2.id && it.status == PredictionStatus.INCORRECT
                }
            }
            assertTrue {
                matchPredictionsSaved.any {
                    it.user.id == testUser3.id && it.group.id == testGroup1.id
                            && it.match.id == testMatch3.id && it.status == PredictionStatus.PARTIAL
                }
            }
            assertTrue {
                matchPredictionsSaved.any {
                    it.user.id == testUser1.id && it.group.id == testGroup2.id
                            && it.match.id == testMatch1.id && it.status == PredictionStatus.CORRECT
                }
            }
            assertTrue {
                matchPredictionsSaved.any {
                    it.user.id == testUser1.id && it.group.id == testGroup2.id
                            && it.match.id == testMatch2.id && it.status == PredictionStatus.INCORRECT
                }
            }
            assertTrue {
                matchPredictionsSaved.any {
                    it.user.id == testUser2.id && it.group.id == testGroup2.id
                            && it.match.id == testMatch1.id && it.status == PredictionStatus.PARTIAL
                }
            }
            assertTrue {
                matchPredictionsSaved.any {
                    it.user.id == testUser2.id && it.group.id == testGroup2.id
                            && it.match.id == testMatch2.id && it.status == PredictionStatus.INCORRECT
                }
            }
            assertTrue {
                matchPredictionsSaved.any {
                    it.user.id == testUser1.id && it.group.id == testGroup3.id
                            && it.match.id == testMatch1.id && it.status == PredictionStatus.CORRECT
                }
            }
            assertTrue {
                matchPredictionsSaved.any {
                    it.user.id == testUser1.id && it.group.id == testGroup3.id
                            && it.match.id == testMatch2.id && it.status == PredictionStatus.INCORRECT
                }
            }
            assertTrue {
                matchPredictionsSaved.any {
                    it.user.id == testUser3.id && it.group.id == testGroup3.id
                            && it.match.id == testMatch1.id && it.status == PredictionStatus.PARTIAL
                }
            }
            assertTrue {
                matchPredictionsSaved.any {
                    it.user.id == testUser3.id && it.group.id == testGroup3.id && it.match.id == testMatch2.id && it.status == PredictionStatus.INCORRECT
                }
            }
        }

        @Test
        fun `recalculateTournamentPoints should properly recalculate the award-predictions points for every group and user`() {
            mockkObject(PredictionsEngine)

            val members = listOf(
                GroupUser(user = testUser1, group = testGroup1, points = 150F, rank = 1, joinedAt = LocalDateTime.now().minusDays(3)),
                GroupUser(user = testUser2, group = testGroup1, points = 100F, rank = 2, joinedAt = LocalDateTime.now().minusDays(2)),
                GroupUser(user = testUser3, group = testGroup1, points = 500F, rank = 3, joinedAt = LocalDateTime.now().minusDays(1)),
                GroupUser(user = testUser1, group = testGroup2, points = 1500F, rank = 2, joinedAt = LocalDateTime.now().minusDays(3)),
                GroupUser(user = testUser2, group = testGroup2, points = 1000F, rank = 1, joinedAt = LocalDateTime.now().minusDays(2)),
                GroupUser(user = testUser3, group = testGroup2, points = 5000F, rank = 1, joinedAt = LocalDateTime.now().minusDays(1)),
            )

            val awardPredictions = listOf(
                awardPrediction(user = testUser1, group = testGroup1, awardType = AwardType.CHAMPION, awardId = winners.champion),
                awardPrediction(user = testUser1, group = testGroup1, awardType = AwardType.TOP_SCORER, awardId = winners.topScorer),
                awardPrediction(user = testUser1, group = testGroup1, awardType = AwardType.BEST_PLAYER, awardId = winners.bestPlayer),
                awardPrediction(user = testUser1, group = testGroup1, awardType = AwardType.BEST_GOALKEEPER, awardId = winners.bestGoalkeeper),
                awardPrediction(user = testUser1, group = testGroup1, awardType = AwardType.BEST_YOUNG_PLAYER, awardId = winners.bestYoungPlayer),
                awardPrediction(user = testUser2, group = testGroup1, awardType = AwardType.CHAMPION, awardId = winners.champion),
                awardPrediction(user = testUser2, group = testGroup1, awardType = AwardType.CHAMPION, awardId = UUID.randomUUID()),
                awardPrediction(user = testUser2, group = testGroup1, awardType = AwardType.TOP_SCORER, awardId = winners.topScorer),
                awardPrediction(user = testUser2, group = testGroup1, awardType = AwardType.TOP_SCORER, awardId = UUID.randomUUID()),
                awardPrediction(user = testUser2, group = testGroup1, awardType = AwardType.TOP_SCORER, awardId = UUID.randomUUID()),
                awardPrediction(user = testUser2, group = testGroup1, awardType = AwardType.BEST_PLAYER, awardId = winners.bestPlayer),
                awardPrediction(user = testUser2, group = testGroup1, awardType = AwardType.BEST_PLAYER, awardId = UUID.randomUUID()),
                awardPrediction(user = testUser2, group = testGroup1, awardType = AwardType.BEST_PLAYER, awardId = UUID.randomUUID()),
                awardPrediction(user = testUser2, group = testGroup1, awardType = AwardType.BEST_GOALKEEPER, awardId = winners.bestGoalkeeper),
                awardPrediction(user = testUser2, group = testGroup1, awardType = AwardType.BEST_GOALKEEPER, awardId = UUID.randomUUID()),
                awardPrediction(user = testUser2, group = testGroup1, awardType = AwardType.BEST_GOALKEEPER, awardId = UUID.randomUUID()),
                awardPrediction(user = testUser2, group = testGroup1, awardType = AwardType.BEST_YOUNG_PLAYER, awardId = winners.bestYoungPlayer),
                awardPrediction(user = testUser2, group = testGroup1, awardType = AwardType.BEST_YOUNG_PLAYER, awardId = UUID.randomUUID()),
                awardPrediction(user = testUser2, group = testGroup1, awardType = AwardType.BEST_YOUNG_PLAYER, awardId = UUID.randomUUID()),
                awardPrediction(user = testUser3, group = testGroup1, awardType = AwardType.CHAMPION, awardId = UUID.randomUUID()),
                awardPrediction(user = testUser3, group = testGroup1, awardType = AwardType.TOP_SCORER, awardId = UUID.randomUUID()),
                awardPrediction(user = testUser3, group = testGroup1, awardType = AwardType.TOP_SCORER, awardId = UUID.randomUUID()),
                awardPrediction(user = testUser3, group = testGroup1, awardType = AwardType.BEST_PLAYER, awardId = UUID.randomUUID()),
                awardPrediction(user = testUser3, group = testGroup1, awardType = AwardType.BEST_PLAYER, awardId = UUID.randomUUID()),
                awardPrediction(user = testUser3, group = testGroup1, awardType = AwardType.BEST_GOALKEEPER, awardId = UUID.randomUUID()),
                awardPrediction(user = testUser3, group = testGroup1, awardType = AwardType.BEST_GOALKEEPER, awardId = UUID.randomUUID()),
                awardPrediction(user = testUser3, group = testGroup1, awardType = AwardType.BEST_YOUNG_PLAYER, awardId = UUID.randomUUID()),
                awardPrediction(user = testUser3, group = testGroup1, awardType = AwardType.BEST_YOUNG_PLAYER, awardId = UUID.randomUUID()),
                awardPrediction(user = testUser1, group = testGroup2, awardType = AwardType.CHAMPION, awardId = winners.champion),
                awardPrediction(user = testUser1, group = testGroup2, awardType = AwardType.CHAMPION, awardId = UUID.randomUUID()),
                awardPrediction(user = testUser1, group = testGroup2, awardType = AwardType.TOP_SCORER, awardId = winners.topScorer),
                awardPrediction(user = testUser1, group = testGroup2, awardType = AwardType.TOP_SCORER, awardId = UUID.randomUUID()),
                awardPrediction(user = testUser1, group = testGroup2, awardType = AwardType.BEST_PLAYER, awardId = winners.bestPlayer),
                awardPrediction(user = testUser1, group = testGroup2, awardType = AwardType.BEST_PLAYER, awardId = UUID.randomUUID()),
                awardPrediction(user = testUser1, group = testGroup2, awardType = AwardType.BEST_GOALKEEPER, awardId = winners.bestGoalkeeper),
                awardPrediction(user = testUser1, group = testGroup2, awardType = AwardType.BEST_GOALKEEPER, awardId = UUID.randomUUID()),
                awardPrediction(user = testUser1, group = testGroup2, awardType = AwardType.BEST_YOUNG_PLAYER, awardId = winners.bestYoungPlayer),
                awardPrediction(user = testUser1, group = testGroup2, awardType = AwardType.BEST_YOUNG_PLAYER, awardId = UUID.randomUUID()),
                awardPrediction(user = testUser3, group = testGroup2, awardType = AwardType.CHAMPION, awardId = UUID.randomUUID()),
                awardPrediction(user = testUser3, group = testGroup2, awardType = AwardType.TOP_SCORER, awardId = UUID.randomUUID()),
                awardPrediction(user = testUser3, group = testGroup2, awardType = AwardType.BEST_PLAYER, awardId = UUID.randomUUID()),
                awardPrediction(user = testUser3, group = testGroup2, awardType = AwardType.BEST_GOALKEEPER, awardId = UUID.randomUUID()),
                awardPrediction(user = testUser3, group = testGroup2, awardType = AwardType.BEST_YOUNG_PLAYER, awardId = UUID.randomUUID()),
            )

            every { matchRepository.findByTournamentId(testTournamentId) } returns listOf(testMatchLocked)
            every { membershipRepository.findByTournamentId(testTournamentId) } returns members
            every { matchPredictionRepository.findByTournamentId(testTournamentId) } returns emptyList()
            every { awardPredictionRepository.findByTournamentId(testTournamentId) } returns awardPredictions

            every { membershipRepository.saveAll(any<List<GroupUser>>()) } answers { firstArg() }
            every { matchPredictionRepository.saveAll(emptyList<MatchPrediction>()) } answers { firstArg() }
            every { awardPredictionRepository.saveAll(any<List<AwardPrediction>>()) } answers { firstArg() }

            predictionService.recalculateTournamentPoints(testTournamentId)

            val slot1 = slot<List<GroupUser>>()
            verify(exactly = 1) { membershipRepository.saveAll(capture(slot1)) }
            val membersSaved = slot1.captured
            assertEquals(6, membersSaved.size)
            assertTrue { membersSaved.any { it.user == testUser1 && it.group == testGroup1 && it.points == 50f && it.rank == 1 } }
            assertTrue { membersSaved.any { it.user == testUser2 && it.group == testGroup1 && it.points == 21f && it.rank == 2 } }
            assertTrue { membersSaved.any { it.user == testUser3 && it.group == testGroup1 && it.points == 0f && it.rank == 3 } }
            assertTrue { membersSaved.any { it.user == testUser1 && it.group == testGroup2 && it.points == 33f && it.rank == 1 } }
            assertTrue { membersSaved.any { it.user == testUser2 && it.group == testGroup2 && it.points == 0f && it.rank == 2 } }
            assertTrue { membersSaved.any { it.user == testUser3 && it.group == testGroup2 && it.points == 0f && it.rank == 3 } }

            val slot2 = slot<List<AwardPrediction>>()
            verify(exactly = 1) { awardPredictionRepository.saveAll(capture(slot2)) }
            val awardPredictionsSaved = slot2.captured
            assertEquals(43, awardPredictionsSaved.size)
            assertTrue {
                awardPredictionsSaved.any {
                    it.user.id == testUser1.id && it.group.id == testGroup1.id && it.status == PredictionStatus.CORRECT &&
                            it.awardType == AwardType.CHAMPION && it.team!!.id == awardPredictions[0].team!!.id
                }
            }
            assertTrue {
                awardPredictionsSaved.any {
                    it.user.id == testUser1.id && it.group.id == testGroup1.id && it.status == PredictionStatus.CORRECT &&
                            it.awardType == AwardType.TOP_SCORER && it.player!!.id == awardPredictions[1].player!!.id
                }
            }
            assertTrue {
                awardPredictionsSaved.any {
                    it.user.id == testUser1.id && it.group.id == testGroup1.id && it.status == PredictionStatus.CORRECT &&
                            it.awardType == AwardType.BEST_PLAYER && it.player!!.id == awardPredictions[2].player!!.id
                }
            }
            assertTrue {
                awardPredictionsSaved.any {
                    it.user.id == testUser1.id && it.group.id == testGroup1.id && it.status == PredictionStatus.CORRECT &&
                            it.awardType == AwardType.BEST_GOALKEEPER && it.player!!.id == awardPredictions[3].player!!.id
                }
            }
            assertTrue {
                awardPredictionsSaved.any {
                    it.user.id == testUser1.id && it.group.id == testGroup1.id && it.status == PredictionStatus.CORRECT &&
                            it.awardType == AwardType.BEST_YOUNG_PLAYER && it.player!!.id == awardPredictions[4].player!!.id
                }
            }
            assertTrue {
                awardPredictionsSaved.any {
                    it.user == testUser2 && it.group.id == testGroup1.id && it.status == PredictionStatus.CORRECT &&
                            it.awardType == AwardType.CHAMPION && it.team!!.id == awardPredictions[5].team!!.id
                }
            }
            assertTrue {
                awardPredictionsSaved.any {
                    it.user == testUser2 && it.group.id == testGroup1.id && it.status == PredictionStatus.INCORRECT &&
                            it.awardType == AwardType.CHAMPION && it.team!!.id == awardPredictions[6].team!!.id
                }
            }
            assertTrue {
                awardPredictionsSaved.any {
                    it.user == testUser2 && it.group.id == testGroup1.id && it.status == PredictionStatus.CORRECT &&
                            it.awardType == AwardType.TOP_SCORER && it.player!!.id == awardPredictions[7].player!!.id
                }
            }
            assertTrue {
                awardPredictionsSaved.any {
                    it.user == testUser2 && it.group.id == testGroup1.id && it.status == PredictionStatus.INCORRECT &&
                            it.awardType == AwardType.TOP_SCORER && it.player!!.id == awardPredictions[8].player!!.id
                }
            }
            assertTrue {
                awardPredictionsSaved.any {
                    it.user == testUser2 && it.group.id == testGroup1.id && it.status == PredictionStatus.INCORRECT &&
                            it.awardType == AwardType.TOP_SCORER && it.player!!.id == awardPredictions[9].player!!.id
                }
            }
            assertTrue {
                awardPredictionsSaved.any {
                    it.user == testUser2 && it.group.id == testGroup1.id && it.status == PredictionStatus.CORRECT &&
                            it.awardType == AwardType.BEST_PLAYER && it.player!!.id == awardPredictions[10].player!!.id
                }
            }
            assertTrue {
                awardPredictionsSaved.any {
                    it.user == testUser2 && it.group.id == testGroup1.id && it.status == PredictionStatus.INCORRECT &&
                            it.awardType == AwardType.BEST_PLAYER && it.player!!.id == awardPredictions[11].player!!.id
                }
            }
            assertTrue {
                awardPredictionsSaved.any {
                    it.user == testUser2 && it.group.id == testGroup1.id && it.status == PredictionStatus.INCORRECT &&
                            it.awardType == AwardType.BEST_PLAYER && it.player!!.id == awardPredictions[12].player!!.id
                }
            }
            assertTrue {
                awardPredictionsSaved.any {
                    it.user == testUser2 && it.group.id == testGroup1.id && it.status == PredictionStatus.CORRECT &&
                            it.awardType == AwardType.BEST_GOALKEEPER && it.player!!.id == awardPredictions[13].player!!.id
                }
            }
            assertTrue {
                awardPredictionsSaved.any {
                    it.user == testUser2 && it.group.id == testGroup1.id && it.status == PredictionStatus.INCORRECT &&
                            it.awardType == AwardType.BEST_GOALKEEPER && it.player!!.id == awardPredictions[14].player!!.id
                }
            }
            assertTrue {
                awardPredictionsSaved.any {
                    it.user == testUser2 && it.group.id == testGroup1.id && it.status == PredictionStatus.INCORRECT &&
                            it.awardType == AwardType.BEST_GOALKEEPER && it.player!!.id == awardPredictions[15].player!!.id
                }
            }
            assertTrue {
                awardPredictionsSaved.any {
                    it.user == testUser2 && it.group.id == testGroup1.id && it.status == PredictionStatus.CORRECT &&
                            it.awardType == AwardType.BEST_YOUNG_PLAYER && it.player!!.id == awardPredictions[16].player!!.id
                }
            }
            assertTrue {
                awardPredictionsSaved.any {
                    it.user == testUser2 && it.group.id == testGroup1.id && it.status == PredictionStatus.INCORRECT &&
                            it.awardType == AwardType.BEST_YOUNG_PLAYER && it.player!!.id == awardPredictions[17].player!!.id
                }
            }
            assertTrue {
                awardPredictionsSaved.any {
                    it.user == testUser2 && it.group.id == testGroup1.id && it.status == PredictionStatus.INCORRECT &&
                            it.awardType == AwardType.BEST_YOUNG_PLAYER && it.player!!.id == awardPredictions[18].player!!.id
                }
            }
            assertTrue {
                awardPredictionsSaved.any {
                    it.user == testUser3 && it.group.id == testGroup1.id && it.status == PredictionStatus.INCORRECT &&
                            it.awardType == AwardType.CHAMPION && it.team!!.id == awardPredictions[19].team!!.id
                }
            }
            assertTrue {
                awardPredictionsSaved.any {
                    it.user == testUser3 && it.group.id == testGroup1.id && it.status == PredictionStatus.INCORRECT &&
                            it.awardType == AwardType.TOP_SCORER && it.player!!.id == awardPredictions[20].player!!.id
                }
            }
            assertTrue {
                awardPredictionsSaved.any {
                    it.user == testUser3 && it.group.id == testGroup1.id && it.status == PredictionStatus.INCORRECT &&
                            it.awardType == AwardType.TOP_SCORER && it.player!!.id == awardPredictions[21].player!!.id
                }
            }
            assertTrue {
                awardPredictionsSaved.any {
                    it.user == testUser3 && it.group.id == testGroup1.id && it.status == PredictionStatus.INCORRECT &&
                            it.awardType == AwardType.BEST_PLAYER && it.player!!.id == awardPredictions[22].player!!.id
                }
            }
            assertTrue {
                awardPredictionsSaved.any {
                    it.user == testUser3 && it.group.id == testGroup1.id && it.status == PredictionStatus.INCORRECT &&
                            it.awardType == AwardType.BEST_PLAYER && it.player!!.id == awardPredictions[23].player!!.id
                }
            }
            assertTrue {
                awardPredictionsSaved.any {
                    it.user == testUser3 && it.group.id == testGroup1.id && it.status == PredictionStatus.INCORRECT &&
                            it.awardType == AwardType.BEST_GOALKEEPER && it.player!!.id == awardPredictions[24].player!!.id
                }
            }
            assertTrue {
                awardPredictionsSaved.any {
                    it.user == testUser3 && it.group.id == testGroup1.id && it.status == PredictionStatus.INCORRECT &&
                            it.awardType == AwardType.BEST_GOALKEEPER && it.player!!.id == awardPredictions[25].player!!.id
                }
            }
            assertTrue {
                awardPredictionsSaved.any {
                    it.user == testUser3 && it.group.id == testGroup1.id && it.status == PredictionStatus.INCORRECT &&
                            it.awardType == AwardType.BEST_YOUNG_PLAYER && it.player!!.id == awardPredictions[26].player!!.id
                }
            }
            assertTrue {
                awardPredictionsSaved.any {
                    it.user == testUser3 && it.group.id == testGroup1.id && it.status == PredictionStatus.INCORRECT &&
                            it.awardType == AwardType.BEST_YOUNG_PLAYER && it.player!!.id == awardPredictions[27].player!!.id
                }
            }
            assertTrue {
                awardPredictionsSaved.any {
                    it.user.id == testUser1.id && it.group.id == testGroup2.id && it.status == PredictionStatus.CORRECT &&
                            it.awardType == AwardType.CHAMPION && it.team!!.id == awardPredictions[28].team!!.id
                }
            }
            assertTrue {
                awardPredictionsSaved.any {
                    it.user.id == testUser1.id && it.group.id == testGroup2.id && it.status == PredictionStatus.INCORRECT &&
                            it.awardType == AwardType.CHAMPION && it.team!!.id == awardPredictions[29].team!!.id
                }
            }
            assertTrue {
                awardPredictionsSaved.any {
                    it.user.id == testUser1.id && it.group.id == testGroup2.id && it.status == PredictionStatus.CORRECT &&
                            it.awardType == AwardType.TOP_SCORER && it.player!!.id == awardPredictions[30].player!!.id
                }
            }
            assertTrue {
                awardPredictionsSaved.any {
                    it.user.id == testUser1.id && it.group.id == testGroup2.id && it.status == PredictionStatus.INCORRECT &&
                            it.awardType == AwardType.TOP_SCORER && it.player!!.id == awardPredictions[31].player!!.id
                }
            }
            assertTrue {
                awardPredictionsSaved.any {
                    it.user.id == testUser1.id && it.group.id == testGroup2.id && it.status == PredictionStatus.CORRECT &&
                            it.awardType == AwardType.BEST_PLAYER && it.player!!.id == awardPredictions[32].player!!.id
                }
            }
            assertTrue {
                awardPredictionsSaved.any {
                    it.user.id == testUser1.id && it.group.id == testGroup2.id && it.status == PredictionStatus.INCORRECT &&
                            it.awardType == AwardType.BEST_PLAYER && it.player!!.id == awardPredictions[33].player!!.id
                }
            }
            assertTrue {
                awardPredictionsSaved.any {
                    it.user.id == testUser1.id && it.group.id == testGroup2.id && it.status == PredictionStatus.CORRECT &&
                            it.awardType == AwardType.BEST_GOALKEEPER && it.player!!.id == awardPredictions[34].player!!.id
                }
            }
            assertTrue {
                awardPredictionsSaved.any {
                    it.user.id == testUser1.id && it.group.id == testGroup2.id && it.status == PredictionStatus.INCORRECT &&
                            it.awardType == AwardType.BEST_GOALKEEPER && it.player!!.id == awardPredictions[35].player!!.id
                }
            }
            assertTrue {
                awardPredictionsSaved.any {
                    it.user.id == testUser1.id && it.group.id == testGroup2.id && it.status == PredictionStatus.CORRECT &&
                            it.awardType == AwardType.BEST_YOUNG_PLAYER && it.player!!.id == awardPredictions[36].player!!.id
                }
            }
            assertTrue {
                awardPredictionsSaved.any {
                    it.user.id == testUser1.id && it.group.id == testGroup2.id && it.status == PredictionStatus.INCORRECT &&
                            it.awardType == AwardType.BEST_YOUNG_PLAYER && it.player!!.id == awardPredictions[37].player!!.id
                }
            }
            assertTrue {
                awardPredictionsSaved.any {
                    it.user == testUser3 && it.group.id == testGroup2.id && it.status == PredictionStatus.INCORRECT &&
                            it.awardType == AwardType.CHAMPION && it.team!!.id == awardPredictions[38].team!!.id
                }
            }
            assertTrue {
                awardPredictionsSaved.any {
                    it.user == testUser3 && it.group.id == testGroup2.id && it.status == PredictionStatus.INCORRECT &&
                            it.awardType == AwardType.TOP_SCORER && it.player!!.id == awardPredictions[39].player!!.id
                }
            }
            assertTrue {
                awardPredictionsSaved.any {
                    it.user == testUser3 && it.group.id == testGroup2.id && it.status == PredictionStatus.INCORRECT &&
                            it.awardType == AwardType.BEST_PLAYER && it.player!!.id == awardPredictions[40].player!!.id
                }
            }
            assertTrue {
                awardPredictionsSaved.any {
                    it.user == testUser3 && it.group.id == testGroup2.id && it.status == PredictionStatus.INCORRECT &&
                            it.awardType == AwardType.BEST_GOALKEEPER && it.player!!.id == awardPredictions[41].player!!.id
                }
            }
            assertTrue {
                awardPredictionsSaved.any {
                    it.user == testUser3 && it.group.id == testGroup2.id && it.status == PredictionStatus.INCORRECT &&
                            it.awardType == AwardType.BEST_YOUNG_PLAYER && it.player!!.id == awardPredictions[42].player!!.id
                }
            }
        }

        @Test
        fun `recalculateTournamentPoints with matches and awards predictions for a user within a group`() {
            mockkObject(PredictionsEngine)

            val members = listOf(
                GroupUser(
                    user = testUser1, group = testGroup1, points = 150F, rank = 1, amountCorrect = 10, amountPartial = 10,
                    amountBonus = 10, lastPredictions = listOf(PredictionStatus.BONUS, PredictionStatus.BONUS, PredictionStatus.BONUS)
                )
            )

            val testMatch1 = Match(
                id = UUID.randomUUID(), code = "X1", tournament = testTournament, homeTeam = testTeam, awayTeam = testTeam,
                homeGoals = 0, awayGoals = 0, stage = MatchStage.GROUP_STAGE, startedAt = ZonedDateTime.now().minusDays(7)
            )
            val testMatch2 = Match(
                id = UUID.randomUUID(), code = "X2", tournament = testTournament, homeTeam = testTeam, awayTeam = testTeam,
                homeGoals = 1, awayGoals = 0, stage = MatchStage.GROUP_STAGE, startedAt = ZonedDateTime.now().minusDays(6)
            )
            val testMatch3 = Match(
                id = UUID.randomUUID(), code = "X3", tournament = testTournament, homeTeam = testTeam, awayTeam = testTeam,
                homeGoals = 0, awayGoals = 1, stage = MatchStage.GROUP_STAGE, startedAt = ZonedDateTime.now().minusDays(6)
            )

            val matchPredictions = listOf(
                MatchPrediction(
                    user = testUser1, group = testGroup1, match = testMatch1,
                    homeGoals = 0, awayGoals = 0, status = PredictionStatus.BONUS
                ),
                MatchPrediction(
                    user = testUser1, group = testGroup1, match = testMatch2,
                    homeGoals = 0, awayGoals = 0, status = PredictionStatus.BONUS
                ),
            )

            val awardPredictions = listOf(
                awardPrediction(user = testUser1, group = testGroup1, awardType = AwardType.CHAMPION, awardId = winners.champion),
                awardPrediction(user = testUser1, group = testGroup1, awardType = AwardType.TOP_SCORER, awardId = winners.topScorer),
                awardPrediction(user = testUser1, group = testGroup1, awardType = AwardType.BEST_PLAYER, awardId = winners.bestPlayer),
                awardPrediction(user = testUser1, group = testGroup1, awardType = AwardType.BEST_PLAYER, awardId = UUID.randomUUID()),
                awardPrediction(user = testUser1, group = testGroup1, awardType = AwardType.BEST_GOALKEEPER, awardId = winners.bestGoalkeeper),
                awardPrediction(user = testUser1, group = testGroup1, awardType = AwardType.BEST_GOALKEEPER, awardId = UUID.randomUUID()),
                awardPrediction(user = testUser1, group = testGroup1, awardType = AwardType.BEST_GOALKEEPER, awardId = UUID.randomUUID()),
            )

            every { matchRepository.findByTournamentId(testTournamentId) } returns listOf(testMatch1, testMatch2, testMatch3)
            every { membershipRepository.findByTournamentId(testTournamentId) } returns members
            every { matchPredictionRepository.findByTournamentId(testTournamentId) } returns matchPredictions
            every { awardPredictionRepository.findByTournamentId(testTournamentId) } returns awardPredictions

            every { membershipRepository.saveAll(any<List<GroupUser>>()) } answers { firstArg() }
            every { matchPredictionRepository.saveAll(any<List<MatchPrediction>>()) } answers { firstArg() }
            every { awardPredictionRepository.saveAll(any<List<AwardPrediction>>()) } answers { firstArg() }

            predictionService.recalculateTournamentPoints(testTournamentId)

            val slot1 = slot<List<GroupUser>>()
            verify(exactly = 1) { membershipRepository.saveAll(capture(slot1)) }
            val membersSaved = slot1.captured
            assertEquals(1, membersSaved.size)
            assertEquals(testUser1, membersSaved[0].user)
            assertEquals(testGroup1, membersSaved[0].group)
            assertEquals(34F, membersSaved[0].points)
            assertEquals(1, membersSaved[0].rank)
            assertEquals(0, membersSaved[0].amountPartial)
            assertEquals(1, membersSaved[0].amountCorrect)
            assertEquals(0, membersSaved[0].amountBonus)
            assertEquals(listOf(PredictionStatus.CORRECT, PredictionStatus.INCORRECT, PredictionStatus.MISSING), membersSaved[0].lastPredictions)

            val slot2 = slot<List<MatchPrediction>>()
            verify(exactly = 1) { matchPredictionRepository.saveAll(capture(slot2)) }
            val matchPredictionsSaved = slot2.captured
            assertEquals(2, matchPredictionsSaved.size)
            assertTrue {
                matchPredictionsSaved.any {
                    it.user.id == testUser1.id && it.group.id == testGroup1.id &&
                            it.match.id == testMatch1.id && it.status == PredictionStatus.CORRECT
                }
            }
            assertTrue {
                matchPredictionsSaved.any {
                    it.user.id == testUser1.id && it.group.id == testGroup1.id &&
                            it.match.id == testMatch2.id && it.status == PredictionStatus.INCORRECT
                }
            }

            val slot3 = slot<List<AwardPrediction>>()
            verify(exactly = 1) { awardPredictionRepository.saveAll(capture(slot3)) }
            val awardPredictionsSaved = slot3.captured
            assertEquals(7, awardPredictionsSaved.size)
            assertTrue {
                awardPredictionsSaved.any {
                    it.user.id == testUser1.id && it.group.id == testGroup1.id && it.status == PredictionStatus.CORRECT &&
                            it.awardType == AwardType.CHAMPION && it.team!!.id == awardPredictions[0].team!!.id
                }
            }
            assertTrue {
                awardPredictionsSaved.any {
                    it.user.id == testUser1.id && it.group.id == testGroup1.id && it.status == PredictionStatus.CORRECT &&
                            it.awardType == AwardType.TOP_SCORER && it.player!!.id == awardPredictions[1].player!!.id
                }
            }
            assertTrue {
                awardPredictionsSaved.any {
                    it.user.id == testUser1.id && it.group.id == testGroup1.id && it.status == PredictionStatus.CORRECT &&
                            it.awardType == AwardType.BEST_PLAYER && it.player!!.id == awardPredictions[2].player!!.id
                }
            }
            assertTrue {
                awardPredictionsSaved.any {
                    it.user.id == testUser1.id && it.group.id == testGroup1.id && it.status == PredictionStatus.INCORRECT &&
                            it.awardType == AwardType.BEST_PLAYER && it.player!!.id == awardPredictions[3].player!!.id
                }
            }
            assertTrue {
                awardPredictionsSaved.any {
                    it.user.id == testUser1.id && it.group.id == testGroup1.id && it.status == PredictionStatus.CORRECT &&
                            it.awardType == AwardType.BEST_GOALKEEPER && it.player!!.id == awardPredictions[4].player!!.id
                }
            }
            assertTrue {
                awardPredictionsSaved.any {
                    it.user.id == testUser1.id && it.group.id == testGroup1.id && it.status == PredictionStatus.INCORRECT &&
                            it.awardType == AwardType.BEST_GOALKEEPER && it.player!!.id == awardPredictions[5].player!!.id
                }
            }
            assertTrue {
                awardPredictionsSaved.any {
                    it.user.id == testUser1.id && it.group.id == testGroup1.id && it.status == PredictionStatus.INCORRECT &&
                            it.awardType == AwardType.BEST_GOALKEEPER && it.player!!.id == awardPredictions[6].player!!.id
                }
            }
        }
    }

    @Nested
    inner class CalculatePredictionsProfileTests {

        private val testGroup = Group(
            id = testGroupId, name = "Test Group",
            tournament = testTournament.copy(status = TournamentStatus.NOT_STARTED),
        )

        private val winners = Awards(
            champion = UUID.randomUUID(), topScorer = UUID.randomUUID(), bestPlayer = UUID.randomUUID(),
            bestGoalkeeper = UUID.randomUUID(), bestYoungPlayer = UUID.randomUUID()
        )

        private fun awardPrediction(awardType: AwardType, awardId: UUID) = AwardPrediction(
            group = testGroup.copy(tournament = testTournament.copy(awards = winners)),
            user = testUser, awardType = awardType, team = testTeam.copy(id = awardId).takeIf { awardType == AwardType.CHAMPION },
            player = testPlayer.copy(id = awardId).takeIf { awardType != AwardType.CHAMPION }
        )

        private fun matchPrediction(match: Match, homeGoals: Int? = null, awayGoals: Int? = null): MatchPredictionView {
            val matchPredictionView = MatchPredictionView(user = testUser, match = match)
            return if (homeGoals != null && awayGoals != null) {
                matchPredictionView.copy(
                    id = UUID.randomUUID(), prediction = MatchPrediction(
                        group = testGroup.copy(tournament = testTournament.copy(awards = winners)),
                        user = testUser, homeGoals = homeGoals, awayGoals = awayGoals, match = match, status = when {
                            match.status != MatchStatus.FINISHED -> PredictionStatus.PENDING
                            homeGoals == match.homeGoals && awayGoals == match.awayGoals && homeGoals + awayGoals >= 5 -> PredictionStatus.BONUS
                            homeGoals == match.homeGoals && awayGoals == match.awayGoals -> PredictionStatus.CORRECT
                            Score(homeGoals, awayGoals).outcome() == match.score()?.outcome() -> PredictionStatus.PARTIAL
                            else -> PredictionStatus.INCORRECT
                        }
                    )
                )
            } else {
                matchPredictionView
            }
        }

        val testMatch1 = Match(
            id = UUID.randomUUID(), code = "X1", tournament = testTournament, homeTeam = testTeam, awayTeam = testTeam,
            homeGoals = 0, awayGoals = 0, homeQuota = 1.1f, drawQuota = 2.2f, awayQuota = 6.1f, hasMultiplier = true,
            stage = MatchStage.GROUP_STAGE, group = MatchGroup.GROUP_A, status = MatchStatus.FINISHED, startedAt = ZonedDateTime.now().minusDays(7)
        )
        val testMatch2 = Match(
            id = UUID.randomUUID(), code = "X2", tournament = testTournament, homeTeam = testTeam, awayTeam = testTeam,
            homeGoals = 1, awayGoals = 1, homeQuota = 1.9f, drawQuota = 2.1f, awayQuota = 3.9f,
            stage = MatchStage.GROUP_STAGE, group = MatchGroup.GROUP_B, status = MatchStatus.FINISHED, startedAt = ZonedDateTime.now().minusDays(4)
        )
        val testMatch3 = Match(
            id = UUID.randomUUID(), code = "X3", tournament = testTournament, homeTeam = testTeam, awayTeam = testTeam,
            homeGoals = 1, awayGoals = 0, homeQuota = 4.4f, drawQuota = 1.9f, awayQuota = 2.0f,
            stage = MatchStage.GROUP_STAGE, group = MatchGroup.GROUP_C, status = MatchStatus.FINISHED, startedAt = ZonedDateTime.now().minusDays(1)
        )
        val testMatch4 = Match(
            id = UUID.randomUUID(), code = "X4", tournament = testTournament, homeTeam = testTeam, awayTeam = testTeam,
            homeGoals = 4, awayGoals = 3, homeQuota = 1.6f, drawQuota = 1.8f, awayQuota = 1.7f,
            stage = MatchStage.GROUP_STAGE, group = MatchGroup.GROUP_D, status = MatchStatus.FINISHED, startedAt = ZonedDateTime.now().minusDays(1)
        )
        val testMatch5 = Match(
            id = UUID.randomUUID(), code = "X5", tournament = testTournament, homeTeam = testTeam, awayTeam = testTeam,
            homeGoals = null, awayGoals = null, homeQuota = 1.2f, drawQuota = 1.3f, awayQuota = 1.3f,
            stage = MatchStage.GROUP_STAGE, group = MatchGroup.GROUP_D, status = MatchStatus.NOT_STARTED, startedAt = ZonedDateTime.now().plusDays(2)
        )

        @Test
        fun `calculatePredictionsProfile should properly calculate the profile when no match-predictions were submitted`() {
            mockMembership(testUser, testGroup)

            every { matchPredictionRepository.findGroupPredictionsForUser(testGroupId, testUserId) } returns emptyList()

            val profile = predictionService.calculatePredictionsProfile(testUserId, testGroupId)
            assertEquals(testGroup.id, profile.group.id)
            assertEquals(0f, profile.totalPoints)
            assertEquals(0f, profile.quotasPoints)
            assertNull(profile.awardsPoints)
            assertEquals(0, profile.commonMatches.missing)
            assertEquals(0, profile.commonMatches.incorrect)
            assertEquals(0, profile.commonMatches.partial)
            assertEquals(0, profile.commonMatches.correct)
            assertEquals(0, profile.commonMatches.bonus)
            assertEquals(0, profile.highlightedMatches.missing)
            assertEquals(0, profile.highlightedMatches.incorrect)
            assertEquals(0, profile.highlightedMatches.partial)
            assertEquals(0, profile.highlightedMatches.correct)
            assertEquals(0, profile.highlightedMatches.bonus)
            assertNull(profile.topSucceededQuota)
            assertNull(profile.topFailedQuota)
        }

        @Test
        fun `calculatePredictionsProfile should properly calculate the profile without missing match-predictions`() {
            mockMembership(testUser, testGroup, points = 16.1f)

            val matchPredictions = listOf(
                matchPrediction(testMatch1, homeGoals = 0, awayGoals = 1),
                matchPrediction(testMatch2, homeGoals = 0, awayGoals = 0),
                matchPrediction(testMatch3, homeGoals = 1, awayGoals = 0),
                matchPrediction(testMatch4, homeGoals = 4, awayGoals = 3),
            )
            every { matchPredictionRepository.findGroupPredictionsForUser(testGroupId, testUserId) } returns matchPredictions

            val profile = predictionService.calculatePredictionsProfile(testUserId, testGroupId)
            assertEquals(testGroup.id, profile.group.id)
            assertEquals(16.1f, profile.totalPoints)
            assertEquals(8.1f, profile.quotasPoints)
            assertNull(profile.awardsPoints)
            assertEquals(0, profile.commonMatches.missing)
            assertEquals(0, profile.commonMatches.incorrect)
            assertEquals(1, profile.commonMatches.partial)
            assertEquals(1, profile.commonMatches.correct)
            assertEquals(1, profile.commonMatches.bonus)
            assertEquals(0, profile.highlightedMatches.missing)
            assertEquals(1, profile.highlightedMatches.incorrect)
            assertEquals(0, profile.highlightedMatches.partial)
            assertEquals(0, profile.highlightedMatches.correct)
            assertEquals(0, profile.highlightedMatches.bonus)
            val topSucceeded = profile.topSucceededQuota!!
            assertEquals(4.4f, topSucceeded.quota)
            assertEquals(testMatch3.id, topSucceeded.prediction.match.id)
            val topFailed = profile.topFailedQuota!!
            assertEquals(6.1f, topFailed.quota)
            assertEquals(testMatch1.id, topFailed.prediction.match.id)
        }

        @Test
        fun `calculatePredictionsProfile should properly calculate the profile with missing match-predictions`() {
            mockMembership(testUser, testGroup, points = 16.1f)

            val matchPredictions = listOf(
                matchPrediction(testMatch1, homeGoals = 0, awayGoals = 1),
                matchPrediction(testMatch2, homeGoals = 0, awayGoals = 0),
                matchPrediction(testMatch3, homeGoals = 1, awayGoals = 0),
                matchPrediction(testMatch4, homeGoals = 4, awayGoals = 3),
                matchPrediction(testMatch5),
            )
            every { matchPredictionRepository.findGroupPredictionsForUser(testGroupId, testUserId) } returns matchPredictions

            val profile = predictionService.calculatePredictionsProfile(testUserId, testGroupId)
            assertEquals(testGroup.id, profile.group.id)
            assertEquals(16.1f, profile.totalPoints)
            assertEquals(8.1f, profile.quotasPoints)
            assertNull(profile.awardsPoints)
            assertEquals(0, profile.commonMatches.missing)
            assertEquals(0, profile.commonMatches.incorrect)
            assertEquals(1, profile.commonMatches.partial)
            assertEquals(1, profile.commonMatches.correct)
            assertEquals(1, profile.commonMatches.bonus)
            assertEquals(0, profile.highlightedMatches.missing)
            assertEquals(1, profile.highlightedMatches.incorrect)
            assertEquals(0, profile.highlightedMatches.partial)
            assertEquals(0, profile.highlightedMatches.correct)
            assertEquals(0, profile.highlightedMatches.bonus)
            val topSucceeded = profile.topSucceededQuota!!
            assertEquals(4.4f, topSucceeded.quota)
            assertEquals(testMatch3.id, topSucceeded.prediction.match.id)
            val topFailed = profile.topFailedQuota!!
            assertEquals(6.1f, topFailed.quota)
            assertEquals(testMatch1.id, topFailed.prediction.match.id)
        }

        @Test
        fun `calculatePredictionsProfile should properly calculate the profile with award-predictions but tournament not finished`() {
            mockMembership(testUser, testGroup, points = 16.1f)

            val matchPredictions = listOf(
                matchPrediction(testMatch1, homeGoals = 0, awayGoals = 1),
                matchPrediction(testMatch2, homeGoals = 0, awayGoals = 0),
                matchPrediction(testMatch3, homeGoals = 1, awayGoals = 0),
                matchPrediction(testMatch4, homeGoals = 4, awayGoals = 3),
                matchPrediction(testMatch5),
            )
            every { matchPredictionRepository.findGroupPredictionsForUser(testGroupId, testUserId) } returns matchPredictions
            verify(exactly = 0) { awardPredictionRepository.findByUserIdAndGroupId(testUserId, testGroupId) }

            val profile = predictionService.calculatePredictionsProfile(testUserId, testGroupId)

            assertEquals(testGroup.id, profile.group.id)
            assertEquals(16.1f, profile.totalPoints)
            assertEquals(8.1f, profile.quotasPoints)
            assertNull(profile.awardsPoints)
            assertEquals(0, profile.commonMatches.missing)
            assertEquals(0, profile.commonMatches.incorrect)
            assertEquals(1, profile.commonMatches.partial)
            assertEquals(1, profile.commonMatches.correct)
            assertEquals(1, profile.commonMatches.bonus)
            assertEquals(0, profile.highlightedMatches.missing)
            assertEquals(1, profile.highlightedMatches.incorrect)
            assertEquals(0, profile.highlightedMatches.partial)
            assertEquals(0, profile.highlightedMatches.correct)
            assertEquals(0, profile.highlightedMatches.bonus)
            val topSucceeded = profile.topSucceededQuota!!
            assertEquals(4.4f, topSucceeded.quota)
            assertEquals(testMatch3.id, topSucceeded.prediction.match.id)
            val topFailed = profile.topFailedQuota!!
            assertEquals(6.1f, topFailed.quota)
            assertEquals(testMatch1.id, topFailed.prediction.match.id)
        }

        @Test
        fun `calculatePredictionsProfile should properly calculate the profile with award-predictions and tournament is finished`() {
            mockMembership(testUser, testGroup.copy(tournament = testTournament.copy(status = TournamentStatus.FINISHED)), points = 16.1f)

            val matchPredictions = listOf(
                matchPrediction(testMatch1, homeGoals = 0, awayGoals = 1),
                matchPrediction(testMatch2, homeGoals = 0, awayGoals = 0),
                matchPrediction(testMatch3, homeGoals = 1, awayGoals = 0),
                matchPrediction(testMatch4, homeGoals = 4, awayGoals = 3),
                matchPrediction(testMatch5),
            )
            every { matchPredictionRepository.findGroupPredictionsForUser(testGroupId, testUserId) } returns matchPredictions

            // Award statuses are persisted during the recalculation that runs when the tournament finishes;
            // the profile reads them as-is. Two champions with one correct pick yields POINTS_DOUBLE_CHAMPION (5).
            val awardPredictions = listOf(
                awardPrediction(AwardType.CHAMPION, winners.champion).copy(status = PredictionStatus.CORRECT),
                awardPrediction(AwardType.CHAMPION, UUID.randomUUID()).copy(status = PredictionStatus.INCORRECT),
            )
            every { awardPredictionRepository.findByUserIdAndGroupId(testUserId, testGroupId) } returns awardPredictions

            val profile = predictionService.calculatePredictionsProfile(testUserId, testGroupId)

            assertEquals(testGroup.id, profile.group.id)
            assertEquals(16.1f, profile.totalPoints)
            assertEquals(8.1f, profile.quotasPoints)
            assertEquals(5.0f, profile.awardsPoints)
            assertEquals(0, profile.commonMatches.missing)
            assertEquals(0, profile.commonMatches.incorrect)
            assertEquals(1, profile.commonMatches.partial)
            assertEquals(1, profile.commonMatches.correct)
            assertEquals(1, profile.commonMatches.bonus)
            assertEquals(0, profile.highlightedMatches.missing)
            assertEquals(1, profile.highlightedMatches.incorrect)
            assertEquals(0, profile.highlightedMatches.partial)
            assertEquals(0, profile.highlightedMatches.correct)
            assertEquals(0, profile.highlightedMatches.bonus)
            val topSucceeded = profile.topSucceededQuota!!
            assertEquals(4.4f, topSucceeded.quota)
            assertEquals(testMatch3.id, topSucceeded.prediction.match.id)
            val topFailed = profile.topFailedQuota!!
            assertEquals(6.1f, topFailed.quota)
            assertEquals(testMatch1.id, topFailed.prediction.match.id)
        }
    }
}
