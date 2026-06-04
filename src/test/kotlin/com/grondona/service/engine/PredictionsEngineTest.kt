package com.grondona.service.engine

import com.grondona.model.AwardPrediction
import com.grondona.model.AwardType
import com.grondona.model.Awards
import com.grondona.model.Group
import com.grondona.model.GroupUser
import com.grondona.model.Match
import com.grondona.model.MatchPrediction
import com.grondona.model.MatchStage
import com.grondona.model.MatchStatus
import com.grondona.model.Player
import com.grondona.model.PlayerPosition
import com.grondona.model.PredictionStatus
import com.grondona.model.Score
import com.grondona.model.Team
import com.grondona.model.Tournament
import com.grondona.model.TournamentStatus
import com.grondona.model.User
import com.grondona.service.engine.PredictionsEngine.rank
import com.grondona.testGroup
import com.grondona.testTournament
import com.grondona.testUser
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID

class PredictionsEngineTest {

    private val anyTournament = Tournament(
        id = UUID.randomUUID(), name = "T", status = TournamentStatus.NOT_STARTED,
        createdAt = LocalDateTime.now(), updatedAt = LocalDateTime.now()
    )
    private val anyTeam = Team(
        id = UUID.randomUUID(), tournament = anyTournament, name = "Team", code = "T", icon = "t.png", englishKey = "T-en"
    )
    private val anyPlayer = Player(
        id = UUID.randomUUID(), team = anyTeam, name = "Player", position = PlayerPosition.FORWARD,
    )
    private val anyUser = User(
        id = UUID.randomUUID(), fullname = "Test", username = "tester",
        email = "test@example.com", passwordHash = "hash",
        createdAt = LocalDateTime.now(), updatedAt = LocalDateTime.now()
    )
    private val anyGroup = Group(
        id = UUID.randomUUID(), name = "Group", isPrivate = false, maxMembers = 10,
        tournament = anyTournament, createdAt = LocalDateTime.now(), updatedAt = LocalDateTime.now()
    )

    private fun testMatch(
        homeGoals: Int, awayGoals: Int, hasMultiplier: Boolean = false,
        homeQuota: Float = 0f, awayQuota: Float = 0f, drawQuota: Float = 0f
    ) = Match(
        id = UUID.randomUUID(), code = "MATCH", tournament = anyTournament, stage = MatchStage.GROUP_STAGE,
        homeTeam = anyTeam, awayTeam = anyTeam, status = MatchStatus.FINISHED, homeGoals = homeGoals, awayGoals = awayGoals,
        hasMultiplier = hasMultiplier, homeQuota = homeQuota, awayQuota = awayQuota, drawQuota = drawQuota,
        startedAt = ZonedDateTime.now().minusHours(2), finishedAt = ZonedDateTime.now().minusHours(1),
    )

    private fun testMatchPrediction(match: Match, homeGoals: Int, awayGoals: Int): MatchPrediction {
        val predictionScore = Score(homeGoals, awayGoals)
        val status = when {
            match.score() == predictionScore && predictionScore.goals() >= 5 -> PredictionStatus.BONUS
            match.score() == predictionScore -> PredictionStatus.CORRECT
            match.score()?.outcome() == predictionScore.outcome() -> PredictionStatus.PARTIAL
            else -> PredictionStatus.INCORRECT
        }

        return MatchPrediction(
            id = UUID.randomUUID(), user = anyUser, group = anyGroup,
            match = match, homeGoals = homeGoals, awayGoals = awayGoals, status = status,
        )
    }

    private fun testAwardPrediction(
        awardType: AwardType, winners: Awards = testWinners(), status: PredictionStatus = PredictionStatus.PENDING,
        playerId: UUID? = null, teamId: UUID? = null
    ) = AwardPrediction(
        id = UUID.randomUUID(), user = anyUser, group = anyGroup.copy(tournament = anyTournament.copy(awards = winners)), status = status,
        awardType = awardType, team = teamId?.let { anyTeam.copy(id = it) }, player = playerId?.let { anyPlayer.copy(id = it) }
    )

    private fun testWinners(
        champion: UUID = UUID.randomUUID(), topScorer: UUID = UUID.randomUUID(), bestPlayer: UUID = UUID.randomUUID(),
        bestGoalkeeper: UUID = UUID.randomUUID(), bestYoungPlayer: UUID = UUID.randomUUID(),
    ) = Awards(champion, topScorer, bestPlayer, bestGoalkeeper, bestYoungPlayer)


    @Nested
    inner class MatchPointsTests {

        @Test
        fun `matchPoints() returns 0 for incorrect prediction`() {
            // home wins 2-1, homeQuota=1.5; prediction: away wins 0-1 → wrong outcome
            val match = testMatch(homeGoals = 2, awayGoals = 1, homeQuota = 1.5f)
            val result = PredictionsEngine.matchPoints(testMatchPrediction(match, 0, 1))
            // 0 (incorrect) + 0 (high-score bonus) + 0 (quota) = 0
            Assertions.assertEquals(0f, result)
        }

        @Test
        fun `matchPoints() returns 0 for incorrect prediction (even with multiplier)`() {
            // home wins 2-1, homeQuota=1.5; prediction: away wins 0-1 → wrong outcome
            val match = testMatch(homeGoals = 2, awayGoals = 1, homeQuota = 1.5f, hasMultiplier = true)
            val result = PredictionsEngine.matchPoints(testMatchPrediction(match, 0, 1))
            // 0 (incorrect) + 0 (high-score bonus) + 0 (quota) = 0
            Assertions.assertEquals(0f, result)
        }

        @Test
        fun `matchPoints() adds partial bonus for correct outcome with wrong score`() {
            val match = testMatch(homeGoals = 2, awayGoals = 1, homeQuota = 1.5f)
            val result = PredictionsEngine.matchPoints(testMatchPrediction(match, 1, 0))
            // 1 (partial) + 0 (high-score bonus) + 1.5 (quota) = 2.5
            Assertions.assertEquals(2.5f, result)
        }

        @Test
        fun `matchPoints() adds correct bonuses for exact score`() {
            val match = testMatch(homeGoals = 2, awayGoals = 1, homeQuota = 1.5f)
            val result = PredictionsEngine.matchPoints(testMatchPrediction(match, 2, 1))
            // 3 (correct) + 0 (high-score bonus) + 1.5 (quota) = 4.5
            Assertions.assertEquals(4.5f, result)
        }

        @Test
        fun `matchPoints() adds high-score bonus when total goals reach exactly 5`() {
            val match = testMatch(homeGoals = 3, awayGoals = 2, homeQuota = 1.5f)
            val result = PredictionsEngine.matchPoints(testMatchPrediction(match, 3, 2))
            // 3 (correct) + 2 (high-score bonus) + 1.5 (quota) = 6.5
            Assertions.assertEquals(6.5f, result)
        }

        @Test
        fun `matchPoints() returns 0 for incorrect outcome with high-score bonus`() {
            val match = testMatch(homeGoals = 3, awayGoals = 2, homeQuota = 1.5f)
            val result = PredictionsEngine.matchPoints(testMatchPrediction(match, 2, 3))
            // 0 (incorrect) + 0 (high-score bonus) + 0 (quota) = 0
            Assertions.assertEquals(0f, result)
        }

        @Test
        fun `matchPoints() does not add high-score bonus for 4 total goals`() {
            val match = testMatch(homeGoals = 2, awayGoals = 2, drawQuota = 1.8f) // 4 goals
            val result = PredictionsEngine.matchPoints(testMatchPrediction(match, 2, 2))
            // 3 (correct) + 0 (high-score bonus) + 1.5 (quota) = 4.5
            Assertions.assertEquals(4.8f, result)
        }

        @Test
        fun `matchPoints() does not add high-score bonus for non-exact score`() {
            val match = testMatch(homeGoals = 4, awayGoals = 2, homeQuota = 1.1f)
            val result = PredictionsEngine.matchPoints(testMatchPrediction(match, 5, 3))
            // 3 (correct) + 0 (high-score bonus) + 1.5 (quota) = 4.1
            Assertions.assertEquals(2.1f, result)
        }

        @Test
        fun `matchPoints() awards away quota when away team wins`() {
            val match = testMatch(homeGoals = 0, awayGoals = 2, awayQuota = 2.0f)
            val result = PredictionsEngine.matchPoints(testMatchPrediction(match, 1, 3))
            // 1 (partial) + 2.0 (quota) = 3.0
            Assertions.assertEquals(3.0f, result)
        }

        @Test
        fun `matchPoints() awards tie quota for tie result`() {
            val match = testMatch(homeGoals = 1, awayGoals = 1, drawQuota = 1.8f)
            val result = PredictionsEngine.matchPoints(testMatchPrediction(match, 0, 0))
            // 1 (partial) + 1.8 (quota) = 2.8
            Assertions.assertEquals(2.8f, result)
        }

        @Test
        fun `matchPoints() returns 0 for match with no goals recorded`() {
            val match = testMatch(homeGoals = 2, awayGoals = 1).copy(homeGoals = null, awayGoals = null)
            val result = PredictionsEngine.matchPoints(testMatchPrediction(match, 2, 1))
            Assertions.assertEquals(0f, result)
        }

        @Test
        fun `matchPoints() rounds result to 1 decimal place`() {
            // homeQuota=1.33333, match: home=1, away=0; prediction: home=2, away=0
            // HOME wins in both → PARTIAL → 1 + 1.333 = 2.333 → rounds to 2.33
            val match = testMatch(homeGoals = 1, awayGoals = 0, homeQuota = 1.3333333f)
            val result = PredictionsEngine.matchPoints(testMatchPrediction(match, 2, 0))
            Assertions.assertEquals(2.3f, result)
        }

        @Test
        fun `matchPoints() returns 1,5x the PARTIAL prediction points for a match with multiplier (ignoring quotas)`() {
            val match = testMatch(homeGoals = 1, awayGoals = 0, homeQuota = 1f, hasMultiplier = true)
            val result = PredictionsEngine.matchPoints(testMatchPrediction(match, 2, 0))
            Assertions.assertEquals(2.5f, result)
        }

        @Test
        fun `matchPoints() returns 1,5x the CORRECT prediction points for a match with multiplier (ignoring quotas)`() {
            val match = testMatch(homeGoals = 1, awayGoals = 0, homeQuota = 1f, hasMultiplier = true)
            val result = PredictionsEngine.matchPoints(testMatchPrediction(match, 1, 0))
            Assertions.assertEquals(5.5f, result)
        }

        @Test
        fun `matchPoints() returns 1,5x the BONUS prediction points for a match with multiplier (ignoring quotas)`() {
            val match = testMatch(homeGoals = 5, awayGoals = 0, homeQuota = 1f, hasMultiplier = true)
            val result = PredictionsEngine.matchPoints(testMatchPrediction(match, 5, 0))
            Assertions.assertEquals(8.5f, result)
        }
    }

    @Nested
    inner class AwardPointsTests {

        @Test
        fun `awardPoints() returns 0 for incorrect single-prediction (champion)`() {
            // winner is champion=A, predictions is champion=B
            val winners = testWinners(champion = UUID.randomUUID())
            val prediction = testAwardPrediction(AwardType.CHAMPION, winners, teamId = UUID.randomUUID())
            val result = PredictionsEngine.awardPoints(listOf(prediction))
            Assertions.assertEquals(0f, result)
        }

        @Test
        fun `awardPoints() returns 0 for incorrect double-prediction (champion)`() {
            // winner is champion=A, predictions is champion=B+C
            val winners = testWinners(champion = UUID.randomUUID())
            val prediction1 = testAwardPrediction(AwardType.CHAMPION, winners, teamId = UUID.randomUUID())
            val prediction2 = testAwardPrediction(AwardType.CHAMPION, winners, teamId = UUID.randomUUID())
            val result = PredictionsEngine.awardPoints(listOf(prediction1, prediction2))
            Assertions.assertEquals(0f, result)
        }

        @Test
        fun `awardPoints() returns 10 for correct single-prediction (champion)`() {
            // winner is champion=A, predictions is champion=A
            val prediction = testAwardPrediction(AwardType.CHAMPION, status = PredictionStatus.CORRECT)
            val result = PredictionsEngine.awardPoints(listOf(prediction))
            Assertions.assertEquals(10f, result)
        }

        @Test
        fun `awardPoints() returns 5 for correct double-prediction (champion)`() {
            // winner is champion=A, predictions is champion=A+B
            val prediction1 = testAwardPrediction(AwardType.CHAMPION, status = PredictionStatus.CORRECT)
            val prediction2 = testAwardPrediction(AwardType.CHAMPION, status = PredictionStatus.INCORRECT)
            val result = PredictionsEngine.awardPoints(listOf(prediction1, prediction2))
            Assertions.assertEquals(5f, result)
        }

        @Test
        fun `awardPoints() returns 0 for incorrect single-prediction (top-scorer)`() {
            // winner is top-scorer=A, predictions is top-scorer=B
            val prediction = testAwardPrediction(AwardType.TOP_SCORER, status = PredictionStatus.INCORRECT)
            val result = PredictionsEngine.awardPoints(listOf(prediction))
            Assertions.assertEquals(0f, result)
        }

        @Test
        fun `awardPoints() returns 0 for incorrect double-prediction (top-scorer)`() {
            // winner is top-scorer=A, predictions is top-scorer=B+C
            val prediction1 = testAwardPrediction(AwardType.TOP_SCORER, status = PredictionStatus.INCORRECT)
            val prediction2 = testAwardPrediction(AwardType.TOP_SCORER, status = PredictionStatus.INCORRECT)
            val result = PredictionsEngine.awardPoints(listOf(prediction1, prediction2))
            Assertions.assertEquals(0f, result)
        }

        @Test
        fun `awardPoints() returns 0 for incorrect triple-prediction (top-scorer)`() {
            // winner is top-scorer=A, predictions is top-scorer=B+C+D
            val prediction1 = testAwardPrediction(AwardType.TOP_SCORER, status = PredictionStatus.INCORRECT)
            val prediction2 = testAwardPrediction(AwardType.TOP_SCORER, status = PredictionStatus.INCORRECT)
            val prediction3 = testAwardPrediction(AwardType.TOP_SCORER, status = PredictionStatus.INCORRECT)
            val result = PredictionsEngine.awardPoints(listOf(prediction1, prediction2, prediction3))
            Assertions.assertEquals(0f, result)
        }

        @Test
        fun `awardPoints() returns 12 for correct single-prediction (top-scorer)`() {
            // winner is top-scorer=A, predictions is top-scorer=A
            val prediction = testAwardPrediction(AwardType.TOP_SCORER, status = PredictionStatus.CORRECT)
            val result = PredictionsEngine.awardPoints(listOf(prediction))
            Assertions.assertEquals(10f, result)
        }

        @Test
        fun `awardPoints() returns 8 for correct double-prediction (top-scorer)`() {
            // winner is top-scorer=A, predictions is top-scorer=A+B
            val prediction1 = testAwardPrediction(AwardType.TOP_SCORER, status = PredictionStatus.CORRECT)
            val prediction2 = testAwardPrediction(AwardType.TOP_SCORER, status = PredictionStatus.CORRECT)
            val result = PredictionsEngine.awardPoints(listOf(prediction1, prediction2))
            Assertions.assertEquals(7f, result)
        }

        @Test
        fun `awardPoints() returns 4 for correct triple-prediction (top-scorer)`() {
            // winner is top-scorer=A, predictions is top-scorer=A+B+C
            val prediction1 = testAwardPrediction(AwardType.TOP_SCORER, status = PredictionStatus.CORRECT)
            val prediction2 = testAwardPrediction(AwardType.TOP_SCORER, status = PredictionStatus.INCORRECT)
            val prediction3 = testAwardPrediction(AwardType.TOP_SCORER, status = PredictionStatus.INCORRECT)
            val result = PredictionsEngine.awardPoints(listOf(prediction1, prediction2, prediction3))
            Assertions.assertEquals(4f, result)
        }

        @Test
        fun `awardPoints() returns 0 for incorrect single-prediction (best player)`() {
            // winner is best-player=A, predictions is best-player=B
            val prediction = testAwardPrediction(AwardType.BEST_PLAYER, status = PredictionStatus.INCORRECT)
            val result = PredictionsEngine.awardPoints(listOf(prediction))
            Assertions.assertEquals(0f, result)
        }

        @Test
        fun `awardPoints() returns 0 for incorrect double-prediction (best player)`() {
            // winner is best-player=A, predictions is best-player=B+C
            val prediction1 = testAwardPrediction(AwardType.BEST_PLAYER, status = PredictionStatus.INCORRECT)
            val prediction2 = testAwardPrediction(AwardType.BEST_PLAYER, status = PredictionStatus.INCORRECT)
            val result = PredictionsEngine.awardPoints(listOf(prediction1, prediction2))
            Assertions.assertEquals(0f, result)
        }

        @Test
        fun `awardPoints() returns 0 for incorrect triple-prediction (best player)`() {
            // winner is best-player=A, predictions is best-player=B+C+D
            val prediction1 = testAwardPrediction(AwardType.BEST_PLAYER, status = PredictionStatus.INCORRECT)
            val prediction2 = testAwardPrediction(AwardType.BEST_PLAYER, status = PredictionStatus.INCORRECT)
            val prediction3 = testAwardPrediction(AwardType.BEST_PLAYER, status = PredictionStatus.INCORRECT)
            val result = PredictionsEngine.awardPoints(listOf(prediction1, prediction2, prediction3))
            Assertions.assertEquals(0f, result)
        }

        @Test
        fun `awardPoints() returns 12 for correct single-prediction (best player)`() {
            // winner is best-player=A, predictions is best-player=A
            val prediction = testAwardPrediction(AwardType.BEST_PLAYER, status = PredictionStatus.CORRECT)
            val result = PredictionsEngine.awardPoints(listOf(prediction))
            Assertions.assertEquals(10f, result)
        }

        @Test
        fun `awardPoints() returns 8 for correct double-prediction (best player)`() {
            // winner is best-player=A, predictions is best-player=A+B
            val prediction1 = testAwardPrediction(AwardType.BEST_PLAYER, status = PredictionStatus.CORRECT)
            val prediction2 = testAwardPrediction(AwardType.BEST_PLAYER, status = PredictionStatus.INCORRECT)
            val result = PredictionsEngine.awardPoints(listOf(prediction1, prediction2))
            Assertions.assertEquals(7f, result)
        }

        @Test
        fun `awardPoints() returns 4 for correct triple-prediction (best player)`() {
            // winner is best-player=A, predictions is best-player=A+B+C
            val prediction1 = testAwardPrediction(AwardType.BEST_PLAYER, status = PredictionStatus.CORRECT)
            val prediction2 = testAwardPrediction(AwardType.BEST_PLAYER, status = PredictionStatus.INCORRECT)
            val prediction3 = testAwardPrediction(AwardType.BEST_PLAYER, status = PredictionStatus.INCORRECT)
            val result = PredictionsEngine.awardPoints(listOf(prediction1, prediction2, prediction3))
            Assertions.assertEquals(4f, result)
        }

        @Test
        fun `awardPoints() returns 0 for incorrect single-prediction (best goalkeeper)`() {
            // winner is best-goalkeeper=A, predictions is best-goalkeeper=B
            val prediction = testAwardPrediction(AwardType.BEST_GOALKEEPER, status = PredictionStatus.INCORRECT)
            val result = PredictionsEngine.awardPoints(listOf(prediction))
            Assertions.assertEquals(0f, result)
        }

        @Test
        fun `awardPoints() returns 0 for incorrect double-prediction (best goalkeeper)`() {
            // winner is best-goalkeeper=A, predictions is best-goalkeeper=B+C
            val prediction1 = testAwardPrediction(AwardType.BEST_GOALKEEPER, status = PredictionStatus.INCORRECT)
            val prediction2 = testAwardPrediction(AwardType.BEST_GOALKEEPER, status = PredictionStatus.INCORRECT)
            val result = PredictionsEngine.awardPoints(listOf(prediction1, prediction2))
            Assertions.assertEquals(0f, result)
        }

        @Test
        fun `awardPoints() returns 0 for incorrect triple-prediction (best goalkeeper)`() {
            // winner is best-goalkeeper=A, predictions is best-goalkeeper=B+C+D
            val prediction1 = testAwardPrediction(AwardType.BEST_GOALKEEPER, status = PredictionStatus.INCORRECT)
            val prediction2 = testAwardPrediction(AwardType.BEST_GOALKEEPER, status = PredictionStatus.INCORRECT)
            val prediction3 = testAwardPrediction(AwardType.BEST_GOALKEEPER, status = PredictionStatus.INCORRECT)
            val result = PredictionsEngine.awardPoints(listOf(prediction1, prediction2, prediction3))
            Assertions.assertEquals(0f, result)
        }

        @Test
        fun `awardPoints() returns 12 for correct single-prediction (best goalkeeper)`() {
            // winner is best-goalkeeper=A, predictions is best-goalkeeper=A
            val prediction = testAwardPrediction(AwardType.BEST_GOALKEEPER, status = PredictionStatus.CORRECT)
            val result = PredictionsEngine.awardPoints(listOf(prediction))
            Assertions.assertEquals(10f, result)
        }

        @Test
        fun `awardPoints() returns 8 for correct double-prediction (best goalkeeper)`() {
            // winner is best-goalkeeper=A, predictions is best-goalkeeper=A+B
            val prediction1 = testAwardPrediction(AwardType.BEST_GOALKEEPER, status = PredictionStatus.CORRECT)
            val prediction2 = testAwardPrediction(AwardType.BEST_GOALKEEPER, status = PredictionStatus.INCORRECT)
            val result = PredictionsEngine.awardPoints(listOf(prediction1, prediction2))
            Assertions.assertEquals(7f, result)
        }

        @Test
        fun `awardPoints() returns 4 for correct triple-prediction (best goalkeeper)`() {
            // winner is best-goalkeeper=A, predictions is best-goalkeeper=A+B+C
            val prediction1 = testAwardPrediction(AwardType.BEST_GOALKEEPER, status = PredictionStatus.CORRECT)
            val prediction2 = testAwardPrediction(AwardType.BEST_GOALKEEPER, status = PredictionStatus.INCORRECT)
            val prediction3 = testAwardPrediction(AwardType.BEST_GOALKEEPER, status = PredictionStatus.INCORRECT)
            val result = PredictionsEngine.awardPoints(listOf(prediction1, prediction2, prediction3))
            Assertions.assertEquals(4f, result)
        }

        @Test
        fun `awardPoints() returns 0 for incorrect single-prediction (best young player)`() {
            // winner is best-young-player=A, predictions is best-young-player=B
            val prediction = testAwardPrediction(AwardType.BEST_YOUNG_PLAYER, status = PredictionStatus.INCORRECT)
            val result = PredictionsEngine.awardPoints(listOf(prediction))
            Assertions.assertEquals(0f, result)
        }

        @Test
        fun `awardPoints() returns 0 for incorrect double-prediction (best young player)`() {
            // winner is best-young-player=A, predictions is best-young-player=B+C
            val prediction1 = testAwardPrediction(AwardType.BEST_YOUNG_PLAYER, status = PredictionStatus.INCORRECT)
            val prediction2 = testAwardPrediction(AwardType.BEST_YOUNG_PLAYER, status = PredictionStatus.INCORRECT)
            val result = PredictionsEngine.awardPoints(listOf(prediction1, prediction2))
            Assertions.assertEquals(0f, result)
        }

        @Test
        fun `awardPoints() returns 0 for incorrect triple-prediction (best young player)`() {
            // winner is best-young-player=A, predictions is best-young-player=B+C+D
            val prediction1 = testAwardPrediction(AwardType.BEST_YOUNG_PLAYER, status = PredictionStatus.INCORRECT)
            val prediction2 = testAwardPrediction(AwardType.BEST_YOUNG_PLAYER, status = PredictionStatus.INCORRECT)
            val prediction3 = testAwardPrediction(AwardType.BEST_YOUNG_PLAYER, status = PredictionStatus.INCORRECT)
            val result = PredictionsEngine.awardPoints(listOf(prediction1, prediction2, prediction3))
            Assertions.assertEquals(0f, result)
        }

        @Test
        fun `awardPoints() returns 12 for correct single-prediction (best young player)`() {
            // winner is best-young-player=A, predictions is best-young-player=A
            val prediction = testAwardPrediction(AwardType.BEST_YOUNG_PLAYER, status = PredictionStatus.CORRECT)
            val result = PredictionsEngine.awardPoints(listOf(prediction))
            Assertions.assertEquals(10f, result)
        }

        @Test
        fun `awardPoints() returns 8 for correct double-prediction (best young player)`() {
            // winner is best-young-player=A, predictions is best-young-player=A+B
            val prediction1 = testAwardPrediction(AwardType.BEST_YOUNG_PLAYER, status = PredictionStatus.CORRECT)
            val prediction2 = testAwardPrediction(AwardType.BEST_YOUNG_PLAYER, status = PredictionStatus.INCORRECT)
            val result = PredictionsEngine.awardPoints(listOf(prediction1, prediction2))
            Assertions.assertEquals(7f, result)
        }

        @Test
        fun `awardPoints() returns 4 for correct triple-prediction (best young player)`() {
            // winner is best-young-player=A, predictions is best-young-player=A+B+C
            val prediction1 = testAwardPrediction(AwardType.BEST_YOUNG_PLAYER, status = PredictionStatus.CORRECT)
            val prediction2 = testAwardPrediction(AwardType.BEST_YOUNG_PLAYER, status = PredictionStatus.INCORRECT)
            val prediction3 = testAwardPrediction(AwardType.BEST_YOUNG_PLAYER, status = PredictionStatus.INCORRECT)
            val result = PredictionsEngine.awardPoints(listOf(prediction1, prediction2, prediction3))
            Assertions.assertEquals(4f, result)
        }
    }

    @Nested
    inner class CheckMatchPredictionsTests {

        @Test
        fun `checkMatchPredictions() marks an INCORRECT prediction`() {
            val match = testMatch(1, 1)
            val prediction = testMatchPrediction(match, 1, 0)
            val results = PredictionsEngine.checkMatchPredictions(listOf(prediction))

            Assertions.assertEquals(1, results.size)
            Assertions.assertEquals(PredictionStatus.INCORRECT, results[0].status)
        }

        @Test
        fun `checkMatchPredictions() marks a PARTIAL prediction`() {
            val match = testMatch(1, 1)
            val prediction = testMatchPrediction(match, 0, 0)
            val results = PredictionsEngine.checkMatchPredictions(listOf(prediction))

            Assertions.assertEquals(1, results.size)
            Assertions.assertEquals(PredictionStatus.PARTIAL, results[0].status)
        }

        @Test
        fun `checkMatchPredictions() marks a CORRECT prediction`() {
            val match = testMatch(1, 1)
            val prediction = testMatchPrediction(match, 1, 1)
            val results = PredictionsEngine.checkMatchPredictions(listOf(prediction))

            Assertions.assertEquals(1, results.size)
            Assertions.assertEquals(PredictionStatus.CORRECT, results[0].status)
        }

        @Test
        fun `checkMatchPredictions() marks a BONUS prediction`() {
            val match = testMatch(3, 3)
            val prediction = testMatchPrediction(match, 3, 3)
            val results = PredictionsEngine.checkMatchPredictions(listOf(prediction))

            Assertions.assertEquals(1, results.size)
            Assertions.assertEquals(PredictionStatus.BONUS, results[0].status)
        }
    }

    @Nested
    inner class CheckAwardPredictionsTests {

        private fun testAwardPrediction(
            awardType: AwardType, winners: Awards, status: PredictionStatus = PredictionStatus.PENDING,
            playerId: UUID? = null, teamId: UUID? = null
        ) = AwardPrediction(
            id = UUID.randomUUID(), user = anyUser, group = anyGroup.copy(tournament = anyTournament.copy(awards = winners, status = TournamentStatus.FINISHED)),
            status = status, awardType = awardType, team = teamId?.let { anyTeam.copy(id = it) }, player = playerId?.let { anyPlayer.copy(id = it) }
        )

        @Test
        fun `checkAwardPredictions() returns empty list when the predictions list is empty`() {
            val results = PredictionsEngine.checkAwardPredictions(emptyList())
            Assertions.assertEquals(0, results.size)
        }

        @Test
        fun `checkAwardPredictions() returns empty list when the tournament is not FINISHED`() {
            val prediction = testAwardPrediction(awardType = AwardType.CHAMPION, winners = testWinners(), teamId = UUID.randomUUID())
                .copy(group = testGroup.copy(tournament = anyTournament.copy(status = TournamentStatus.NOT_STARTED)))
            val results = PredictionsEngine.checkAwardPredictions(listOf(prediction))
            Assertions.assertEquals(0, results.size)
        }

        @Test
        fun `checkAwardPredictions() properly marks predictions for champions`() {
            val winners = testWinners()
            val prediction1 = testAwardPrediction(awardType = AwardType.CHAMPION, winners = winners, teamId = winners.champion)
            val prediction2 = testAwardPrediction(awardType = AwardType.CHAMPION, winners = winners, teamId = UUID.randomUUID())
            val results = PredictionsEngine.checkAwardPredictions(listOf(prediction1, prediction2))

            Assertions.assertEquals(2, results.size)
            Assertions.assertEquals(PredictionStatus.CORRECT, results[0].status)
            Assertions.assertEquals(PredictionStatus.INCORRECT, results[1].status)
        }

        @Test
        fun `checkAwardPredictions() properly marks predictions for top-scorers`() {
            val winners = testWinners()
            val prediction1 = testAwardPrediction(awardType = AwardType.TOP_SCORER, winners = winners, playerId = winners.topScorer)
            val prediction2 = testAwardPrediction(awardType = AwardType.TOP_SCORER, winners = winners, playerId = UUID.randomUUID())
            val prediction3 = testAwardPrediction(awardType = AwardType.TOP_SCORER, winners = winners, playerId = UUID.randomUUID())
            val results = PredictionsEngine.checkAwardPredictions(listOf(prediction1, prediction2, prediction3))

            Assertions.assertEquals(3, results.size)
            Assertions.assertEquals(PredictionStatus.CORRECT, results[0].status)
            Assertions.assertEquals(PredictionStatus.INCORRECT, results[1].status)
            Assertions.assertEquals(PredictionStatus.INCORRECT, results[2].status)
        }

        @Test
        fun `checkAwardPredictions() properly marks predictions for best player`() {
            val winners = testWinners()
            val prediction1 = testAwardPrediction(awardType = AwardType.BEST_PLAYER, winners = winners, playerId = winners.bestPlayer)
            val prediction2 = testAwardPrediction(awardType = AwardType.BEST_PLAYER, winners = winners, playerId = UUID.randomUUID())
            val prediction3 = testAwardPrediction(awardType = AwardType.BEST_PLAYER, winners = winners, playerId = UUID.randomUUID())
            val results = PredictionsEngine.checkAwardPredictions(listOf(prediction1, prediction2, prediction3))

            Assertions.assertEquals(3, results.size)
            Assertions.assertEquals(PredictionStatus.CORRECT, results[0].status)
            Assertions.assertEquals(PredictionStatus.INCORRECT, results[1].status)
            Assertions.assertEquals(PredictionStatus.INCORRECT, results[2].status)
        }

        @Test
        fun `checkAwardPredictions() properly marks predictions for best goalkeepers`() {
            val winners = testWinners()
            val prediction1 = testAwardPrediction(awardType = AwardType.BEST_GOALKEEPER, winners = winners, playerId = winners.bestGoalkeeper)
            val prediction2 = testAwardPrediction(awardType = AwardType.BEST_GOALKEEPER, winners = winners, playerId = UUID.randomUUID())
            val prediction3 = testAwardPrediction(awardType = AwardType.BEST_GOALKEEPER, winners = winners, playerId = UUID.randomUUID())
            val results = PredictionsEngine.checkAwardPredictions(listOf(prediction1, prediction2, prediction3))

            Assertions.assertEquals(3, results.size)
            Assertions.assertEquals(PredictionStatus.CORRECT, results[0].status)
            Assertions.assertEquals(PredictionStatus.INCORRECT, results[1].status)
            Assertions.assertEquals(PredictionStatus.INCORRECT, results[2].status)
        }

        @Test
        fun `checkAwardPredictions() properly marks predictions for best young players`() {
            val winners = testWinners()
            val prediction1 = testAwardPrediction(awardType = AwardType.BEST_YOUNG_PLAYER, winners = winners, playerId = winners.bestYoungPlayer)
            val prediction2 = testAwardPrediction(awardType = AwardType.BEST_YOUNG_PLAYER, winners = winners, playerId = UUID.randomUUID())
            val prediction3 = testAwardPrediction(awardType = AwardType.BEST_YOUNG_PLAYER, winners = winners, playerId = UUID.randomUUID())
            val results = PredictionsEngine.checkAwardPredictions(listOf(prediction1, prediction2, prediction3))

            Assertions.assertEquals(3, results.size)
            Assertions.assertEquals(PredictionStatus.CORRECT, results[0].status)
            Assertions.assertEquals(PredictionStatus.INCORRECT, results[1].status)
            Assertions.assertEquals(PredictionStatus.INCORRECT, results[2].status)
        }
    }

    @Nested
    inner class UpdateMatchPointsTests {

        private val memberId1 = UUID.randomUUID()
        private val member1 = GroupUser(
            group = anyGroup,
            user = anyUser.copy(id = memberId1), joinedAt = LocalDateTime.now()
        )
        private val memberId2 = UUID.randomUUID()
        private val member2 = GroupUser(
            group = anyGroup,
            user = anyUser.copy(id = memberId2), joinedAt = LocalDateTime.now().minus(1, ChronoUnit.MINUTES)
        )
        private val members = listOf(member1, member2)

        @Test
        fun `updateMatchPoints() returns the same members with no changes if there are no new predictions`() {
            val rank1 = PredictionsEngine.updateMatchPoints(members, emptyMap())
            val rank2 = PredictionsEngine.updateMatchPoints(rank1, emptyMap())
            Assertions.assertEquals(rank2, rank1)
        }

        @Test
        fun `updateMatchPoints() ignores multiple predictions for the same match, considering only the first one`() {
            val match = testMatch(homeGoals = 1, awayGoals = 0)
            val predictions = mapOf(
                memberId1 to listOf(
                    testMatchPrediction(match, homeGoals = 2, awayGoals = 0),
                    testMatchPrediction(match, homeGoals = 1, awayGoals = 0),
                ),
            )

            val newRank = PredictionsEngine.updateMatchPoints(listOf(member1), predictions)
            Assertions.assertEquals(member1.id, newRank[0].id)
            Assertions.assertEquals(1, newRank[0].rank)
            Assertions.assertEquals(1f, newRank[0].points)
        }

        @Test
        fun `updateMatchPoints() returns a list of the last 5 predictions (truncating it if there are more)`() {
            val match1 = testMatch(homeGoals = 1, awayGoals = 0)
            val match2 = testMatch(homeGoals = 1, awayGoals = 0)
            val match3 = testMatch(homeGoals = 1, awayGoals = 0)
            val newPredictions1 = mapOf(
                memberId1 to listOf(
                    testMatchPrediction(match1, homeGoals = 1, awayGoals = 0),
                    testMatchPrediction(match2, homeGoals = 2, awayGoals = 0),
                    testMatchPrediction(match3, homeGoals = 0, awayGoals = 0),
                ),
            )

            val newRank1 = PredictionsEngine.updateMatchPoints(listOf(member1), newPredictions1)
            Assertions.assertEquals(3, newRank1[0].lastPredictions.size)
            Assertions.assertEquals(PredictionStatus.CORRECT, newRank1[0].lastPredictions[0])
            Assertions.assertEquals(PredictionStatus.PARTIAL, newRank1[0].lastPredictions[1])
            Assertions.assertEquals(PredictionStatus.INCORRECT, newRank1[0].lastPredictions[2])

            val match4 = testMatch(homeGoals = 1, awayGoals = 0)
            val match5 = testMatch(homeGoals = 1, awayGoals = 0)
            val newPredictions2 = mapOf(
                memberId1 to listOf(
                    testMatchPrediction(match4, homeGoals = 1, awayGoals = 0),
                    testMatchPrediction(match5, homeGoals = 0, awayGoals = 0),
                ),
            )

            val newRank2 = PredictionsEngine.updateMatchPoints(newRank1, newPredictions2)
            Assertions.assertEquals(5, newRank2[0].lastPredictions.size)
            Assertions.assertEquals(PredictionStatus.CORRECT, newRank2[0].lastPredictions[0])
            Assertions.assertEquals(PredictionStatus.PARTIAL, newRank2[0].lastPredictions[1])
            Assertions.assertEquals(PredictionStatus.INCORRECT, newRank2[0].lastPredictions[2])
            Assertions.assertEquals(PredictionStatus.CORRECT, newRank2[0].lastPredictions[3])
            Assertions.assertEquals(PredictionStatus.INCORRECT, newRank2[0].lastPredictions[4])

            val match6 = testMatch(homeGoals = 5, awayGoals = 0)
            val match7 = testMatch(homeGoals = 5, awayGoals = 0)
            val newPredictions3 = mapOf(
                memberId1 to listOf(
                    testMatchPrediction(match6, homeGoals = 5, awayGoals = 0),
                    testMatchPrediction(match7, homeGoals = 4, awayGoals = 0),
                ),
            )

            val newRank3 = PredictionsEngine.updateMatchPoints(newRank2, newPredictions3)
            Assertions.assertEquals(5, newRank3[0].lastPredictions.size)
            Assertions.assertEquals(PredictionStatus.INCORRECT, newRank3[0].lastPredictions[0])
            Assertions.assertEquals(PredictionStatus.CORRECT, newRank3[0].lastPredictions[1])
            Assertions.assertEquals(PredictionStatus.INCORRECT, newRank3[0].lastPredictions[2])
            Assertions.assertEquals(PredictionStatus.BONUS, newRank3[0].lastPredictions[3])
            Assertions.assertEquals(PredictionStatus.PARTIAL, newRank3[0].lastPredictions[4])
        }
    }

    @Nested
    inner class UpdateAwardPointsTests {

        val winners = testWinners()
        val member = GroupUser(
            user = testUser,
            group = testGroup.copy(
                tournament = testTournament.copy(status = TournamentStatus.FINISHED, awards = winners)
            ),
        )

        @Test
        fun `updateAwardPoints() does not change points when tournament is not finished`() {
            val group = testGroup.copy(tournament = testTournament.copy(status = TournamentStatus.NOT_STARTED))
            val member1 = member.copy(id = UUID.randomUUID(), user = testUser.copy(id = UUID.randomUUID()), group = group, points = 32.1f)
            val member2 = member.copy(id = UUID.randomUUID(), user = testUser.copy(id = UUID.randomUUID()), group = group, points = 12.6f)
            val members = listOf(member1, member2)

            val predictions = listOf(
                testAwardPrediction(AwardType.CHAMPION, winners = winners, teamId = winners.champion).copy(user = member1.user),
                testAwardPrediction(AwardType.CHAMPION, winners = winners, teamId = UUID.randomUUID()).copy(user = member2.user)
            )

            val ranked = PredictionsEngine.updateAwardPoints(members, predictions.groupBy { it.user.id!! })
            Assertions.assertEquals(2, ranked.size)
            Assertions.assertEquals(member1.user.id, ranked[0].user.id)
            Assertions.assertEquals(member1.points, ranked[0].points)
            Assertions.assertEquals(member2.user.id, ranked[1].user.id)
            Assertions.assertEquals(member2.points, ranked[1].points)
        }

        @Test
        fun `updateAwardPoints() does not change points when predictions is empty`() {
            val group = testGroup.copy(tournament = testTournament.copy(status = TournamentStatus.NOT_STARTED))
            val member1 = member.copy(id = UUID.randomUUID(), user = testUser.copy(id = UUID.randomUUID()), group = group, points = 32.1f)
            val member2 = member.copy(id = UUID.randomUUID(), user = testUser.copy(id = UUID.randomUUID()), group = group, points = 12.6f)
            val members = listOf(member1, member2)

            val ranked = PredictionsEngine.updateAwardPoints(members, emptyMap())
            Assertions.assertEquals(2, ranked.size)
            Assertions.assertEquals(member1.user.id, ranked[0].user.id)
            Assertions.assertEquals(member1.points, ranked[0].points)
            Assertions.assertEquals(member2.user.id, ranked[1].user.id)
            Assertions.assertEquals(member2.points, ranked[1].points)
        }

        @Test
        fun `updateAwardPoints() properly change points when tournament is finished`() {
            val group = testGroup.copy(tournament = testTournament.copy(status = TournamentStatus.FINISHED))
            val member1 = member.copy(id = UUID.randomUUID(), user = testUser.copy(id = UUID.randomUUID()), group = group, points = 32.1f)
            val member2 = member.copy(id = UUID.randomUUID(), user = testUser.copy(id = UUID.randomUUID()), group = group, points = 12.6f)
            val members = listOf(member1, member2)

            val predictions = listOf(
                testAwardPrediction(AwardType.CHAMPION, status = PredictionStatus.CORRECT).copy(user = member1.user),
                testAwardPrediction(AwardType.CHAMPION, status = PredictionStatus.INCORRECT).copy(user = member2.user)
            )

            val ranked = PredictionsEngine.updateAwardPoints(members, predictions.groupBy { it.user.id!! })
            Assertions.assertEquals(2, ranked.size)
            Assertions.assertEquals(member1.user.id, ranked[0].user.id)
            Assertions.assertEquals(42.1f, ranked[0].points)
            Assertions.assertEquals(member2.user.id, ranked[1].user.id)
            Assertions.assertEquals(12.6f, ranked[1].points)
        }
    }

    @Nested
    inner class RankTests {

        private val member = GroupUser(group = anyGroup, user = anyUser, joinedAt = LocalDateTime.now())

        @Test
        fun `rank() returns the members ranked by points`() {
            val member1 = member.copy(id = UUID.randomUUID(), points = 5f)
            val member2 = member.copy(id = UUID.randomUUID(), points = 10f)
            val members = listOf(member1, member2)

            val newRank = members.rank()
            Assertions.assertEquals(2, newRank.size)
            Assertions.assertEquals(member2.id, newRank[0].id)
            Assertions.assertEquals(1, newRank[0].rank)
            Assertions.assertEquals(member1.id, newRank[1].id)
            Assertions.assertEquals(2, newRank[1].rank)
        }

        @Test
        fun `rank() returns the new members ranked by points (first dedup, general predictions)`() {
            val member1 = member.copy(id = UUID.randomUUID(), points = 10f, amountPartial = 1, amountCorrect = 1, amountBonus = 1)
            val member2 = member.copy(id = UUID.randomUUID(), points = 10f, amountPartial = 0, amountCorrect = 1, amountBonus = 1)
            val member3 = member.copy(id = UUID.randomUUID(), points = 10f, amountPartial = 1, amountCorrect = 0, amountBonus = 0)
            val members = listOf(member1, member2, member3)

            val newRank = members.rank()
            Assertions.assertEquals(3, newRank.size)
            Assertions.assertEquals(member1.id, newRank[0].id)
            Assertions.assertEquals(1, newRank[0].rank)
            Assertions.assertEquals(member2.id, newRank[1].id)
            Assertions.assertEquals(2, newRank[1].rank)
            Assertions.assertEquals(member3.id, newRank[2].id)
            Assertions.assertEquals(3, newRank[2].rank)
        }

        @Test
        fun `rank() returns the new members ranked by points (second dedup, exact predictions)`() {
            val member1 = member.copy(id = UUID.randomUUID(), points = 10f, amountPartial = 2, amountCorrect = 1, amountBonus = 0)
            val member2 = member.copy(id = UUID.randomUUID(), points = 10f, amountPartial = 1, amountCorrect = 1, amountBonus = 1)
            val members = listOf(member1, member2)

            val newRank = members.rank()
            Assertions.assertEquals(2, newRank.size)
            Assertions.assertEquals(member2.id, newRank[0].id)
            Assertions.assertEquals(1, newRank[0].rank)
            Assertions.assertEquals(member1.id, newRank[1].id)
            Assertions.assertEquals(2, newRank[1].rank)
        }

        @Test
        fun `rank() returns the new members ranked by points (third dedup, bonus predictions)`() {
            val member1 = member.copy(id = UUID.randomUUID(), points = 8f, amountPartial = 1, amountCorrect = 2, amountBonus = 0)
            val member2 = member.copy(id = UUID.randomUUID(), points = 8f, amountPartial = 1, amountCorrect = 1, amountBonus = 1)
            val members = listOf(member1, member2)

            val newRank = members.rank()
            Assertions.assertEquals(2, newRank.size)
            Assertions.assertEquals(member2.id, newRank[0].id)
            Assertions.assertEquals(1, newRank[0].rank)
            Assertions.assertEquals(member1.id, newRank[1].id)
            Assertions.assertEquals(2, newRank[1].rank)
        }

        @Test
        fun `rank() returns the new members ranked by points (fourth dedup, joined-at)`() {
            val member1 = member.copy(id = UUID.randomUUID(), points = 8f, amountPartial = 1, amountCorrect = 1, amountBonus = 1, joinedAt = LocalDateTime.now())
            val member2 = member.copy(id = UUID.randomUUID(), points = 8f, amountPartial = 1, amountCorrect = 1, amountBonus = 1, joinedAt = LocalDateTime.now().minusHours(1))
            val members = listOf(member1, member2)

            val newRank = members.rank()
            Assertions.assertEquals(2, newRank.size)
            Assertions.assertEquals(member2.id, newRank[0].id)
            Assertions.assertEquals(1, newRank[0].rank)
            Assertions.assertEquals(member1.id, newRank[1].id)
            Assertions.assertEquals(2, newRank[1].rank)
        }
    }
}