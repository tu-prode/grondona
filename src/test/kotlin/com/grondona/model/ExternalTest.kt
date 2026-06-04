package com.grondona.model

import com.grondona.exception.ExternalServiceException
import com.grondona.service.engine.WorldCupEngine
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.ZonedDateTime
import java.util.*

class ExternalMatchTest {

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
        homeTeam = Team(tournament = testTournament, name = home, code = home, icon = "test", englishKey = "test-en"),
        awayTeam = Team(tournament = testTournament, name = away, code = away, icon = "test", englishKey = "test-en"),
        status = status, homeQuota = homeQuota, drawQuota = drawQuota, awayQuota = awayQuota, startedAt = startedAt,
        tournament = testTournament, code = "test", homeGoals = homeGoals, awayGoals = awayGoals,
    )

    private fun matchFromAPI(
        home: String = "XXX", away: String = "XXX", stage: MatchStage = MatchStage.GROUP_STAGE, group: MatchGroup = MatchGroup.GROUP_J,
        homeGoals: Int = 0, awayGoals: Int = 0, substatus: String? = null, status: MatchStatus = MatchStatus.NOT_STARTED,
    ) = ExternalMatch(
        home = home, away = away, homeGoals = homeGoals, awayGoals = awayGoals,
        status = status, substatus = substatus, stage = stage, group = group, startedAt = ZonedDateTime.now(),
    )

    @Nested
    inner class ToExistingMatchTests {

        @Test
        fun `toExistingMatch returns null when the external match is not found between the stored ones`() {
            val dbMatches = (0..9).map { matchFromDB(home = "XX$it", away = "XY$it").copy(code = "$it") }
            val externalMatch = matchFromAPI(home = "XA1", away = "XB1", status = MatchStatus.NOT_STARTED)
            val match = externalMatch.toExistingMatch(dbMatches)
            assertNull(match)
        }

        @Test
        fun `toExistingMatch returns the proper match updated when it is found between the stored ones (and not-finished)`() {
            val finishedAt = ZonedDateTime.now()
            val dbMatches = (0..9).map { matchFromDB(home = "XX$it", away = "XY$it").copy(code = "$it") }
            val externalMatch = matchFromAPI(home = "XX1", away = "XY1").copy(
                homeGoals = 8, awayGoals = 9, homePenalties = 10, awayPenalties = 11,
                status = MatchStatus.IN_PROGRESS, substatus = MatchSubstatus.PENALTIES.label, finishedAt = finishedAt,
            )

            val match = externalMatch.toExistingMatch(dbMatches)
            assertNotNull(match); match!!
            assertEquals(8, match.homeGoals)
            assertEquals(9, match.awayGoals)
            assertEquals(10, match.homePenalties)
            assertEquals(11, match.awayPenalties)
            assertEquals(1f, match.homeQuota)
            assertEquals(1f, match.drawQuota)
            assertEquals(1f, match.awayQuota)
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
                status = MatchStatus.FINISHED, substatus = MatchSubstatus.FINISHED.label, finishedAt = finishedAt,
            )

            val match = externalMatch.toExistingMatch(dbMatches)
            assertNotNull(match); match!!
            assertEquals(8, match.homeGoals)
            assertEquals(8, match.awayGoals)
            assertEquals(10, match.homePenalties)
            assertEquals(11, match.awayPenalties)
            assertEquals(1f, match.homeQuota)
            assertEquals(1f, match.drawQuota)
            assertEquals(1f, match.awayQuota)
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

class ExternalOddsTest {

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
        homeTeam = Team(tournament = testTournament, name = home, code = home, icon = "test", englishKey = "$home-en"),
        awayTeam = Team(tournament = testTournament, name = away, code = away, icon = "test", englishKey = "$away-en"),
        status = status, homeQuota = homeQuota, drawQuota = drawQuota, awayQuota = awayQuota, startedAt = startedAt,
        tournament = testTournament, code = "test", homeGoals = homeGoals, awayGoals = awayGoals,
    )

    private fun oddsFromAPI(
        home: String = "XXX", away: String = "XXX", startedAt: ZonedDateTime = ZonedDateTime.now(),
        homeOdds: Float = 1f, drawOdds: Float = 1f, awayOdds: Float = 1f,
    ) = ExternalOdds(
        homeKey = "$home-en", awayKey = "$away-en", startedAt = startedAt, homeOdds = homeOdds, drawOdds = drawOdds, awayOdds = awayOdds,
    )

    @Nested
    inner class ToMatchUpdatedTests {

        private val tournamentTeams = mapOf(
            "TA-en" to Team(tournament = testTournament, code = "TA", name = "TeamA", englishKey = "TA-en"),
            "TB-en" to Team(tournament = testTournament, code = "TB", name = "TeamB", englishKey = "TB-en")
        )

        @Test
        fun `toMatchUpdated returns an error when the home team is not found`() {
            val dbMatches = (0..9).map { matchFromDB(home = "XX$it", away = "XY$it").copy(code = "$it") }

            val externalOdd = oddsFromAPI(away = "TB")
            val exception = assertThrows<ExternalServiceException> {
                externalOdd.toMatchUpdated(dbMatches, tournamentTeams)
            }
            assertTrue { exception.message!!.contains("Home team not found in the DB") }
        }

        @Test
        fun `toMatchUpdated returns an error when the away team is not found`() {
            val dbMatches = (0..9).map { matchFromDB(home = "XX$it", away = "XY$it").copy(code = "$it") }

            val externalOdd = oddsFromAPI(home = "TA")
            val exception = assertThrows<ExternalServiceException> {
                externalOdd.toMatchUpdated(dbMatches, tournamentTeams)
            }
            assertTrue { exception.message!!.contains("Away team not found in the DB") }
        }

        @Test
        fun `toMatchUpdated returns null when the match was not found in the system`() {
            val dbMatches = (0..9).map { matchFromDB(home = "XX$it", away = "XY$it").copy(code = "$it") }

            val externalOdd = oddsFromAPI(home = "TA", away = "TB")
            assertNull(externalOdd.toMatchUpdated(dbMatches, tournamentTeams))
        }

        @Test
        fun `toMatchUpdated returns the proper match when it is found in the DB`() {
            val targetMatch = matchFromDB(home = "TA", away = "TB").copy(startedAt = ZonedDateTime.now().minusHours(2))
            val dbMatches = (0..9).map { matchFromDB(home = "XX$it", away = "XY$it").copy(code = "$it") } + targetMatch

            val externalOdd = oddsFromAPI(home = "TA", away = "TB").copy(startedAt = ZonedDateTime.now().minusHours(2))
            val foundMatch = externalOdd.toMatchUpdated(dbMatches, tournamentTeams)
            assertEquals(targetMatch.id, foundMatch!!.id)
        }
    }
}
