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
        home: String, away: String, status: MatchStatus = MatchStatus.NOT_STARTED, startedAt: ZonedDateTime = ZonedDateTime.now().plusDays(1),
        homeGoals: Int = 0, awayGoals: Int = 0, homeQuota: Float = 1f, drawQuota: Float = 1f, awayQuota: Float = 1f,
    ) = Match(
        id = UUID.randomUUID(),
        homeTeam = Team(tournament = testTournament, name = home, code = home, icon = "test"),
        awayTeam = Team(tournament = testTournament, name = away, code = away, icon = "test"),
        status = status, homeQuota = homeQuota, drawQuota = drawQuota, awayQuota = awayQuota, startedAt = startedAt,
        tournament = testTournament, code = "test", homeGoals = homeGoals, awayGoals = awayGoals,
    )

    private fun matchFromAPI(
        home: String = "XXX", away: String = "XXX", homeGoals: Int = 0, awayGoals: Int = 0,
        substatus: String? = null, status: MatchStatus = MatchStatus.NOT_STARTED,
        homeOdds: Float = 1f, drawOdds: Float = 1f, awayOdds: Float = 1f,
    ) = ExternalMatch(
        code = "XX", home = home, away = away, homeGoals = homeGoals, awayGoals = awayGoals, status = status, substatus = substatus,
        homeOdds = homeOdds, drawOdds = drawOdds, awayOdds = awayOdds, startedAt = ZonedDateTime.now(),
    )

    @Nested
    inner class ToMatchUpdatedTests {

        @Test
        fun `toMatchUpdated returns null when the external match is not found between the stored ones`() {
            val dbMatches = (0..9).map { matchFromDB(home = "XX$it", away = "XY$it").copy(code = "$it") }
            val externalMatch = matchFromAPI(home = "XA1", away = "XB1", status = MatchStatus.NOT_STARTED, homeOdds = 10f, drawOdds = 10f, awayOdds = 10f)
            val match = externalMatch.toMatchUpdated(dbMatches)
            assertNull(match)
        }

        @Test
        fun `toMatchUpdated returns null when the external match is not started`() {
            val dbMatches = (0..9).map { matchFromDB(home = "XX$it", away = "XY$it").copy(code = "$it") }
            val externalMatch = matchFromAPI(home = "XX1", away = "XY1", status = MatchStatus.NOT_STARTED, homeGoals = 0, awayGoals = 0)
            val match = externalMatch.toMatchUpdated(dbMatches)
            assertNull(match)
        }

        @Test
        fun `toMatchUpdated returns null when the correspondent stored match is finished`() {
            val dbMatches = (0..9).map { matchFromDB(home = "XX$it", away = "XY$it", status = MatchStatus.FINISHED).copy(code = "$it") }
            val externalMatch = matchFromAPI(home = "XX1", away = "XY1", status = MatchStatus.IN_PROGRESS, homeGoals = 0, awayGoals = 0)
            val match = externalMatch.toMatchUpdated(dbMatches)
            assertNull(match)
        }

        @Test
        fun `toMatchUpdated does not update the match quotas`() {
            val dbMatches = (0..9).map { matchFromDB(home = "XX$it", away = "XY$it", homeQuota = 1f, drawQuota = 2f, awayQuota = 3f).copy(code = "$it") }
            val externalMatch = matchFromAPI(home = "XX1", away = "XY1", status = MatchStatus.IN_PROGRESS, homeOdds = 22f, drawOdds = 11f, awayOdds = 33f)

            val match = externalMatch.toMatchUpdated(dbMatches)
            assertNotNull(match); match!!
            assertEquals(1f, match.homeQuota)
            assertEquals(2f, match.drawQuota)
            assertEquals(3f, match.awayQuota)
        }

        @Test
        fun `toMatchUpdated returns the proper match updated when it is in-progress`() {
            val dbMatches = (0..9).map { matchFromDB(home = "XX$it", away = "XY$it").copy(code = "$it") }
            val externalMatch = matchFromAPI(home = "XX1", away = "XY1", status = MatchStatus.IN_PROGRESS, homeGoals = 3, awayGoals = 2, substatus = "28' PT")

            val match = externalMatch.toMatchUpdated(dbMatches)
            assertNotNull(match); match!!
            assertEquals(3, match.homeGoals)
            assertEquals(2, match.awayGoals)
            assertNull(match.homePenalties)
            assertNull(match.awayPenalties)
            assertEquals(MatchStatus.IN_PROGRESS, match.status)
            assertEquals("28' PT", match.substatus)
        }

        @Test
        fun `toMatchUpdated returns the proper match updated when it is finished`() {
            val dbMatches = (0..9).map { matchFromDB(home = "XX$it", away = "XY$it").copy(code = "$it") }
            val externalMatch = matchFromAPI(home = "XX1", away = "XY1", status = MatchStatus.FINISHED, homeGoals = 2, awayGoals = 1, substatus = "FIN")

            val match = externalMatch.toMatchUpdated(dbMatches)
            assertNotNull(match); match!!
            assertEquals(2, match.homeGoals)
            assertEquals(1, match.awayGoals)
            assertNull(match.homePenalties)
            assertNull(match.awayPenalties)
            assertEquals(MatchStatus.FINISHED, match.status)
            assertEquals(MatchSubstatus.FINISHED.label, match.substatus)
        }
    }

    @Nested
    inner class ToQuotasUpdatedTests {

        @Test
        fun `toQuotasUpdated returns null when the external match is not found between the stored ones`() {
            val dbMatches = (0..9).map { matchFromDB(home = "XX$it", away = "XY$it").copy(code = "$it") }
            val externalMatch = matchFromAPI(home = "XA1", away = "XB1", status = MatchStatus.IN_PROGRESS, homeOdds = 10f, drawOdds = 10f, awayOdds = 10f)
            val match = externalMatch.toQuotasUpdated(dbMatches)
            assertNull(match)
        }

        @Test
        fun `toQuotasUpdated returns null when the stored match has already started`() {
            val dbMatches = (0..9).map { matchFromDB(home = "XX$it", away = "XY$it").copy(code = "$it", status = MatchStatus.IN_PROGRESS) }
            val externalMatch = matchFromAPI(home = "XX1", away = "XY1", status = MatchStatus.IN_PROGRESS, homeGoals = 0, awayGoals = 0, substatus = "25' PT")
            val match = externalMatch.toQuotasUpdated(dbMatches)
            assertNull(match)
        }

        @Test
        fun `toQuotasUpdated returns null when the stored match has already finished`() {
            val dbMatches = (0..9).map { matchFromDB(home = "XX$it", away = "XY$it").copy(code = "$it", status = MatchStatus.FINISHED) }
            val externalMatch = matchFromAPI(home = "XX1", away = "XY1", status = MatchStatus.FINISHED, homeGoals = 2, awayGoals = 0, substatus = "FIN")
            val match = externalMatch.toQuotasUpdated(dbMatches)
            assertNull(match)
        }

        @Test
        fun `toQuotasUpdated returns null when the match is already locked`() {
            val dbMatches = (0..9).map { matchFromDB(home = "XX$it", away = "XY$it").copy(code = "$it", startedAt = ZonedDateTime.now()) }
            val externalMatch = matchFromAPI(home = "XX1", away = "XY1", status = MatchStatus.NOT_STARTED)
            val match = externalMatch.toQuotasUpdated(dbMatches)
            assertNull(match)
        }

        @Test
        fun `toQuotasUpdated properly updates the quotas when the match has not started and it is not locked`() {
            val dbMatches =
                (0..9).map { matchFromDB(home = "XX$it", away = "XY$it").copy(code = "$it", startedAt = ZonedDateTime.now().plusDays(1L)) }
            val externalMatch = matchFromAPI(home = "XX1", away = "XY1", status = MatchStatus.NOT_STARTED, homeOdds = 2.34F, drawOdds = 1.91F, awayOdds = 1.13F)
            val match = externalMatch.toQuotasUpdated(dbMatches)
            assertNotNull(match); match!!
            assertEquals(externalMatch.homeOdds!!.oddsToQuota(), match.homeQuota)
            assertEquals(externalMatch.drawOdds!!.oddsToQuota(), match.drawQuota)
            assertEquals(externalMatch.awayOdds!!.oddsToQuota(), match.awayQuota)
        }
    }
}
