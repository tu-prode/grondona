package com.grondona.service.engine

import com.grondona.model.ExternalMatch
import com.grondona.model.Match
import com.grondona.model.MatchStatus
import com.grondona.model.Team
import com.grondona.model.Tournament
import com.grondona.model.TournamentStatus
import io.mockk.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.*

class WorldCupEngineTest {

    private val testTournamentId = WorldCupEngine.SYSTEM_TOURNAMENT_ID
    private val testTournament: Tournament = Tournament(
        id = testTournamentId, name = "World Cup", status = TournamentStatus.NOT_STARTED,
    )

    private fun matchFromDB(
        home: String = "XXX", away: String = "XXX", status: MatchStatus = MatchStatus.NOT_STARTED, startedAt: LocalDateTime? = null,
        code: String = "XXX", homeGoals: Int = 0, awayGoals: Int = 0, homeQuota: Float = 1f, drawQuota: Float = 1f, awayQuota: Float = 1f,
    ) = Match(
        id = UUID.randomUUID(), code = code,
        homeTeam = Team(id = UUID.randomUUID(), tournament = testTournament, name = home, code = home, icon = "test"),
        awayTeam = Team(id = UUID.randomUUID(), tournament = testTournament, name = away, code = away, icon = "test"),
        status = status, homeQuota = homeQuota, drawQuota = drawQuota, awayQuota = awayQuota, startedAt = startedAt,
        tournament = testTournament, homeGoals = homeGoals, awayGoals = awayGoals,
    )

    private fun matchFromAPI(
        home: String = "XXX", away: String = "XXX", homeGoals: Int = 0, awayGoals: Int = 0,
        code: String = "XXX", minutes: Int = 0, half: Int = 0, status: String = "TO_START",
        homeOdds: Float = 1f, drawOdds: Float = 1f, awayOdds: Float = 1f,
    ) = ExternalMatch(
        code = code, home = home, away = away, homeGoals = homeGoals, awayGoals = awayGoals, status = status,
        minutes = minutes, half = half, homeOdds = homeOdds, drawOdds = drawOdds, awayOdds = awayOdds, startedAt = LocalDateTime.now(),
    )

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
    }

    @Nested
    inner class CalculateTournamentStatusTests {

        @Test
        fun `calculateTournamentStatus should return IN_PROGRESS when the tournament has not started and there is any started match`() {
            val newMatches = listOf(
                matchFromDB("ITA", "CHI")
                    .copy(status = MatchStatus.IN_PROGRESS)
            )
            val newStatus = WorldCupEngine.calculateTournamentStatus(newMatches)
            assertEquals(TournamentStatus.IN_PROGRESS, newStatus)
        }

        @Test
        fun `calculateTournamentStatus should return FINISHED when the tournament has started and the last match has finished`() {
            val newMatches = listOf(matchFromDB("ITA", "CHI").copy(
                status = MatchStatus.FINISHED,
                code = WorldCupEngine.FINAL_MATCH_CODE,
                tournament = testTournament.copy(status = TournamentStatus.IN_PROGRESS))
            )
            val newStatus = WorldCupEngine.calculateTournamentStatus(newMatches)
            assertEquals(TournamentStatus.FINISHED, newStatus)
        }

        @Test
        fun `calculateTournamentStatus should return null in other cases`() {
            val newMatches = listOf(matchFromDB("ITA", "CHI")
                .copy(status = MatchStatus.FINISHED, tournament = testTournament.copy(status = TournamentStatus.IN_PROGRESS))
            )
            val newStatus = WorldCupEngine.calculateTournamentStatus(newMatches)
            assertNull(newStatus)
        }

    }

    @Nested
    inner class CalculateNewMatchesTests {

        private fun gatherTeams(matches: List<Match>) =
            matches.flatMap { listOf(it.homeTeam, it.awayTeam) }

        private fun randomTeamMatch(code: String, availableTeams: List<Team>) =
            matchFromAPI(code = code, home = availableTeams.random().code, away = availableTeams.random().code)

        @Test
        fun `calculateNewMatches returns no matches for the round of 32 when there are non-finished matches from the group stage`() {
            val finishedCodes = WorldCupEngine.GS_MATCHES_CODE.dropLast(1)
            val systemMatches = finishedCodes.map { matchFromDB(code = it, home = "H$it", away = "A$it", status = MatchStatus.FINISHED) } +
                    matchFromDB(code = WorldCupEngine.GS_MATCHES_CODE.last(), status = MatchStatus.IN_PROGRESS)

            val externalCodes = WorldCupEngine.GS_MATCHES_CODE + WorldCupEngine.RO32_MATCHES_CODE
            val externalMatches = externalCodes.map { randomTeamMatch(code = it, gatherTeams(systemMatches)) }

            val newMatches = WorldCupEngine.calculateNewMatches(systemMatches, externalMatches)
            assertEquals(0, newMatches.size)
        }

        @Test
        fun `calculateNewMatches returns new matches for the round of 32 when all the group stage matches have finished and there are no matches for this stage`() {
            val finishedCodes = WorldCupEngine.GS_MATCHES_CODE
            val systemMatches = finishedCodes.map { matchFromDB(code = it, home = "H$it", away = "A$it", status = MatchStatus.FINISHED) }

            val externalCodes = WorldCupEngine.GS_MATCHES_CODE + WorldCupEngine.RO32_MATCHES_CODE
            val externalMatches = externalCodes.map { randomTeamMatch(code = it, gatherTeams(systemMatches)) }

            val newMatches = WorldCupEngine.calculateNewMatches(systemMatches, externalMatches)
            assertEquals(16, newMatches.size)
            assertEquals(WorldCupEngine.RO32_MATCHES_CODE, newMatches.map { it.code })
            assertEquals(externalMatches.filter { it.code in WorldCupEngine.RO32_MATCHES_CODE }.map { it.home }, newMatches.map { it.homeTeam.code })
            assertEquals(externalMatches.filter { it.code in WorldCupEngine.RO32_MATCHES_CODE }.map { it.away }, newMatches.map { it.awayTeam.code })

            val totalTeams = systemMatches.flatMap { listOf(it.homeTeam, it.awayTeam) }.associateBy { it.code }
            newMatches.forEach {
                assertEquals(totalTeams[it.homeTeam.code]!!.id!!, it.homeTeam.id)
                assertEquals(totalTeams[it.awayTeam.code]!!.id!!, it.awayTeam.id)
            }
        }

        @Test
        fun `calculateNewMatches returns no matches for the round of 32 when all the group stage matches have finished but there are some matches for this stage`() {
            val finishedCodes = (WorldCupEngine.GS_MATCHES_CODE + WorldCupEngine.RO32_MATCHES_CODE).dropLast(1)
            val systemMatches = finishedCodes.map { matchFromDB(code = it, home = "H$it", away = "A$it", status = MatchStatus.FINISHED) } +
                    matchFromDB(code = WorldCupEngine.RO32_MATCHES_CODE.last(), status = MatchStatus.FINISHED)

            val externalCodes = WorldCupEngine.GS_MATCHES_CODE + WorldCupEngine.RO32_MATCHES_CODE
            val externalMatches = externalCodes.map { randomTeamMatch(code = it, gatherTeams(systemMatches)) }

            val newMatches = WorldCupEngine.calculateNewMatches(systemMatches, externalMatches)
            assertEquals(0, newMatches.size)
        }

        @Test
        fun `calculateNewMatches returns no matches for the round of 16 when there are non-finished matches from the round of 32`() {
            val finishedCodes = WorldCupEngine.RO32_MATCHES_CODE.dropLast(1)
            val systemMatches = finishedCodes.map { matchFromDB(code = it, home = "H$it", away = "A$it", status = MatchStatus.FINISHED) } +
                    matchFromDB(code = WorldCupEngine.RO32_MATCHES_CODE.last(), status = MatchStatus.IN_PROGRESS)

            val externalCodes = WorldCupEngine.RO32_MATCHES_CODE + WorldCupEngine.RO16_MATCHES_CODE
            val externalMatches = externalCodes.map { matchFromAPI(code = it) }

            val newMatches = WorldCupEngine.calculateNewMatches(systemMatches, externalMatches)
            assertEquals(0, newMatches.size)
        }

        @Test
        fun `calculateNewMatches returns new matches for the round of 16 when all the round of 32 matches have finished and there are no matches for this stage`() {
            val finishedCodes = WorldCupEngine.RO32_MATCHES_CODE
            val systemMatches = finishedCodes.map { matchFromDB(code = it, home = "H$it", away = "A$it", status = MatchStatus.FINISHED) }

            val externalCodes = WorldCupEngine.RO32_MATCHES_CODE + WorldCupEngine.RO16_MATCHES_CODE
            val externalMatches = externalCodes.map { randomTeamMatch(code = it, gatherTeams(systemMatches)) }

            val newMatches = WorldCupEngine.calculateNewMatches(systemMatches, externalMatches)
            assertEquals(8, newMatches.size)
            assertEquals(WorldCupEngine.RO16_MATCHES_CODE, newMatches.map { it.code })
            assertEquals(externalMatches.filter { it.code in WorldCupEngine.RO16_MATCHES_CODE }.map { it.home }, newMatches.map { it.homeTeam.code })
            assertEquals(externalMatches.filter { it.code in WorldCupEngine.RO16_MATCHES_CODE }.map { it.away }, newMatches.map { it.awayTeam.code })

            val totalTeams = systemMatches.flatMap { listOf(it.homeTeam, it.awayTeam) }.associateBy { it.code }
            newMatches.forEach {
                assertEquals(totalTeams[it.homeTeam.code]!!.id!!, it.homeTeam.id)
                assertEquals(totalTeams[it.awayTeam.code]!!.id!!, it.awayTeam.id)
            }
        }

        @Test
        fun `calculateNewMatches returns no matches for the round of 16 when all the round of 32 matches have finished but there are some matches for this stage`() {
            val finishedCodes = (WorldCupEngine.RO32_MATCHES_CODE + WorldCupEngine.RO16_MATCHES_CODE).dropLast(1)
            val systemMatches = finishedCodes.map { matchFromDB(code = it, home = "H$it", away = "A$it", status = MatchStatus.FINISHED) } +
                    matchFromDB(code = WorldCupEngine.RO16_MATCHES_CODE.last(), status = MatchStatus.FINISHED)

            val externalCodes = WorldCupEngine.RO32_MATCHES_CODE + WorldCupEngine.RO16_MATCHES_CODE
            val externalMatches = externalCodes.map { matchFromAPI(code = it) }

            val newMatches = WorldCupEngine.calculateNewMatches(systemMatches, externalMatches)
            assertEquals(0, newMatches.size)
        }

        @Test
        fun `calculateNewMatches returns no matches for the quarterfinals when there are non-finished matches from the round of 16`() {
            val finishedCodes = WorldCupEngine.RO16_MATCHES_CODE.dropLast(1)
            val systemMatches = finishedCodes.map { matchFromDB(code = it, home = "H$it", away = "A$it", status = MatchStatus.FINISHED) } +
                    matchFromDB(code = WorldCupEngine.RO16_MATCHES_CODE.last(), status = MatchStatus.IN_PROGRESS)

            val externalCodes = WorldCupEngine.RO16_MATCHES_CODE + WorldCupEngine.QUARTERFINALS_MATCHES_CODE
            val externalMatches = externalCodes.map { randomTeamMatch(code = it, gatherTeams(systemMatches)) }

            val newMatches = WorldCupEngine.calculateNewMatches(systemMatches, externalMatches)
            assertEquals(0, newMatches.size)
        }

        @Test
        fun `calculateNewMatches returns new matches for the quarterfinals when all the round of 16 matches have finished and there are no matches for this stage`() {
            val finishedCodes = WorldCupEngine.RO16_MATCHES_CODE
            val systemMatches = finishedCodes.map { matchFromDB(code = it, home = "H$it", away = "A$it", status = MatchStatus.FINISHED) }

            val externalCodes = WorldCupEngine.RO16_MATCHES_CODE + WorldCupEngine.QUARTERFINALS_MATCHES_CODE
            val externalMatches = externalCodes.map { randomTeamMatch(code = it, gatherTeams(systemMatches)) }

            val newMatches = WorldCupEngine.calculateNewMatches(systemMatches, externalMatches)
            assertEquals(4, newMatches.size)
            assertEquals(WorldCupEngine.QUARTERFINALS_MATCHES_CODE, newMatches.map { it.code })
            assertEquals(externalMatches.filter { it.code in WorldCupEngine.QUARTERFINALS_MATCHES_CODE }.map { it.home }, newMatches.map { it.homeTeam.code })
            assertEquals(externalMatches.filter { it.code in WorldCupEngine.QUARTERFINALS_MATCHES_CODE }.map { it.away }, newMatches.map { it.awayTeam.code })

            val totalTeams = systemMatches.flatMap { listOf(it.homeTeam, it.awayTeam) }.associateBy { it.code }
            newMatches.forEach {
                assertEquals(totalTeams[it.homeTeam.code]!!.id!!, it.homeTeam.id)
                assertEquals(totalTeams[it.awayTeam.code]!!.id!!, it.awayTeam.id)
            }
        }

        @Test
        fun `calculateNewMatches returns no matches for the quarterfinals when all the round of 16 matches have finished but there are some matches for this stage`() {
            val finishedCodes = (WorldCupEngine.RO16_MATCHES_CODE + WorldCupEngine.QUARTERFINALS_MATCHES_CODE).dropLast(1)
            val systemMatches = finishedCodes.map { matchFromDB(code = it, home = "H$it", away = "A$it", status = MatchStatus.FINISHED) } +
                    matchFromDB(code = WorldCupEngine.QUARTERFINALS_MATCHES_CODE.last(), status = MatchStatus.FINISHED)

            val externalCodes = WorldCupEngine.RO16_MATCHES_CODE + WorldCupEngine.QUARTERFINALS_MATCHES_CODE
            val externalMatches = externalCodes.map { matchFromAPI(code = it) }

            val newMatches = WorldCupEngine.calculateNewMatches(systemMatches, externalMatches)
            assertEquals(0, newMatches.size)
        }

        @Test
        fun `calculateNewMatches returns no matches for the semifinals when there are non-finished matches from the quarterfinals`() {
            val finishedCodes = WorldCupEngine.QUARTERFINALS_MATCHES_CODE.dropLast(1)
            val systemMatches = finishedCodes.map { matchFromDB(code = it, home = "H$it", away = "A$it", status = MatchStatus.FINISHED) } +
                    matchFromDB(code = WorldCupEngine.QUARTERFINALS_MATCHES_CODE.last(), status = MatchStatus.IN_PROGRESS)

            val externalCodes = WorldCupEngine.QUARTERFINALS_MATCHES_CODE + WorldCupEngine.SEMIFINALS_MATCHES_CODE
            val externalMatches = externalCodes.map { randomTeamMatch(code = it, gatherTeams(systemMatches)) }

            val newMatches = WorldCupEngine.calculateNewMatches(systemMatches, externalMatches)
            assertEquals(0, newMatches.size)
        }

        @Test
        fun `calculateNewMatches returns new matches for the semifinals when all the quarterfinals matches have finished and there are no matches for this stage`() {
            val finishedCodes = WorldCupEngine.QUARTERFINALS_MATCHES_CODE
            val systemMatches = finishedCodes.map { matchFromDB(code = it, home = "H$it", away = "A$it", status = MatchStatus.FINISHED) }

            val externalCodes = WorldCupEngine.QUARTERFINALS_MATCHES_CODE + WorldCupEngine.SEMIFINALS_MATCHES_CODE
            val externalMatches = externalCodes.map { randomTeamMatch(code = it, gatherTeams(systemMatches)) }

            val newMatches = WorldCupEngine.calculateNewMatches(systemMatches, externalMatches)
            assertEquals(2, newMatches.size)
            assertEquals(WorldCupEngine.SEMIFINALS_MATCHES_CODE, newMatches.map { it.code })
            assertEquals(externalMatches.filter { it.code in WorldCupEngine.SEMIFINALS_MATCHES_CODE }.map { it.home }, newMatches.map { it.homeTeam.code })
            assertEquals(externalMatches.filter { it.code in WorldCupEngine.SEMIFINALS_MATCHES_CODE }.map { it.away }, newMatches.map { it.awayTeam.code })

            val totalTeams = systemMatches.flatMap { listOf(it.homeTeam, it.awayTeam) }.associateBy { it.code }
            newMatches.forEach {
                assertEquals(totalTeams[it.homeTeam.code]!!.id!!, it.homeTeam.id)
                assertEquals(totalTeams[it.awayTeam.code]!!.id!!, it.awayTeam.id)
            }
        }

        @Test
        fun `calculateNewMatches returns no matches for the semifinals when all the quarterfinals matches have finished but there are some matches for this stage`() {
            val finishedCodes = (WorldCupEngine.QUARTERFINALS_MATCHES_CODE + WorldCupEngine.SEMIFINALS_MATCHES_CODE).dropLast(1)
            val systemMatches = finishedCodes.map { matchFromDB(code = it, home = "H$it", away = "A$it", status = MatchStatus.FINISHED) } +
                    matchFromDB(code = WorldCupEngine.SEMIFINALS_MATCHES_CODE.last(), status = MatchStatus.FINISHED)

            val externalCodes = WorldCupEngine.QUARTERFINALS_MATCHES_CODE + WorldCupEngine.SEMIFINALS_MATCHES_CODE
            val externalMatches = externalCodes.map { matchFromAPI(code = it) }

            val newMatches = WorldCupEngine.calculateNewMatches(systemMatches, externalMatches)
            assertEquals(0, newMatches.size)
        }

        @Test
        fun `calculateNewMatches returns no matches for the last round when there are non-finished matches from the semifinals`() {
            val finishedCodes = WorldCupEngine.SEMIFINALS_MATCHES_CODE.dropLast(1)
            val systemMatches = finishedCodes.map { matchFromDB(code = it, home = "H$it", away = "A$it", status = MatchStatus.FINISHED) } +
                    matchFromDB(code = WorldCupEngine.SEMIFINALS_MATCHES_CODE.last(), status = MatchStatus.IN_PROGRESS)

            val externalCodes = WorldCupEngine.SEMIFINALS_MATCHES_CODE + WorldCupEngine.LAST_ROUND_MATCHES_CODE
            val externalMatches = externalCodes.map { randomTeamMatch(code = it, gatherTeams(systemMatches)) }

            val newMatches = WorldCupEngine.calculateNewMatches(systemMatches, externalMatches)
            assertEquals(0, newMatches.size)
        }

        @Test
        fun `calculateNewMatches returns new matches for the last round when all the semifinals matches have finished and there are no matches for this stage`() {
            val finishedCodes = WorldCupEngine.SEMIFINALS_MATCHES_CODE
            val systemMatches = finishedCodes.map { matchFromDB(code = it, home = "H$it", away = "A$it", status = MatchStatus.FINISHED) }

            val externalCodes = WorldCupEngine.SEMIFINALS_MATCHES_CODE + WorldCupEngine.LAST_ROUND_MATCHES_CODE
            val externalMatches = externalCodes.map { randomTeamMatch(code = it, gatherTeams(systemMatches)) }

            val newMatches = WorldCupEngine.calculateNewMatches(systemMatches, externalMatches)
            assertEquals(2, newMatches.size)
            assertEquals(WorldCupEngine.LAST_ROUND_MATCHES_CODE, newMatches.map { it.code })
            assertEquals(externalMatches.filter { it.code in WorldCupEngine.LAST_ROUND_MATCHES_CODE }.map { it.home }, newMatches.map { it.homeTeam.code })
            assertEquals(externalMatches.filter { it.code in WorldCupEngine.LAST_ROUND_MATCHES_CODE }.map { it.away }, newMatches.map { it.awayTeam.code })

            val totalTeams = systemMatches.flatMap { listOf(it.homeTeam, it.awayTeam) }.associateBy { it.code }
            newMatches.forEach {
                assertEquals(totalTeams[it.homeTeam.code]!!.id!!, it.homeTeam.id)
                assertEquals(totalTeams[it.awayTeam.code]!!.id!!, it.awayTeam.id)
            }
        }

        @Test
        fun `calculateNewMatches returns no matches for the last round when all the semifinals matches have finished but there are some matches for this stage`() {
            val finishedCodes = (WorldCupEngine.SEMIFINALS_MATCHES_CODE + WorldCupEngine.LAST_ROUND_MATCHES_CODE).dropLast(1)
            val systemMatches = finishedCodes.map { matchFromDB(code = it, home = "H$it", away = "A$it", status = MatchStatus.FINISHED) } +
                    matchFromDB(code = WorldCupEngine.LAST_ROUND_MATCHES_CODE.last(), status = MatchStatus.FINISHED)

            val externalCodes = WorldCupEngine.SEMIFINALS_MATCHES_CODE + WorldCupEngine.LAST_ROUND_MATCHES_CODE
            val externalMatches = externalCodes.map { matchFromAPI(code = it) }

            val newMatches = WorldCupEngine.calculateNewMatches(systemMatches, externalMatches)
            assertEquals(0, newMatches.size)
        }

    }

}
