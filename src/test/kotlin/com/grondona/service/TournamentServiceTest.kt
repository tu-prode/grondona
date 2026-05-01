package com.grondona.service

import com.grondona.exception.BadRequestException
import com.grondona.exception.ConflictException
import com.grondona.exception.NotFoundException
import com.grondona.model.AwardPrediction
import com.grondona.model.AwardType
import com.grondona.model.Awards
import com.grondona.model.ExtendedAwards
import com.grondona.model.Group
import com.grondona.model.GroupUser
import com.grondona.model.Match
import com.grondona.model.MatchStatus
import com.grondona.model.Player
import com.grondona.model.PlayerPosition
import com.grondona.model.PredictionStatus
import com.grondona.model.Team
import com.grondona.model.Tournament
import com.grondona.model.TournamentStatus
import com.grondona.model.User
import com.grondona.model.dto.request.CreateTournamentRequest
import com.grondona.model.dto.request.UpdateTournamentRequest
import com.grondona.repository.AwardPredictionRepository
import com.grondona.repository.MatchRepository
import com.grondona.repository.MembershipRepository
import com.grondona.repository.PlayerRepository
import com.grondona.repository.TeamRepository
import com.grondona.repository.TournamentRepository
import com.grondona.service.engine.PredictionsEngine
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

class TournamentServiceTest {

    @MockK
    private lateinit var teamRepository: TeamRepository

    @MockK
    private lateinit var matchRepository: MatchRepository

    @MockK
    private lateinit var playerRepository: PlayerRepository

    @MockK
    private lateinit var tournamentRepository: TournamentRepository

    @MockK
    private lateinit var membershipRepository: MembershipRepository

    @MockK
    private lateinit var awardPredictionRepository: AwardPredictionRepository

    @InjectMockKs
    private lateinit var tournamentService: TournamentService

    private val testTournamentId = UUID.randomUUID()
    private val testTournament = Tournament(
        id = testTournamentId,
        name = "Test Tournament",
        status = TournamentStatus.NOT_STARTED,
        createdAt = LocalDateTime.now(),
        updatedAt = LocalDateTime.now()
    )

    private val testUserId = UUID.randomUUID()
    private val testUser = User(
        id = testUserId,
        fullname = "User",
        username = "user",
        email = "user@gmail",
        passwordHash = "pass",
    )

    private val testGroupId = UUID.randomUUID()
    private val testGroup = Group(
        id = testGroupId,
        name = "Group",
        tournament = testTournament,
    )

    private val testMember = GroupUser(user = testUser, group = testGroup)

    private fun UUID.toTeam() = Team(
        id = this, tournament = testTournament, name = "Team", code = "TEAM", icon = "icon",
    )

    private fun UUID.toPlayer() = Player(
        id = this, team = UUID.randomUUID().toTeam(), name = "Player", position = PlayerPosition.MIDFIELDER,
    )

    private fun testAwardPrediction(awardType: AwardType, awardId: UUID = UUID.randomUUID()) = AwardPrediction(
        id = UUID.randomUUID(), user = testUser, group = testGroup, awardType = awardType,
        team = awardId.takeIf { awardType == AwardType.CHAMPION }?.toTeam(),
        player = awardId.takeIf { awardType != AwardType.CHAMPION }?.toPlayer(),
    )

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)

        tournamentService = spyk(
            TournamentService(
                teamRepository,
                matchRepository,
                playerRepository,
                tournamentRepository,
                membershipRepository,
                awardPredictionRepository,
            )
        )
    }

    @Nested
    inner class CreateTournamentTests {

        @Test
        fun `createTournament should return TournamentResponse when name is unique`() {
            val request = CreateTournamentRequest(name = "New Tournament")
            every { tournamentRepository.existsByName("New Tournament") } returns false
            every { tournamentRepository.save(any()) } returns testTournament.copy(name = "New Tournament")

            val result = tournamentService.createTournament(request)

            assertEquals("New Tournament", result.name)
            assertEquals(TournamentStatus.NOT_STARTED, result.status)
            verify { tournamentRepository.save(any()) }
        }

        @Test
        fun `createTournament should use provided status`() {
            val request = CreateTournamentRequest(name = "Active Tournament", status = TournamentStatus.IN_PROGRESS)
            every { tournamentRepository.existsByName("Active Tournament") } returns false
            every { tournamentRepository.save(any()) } returns testTournament.copy(
                name = "Active Tournament",
                status = TournamentStatus.IN_PROGRESS
            )

            val result = tournamentService.createTournament(request)

            assertEquals(TournamentStatus.IN_PROGRESS, result.status)
        }

        @Test
        fun `createTournament should default to NOT_STARTED when no status provided`() {
            val request = CreateTournamentRequest(name = "Default Tournament", status = null)
            every { tournamentRepository.existsByName("Default Tournament") } returns false
            every { tournamentRepository.save(any()) } answers {
                val saved = firstArg<Tournament>()
                assertEquals(TournamentStatus.NOT_STARTED, saved.status)
                testTournament.copy(name = "Default Tournament")
            }

            tournamentService.createTournament(request)
        }

        @Test
        fun `createTournament should throw ConflictException when name already exists`() {
            val request = CreateTournamentRequest(name = "Existing Tournament")
            every { tournamentRepository.existsByName("Existing Tournament") } returns true

            val exception = assertThrows<ConflictException> {
                tournamentService.createTournament(request)
            }
            assertEquals("Tournament name already exists", exception.message)
            assertEquals("name", exception.field)
            assertEquals("Existing Tournament", exception.rejectedValue)
            verify(exactly = 0) { tournamentRepository.save(any()) }
        }
    }

    @Nested
    inner class UpdateTournamentTests {

        @Test
        fun `updateTournament should update name when provided and available`() {
            val request = UpdateTournamentRequest(name = "Updated Name")
            val tournamentCopy = testTournament.copy()
            every { tournamentRepository.findById(testTournamentId) } returns Optional.of(tournamentCopy)
            every { tournamentRepository.existsByName("Updated Name") } returns false
            every { tournamentRepository.save(any()) } answers { firstArg() }

            val result = tournamentService.updateTournament(testTournamentId, request)

            assertEquals("Updated Name", result.name)
        }

        @Test
        fun `updateTournament should update status when provided`() {
            val request = UpdateTournamentRequest(name = null, status = TournamentStatus.IN_PROGRESS)
            val tournamentCopy = testTournament.copy()
            every { tournamentRepository.findById(testTournamentId) } returns Optional.of(tournamentCopy)
            every { tournamentRepository.save(any()) } answers { firstArg() }

            val result = tournamentService.updateTournament(testTournamentId, request)

            assertEquals(TournamentStatus.IN_PROGRESS, result.status)
        }

        @Test
        fun `updateTournament should allow same name without conflict check`() {
            val request = UpdateTournamentRequest(name = testTournament.name)
            val tournamentCopy = testTournament.copy()
            every { tournamentRepository.findById(testTournamentId) } returns Optional.of(tournamentCopy)
            every { tournamentRepository.save(any()) } answers { firstArg() }

            val result = tournamentService.updateTournament(testTournamentId, request)

            assertEquals(testTournament.name, result.name)
            verify(exactly = 0) { tournamentRepository.existsByName(any()) }
        }

        @Test
        fun `updateTournament should throw ConflictException when new name already taken`() {
            val request = UpdateTournamentRequest(name = "Taken Name")
            every { tournamentRepository.findById(testTournamentId) } returns Optional.of(testTournament.copy())
            every { tournamentRepository.existsByName("Taken Name") } returns true

            val exception = assertThrows<ConflictException> {
                tournamentService.updateTournament(testTournamentId, request)
            }
            assertEquals("Tournament name already exists", exception.message)
            assertEquals("name", exception.field)
            assertEquals("Taken Name", exception.rejectedValue)
        }

        @Test
        fun `updateTournament should throw NotFoundException when tournament not found`() {
            val request = UpdateTournamentRequest(name = "Any Name")
            every { tournamentRepository.findById(testTournamentId) } returns Optional.empty()

            val exception = assertThrows<NotFoundException> {
                tournamentService.updateTournament(testTournamentId, request)
            }
            assertEquals("Tournament not found", exception.message)
        }

        @Test
        fun `updateTournament should trigger a points update when the awards are set`() {
            val awards = Awards(
                champion = UUID.randomUUID(),
                topScorer = UUID.randomUUID(),
                bestPlayer = UUID.randomUUID(),
                bestGoalkeeper = UUID.randomUUID(),
                bestYoungPlayer = UUID.randomUUID(),
            )

            val request = UpdateTournamentRequest(awards = awards)
            val tournamentCopy = testTournament.copy(status = TournamentStatus.FINISHED)
            every { tournamentRepository.findById(testTournamentId) } returns Optional.of(tournamentCopy)
            every { tournamentRepository.save(any()) } answers { firstArg() }
            every { tournamentService.updateAwardPredictionsPoints(any()) } just Runs
            every { tournamentService.checkAwards(any()) } returns ExtendedAwards(
                champion = awards.champion.toTeam(),
                topScorer = awards.topScorer.toPlayer(),
                bestPlayer = awards.bestPlayer.toPlayer(),
                bestGoalkeeper = awards.bestGoalkeeper.toPlayer(),
                bestYoungPlayer = awards.bestYoungPlayer.toPlayer(),
            )

            tournamentService.updateTournament(testTournamentId, request)
            verify(exactly = 1) { tournamentService.updateAwardPredictionsPoints(any()) }
        }

        @Test
        fun `updateTournament should throw an error when updating awards for a not-finished tournament`() {
            val awards = Awards(
                champion = UUID.randomUUID(),
                topScorer = UUID.randomUUID(),
                bestPlayer = UUID.randomUUID(),
                bestGoalkeeper = UUID.randomUUID(),
                bestYoungPlayer = UUID.randomUUID(),
            )

            val request = UpdateTournamentRequest(awards = awards)
            val tournamentCopy = testTournament.copy(status = TournamentStatus.IN_PROGRESS)
            every { tournamentRepository.findById(testTournamentId) } returns Optional.of(tournamentCopy)

            val exception = assertThrows<BadRequestException> {
                tournamentService.updateTournament(testTournamentId, request)
            }

            assertEquals("Setting awards for a non-finished tournament", exception.message)
        }
    }

    @Nested
    inner class DeleteTournamentTests {

        @Test
        fun `deleteTournament should delete when tournament exists`() {
            every { tournamentRepository.findById(testTournamentId) } returns Optional.of(testTournament)
            every { tournamentRepository.delete(testTournament) } just Runs

            assertDoesNotThrow { tournamentService.deleteTournament(testTournamentId) }
            verify { tournamentRepository.findById(testTournamentId) }
        }

        @Test
        fun `deleteTournament should throw NotFoundException when tournament not found`() {
            every { tournamentRepository.findById(testTournamentId) } returns Optional.empty()

            val exception = assertThrows<NotFoundException> {
                tournamentService.deleteTournament(testTournamentId)
            }
            assertEquals("Tournament not found", exception.message)
        }
    }

    @Nested
    inner class GetTournamentByIdTests {

        @Test
        fun `getTournamentById should return TournamentResponse when exists`() {
            every { tournamentRepository.findById(testTournamentId) } returns Optional.of(testTournament)

            val result = tournamentService.getTournamentById(testTournamentId)

            assertEquals(testTournamentId, result.id)
            assertEquals(testTournament.name, result.name)
            assertEquals(testTournament.status, result.status)
        }

        @Test
        fun `getTournamentById should throw NotFoundException when not found`() {
            every { tournamentRepository.findById(testTournamentId) } returns Optional.empty()

            val exception = assertThrows<NotFoundException> {
                tournamentService.getTournamentById(testTournamentId)
            }
            assertEquals("Tournament not found", exception.message)
        }
    }

    @Nested
    inner class GetTournamentMatchesTests {

        private fun makeMatch(status: MatchStatus, startedAt: LocalDateTime? = null): Match {
            val team = Team(
                id = UUID.randomUUID(),
                tournament = testTournament,
                name = "Team",
                code = "T",
                icon = "icon.png"
            )
            return Match(
                id = UUID.randomUUID(),
                tournament = testTournament,
                code = "M-${UUID.randomUUID()}",
                homeTeam = team,
                awayTeam = team,
                status = status,
                startedAt = startedAt
            )
        }

        @Test
        fun `getTournamentMatches should return empty response when no matches exist`() {
            every { tournamentRepository.findById(testTournamentId) } returns Optional.of(testTournament)
            every { matchRepository.findByTournamentIdOrderByStartedAt(testTournamentId) } returns emptyList()

            val result = tournamentService.getTournamentMatches(testTournamentId, null, null, null)

            assertEquals(testTournamentId, result.tournamentId)
            assertEquals(testTournament.name, result.tournamentName)
            assertTrue(result.pastMatches.isEmpty())
            assertTrue(result.liveMatches.isEmpty())
            assertTrue(result.nextMatches.isEmpty())
        }

        @Test
        fun `getTournamentMatches should categorize matches by status`() {
            val pastMatch = makeMatch(MatchStatus.FINISHED, LocalDateTime.now().minusDays(1))
            val liveMatch = makeMatch(MatchStatus.IN_PROGRESS, LocalDateTime.now())
            val nextMatch = makeMatch(MatchStatus.NOT_STARTED, LocalDateTime.now().plusDays(1))

            every { tournamentRepository.findById(testTournamentId) } returns Optional.of(testTournament)
            every { matchRepository.findByTournamentIdOrderByStartedAt(testTournamentId) } returns
                    listOf(pastMatch, liveMatch, nextMatch)

            val result = tournamentService.getTournamentMatches(testTournamentId, null, null, null)

            assertEquals(1, result.pastMatches.size)
            assertEquals(1, result.liveMatches.size)
            assertEquals(1, result.nextMatches.size)
        }

        @Test
        fun `getTournamentMatches should throw NotFoundException when tournament not found`() {
            every { tournamentRepository.findById(testTournamentId) } returns Optional.empty()

            val exception = assertThrows<NotFoundException> {
                tournamentService.getTournamentMatches(testTournamentId, null, null, null)
            }
            assertEquals("Tournament not found", exception.message)
        }
    }

    @Nested
    inner class UpdateAwardPredictionsPointsTests {

        val userId1: UUID = UUID.randomUUID()
        val userId2: UUID = UUID.randomUUID()
        val userId3: UUID = UUID.randomUUID()
        val groupId1: UUID = UUID.randomUUID()
        val groupId2: UUID = UUID.randomUUID()

        val members = listOf(
            testMember.copy(user = testUser.copy(id = userId1), group = testGroup.copy(id = groupId1)),
            testMember.copy(user = testUser.copy(id = userId2), group = testGroup.copy(id = groupId1)),
            testMember.copy(user = testUser.copy(id = userId1), group = testGroup.copy(id = groupId2)),
            testMember.copy(user = testUser.copy(id = userId3), group = testGroup.copy(id = groupId2)),
        )

        @Test
        fun `updateAwardPredictionsPoints saves predictions updated`() {
            mockkObject(PredictionsEngine)

            val awardPredictions = listOf(testAwardPrediction(awardType = AwardType.BEST_PLAYER))
            every { awardPredictionRepository.findByTournamentId(testTournamentId) } returns awardPredictions
            every { tournamentService.checkAwardPredictions(any()) } returns awardPredictions
            every { awardPredictionRepository.saveAll(any<List<AwardPrediction>>()) } answers { firstArg() }

            every { membershipRepository.findMembers(any()) } answers { members }
            every { PredictionsEngine.updateAwardPoints(any(), any()) } answers { firstArg() }
            every { membershipRepository.saveAll(any<List<GroupUser>>()) } answers { firstArg() }

            tournamentService.updateAwardPredictionsPoints(testTournament)

            val slot = slot<List<AwardPrediction>>()
            verify(exactly = 1) { awardPredictionRepository.saveAll(capture(slot)) }
            val predictionsSaved = slot.captured
            assertEquals(awardPredictions, predictionsSaved)
        }

        @Test
        fun `updateAwardPredictionsPoints calls PointEngine with the proper data`() {
            mockkObject(PredictionsEngine)

            val bestPlayerPrediction = testAwardPrediction(awardType = AwardType.BEST_PLAYER)
            val awardPredictions = listOf(
                bestPlayerPrediction.copy(user = testUser.copy(id = userId1), group = testGroup.copy(id = groupId1)),
                bestPlayerPrediction.copy(user = testUser.copy(id = userId2), group = testGroup.copy(id = groupId1)),
                bestPlayerPrediction.copy(user = testUser.copy(id = userId1), group = testGroup.copy(id = groupId2)),
                bestPlayerPrediction.copy(user = testUser.copy(id = userId3), group = testGroup.copy(id = groupId2)),
                testAwardPrediction(awardType = AwardType.CHAMPION).copy(user = testUser.copy(id = userId1), group = testGroup.copy(id = groupId1)),
                testAwardPrediction(awardType = AwardType.CHAMPION).copy(user = testUser.copy(id = userId3), group = testGroup.copy(id = groupId2)),
            )
            every { awardPredictionRepository.findByTournamentId(testTournamentId) } returns awardPredictions
            every { tournamentService.checkAwardPredictions(any()) } returns awardPredictions
            every { awardPredictionRepository.saveAll(any<List<AwardPrediction>>()) } answers { firstArg() }

            every { membershipRepository.findMembers(any()) } answers { members }

            val membersForGroup1 = listOf(members[0], members[1])
            val userPredictionsForGroup1 = mapOf(
                members[0].user.id!! to listOf(awardPredictions[0], awardPredictions[4]),
                members[1].user.id!! to listOf(awardPredictions[1])
            )
            val membersForGroup2 = listOf(members[2], members[3])
            val userPredictionsForGroup2 = mapOf(
                members[2].user.id!! to listOf(awardPredictions[2]),
                members[3].user.id!! to listOf(awardPredictions[3], awardPredictions[5])
            )
            every { PredictionsEngine.updateAwardPoints(any(), any()) } answers { firstArg() }
            every { membershipRepository.saveAll(any<List<GroupUser>>()) } answers { firstArg() }

            tournamentService.updateAwardPredictionsPoints(testTournament)

            val membersSlot = mutableListOf<List<GroupUser>>()
            val predictionsSlot = mutableListOf<Map<UUID, List<AwardPrediction>>>()
            verify(exactly = 2) { PredictionsEngine.updateAwardPoints(capture(membersSlot), capture(predictionsSlot)) }

            assertTrue(membersSlot[0].containsAll(membersForGroup1))
            assertTrue(membersSlot[1].containsAll(membersForGroup2))
            assertTrue(predictionsSlot[0].keys == userPredictionsForGroup1.keys)
            assertTrue(predictionsSlot[0][userId1] == userPredictionsForGroup1[userId1])
            assertTrue(predictionsSlot[0][userId2] == userPredictionsForGroup1[userId2])
            assertTrue(predictionsSlot[1].keys == userPredictionsForGroup2.keys)
            assertTrue(predictionsSlot[1][userId1] == userPredictionsForGroup2[userId1])
            assertTrue(predictionsSlot[1][userId3] == userPredictionsForGroup2[userId3])
        }
    }

    @Nested
    inner class CheckAwardsPredictionsTests {

        @Test
        fun `checkAwardsPredictions ignores predictions with status not PENDING`() {
            mockkObject(PredictionsEngine)
            val predictions = listOf(
                testAwardPrediction(AwardType.CHAMPION).copy(
                    status = PredictionStatus.CORRECT,
                    group = testGroup.copy(tournament = testTournament.copy(status = TournamentStatus.FINISHED))
                )
            )
            tournamentService.checkAwardPredictions(predictions)
            verify(exactly = 1) { PredictionsEngine.checkAwardPredictions(emptyList()) }
        }

        @Test
        fun `checkAwardPredictions ignores predictions with tournament status not FINISHED`() {
            mockkObject(PredictionsEngine)
            val predictions = listOf(
                testAwardPrediction(AwardType.CHAMPION).copy(
                    status = PredictionStatus.PENDING,
                    group = testGroup.copy(tournament = testTournament.copy(status = TournamentStatus.IN_PROGRESS))
                )
            )
            tournamentService.checkAwardPredictions(predictions)
            verify(exactly = 1) { PredictionsEngine.checkAwardPredictions(emptyList()) }
        }

        @Test
        fun `checkAwardPredictions processes predictions with status PENDING from tournament with status FINISHED`() {
            mockkObject(PredictionsEngine)
            val predictions = listOf(
                testAwardPrediction(AwardType.CHAMPION).copy(
                    status = PredictionStatus.PENDING,
                    group = testGroup.copy(tournament = testTournament.copy(status = TournamentStatus.FINISHED))
                )
            )
            tournamentService.checkAwardPredictions(predictions)
            verify(exactly = 1) { PredictionsEngine.checkAwardPredictions(match { it.size == 1 }) }
        }
    }
}
