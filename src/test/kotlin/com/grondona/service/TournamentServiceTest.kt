package com.grondona.service

import com.grondona.exception.ConflictException
import com.grondona.exception.NotFoundException
import com.grondona.model.Match
import com.grondona.model.MatchStatus
import com.grondona.model.Team
import com.grondona.model.Tournament
import com.grondona.model.TournamentStatus
import com.grondona.model.dto.request.CreateTournamentRequest
import com.grondona.model.dto.request.UpdateTournamentRequest
import com.grondona.repository.MatchRepository
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

class TournamentServiceTest {

    @MockK
    private lateinit var matchRepository: MatchRepository

    // relaxUnitFun avoids Kotlin overload-resolution ambiguity for delete(T) vs delete(Specification<T>)
    @MockK(relaxUnitFun = true)
    private lateinit var tournamentRepository: TournamentRepository

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

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
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
    }

    @Nested
    inner class DeleteTournamentTests {

        @Test
        fun `deleteTournament should delete when tournament exists`() {
            every { tournamentRepository.findById(testTournamentId) } returns Optional.of(testTournament)

            // delete(Tournament): Unit is auto-relaxed; we verify the service completes successfully
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
}
