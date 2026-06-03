package com.grondona.model

import com.grondona.service.engine.WorldCupEngine
import com.grondona.utils.oddsToQuota
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.ZonedDateTime
import java.util.*

class ExternalTest {

    private val testTournamentId = WorldCupEngine.SYSTEM_TOURNAMENT_ID
    private val testTournament: Tournament = Tournament(
        id = testTournamentId, name = "World Cup", status = TournamentStatus.NOT_STARTED,
    )

    private fun matchFromDB(
        home: String, away: String, stage: MatchStage = MatchStage.GROUP_STAGE, group: MatchGroup = MatchGroup.GROUP_J, 
        status: MatchStatus = MatchStatus.NOT_STARTED, startedAt: ZonedDateTime = ZonedDateTime.now().plusDays(1),
        homeGoals: Int = 0, awayGoals: Int = 0, homeQuota: Float = 1f, drawQuota: Float = 1f, awayQuota: Float = 1f,
    ) = Match(
        id = UUID.randomUUID(), stage = stage, group = group,
        homeTeam = Team(tournament = testTournament, name = home, code = home, icon = "test"),
        awayTeam = Team(tournament = testTournament, name = away, code = away, icon = "test"),
        status = status, homeQuota = homeQuota, drawQuota = drawQuota, awayQuota = awayQuota, startedAt = startedAt,
        tournament = testTournament, code = "test", homeGoals = homeGoals, awayGoals = awayGoals,
    )

    private fun matchFromAPI(
        home: String = "XXX", away: String = "XXX", stage: MatchStage = MatchStage.GROUP_STAGE, group: MatchGroup = MatchGroup.GROUP_J,
        homeGoals: Int = 0, awayGoals: Int = 0, substatus: String? = null, status: MatchStatus = MatchStatus.NOT_STARTED,
        homeOdds: Float = 1f, drawOdds: Float = 1f, awayOdds: Float = 1f,
    ) = ExternalMatch(
        home = home, away = away, homeGoals = homeGoals, awayGoals = awayGoals, status = status, substatus = substatus,
        stage = stage, group = group, homeOdds = homeOdds, drawOdds = drawOdds, awayOdds = awayOdds, startedAt = ZonedDateTime.now(),
    )

    @Nested
    inner class ToExistingMatchTests {

        @Test
        fun `toExistingMatch returns null when the external match is not found between the stored ones`() {
            val dbMatches = (0..9).map { matchFromDB(home = "XX$it", away = "XY$it").copy(code = "$it") }
            val externalMatch = matchFromAPI(home = "XA1", away = "XB1", status = MatchStatus.NOT_STARTED, homeOdds = 10f, drawOdds = 10f, awayOdds = 10f)
            val match = externalMatch.toExistingMatch(dbMatches)
            assertNull(match)
        }

        @Test
        fun `toExistingMatch returns the proper match updated when it is found between the stored ones (and not-finished)`() {
            val finishedAt = ZonedDateTime.now()
            val dbMatches = (0..9).map { matchFromDB(home = "XX$it", away = "XY$it").copy(code = "$it") }
            val externalMatch = matchFromAPI(home = "XX1", away = "XY1").copy(
                homeGoals = 8, awayGoals = 9, homePenalties = 10, awayPenalties = 11,
                homeOdds = 10f, drawOdds = 11f, awayOdds = 12f,
                status = MatchStatus.IN_PROGRESS, substatus = MatchSubstatus.PENALTIES.label, finishedAt = finishedAt,
            )

            val match = externalMatch.toExistingMatch(dbMatches)
            assertNotNull(match); match!!
            assertEquals(8, match.homeGoals)
            assertEquals(9, match.awayGoals)
            assertEquals(10, match.homePenalties)
            assertEquals(11, match.awayPenalties)
            assertEquals(10f.oddsToQuota(), match.homeQuota)
            assertEquals(11f.oddsToQuota(), match.drawQuota)
            assertEquals(12f.oddsToQuota(), match.awayQuota)
            assertEquals(MatchStatus.IN_PROGRESS, match.status)
            assertEquals(MatchSubstatus.PENALTIES.label, match.substatus)
            assertNull(match.finishedAt)
        }

        @Test
        fun `toExistingMatch returns the proper match updated when it is found between the stored ones (and finished)`() {
            val finishedAt = ZonedDateTime.now()
            val dbMatches = (0..9).map { matchFromDB(home = "XX$it", away = "XY$it").copy(code = "$it") }
            val externalMatch = matchFromAPI(home = "XX1", away = "XY1").copy(
                homeGoals = 8, awayGoals = 8, homePenalties = 10, awayPenalties = 11,
                homeOdds = 10f, drawOdds = 11f, awayOdds = 12f,
                status = MatchStatus.FINISHED, substatus = MatchSubstatus.FINISHED.label, finishedAt = finishedAt,
            )

            val match = externalMatch.toExistingMatch(dbMatches)
            assertNotNull(match); match!!
            assertEquals(8, match.homeGoals)
            assertEquals(8, match.awayGoals)
            assertEquals(10, match.homePenalties)
            assertEquals(11, match.awayPenalties)
            assertEquals(10f.oddsToQuota(), match.homeQuota)
            assertEquals(11f.oddsToQuota(), match.drawQuota)
            assertEquals(12f.oddsToQuota(), match.awayQuota)
            assertEquals(MatchStatus.FINISHED, match.status)
            assertEquals(MatchSubstatus.FINISHED.label, match.substatus)
            assertEquals(finishedAt, match.finishedAt)
        }

        @Test
        fun `toExistingMatch distinguish between same teams matches on different rounds`() {
            val dbMatches = listOf(matchFromDB(home = "XX", away = "YY", stage = MatchStage.GROUP_STAGE, group = MatchGroup.GROUP_A).copy(code = "1"))
            val externalMatch = matchFromAPI(home = "XX", away = "YY", stage = MatchStage.FINAL)

            val match = externalMatch.toExistingMatch(dbMatches)
            assertNull(match)
        }
    }
}
