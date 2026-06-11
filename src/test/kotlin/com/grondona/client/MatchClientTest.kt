package com.grondona.client

import com.grondona.model.MatchGroup
import com.grondona.model.MatchStage
import com.grondona.model.MatchStatus
import com.grondona.model.MatchSubstatus
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.ZonedDateTime

class MatchClientTest {

    @Nested
    inner class MocknaldoTest {

        private val started = ZonedDateTime.now().minusHours(12)
        private val finished = ZonedDateTime.now().minusHours(1)

        private fun matchFromAPI(
            code: String = "XXX", home: String = "T1", away: String = "T2", homeGoals: Int = 0, awayGoals: Int = 0,
            stage: String = MocknaldoMatchClient.Response.Match.Stage.GS.name, group: String? = MocknaldoMatchClient.Response.Match.Group.J.name,
            status: String = MocknaldoMatchClient.Response.Match.Status.TO_START.name, minutes: Int = 0, half: Int = 0,
            homePenalties: Int? = null, awayPenalties: Int? = null, startedAt: ZonedDateTime = started, endedAt: ZonedDateTime? = null,
        ) = MocknaldoMatchClient.Response.Match(
            code = code, home = home, away = away, stage = stage, group = group, homeGoals = homeGoals, awayGoals = awayGoals,
            status = status, minutes = minutes, half = half, homePenalties = homePenalties, awayPenalties = awayPenalties,
            startedAt = startedAt, endedAt = endedAt,
        )

        @Test
        fun `toExternalMatch properly maps a non-started match`() {
            val apiMatch = matchFromAPI(status = "TO_START")
            val resultMatch = apiMatch.toExternalMatch()!!

            assertEquals("T1", resultMatch.home)
            assertEquals("T2", resultMatch.away)
            assertEquals(MatchStage.GROUP_STAGE, resultMatch.stage)
            assertEquals(MatchGroup.GROUP_J, resultMatch.group)
            assertEquals(MatchStatus.NOT_STARTED, resultMatch.status)
            assertNull(resultMatch.substatus)
            assertEquals(0, resultMatch.homeGoals)
            assertEquals(0, resultMatch.awayGoals)
            assertNull(resultMatch.homePenalties)
            assertNull(resultMatch.awayPenalties)
            assertEquals(started, resultMatch.startedAt)
            assertNull(resultMatch.finishedAt)
        }

        @Test
        fun `toExternalMatch properly maps a match during the first half`() {
            val apiMatch = matchFromAPI(status = "IN_PLAY", homeGoals = 1, awayGoals = 0, half = 1, minutes = 15)
            val resultMatch = apiMatch.toExternalMatch()!!

            assertEquals("T1", resultMatch.home)
            assertEquals("T2", resultMatch.away)
            assertEquals(MatchStage.GROUP_STAGE, resultMatch.stage)
            assertEquals(MatchGroup.GROUP_J, resultMatch.group)
            assertEquals(MatchStatus.IN_PROGRESS, resultMatch.status)
            assertEquals("15' PT", resultMatch.substatus)
            assertEquals(1, resultMatch.homeGoals)
            assertEquals(0, resultMatch.awayGoals)
            assertNull(resultMatch.homePenalties)
            assertNull(resultMatch.awayPenalties)
            assertEquals(started, resultMatch.startedAt)
            assertNull(resultMatch.finishedAt)
        }

        @Test
        fun `toExternalMatch properly maps a match during the first half stoppage`() {
            val apiMatch = matchFromAPI(status = "IN_PLAY", homeGoals = 1, awayGoals = 1, half = 1, minutes = 48)
            val resultMatch = apiMatch.toExternalMatch()!!

            assertEquals("T1", resultMatch.home)
            assertEquals("T2", resultMatch.away)
            assertEquals(MatchStage.GROUP_STAGE, resultMatch.stage)
            assertEquals(MatchGroup.GROUP_J, resultMatch.group)
            assertEquals(MatchStatus.IN_PROGRESS, resultMatch.status)
            assertEquals("45+3' PT", resultMatch.substatus)
            assertEquals(1, resultMatch.homeGoals)
            assertEquals(1, resultMatch.awayGoals)
            assertNull(resultMatch.homePenalties)
            assertNull(resultMatch.awayPenalties)
            assertEquals(started, resultMatch.startedAt)
            assertNull(resultMatch.finishedAt)
        }

        @Test
        fun `toExternalMatch properly maps a match during the half time`() {
            val apiMatch = matchFromAPI(status = "HALF_TIME", homeGoals = 1, awayGoals = 1, half = 1, minutes = 48)
            val resultMatch = apiMatch.toExternalMatch()!!

            assertEquals("T1", resultMatch.home)
            assertEquals("T2", resultMatch.away)
            assertEquals(MatchStage.GROUP_STAGE, resultMatch.stage)
            assertEquals(MatchGroup.GROUP_J, resultMatch.group)
            assertEquals(MatchStatus.IN_PROGRESS, resultMatch.status)
            assertEquals(MatchSubstatus.HALFTIME.label, resultMatch.substatus)
            assertEquals(1, resultMatch.homeGoals)
            assertEquals(1, resultMatch.awayGoals)
            assertNull(resultMatch.homePenalties)
            assertNull(resultMatch.awayPenalties)
            assertEquals(started, resultMatch.startedAt)
            assertNull(resultMatch.finishedAt)
        }

        @Test
        fun `toExternalMatch properly maps a match during the second half`() {
            val apiMatch = matchFromAPI(status = "IN_PLAY", homeGoals = 2, awayGoals = 1, half = 2, minutes = 57)
            val resultMatch = apiMatch.toExternalMatch()!!

            assertEquals("T1", resultMatch.home)
            assertEquals("T2", resultMatch.away)
            assertEquals(MatchStage.GROUP_STAGE, resultMatch.stage)
            assertEquals(MatchGroup.GROUP_J, resultMatch.group)
            assertEquals(MatchStatus.IN_PROGRESS, resultMatch.status)
            assertEquals("12' ST", resultMatch.substatus)
            assertEquals(2, resultMatch.homeGoals)
            assertEquals(1, resultMatch.awayGoals)
            assertNull(resultMatch.homePenalties)
            assertNull(resultMatch.awayPenalties)
            assertEquals(started, resultMatch.startedAt)
            assertNull(resultMatch.finishedAt)
        }

        @Test
        fun `toExternalMatch properly maps a match during the second half stoppage`() {
            val apiMatch = matchFromAPI(status = "IN_PLAY", homeGoals = 2, awayGoals = 2, half = 2, minutes = 91)
            val resultMatch = apiMatch.toExternalMatch()!!

            assertEquals("T1", resultMatch.home)
            assertEquals("T2", resultMatch.away)
            assertEquals(MatchStage.GROUP_STAGE, resultMatch.stage)
            assertEquals(MatchGroup.GROUP_J, resultMatch.group)
            assertEquals(MatchStatus.IN_PROGRESS, resultMatch.status)
            assertEquals("45+1' ST", resultMatch.substatus)
            assertEquals(2, resultMatch.homeGoals)
            assertEquals(2, resultMatch.awayGoals)
            assertNull(resultMatch.homePenalties)
            assertNull(resultMatch.awayPenalties)
            assertEquals(started, resultMatch.startedAt)
            assertNull(resultMatch.finishedAt)
        }

        @Test
        fun `toExternalMatch properly maps a match finished after regular time`() {
            val apiMatch = matchFromAPI(status = "COMPLETED", homeGoals = 2, awayGoals = 2, half = 2, minutes = 91, endedAt = finished)
            val resultMatch = apiMatch.toExternalMatch()!!

            assertEquals("T1", resultMatch.home)
            assertEquals("T2", resultMatch.away)
            assertEquals(MatchStage.GROUP_STAGE, resultMatch.stage)
            assertEquals(MatchGroup.GROUP_J, resultMatch.group)
            assertEquals(MatchStatus.FINISHED, resultMatch.status)
            assertEquals(MatchSubstatus.FINISHED.label, resultMatch.substatus)
            assertEquals(2, resultMatch.homeGoals)
            assertEquals(2, resultMatch.awayGoals)
            assertNull(resultMatch.homePenalties)
            assertNull(resultMatch.awayPenalties)
            assertEquals(started, resultMatch.startedAt)
            assertEquals(finished, resultMatch.finishedAt)
        }

        @Test
        fun `toExternalMatch properly maps a match waiting for extra time`() {
            val apiMatch = matchFromAPI(status = "HALF_TIME", homeGoals = 2, awayGoals = 2, half = 2, minutes = 91)
            val resultMatch = apiMatch.toExternalMatch()!!

            assertEquals("T1", resultMatch.home)
            assertEquals("T2", resultMatch.away)
            assertEquals(MatchStage.GROUP_STAGE, resultMatch.stage)
            assertEquals(MatchGroup.GROUP_J, resultMatch.group)
            assertEquals(MatchStatus.IN_PROGRESS, resultMatch.status)
            assertEquals(MatchSubstatus.HALFTIME.label, resultMatch.substatus)
            assertEquals(2, resultMatch.homeGoals)
            assertEquals(2, resultMatch.awayGoals)
            assertNull(resultMatch.homePenalties)
            assertNull(resultMatch.awayPenalties)
            assertEquals(started, resultMatch.startedAt)
            assertNull(resultMatch.finishedAt)
        }

        @Test
        fun `toExternalMatch properly maps a match during the first extra time`() {
            val apiMatch = matchFromAPI(status = "IN_PLAY", homeGoals = 2, awayGoals = 2, half = 3, minutes = 99)
            val resultMatch = apiMatch.toExternalMatch()!!

            assertEquals("T1", resultMatch.home)
            assertEquals("T2", resultMatch.away)
            assertEquals(MatchStage.GROUP_STAGE, resultMatch.stage)
            assertEquals(MatchGroup.GROUP_J, resultMatch.group)
            assertEquals(MatchStatus.IN_PROGRESS, resultMatch.status)
            assertEquals("9' PTE", resultMatch.substatus)
            assertEquals(2, resultMatch.homeGoals)
            assertEquals(2, resultMatch.awayGoals)
            assertNull(resultMatch.homePenalties)
            assertNull(resultMatch.awayPenalties)
            assertEquals(started, resultMatch.startedAt)
            assertNull(resultMatch.finishedAt)
        }

        @Test
        fun `toExternalMatch properly maps a match during the first extra time stoppage`() {
            val apiMatch = matchFromAPI(status = "IN_PLAY", homeGoals = 3, awayGoals = 2, half = 3, minutes = 106)
            val resultMatch = apiMatch.toExternalMatch()!!

            assertEquals("T1", resultMatch.home)
            assertEquals("T2", resultMatch.away)
            assertEquals(MatchStage.GROUP_STAGE, resultMatch.stage)
            assertEquals(MatchGroup.GROUP_J, resultMatch.group)
            assertEquals(MatchStatus.IN_PROGRESS, resultMatch.status)
            assertEquals("15+1' PTE", resultMatch.substatus)
            assertEquals(3, resultMatch.homeGoals)
            assertEquals(2, resultMatch.awayGoals)
            assertNull(resultMatch.homePenalties)
            assertNull(resultMatch.awayPenalties)
            assertEquals(started, resultMatch.startedAt)
            assertNull(resultMatch.finishedAt)
        }

        @Test
        fun `toExternalMatch properly maps a match during the extra time interval`() {
            val apiMatch = matchFromAPI(status = "HALF_TIME", homeGoals = 3, awayGoals = 2, half = 3, minutes = 106)
            val resultMatch = apiMatch.toExternalMatch()!!

            assertEquals("T1", resultMatch.home)
            assertEquals("T2", resultMatch.away)
            assertEquals(MatchStage.GROUP_STAGE, resultMatch.stage)
            assertEquals(MatchGroup.GROUP_J, resultMatch.group)
            assertEquals(MatchStatus.IN_PROGRESS, resultMatch.status)
            assertEquals(MatchSubstatus.HALFTIME.label, resultMatch.substatus)
            assertEquals(3, resultMatch.homeGoals)
            assertEquals(2, resultMatch.awayGoals)
            assertNull(resultMatch.homePenalties)
            assertNull(resultMatch.awayPenalties)
            assertEquals(started, resultMatch.startedAt)
            assertNull(resultMatch.finishedAt)
        }

        @Test
        fun `toExternalMatch properly maps a match during the second extra time`() {
            val apiMatch = matchFromAPI(status = "IN_PLAY", homeGoals = 3, awayGoals = 2, half = 4, minutes = 106)
            val resultMatch = apiMatch.toExternalMatch()!!

            assertEquals("T1", resultMatch.home)
            assertEquals("T2", resultMatch.away)
            assertEquals(MatchStage.GROUP_STAGE, resultMatch.stage)
            assertEquals(MatchGroup.GROUP_J, resultMatch.group)
            assertEquals(MatchStatus.IN_PROGRESS, resultMatch.status)
            assertEquals("1' STE", resultMatch.substatus)
            assertEquals(3, resultMatch.homeGoals)
            assertEquals(2, resultMatch.awayGoals)
            assertNull(resultMatch.homePenalties)
            assertNull(resultMatch.awayPenalties)
            assertEquals(started, resultMatch.startedAt)
            assertNull(resultMatch.finishedAt)
        }

        @Test
        fun `toExternalMatch properly maps a match during the second extra time stoppage`() {
            val apiMatch = matchFromAPI(status = "IN_PLAY", homeGoals = 3, awayGoals = 2, half = 4, minutes = 122)
            val resultMatch = apiMatch.toExternalMatch()!!

            assertEquals("T1", resultMatch.home)
            assertEquals("T2", resultMatch.away)
            assertEquals(MatchStage.GROUP_STAGE, resultMatch.stage)
            assertEquals(MatchGroup.GROUP_J, resultMatch.group)
            assertEquals(MatchStatus.IN_PROGRESS, resultMatch.status)
            assertEquals("15+2' STE", resultMatch.substatus)
            assertEquals(3, resultMatch.homeGoals)
            assertEquals(2, resultMatch.awayGoals)
            assertNull(resultMatch.homePenalties)
            assertNull(resultMatch.awayPenalties)
            assertEquals(started, resultMatch.startedAt)
            assertNull(resultMatch.finishedAt)
        }

        @Test
        fun `toExternalMatch properly maps a match finished after extra time`() {
            val apiMatch = matchFromAPI(status = "COMPLETED", homeGoals = 3, awayGoals = 2, half = 4, minutes = 122, endedAt = finished)
            val resultMatch = apiMatch.toExternalMatch()!!

            assertEquals("T1", resultMatch.home)
            assertEquals("T2", resultMatch.away)
            assertEquals(MatchStage.GROUP_STAGE, resultMatch.stage)
            assertEquals(MatchGroup.GROUP_J, resultMatch.group)
            assertEquals(MatchStatus.FINISHED, resultMatch.status)
            assertEquals("FIN", resultMatch.substatus)
            assertEquals(3, resultMatch.homeGoals)
            assertEquals(2, resultMatch.awayGoals)
            assertNull(resultMatch.homePenalties)
            assertNull(resultMatch.awayPenalties)
            assertEquals(started, resultMatch.startedAt)
            assertEquals(finished, resultMatch.finishedAt)
        }

        @Test
        fun `toExternalMatch properly maps a match in penalties`() {
            val apiMatch = matchFromAPI(status = "PENALTIES", homeGoals = 3, awayGoals = 3, homePenalties = 0, awayPenalties = 0, half = 4, minutes = 122)
            val resultMatch = apiMatch.toExternalMatch()!!

            assertEquals("T1", resultMatch.home)
            assertEquals("T2", resultMatch.away)
            assertEquals(MatchStage.GROUP_STAGE, resultMatch.stage)
            assertEquals(MatchGroup.GROUP_J, resultMatch.group)
            assertEquals(MatchStatus.IN_PROGRESS, resultMatch.status)
            assertEquals(MatchSubstatus.PENALTIES.label, resultMatch.substatus)
            assertEquals(3, resultMatch.homeGoals)
            assertEquals(3, resultMatch.awayGoals)
            assertEquals(0, resultMatch.homePenalties)
            assertEquals(0, resultMatch.awayPenalties)
            assertEquals(started, resultMatch.startedAt)
            assertNull(resultMatch.finishedAt)
        }

        @Test
        fun `toExternalMatch properly maps a match finished after penalties`() {
            val apiMatch = matchFromAPI(status = "COMPLETED", half = 4, minutes = 122, endedAt = finished,
                homeGoals = 3, awayGoals = 3, homePenalties = 5, awayPenalties = 4)
            val resultMatch = apiMatch.toExternalMatch()!!

            assertEquals("T1", resultMatch.home)
            assertEquals("T2", resultMatch.away)
            assertEquals(MatchStage.GROUP_STAGE, resultMatch.stage)
            assertEquals(MatchGroup.GROUP_J, resultMatch.group)
            assertEquals(MatchStatus.FINISHED, resultMatch.status)
            assertEquals(MatchSubstatus.FINISHED.label, resultMatch.substatus)
            assertEquals(3, resultMatch.homeGoals)
            assertEquals(3, resultMatch.awayGoals)
            assertEquals(5, resultMatch.homePenalties)
            assertEquals(4, resultMatch.awayPenalties)
            assertEquals(started, resultMatch.startedAt)
            assertEquals(finished, resultMatch.finishedAt)
        }
    }

    @Nested
    inner class FootballDataTest {

        private val started = ZonedDateTime.now().minusMinutes(20)
        private val finished = ZonedDateTime.now().minusHours(1)

        private fun matchFromAPI(
            home: String? = "T1", away: String? = "T2", startedAt: ZonedDateTime = started, endedAt: ZonedDateTime? = null,
            stage: String = FootballDataMatchClient.Response.Match.Stage.GROUP_STAGE.name, group: String? = FootballDataMatchClient.Response.Match.Group.GROUP_J.name,
            regularTimeHomeGoals: Int? = null, regularTimeAwayGoals: Int? = null, extraTimeHomeGoals: Int? = null, extraTimeAwayGoals: Int? = null,
            status: String = FootballDataMatchClient.Response.Match.Status.TIMED.name, minutes: Int = 0, injuryTime: Int? = null,
            homePenalties: Int? = null, awayPenalties: Int? = null,
        ) = FootballDataMatchClient.Response.Match(
            utcDate = startedAt, lastUpdated = endedAt ?: ZonedDateTime.now(), status = status, stage = stage, group = group,
            homeTeam = FootballDataMatchClient.Response.Match.Team(tla = home), awayTeam = FootballDataMatchClient.Response.Match.Team(tla = away),
            score = FootballDataMatchClient.Response.Match.Score(
                duration = when {
                    homePenalties != null -> FootballDataMatchClient.Response.Match.ScoreDuration.PENALTY_SHOOTOUT
                    extraTimeHomeGoals != null -> FootballDataMatchClient.Response.Match.ScoreDuration.EXTRA_TIME
                    else -> FootballDataMatchClient.Response.Match.ScoreDuration.REGULAR
                }.name,
                regularTime = FootballDataMatchClient.Response.Match.InnerScore(home = regularTimeHomeGoals, away = regularTimeAwayGoals),
                extraTime = extraTimeHomeGoals?.let { FootballDataMatchClient.Response.Match.InnerScore(home = extraTimeHomeGoals, away = extraTimeAwayGoals) },
                penalties = homePenalties?.let { FootballDataMatchClient.Response.Match.InnerScore(home = homePenalties, away = awayPenalties) },
                fullTime = FootballDataMatchClient.Response.Match.InnerScore(
                    home = (regularTimeHomeGoals ?: 0) + (extraTimeHomeGoals ?: 0) + (homePenalties ?: 0),
                    away = (regularTimeAwayGoals ?: 0) + (extraTimeAwayGoals ?: 0) + (awayPenalties ?: 0),
                )
            )
        )

        @Test
        fun `toExternalMatch properly returns no map when it has no teams assigned`() {
            val apiMatch = matchFromAPI(home = null, away = null)
            val resultMatch = apiMatch.toExternalMatch()
            assertNull(resultMatch)
        }

        @Test
        fun `toExternalMatch properly maps a non-started match`() {
            val newStarted = ZonedDateTime.now().plusMinutes(20)
            val apiMatch = matchFromAPI(status = "TIMED", startedAt = newStarted)
            val resultMatch = apiMatch.toExternalMatch()!!

            assertEquals("T1", resultMatch.home)
            assertEquals("T2", resultMatch.away)
            assertEquals(MatchStatus.NOT_STARTED, resultMatch.status)
            assertNull(resultMatch.substatus)
            assertEquals(0, resultMatch.homeGoals)
            assertEquals(0, resultMatch.awayGoals)
            assertNull(resultMatch.homePenalties)
            assertNull(resultMatch.awayPenalties)
            assertEquals(newStarted, resultMatch.startedAt)
            assertNull(resultMatch.finishedAt)
        }

        @Test
        fun `toExternalMatch properly maps a coming-soon match`() {
            val newStarted = ZonedDateTime.now().plusMinutes(5)
            val apiMatch = matchFromAPI(status = "TIMED", startedAt = newStarted)
            val resultMatch = apiMatch.toExternalMatch()!!

            assertEquals("T1", resultMatch.home)
            assertEquals("T2", resultMatch.away)
            assertEquals(MatchStatus.NOT_STARTED, resultMatch.status)
            assertEquals(MatchSubstatus.NEXT.label, resultMatch.substatus)
            assertEquals(0, resultMatch.homeGoals)
            assertEquals(0, resultMatch.awayGoals)
            assertNull(resultMatch.homePenalties)
            assertNull(resultMatch.awayPenalties)
            assertEquals(newStarted, resultMatch.startedAt)
            assertNull(resultMatch.finishedAt)
        }

        @Test
        fun `toExternalMatch properly maps a match during the first half`() {
            val apiMatch = matchFromAPI(status = "IN_PLAY", regularTimeHomeGoals = 1, regularTimeAwayGoals = 0, minutes = 15)
            val resultMatch = apiMatch.toExternalMatch()!!

            assertEquals("T1", resultMatch.home)
            assertEquals("T2", resultMatch.away)
            assertEquals(MatchStatus.IN_PROGRESS, resultMatch.status)
            assertEquals(MatchSubstatus.LIVE.label, resultMatch.substatus)
            assertEquals(1, resultMatch.homeGoals)
            assertEquals(0, resultMatch.awayGoals)
            assertNull(resultMatch.homePenalties)
            assertNull(resultMatch.awayPenalties)
            assertEquals(started, resultMatch.startedAt)
            assertNull(resultMatch.finishedAt)
        }

        @Test
        fun `toExternalMatch properly maps a match during the first half stoppage`() {
            val apiMatch = matchFromAPI(status = "IN_PLAY", regularTimeHomeGoals = 1, regularTimeAwayGoals = 1, minutes = 45, injuryTime = 3)
            val resultMatch = apiMatch.toExternalMatch()!!

            assertEquals("T1", resultMatch.home)
            assertEquals("T2", resultMatch.away)
            assertEquals(MatchStatus.IN_PROGRESS, resultMatch.status)
            assertEquals(MatchSubstatus.LIVE.label, resultMatch.substatus)
            assertEquals(1, resultMatch.homeGoals)
            assertEquals(1, resultMatch.awayGoals)
            assertNull(resultMatch.homePenalties)
            assertNull(resultMatch.awayPenalties)
            assertEquals(started, resultMatch.startedAt)
            assertNull(resultMatch.finishedAt)
        }

        @Test
        fun `toExternalMatch properly maps a match during the half time`() {
            val apiMatch = matchFromAPI(status = "PAUSED", regularTimeHomeGoals = 1, regularTimeAwayGoals = 1, minutes = 45, injuryTime = 3)
            val resultMatch = apiMatch.toExternalMatch()!!

            assertEquals("T1", resultMatch.home)
            assertEquals("T2", resultMatch.away)
            assertEquals(MatchStatus.IN_PROGRESS, resultMatch.status)
            assertEquals(MatchSubstatus.HALFTIME.label, resultMatch.substatus)
            assertEquals(1, resultMatch.homeGoals)
            assertEquals(1, resultMatch.awayGoals)
            assertNull(resultMatch.homePenalties)
            assertNull(resultMatch.awayPenalties)
            assertEquals(started, resultMatch.startedAt)
            assertNull(resultMatch.finishedAt)
        }

        @Test
        fun `toExternalMatch properly maps a match during the second half`() {
            val apiMatch = matchFromAPI(status = "IN_PLAY", regularTimeHomeGoals = 2, regularTimeAwayGoals = 1, minutes = 57)
            val resultMatch = apiMatch.toExternalMatch()!!

            assertEquals("T1", resultMatch.home)
            assertEquals("T2", resultMatch.away)
            assertEquals(MatchStatus.IN_PROGRESS, resultMatch.status)
            assertEquals(MatchSubstatus.LIVE.label, resultMatch.substatus)
            assertEquals(2, resultMatch.homeGoals)
            assertEquals(1, resultMatch.awayGoals)
            assertNull(resultMatch.homePenalties)
            assertNull(resultMatch.awayPenalties)
            assertEquals(started, resultMatch.startedAt)
            assertNull(resultMatch.finishedAt)
        }

        @Test
        fun `toExternalMatch properly maps a match during the second half stoppage`() {
            val apiMatch = matchFromAPI(status = "IN_PLAY", regularTimeHomeGoals = 2, regularTimeAwayGoals = 2, minutes = 90, injuryTime = 1)
            val resultMatch = apiMatch.toExternalMatch()!!

            assertEquals("T1", resultMatch.home)
            assertEquals("T2", resultMatch.away)
            assertEquals(MatchStatus.IN_PROGRESS, resultMatch.status)
            assertEquals(MatchSubstatus.LIVE.label, resultMatch.substatus)
            assertEquals(2, resultMatch.homeGoals)
            assertEquals(2, resultMatch.awayGoals)
            assertNull(resultMatch.homePenalties)
            assertNull(resultMatch.awayPenalties)
            assertEquals(started, resultMatch.startedAt)
            assertNull(resultMatch.finishedAt)
        }

        @Test
        fun `toExternalMatch properly maps a match finished after regular time`() {
            val apiMatch = matchFromAPI(status = "FINISHED", minutes = 90, injuryTime = 1, endedAt = finished,
                regularTimeHomeGoals = 2, regularTimeAwayGoals = 2)
            val resultMatch = apiMatch.toExternalMatch()!!

            assertEquals("T1", resultMatch.home)
            assertEquals("T2", resultMatch.away)
            assertEquals(MatchStatus.FINISHED, resultMatch.status)
            assertEquals(MatchSubstatus.FINISHED.label, resultMatch.substatus)
            assertEquals(2, resultMatch.homeGoals)
            assertEquals(2, resultMatch.awayGoals)
            assertNull(resultMatch.homePenalties)
            assertNull(resultMatch.awayPenalties)
            assertEquals(started, resultMatch.startedAt)
            assertEquals(finished, resultMatch.finishedAt)
        }

        @Test
        fun `toExternalMatch properly maps a match waiting for extra time`() {
            val apiMatch = matchFromAPI(status = "PAUSED", regularTimeHomeGoals = 2, regularTimeAwayGoals = 2, minutes = 90, injuryTime = 1)
            val resultMatch = apiMatch.toExternalMatch()!!

            assertEquals("T1", resultMatch.home)
            assertEquals("T2", resultMatch.away)
            assertEquals(MatchStatus.IN_PROGRESS, resultMatch.status)
            assertEquals(MatchSubstatus.HALFTIME.label, resultMatch.substatus)
            assertEquals(2, resultMatch.homeGoals)
            assertEquals(2, resultMatch.awayGoals)
            assertNull(resultMatch.homePenalties)
            assertNull(resultMatch.awayPenalties)
            assertEquals(started, resultMatch.startedAt)
            assertNull(resultMatch.finishedAt)
        }

        @Test
        fun `toExternalMatch properly maps a match during the first extra time`() {
            val apiMatch = matchFromAPI(status = "IN_PLAY", minutes = 99,
                regularTimeHomeGoals = 2, regularTimeAwayGoals = 2, extraTimeHomeGoals = 0, extraTimeAwayGoals = 0)
            val resultMatch = apiMatch.toExternalMatch()!!

            assertEquals("T1", resultMatch.home)
            assertEquals("T2", resultMatch.away)
            assertEquals(MatchStatus.IN_PROGRESS, resultMatch.status)
            assertEquals(MatchSubstatus.LIVE.label, resultMatch.substatus)
            assertEquals(2, resultMatch.homeGoals)
            assertEquals(2, resultMatch.awayGoals)
            assertNull(resultMatch.homePenalties)
            assertNull(resultMatch.awayPenalties)
            assertEquals(started, resultMatch.startedAt)
            assertNull(resultMatch.finishedAt)
        }

        @Test
        fun `toExternalMatch properly maps a match during the first extra time stoppage`() {
            val apiMatch = matchFromAPI(status = "IN_PLAY", minutes = 105, injuryTime = 1,
                regularTimeHomeGoals = 2, regularTimeAwayGoals = 2, extraTimeHomeGoals = 1, extraTimeAwayGoals = 0)
            val resultMatch = apiMatch.toExternalMatch()!!

            assertEquals("T1", resultMatch.home)
            assertEquals("T2", resultMatch.away)
            assertEquals(MatchStatus.IN_PROGRESS, resultMatch.status)
            assertEquals(MatchSubstatus.LIVE.label, resultMatch.substatus)
            assertEquals(3, resultMatch.homeGoals)
            assertEquals(2, resultMatch.awayGoals)
            assertNull(resultMatch.homePenalties)
            assertNull(resultMatch.awayPenalties)
            assertEquals(started, resultMatch.startedAt)
            assertNull(resultMatch.finishedAt)
        }

        @Test
        fun `toExternalMatch properly maps a match during the extra time interval`() {
            val apiMatch = matchFromAPI(status = "PAUSED", minutes = 106,
                regularTimeHomeGoals = 2, regularTimeAwayGoals = 2, extraTimeHomeGoals = 1, extraTimeAwayGoals = 0)
            val resultMatch = apiMatch.toExternalMatch()!!

            assertEquals("T1", resultMatch.home)
            assertEquals("T2", resultMatch.away)
            assertEquals(MatchStatus.IN_PROGRESS, resultMatch.status)
            assertEquals(MatchSubstatus.HALFTIME.label, resultMatch.substatus)
            assertEquals(3, resultMatch.homeGoals)
            assertEquals(2, resultMatch.awayGoals)
            assertNull(resultMatch.homePenalties)
            assertNull(resultMatch.awayPenalties)
            assertEquals(started, resultMatch.startedAt)
            assertNull(resultMatch.finishedAt)
        }

        @Test
        fun `toExternalMatch properly maps a match during the second extra time`() {
            val apiMatch = matchFromAPI(status = "IN_PLAY", minutes = 106,
                regularTimeHomeGoals = 2, regularTimeAwayGoals = 2, extraTimeHomeGoals = 1, extraTimeAwayGoals = 0)
            val resultMatch = apiMatch.toExternalMatch()!!

            assertEquals("T1", resultMatch.home)
            assertEquals("T2", resultMatch.away)
            assertEquals(MatchStatus.IN_PROGRESS, resultMatch.status)
            assertEquals(MatchSubstatus.LIVE.label, resultMatch.substatus)
            assertEquals(3, resultMatch.homeGoals)
            assertEquals(2, resultMatch.awayGoals)
            assertNull(resultMatch.homePenalties)
            assertNull(resultMatch.awayPenalties)
            assertEquals(started, resultMatch.startedAt)
            assertNull(resultMatch.finishedAt)
        }

        @Test
        fun `toExternalMatch properly maps a match during the second extra time stoppage`() {
            val apiMatch = matchFromAPI(status = "IN_PLAY", minutes = 120, injuryTime = 2,
                regularTimeHomeGoals = 2, regularTimeAwayGoals = 2, extraTimeHomeGoals = 1, extraTimeAwayGoals = 0)
            val resultMatch = apiMatch.toExternalMatch()!!

            assertEquals("T1", resultMatch.home)
            assertEquals("T2", resultMatch.away)
            assertEquals(MatchStatus.IN_PROGRESS, resultMatch.status)
            assertEquals(MatchSubstatus.LIVE.label, resultMatch.substatus)
            assertEquals(3, resultMatch.homeGoals)
            assertEquals(2, resultMatch.awayGoals)
            assertNull(resultMatch.homePenalties)
            assertNull(resultMatch.awayPenalties)
            assertEquals(started, resultMatch.startedAt)
            assertNull(resultMatch.finishedAt)
        }

        @Test
        fun `toExternalMatch properly maps a match finished after extra time`() {
            val apiMatch = matchFromAPI(status = "FINISHED", minutes = 120, injuryTime = 2, endedAt = finished,
                regularTimeHomeGoals = 2, regularTimeAwayGoals = 2, extraTimeHomeGoals = 1, extraTimeAwayGoals = 0)
            val resultMatch = apiMatch.toExternalMatch()!!

            assertEquals("T1", resultMatch.home)
            assertEquals("T2", resultMatch.away)
            assertEquals(MatchStatus.FINISHED, resultMatch.status)
            assertEquals("FIN", resultMatch.substatus)
            assertEquals(3, resultMatch.homeGoals)
            assertEquals(2, resultMatch.awayGoals)
            assertNull(resultMatch.homePenalties)
            assertNull(resultMatch.awayPenalties)
            assertEquals(started, resultMatch.startedAt)
            assertEquals(finished, resultMatch.finishedAt)
        }

        @Test
        fun `toExternalMatch properly maps a match in penalties`() {
            val apiMatch = matchFromAPI(status = "IN_PLAY", minutes = 120, injuryTime = 2,
                regularTimeHomeGoals = 2, regularTimeAwayGoals = 2, extraTimeHomeGoals = 1, extraTimeAwayGoals = 1, homePenalties = 0, awayPenalties = 0)
            val resultMatch = apiMatch.toExternalMatch()!!

            assertEquals("T1", resultMatch.home)
            assertEquals("T2", resultMatch.away)
            assertEquals(MatchStatus.IN_PROGRESS, resultMatch.status)
            assertEquals(MatchSubstatus.PENALTIES.label, resultMatch.substatus)
            assertEquals(3, resultMatch.homeGoals)
            assertEquals(3, resultMatch.awayGoals)
            assertEquals(0, resultMatch.homePenalties)
            assertEquals(0, resultMatch.awayPenalties)
            assertEquals(started, resultMatch.startedAt)
            assertNull(resultMatch.finishedAt)
        }

        @Test
        fun `toExternalMatch properly maps a match finished after penalties`() {
            val apiMatch = matchFromAPI(status = "FINISHED", minutes = 120, injuryTime = 2, endedAt = finished,
                regularTimeHomeGoals = 2, regularTimeAwayGoals = 2, extraTimeHomeGoals = 1, extraTimeAwayGoals = 1, homePenalties = 5, awayPenalties = 4)
            val resultMatch = apiMatch.toExternalMatch()!!

            assertEquals("T1", resultMatch.home)
            assertEquals("T2", resultMatch.away)
            assertEquals(MatchStatus.FINISHED, resultMatch.status)
            assertEquals(MatchSubstatus.FINISHED.label, resultMatch.substatus)
            assertEquals(3, resultMatch.homeGoals)
            assertEquals(3, resultMatch.awayGoals)
            assertEquals(5, resultMatch.homePenalties)
            assertEquals(4, resultMatch.awayPenalties)
            assertEquals(started, resultMatch.startedAt)
            assertEquals(finished, resultMatch.finishedAt)
        }
    }

}
