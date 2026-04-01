package com.grondona.utils

import com.grondona.model.Group
import com.grondona.model.Match
import com.grondona.model.MatchStatus
import com.grondona.model.Prediction
import com.grondona.model.PredictionStatus
import com.grondona.model.Score
import com.grondona.model.Team
import com.grondona.model.Tournament
import com.grondona.model.TournamentStatus
import com.grondona.model.User
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

class PredictionEngineTest {

    private val anyTournament = Tournament(
        id = UUID.randomUUID(), name = "T", status = TournamentStatus.NOT_STARTED,
        createdAt = LocalDateTime.now(), updatedAt = LocalDateTime.now()
    )
    private val anyTeam = Team(
        id = UUID.randomUUID(), tournament = anyTournament, name = "Team", code = "T", icon = "t.png"
    )
    private val anyUser = User(
        id = UUID.randomUUID(), fullname = "Test", username = "testuser",
        email = "test@example.com", passwordHash = "hash",
        createdAt = LocalDateTime.now(), updatedAt = LocalDateTime.now()
    )
    private val anyGroup = Group(
        id = UUID.randomUUID(), name = "Group", isPrivate = false, maxMembers = 10,
        tournament = anyTournament, createdAt = LocalDateTime.now(), updatedAt = LocalDateTime.now()
    )

    private fun finishedMatch(
        homeGoals: Int,
        awayGoals: Int,
        homeQuota: Float = 1.5f,
        awayQuota: Float = 2.0f,
        tieQuota: Float = 1.8f,
    ) = Match(
        id = UUID.randomUUID(), code = "MATCH", tournament = anyTournament,
        homeTeam = anyTeam, awayTeam = anyTeam, status = MatchStatus.FINISHED,
        homeGoals = homeGoals, awayGoals = awayGoals,
        homeQuota = homeQuota, awayQuota = awayQuota, tieQuota = tieQuota,
        startedAt = LocalDateTime.now().minusHours(2),
        finishedAt = LocalDateTime.now().minusHours(1),
    )

    private fun pendingPrediction(
        match: Match,
        homeGoals: Int,
        awayGoals: Int,
        status: PredictionStatus = PredictionStatus.PENDING,
    ) = Prediction(
        id = UUID.randomUUID(), user = anyUser, group = anyGroup,
        match = match, homeGoals = homeGoals, awayGoals = awayGoals, status = status,
    )

    private fun closedPrediction(
        match: Match,
        homeGoals: Int,
        awayGoals: Int,
    ): Prediction {
        val predictionScore = Score(homeGoals, awayGoals)
        val status = when {
            match.score() == predictionScore && predictionScore.goals() >= 5 -> PredictionStatus.BONUS
            match.score() == predictionScore -> PredictionStatus.CORRECT
            match.score()?.outcome() == predictionScore.outcome() -> PredictionStatus.PARTIAL
            else -> PredictionStatus.INCORRECT
        }

        return Prediction(
            id = UUID.randomUUID(), user = anyUser, group = anyGroup,
            match = match, homeGoals = homeGoals, awayGoals = awayGoals, status = status,
        )
    }

    @Nested
    inner class CheckTests {

        @Test
        fun `check sets CORRECT when prediction matches exact score`() {
            val match = finishedMatch(homeGoals = 2, awayGoals = 1)
            val result = PredictionEngine.check(listOf(pendingPrediction(match, 2, 1)))
            assertEquals(PredictionStatus.CORRECT, result[0].status)
        }

        @Test
        fun `check sets PARTIAL when outcome matches but score differs`() {
            val match = finishedMatch(homeGoals = 2, awayGoals = 1)
            val result = PredictionEngine.check(listOf(pendingPrediction(match, 1, 0)))
            assertEquals(PredictionStatus.PARTIAL, result[0].status)
        }

        @Test
        fun `check sets INCORRECT when predicted outcome is wrong`() {
            val match = finishedMatch(homeGoals = 2, awayGoals = 1)
            val result = PredictionEngine.check(listOf(pendingPrediction(match, 0, 1)))
            assertEquals(PredictionStatus.INCORRECT, result[0].status)
        }

        @Test
        fun `check sets CORRECT for exact tie score`() {
            val match = finishedMatch(homeGoals = 1, awayGoals = 1)
            val result = PredictionEngine.check(listOf(pendingPrediction(match, 1, 1)))
            assertEquals(PredictionStatus.CORRECT, result[0].status)
        }

        @Test
        fun `check sets BONUS for exact score with high-goal bonus`() {
            val match = finishedMatch(homeGoals = 7, awayGoals = 1)
            val result = PredictionEngine.check(listOf(pendingPrediction(match, 7, 1)))
            assertEquals(PredictionStatus.BONUS, result[0].status)
        }

        @Test
        fun `check sets PARTIAL when both predict tie but different score`() {
            val match = finishedMatch(homeGoals = 1, awayGoals = 1)
            val result = PredictionEngine.check(listOf(pendingPrediction(match, 2, 2)))
            assertEquals(PredictionStatus.PARTIAL, result[0].status)
        }

        @Test
        fun `check sets INCORRECT when predicted tie but home team wins`() {
            val match = finishedMatch(homeGoals = 1, awayGoals = 0)
            val result = PredictionEngine.check(listOf(pendingPrediction(match, 0, 0)))
            assertEquals(PredictionStatus.INCORRECT, result[0].status)
        }

        @Test
        fun `check leaves status PENDING when match has no goals recorded`() {
            val match = finishedMatch(homeGoals = 2, awayGoals = 1).copy(homeGoals = null, awayGoals = null)
            val result = PredictionEngine.check(listOf(pendingPrediction(match, 2, 1)))
            assertEquals(PredictionStatus.PENDING, result[0].status)
        }

        @Test
        fun `check does not update prediction when match is NOT_STARTED`() {
            val match = Match(
                id = UUID.randomUUID(), code = "M", tournament = anyTournament,
                homeTeam = anyTeam, awayTeam = anyTeam, status = MatchStatus.NOT_STARTED,
            )
            val result = PredictionEngine.check(listOf(pendingPrediction(match, 1, 0)))
            assertEquals(PredictionStatus.PENDING, result[0].status)
        }

        @Test
        fun `check does not update prediction when match is IN_PROGRESS`() {
            val match = Match(
                id = UUID.randomUUID(), code = "M", tournament = anyTournament,
                homeTeam = anyTeam, awayTeam = anyTeam, status = MatchStatus.IN_PROGRESS,
            )
            val result = PredictionEngine.check(listOf(pendingPrediction(match, 1, 0)))
            assertEquals(PredictionStatus.PENDING, result[0].status)
        }

        @Test
        fun `check does not modify already evaluated predictions`() {
            val match = finishedMatch(homeGoals = 2, awayGoals = 1)
            val predictions = listOf(
                pendingPrediction(match, 2, 1, PredictionStatus.CORRECT),
                pendingPrediction(match, 1, 0, PredictionStatus.PARTIAL),
                pendingPrediction(match, 0, 1, PredictionStatus.INCORRECT),
            )
            val result = PredictionEngine.check(predictions)
            assertEquals(PredictionStatus.CORRECT, result[0].status)
            assertEquals(PredictionStatus.PARTIAL, result[1].status)
            assertEquals(PredictionStatus.INCORRECT, result[2].status)
        }

        @Test
        fun `check returns empty list for empty input`() {
            assertTrue(PredictionEngine.check(emptyList()).isEmpty())
        }

        @Test
        fun `check handles a mix of pending and already-evaluated predictions`() {
            val match = finishedMatch(homeGoals = 2, awayGoals = 0)
            val predictions = listOf(
                pendingPrediction(match, 2, 0, PredictionStatus.PENDING),   // should become CORRECT
                pendingPrediction(match, 1, 0, PredictionStatus.CORRECT),   // already evaluated, unchanged
            )
            val result = PredictionEngine.check(predictions)
            assertEquals(PredictionStatus.CORRECT, result[0].status)
            assertEquals(PredictionStatus.CORRECT, result[1].status)
        }
    }

    @Nested
    inner class PointsTests {

        @Test
        fun `points returns 0 for empty list`() {
            assertEquals(0f, PredictionEngine.points(emptyList()))
        }

        @Test
        fun `points returns 0 for incorrect prediction`() {
            // home wins 2-1, homeQuota=1.5; prediction: away wins 0-1 → wrong outcome
            val match = finishedMatch(homeGoals = 2, awayGoals = 1, homeQuota = 1.5f)
            val result = PredictionEngine.points(listOf(closedPrediction(match, 0, 1)))
            // 0 (incorrect) + 0 (high-score bonus) + 0 (quota) = 0
            assertEquals(0f, result)
        }

        @Test
        fun `points adds partial bonus for correct outcome with wrong score`() {
            val match = finishedMatch(homeGoals = 2, awayGoals = 1, homeQuota = 1.5f)
            val result = PredictionEngine.points(listOf(closedPrediction(match, 1, 0)))
            // 1 (partial) + 0 (high-score bonus) + 1.5 (quota) = 2.5
            assertEquals(2.5f, result)
        }

        @Test
        fun `points adds correct bonuses for exact score`() {
            val match = finishedMatch(homeGoals = 2, awayGoals = 1, homeQuota = 1.5f)
            val result = PredictionEngine.points(listOf(closedPrediction(match, 2, 1)))
            // 3 (correct) + 0 (high-score bonus) + 1.5 (quota) = 4.5
            assertEquals(4.5f, result)
        }

        @Test
        fun `points adds high-score bonus when total goals reach exactly 5`() {
            val match = finishedMatch(homeGoals = 3, awayGoals = 2, homeQuota = 1.5f)
            val result = PredictionEngine.points(listOf(closedPrediction(match, 3, 2)))
            // 3 (correct) + 2 (high-score bonus) + 1.5 (quota) = 6.5
            assertEquals(6.5f, result)
        }

        @Test
        fun `points returns 0 for incorrect outcome with high-score bonus`() {
            val match = finishedMatch(homeGoals = 3, awayGoals = 2, homeQuota = 1.5f)
            val result = PredictionEngine.points(listOf(closedPrediction(match, 2, 3)))
            // 0 (incorrect) + 0 (high-score bonus) + 0 (quota) = 0
            assertEquals(0f, result)
        }

        @Test
        fun `points does not add high-score bonus for 4 total goals`() {
            val match = finishedMatch(homeGoals = 2, awayGoals = 2, tieQuota = 1.8f) // 4 goals
            val result = PredictionEngine.points(listOf(closedPrediction(match, 2, 2)))
            // 3 (correct) + 0 (high-score bonus) + 1.5 (quota) = 4.5
            assertEquals(4.8f, result)
        }

        @Test
        fun `points does not add high-score bonus for non-exact score`() {
            val match = finishedMatch(homeGoals = 4, awayGoals = 2, homeQuota = 1.1f)
            val result = PredictionEngine.points(listOf(closedPrediction(match, 5, 3)))
            // 3 (correct) + 0 (high-score bonus) + 1.5 (quota) = 4.1
            assertEquals(2.1f, result)
        }

        @Test
        fun `points awards away quota when away team wins`() {
            val match = finishedMatch(homeGoals = 0, awayGoals = 2, awayQuota = 2.0f)
            val result = PredictionEngine.points(listOf(closedPrediction(match, 1, 3)))
            // 1 (partial) + 2.0 (quota) = 3.0
            assertEquals(3.0f, result)
        }

        @Test
        fun `points awards tie quota for tie result`() {
            val match = finishedMatch(homeGoals = 1, awayGoals = 1, tieQuota = 1.8f)
            val result = PredictionEngine.points(listOf(closedPrediction(match, 0, 0)))
            // 1 (partial) + 1.8 (quota) = 2.8
            assertEquals(2.8f, result)
        }

        @Test
        fun `points returns 0 for match with no goals recorded`() {
            val match = finishedMatch(homeGoals = 2, awayGoals = 1).copy(homeGoals = null, awayGoals = null)
            val result = PredictionEngine.points(listOf(closedPrediction(match, 2, 1)))
            assertEquals(0f, result)
        }

        @Test
        fun `points sums correctly across multiple predictions`() {
            // pred1: exact home win, homeQuota=2.0 → 3+2.0 = 5.0
            // pred2: exact tie, tieQuota=1.5 → 3+1.5 = 4.5
            // pred3: partial away win, awayQuota=1.3 → 1+1.3 = 2.3
            // pred4: incorrect tie, tieQuota=1.1 → 0+0.0 = 0.0
            val match1 = finishedMatch(homeGoals = 1, awayGoals = 0, homeQuota = 2.0f, tieQuota = 1.4f, awayQuota = 1.5f)
            val match2 = finishedMatch(homeGoals = 0, awayGoals = 0, homeQuota = 1.9f, tieQuota = 1.5f, awayQuota = 2.1f)
            val match3 = finishedMatch(homeGoals = 0, awayGoals = 1, homeQuota = 1.1f, tieQuota = 1.4f, awayQuota = 1.3f)
            val match4 = finishedMatch(homeGoals = 1, awayGoals = 2, homeQuota = 2.2f, tieQuota = 1.1f, awayQuota = 1.1f)
            val result = PredictionEngine.points(listOf(
                closedPrediction(match1, 1, 0),
                closedPrediction(match2, 0, 0),
                closedPrediction(match3, 0, 2),
                closedPrediction(match4, 0, 0),
            ))
            assertEquals(11.8f, result)
        }

        @Test
        fun `points rounds result to 2 decimal places`() {
            // homeQuota=1.33333, match: home=1, away=0; prediction: home=2, away=0
            // HOME wins in both → PARTIAL → 1 + 1.333 = 2.333 → rounds to 2.33
            val match = finishedMatch(homeGoals = 1, awayGoals = 0, homeQuota = 1.3333333f)
            val result = PredictionEngine.points(listOf(closedPrediction(match, 2, 0)))
            assertEquals(2.33f, result)
        }
    }
}
