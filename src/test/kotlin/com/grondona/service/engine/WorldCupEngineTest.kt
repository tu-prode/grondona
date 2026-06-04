package com.grondona.service.engine

import com.grondona.model.ExternalMatch
import com.grondona.model.Match
import com.grondona.model.MatchStage
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
import java.time.ZonedDateTime
import java.util.*

class WorldCupEngineTest {

    private val testTournamentId = WorldCupEngine.SYSTEM_TOURNAMENT_ID
    private val testTournament: Tournament = Tournament(
        id = testTournamentId, name = "World Cup", status = TournamentStatus.NOT_STARTED,
    )

    private fun matchFromDB(
        home: String = "XXX", away: String = "XXX", status: MatchStatus = MatchStatus.NOT_STARTED, startedAt: ZonedDateTime = ZonedDateTime.now().plusDays(1),
        code: String = "XXX", homeGoals: Int = 0, awayGoals: Int = 0, homeQuota: Float = 1f, drawQuota: Float = 1f, awayQuota: Float = 1f,
    ) = Match(
        id = UUID.randomUUID(), code = code,
        homeTeam = Team(id = UUID.randomUUID(), tournament = testTournament, name = home, code = home, icon = "test", englishKey = "$code-en"),
        awayTeam = Team(id = UUID.randomUUID(), tournament = testTournament, name = away, code = away, icon = "test", englishKey = "$code-en"),
        status = status, homeQuota = homeQuota, drawQuota = drawQuota, awayQuota = awayQuota, startedAt = startedAt,
        tournament = testTournament, homeGoals = homeGoals, awayGoals = awayGoals, stage = MatchStage.GROUP_STAGE,
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

}
