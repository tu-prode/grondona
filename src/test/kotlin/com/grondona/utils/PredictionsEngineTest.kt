package com.grondona.utils

import com.grondona.model.Group
import com.grondona.model.GroupUser
import com.grondona.model.Match
import com.grondona.model.MatchStatus
import com.grondona.model.MatchPrediction
import com.grondona.model.PredictionStatus
import com.grondona.model.Score
import com.grondona.model.Team
import com.grondona.model.Tournament
import com.grondona.model.TournamentStatus
import com.grondona.model.User
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID

class PredictionsEngineTest {

    private val anyTournament = Tournament(
        id = UUID.randomUUID(), name = "T", status = TournamentStatus.NOT_STARTED,
        createdAt = LocalDateTime.now(), updatedAt = LocalDateTime.now()
    )
    private val anyTeam = Team(
        id = UUID.randomUUID(), tournament = anyTournament, name = "Team", code = "T", icon = "t.png"
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
        homeGoals: Int,
        awayGoals: Int,
        homeQuota: Float = 0f,
        awayQuota: Float = 0f,
        drawQuota: Float = 0f,
    ) = Match(
        id = UUID.randomUUID(), code = "MATCH", tournament = anyTournament,
        homeTeam = anyTeam, awayTeam = anyTeam, status = MatchStatus.FINISHED,
        homeGoals = homeGoals, awayGoals = awayGoals,
        homeQuota = homeQuota, awayQuota = awayQuota, drawQuota = drawQuota,
        startedAt = LocalDateTime.now().minusHours(2),
        finishedAt = LocalDateTime.now().minusHours(1),
    )

    private fun testPrediction(match: Match, homeGoals: Int, awayGoals: Int): MatchPrediction {
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

    @Nested
    inner class PointsTests {

        @Test
        fun `points returns 0 for incorrect prediction`() {
            // home wins 2-1, homeQuota=1.5; prediction: away wins 0-1 → wrong outcome
            val match = testMatch(homeGoals = 2, awayGoals = 1, homeQuota = 1.5f)
            val result = PredictionsEngine.points(testPrediction(match, 0, 1))
            // 0 (incorrect) + 0 (high-score bonus) + 0 (quota) = 0
            assertEquals(0f, result)
        }

        @Test
        fun `points adds partial bonus for correct outcome with wrong score`() {
            val match = testMatch(homeGoals = 2, awayGoals = 1, homeQuota = 1.5f)
            val result = PredictionsEngine.points(testPrediction(match, 1, 0))
            // 1 (partial) + 0 (high-score bonus) + 1.5 (quota) = 2.5
            assertEquals(2.5f, result)
        }

        @Test
        fun `points adds correct bonuses for exact score`() {
            val match = testMatch(homeGoals = 2, awayGoals = 1, homeQuota = 1.5f)
            val result = PredictionsEngine.points(testPrediction(match, 2, 1))
            // 3 (correct) + 0 (high-score bonus) + 1.5 (quota) = 4.5
            assertEquals(4.5f, result)
        }

        @Test
        fun `points adds high-score bonus when total goals reach exactly 5`() {
            val match = testMatch(homeGoals = 3, awayGoals = 2, homeQuota = 1.5f)
            val result = PredictionsEngine.points(testPrediction(match, 3, 2))
            // 3 (correct) + 2 (high-score bonus) + 1.5 (quota) = 6.5
            assertEquals(6.5f, result)
        }

        @Test
        fun `points returns 0 for incorrect outcome with high-score bonus`() {
            val match = testMatch(homeGoals = 3, awayGoals = 2, homeQuota = 1.5f)
            val result = PredictionsEngine.points(testPrediction(match, 2, 3))
            // 0 (incorrect) + 0 (high-score bonus) + 0 (quota) = 0
            assertEquals(0f, result)
        }

        @Test
        fun `points does not add high-score bonus for 4 total goals`() {
            val match = testMatch(homeGoals = 2, awayGoals = 2, drawQuota = 1.8f) // 4 goals
            val result = PredictionsEngine.points(testPrediction(match, 2, 2))
            // 3 (correct) + 0 (high-score bonus) + 1.5 (quota) = 4.5
            assertEquals(4.8f, result)
        }

        @Test
        fun `points does not add high-score bonus for non-exact score`() {
            val match = testMatch(homeGoals = 4, awayGoals = 2, homeQuota = 1.1f)
            val result = PredictionsEngine.points(testPrediction(match, 5, 3))
            // 3 (correct) + 0 (high-score bonus) + 1.5 (quota) = 4.1
            assertEquals(2.1f, result)
        }

        @Test
        fun `points awards away quota when away team wins`() {
            val match = testMatch(homeGoals = 0, awayGoals = 2, awayQuota = 2.0f)
            val result = PredictionsEngine.points(testPrediction(match, 1, 3))
            // 1 (partial) + 2.0 (quota) = 3.0
            assertEquals(3.0f, result)
        }

        @Test
        fun `points awards tie quota for tie result`() {
            val match = testMatch(homeGoals = 1, awayGoals = 1, drawQuota = 1.8f)
            val result = PredictionsEngine.points(testPrediction(match, 0, 0))
            // 1 (partial) + 1.8 (quota) = 2.8
            assertEquals(2.8f, result)
        }

        @Test
        fun `points returns 0 for match with no goals recorded`() {
            val match = testMatch(homeGoals = 2, awayGoals = 1).copy(homeGoals = null, awayGoals = null)
            val result = PredictionsEngine.points(testPrediction(match, 2, 1))
            assertEquals(0f, result)
        }

        @Test
        fun `points rounds result to 2 decimal places`() {
            // homeQuota=1.33333, match: home=1, away=0; prediction: home=2, away=0
            // HOME wins in both → PARTIAL → 1 + 1.333 = 2.333 → rounds to 2.33
            val match = testMatch(homeGoals = 1, awayGoals = 0, homeQuota = 1.3333333f)
            val result = PredictionsEngine.points(testPrediction(match, 2, 0))
            assertEquals(2.33f, result)
        }
    }

    @Nested
    inner class CheckPredictionsTests {

        @Test
        fun `checkPredictions marks an INCORRECT prediction`() {
            val match = testMatch(1, 1)
            val prediction = testPrediction(match, 1, 0)
            val results = PredictionsEngine.checkPredictions(listOf(prediction))

            assertEquals(1, results.size)
            assertEquals(PredictionStatus.INCORRECT, results[0].status)
        }

        @Test
        fun `checkPredictions marks a PARTIAL prediction`() {
            val match = testMatch(1, 1)
            val prediction = testPrediction(match, 0, 0)
            val results = PredictionsEngine.checkPredictions(listOf(prediction))

            assertEquals(1, results.size)
            assertEquals(PredictionStatus.PARTIAL, results[0].status)
        }

        @Test
        fun `checkPredictions marks a CORRECT prediction`() {
            val match = testMatch(1, 1)
            val prediction = testPrediction(match, 1, 1)
            val results = PredictionsEngine.checkPredictions(listOf(prediction))

            assertEquals(1, results.size)
            assertEquals(PredictionStatus.CORRECT, results[0].status)
        }

        @Test
        fun `checkPredictions marks a BONUS prediction`() {
            val match = testMatch(3, 3)
            val prediction = testPrediction(match, 3, 3)
            val results = PredictionsEngine.checkPredictions(listOf(prediction))

            assertEquals(1, results.size)
            assertEquals(PredictionStatus.BONUS, results[0].status)
        }
    }

    @Nested
    inner class UpdateStandingsTests {

        private val memberId1 = UUID.randomUUID()
        private val member1 = GroupUser(group = anyGroup,
            user = anyUser.copy(id = memberId1), joinedAt = LocalDateTime.now())
        private val memberId2 = UUID.randomUUID()
        private val member2 = GroupUser(group = anyGroup,
            user = anyUser.copy(id = memberId2), joinedAt = LocalDateTime.now().minus(1, ChronoUnit.MINUTES))
        private val members = listOf(member1, member2)

        @Test
        fun `updateStandings returns the same members with no changes if there are no new predictions`() {
            val rank1 = PredictionsEngine.updateStandings(members, emptyMap())
            val rank2 = PredictionsEngine.updateStandings(rank1, emptyMap())
            assertEquals(rank2, rank1)
        }

        @Test
        fun `updateStandings ignores multiple predictions for the same match, considering only the first one`() {
            val match = testMatch(homeGoals = 1, awayGoals = 0)
            val predictions = mapOf(
                memberId1 to listOf(
                    testPrediction(match, homeGoals = 2, awayGoals = 0),
                    testPrediction(match, homeGoals = 1, awayGoals = 0),
                ),
            )

            val newRank = PredictionsEngine.updateStandings(listOf(member1), predictions)
            assertEquals(member1.id, newRank[0].id)
            assertEquals(1, newRank[0].rank)
            assertEquals(1f, newRank[0].points)
        }

        @Test
        fun `updateStandings returns a list of the last 5 predictions (truncating it if there are more)`() {
            val match1 = testMatch(homeGoals = 1, awayGoals = 0)
            val match2 = testMatch(homeGoals = 1, awayGoals = 0)
            val match3 = testMatch(homeGoals = 1, awayGoals = 0)
            val newPredictions1 = mapOf(
                memberId1 to listOf(
                    testPrediction(match1, homeGoals = 1, awayGoals = 0),
                    testPrediction(match2, homeGoals = 2, awayGoals = 0),
                    testPrediction(match3, homeGoals = 0, awayGoals = 0),
                ),
            )

            val newRank1 = PredictionsEngine.updateStandings(listOf(member1), newPredictions1)
            assertEquals(3, newRank1[0].lastPredictions.size)
            assertEquals(PredictionStatus.CORRECT, newRank1[0].lastPredictions[0])
            assertEquals(PredictionStatus.PARTIAL, newRank1[0].lastPredictions[1])
            assertEquals(PredictionStatus.INCORRECT, newRank1[0].lastPredictions[2])

            val match4 = testMatch(homeGoals = 1, awayGoals = 0)
            val match5 = testMatch(homeGoals = 1, awayGoals = 0)
            val newPredictions2 = mapOf(
                memberId1 to listOf(
                    testPrediction(match4, homeGoals = 1, awayGoals = 0),
                    testPrediction(match5, homeGoals = 0, awayGoals = 0),
                ),
            )

            val newRank2 = PredictionsEngine.updateStandings(newRank1, newPredictions2)
            assertEquals(5, newRank2[0].lastPredictions.size)
            assertEquals(PredictionStatus.CORRECT, newRank2[0].lastPredictions[0])
            assertEquals(PredictionStatus.PARTIAL, newRank2[0].lastPredictions[1])
            assertEquals(PredictionStatus.INCORRECT, newRank2[0].lastPredictions[2])
            assertEquals(PredictionStatus.CORRECT, newRank2[0].lastPredictions[3])
            assertEquals(PredictionStatus.INCORRECT, newRank2[0].lastPredictions[4])

            val match6 = testMatch(homeGoals = 5, awayGoals = 0)
            val match7 = testMatch(homeGoals = 5, awayGoals = 0)
            val newPredictions3 = mapOf(
                memberId1 to listOf(
                    testPrediction(match6, homeGoals = 5, awayGoals = 0),
                    testPrediction(match7, homeGoals = 4, awayGoals = 0),
                ),
            )

            val newRank3 = PredictionsEngine.updateStandings(newRank2, newPredictions3)
            assertEquals(5, newRank3[0].lastPredictions.size)
            assertEquals(PredictionStatus.INCORRECT, newRank3[0].lastPredictions[0])
            assertEquals(PredictionStatus.CORRECT, newRank3[0].lastPredictions[1])
            assertEquals(PredictionStatus.INCORRECT, newRank3[0].lastPredictions[2])
            assertEquals(PredictionStatus.BONUS, newRank3[0].lastPredictions[3])
            assertEquals(PredictionStatus.PARTIAL, newRank3[0].lastPredictions[4])
        }

        @Test
        fun `updateStandings returns the new members ranked by points`() {
            val match = testMatch(homeGoals = 1, awayGoals = 0)
            val predictions = mapOf(
                memberId1 to listOf(testPrediction(match, homeGoals = 2, awayGoals = 0)),
                memberId2 to listOf(testPrediction(match, homeGoals = 1, awayGoals = 0)),
            )

            val newRank = PredictionsEngine.updateStandings(members, predictions)
            assertEquals(member2.id, newRank[0].id)
            assertEquals(1, newRank[0].rank)
            assertEquals(member1.id, newRank[1].id)
            assertEquals(2, newRank[1].rank)
        }

        @Test
        fun `updateStandings returns the new members ranked by points (first dedup, general predictions)`() {
            val match1 = testMatch(homeGoals = 2, awayGoals = 0)
            val match2 = testMatch(homeGoals = 2, awayGoals = 0)
            val match3 = testMatch(homeGoals = 2, awayGoals = 0)
            val predictions = mapOf(
                memberId1 to listOf(testPrediction(match1, homeGoals = 2, awayGoals = 0)),
                memberId1 to listOf(testPrediction(match2, homeGoals = 0, awayGoals = 0)),
                memberId1 to listOf(testPrediction(match3, homeGoals = 0, awayGoals = 0)),
                memberId2 to listOf(testPrediction(match1, homeGoals = 1, awayGoals = 0)),
                memberId2 to listOf(testPrediction(match2, homeGoals = 1, awayGoals = 0)),
                memberId2 to listOf(testPrediction(match3, homeGoals = 1, awayGoals = 0)),
            )

            val newRank = PredictionsEngine.updateStandings(members, predictions)
            assertEquals(member1.id, member2.id)
            assertEquals(member2.id, newRank[0].id)
            assertEquals(1, newRank[0].rank)
            assertEquals(member1.id, newRank[1].id)
            assertEquals(2, newRank[1].rank)
        }

        @Test
        fun `updateStandings returns the new members ranked by points (second dedup, exact predictions)`() {
            val match1 = testMatch(homeGoals = 2, awayGoals = 0)
            val match2 = testMatch(homeGoals = 2, awayGoals = 0)
            val match3 = testMatch(homeGoals = 2, awayGoals = 0, homeQuota = 2f)
            val predictions = mapOf(
                memberId1 to listOf(testPrediction(match1, homeGoals = 2, awayGoals = 0)),
                memberId1 to listOf(testPrediction(match2, homeGoals = 0, awayGoals = 0)),
                memberId1 to listOf(testPrediction(match3, homeGoals = 1, awayGoals = 0)),
                memberId2 to listOf(testPrediction(match1, homeGoals = 2, awayGoals = 0)),
                memberId2 to listOf(testPrediction(match2, homeGoals = 2, awayGoals = 0)),
                memberId2 to listOf(testPrediction(match3, homeGoals = 0, awayGoals = 0)),
            )

            val newRank = PredictionsEngine.updateStandings(members, predictions)
            assertEquals(member1.id, member2.id)
            assertEquals(member2.id, newRank[0].id)
            assertEquals(1, newRank[0].rank)
            assertEquals(member1.id, newRank[1].id)
            assertEquals(2, newRank[1].rank)
        }

        @Test
        fun `updateStandings returns the new members ranked by points (third dedup, bonus predictions)`() {
            val match1 = testMatch(homeGoals = 5, awayGoals = 0)
            val match2 = testMatch(homeGoals = 2, awayGoals = 0)
            val match3 = testMatch(homeGoals = 2, awayGoals = 0)
            val match4 = testMatch(homeGoals = 2, awayGoals = 0, homeQuota = 2f)
            val predictions = mapOf(
                memberId1 to listOf(testPrediction(match1, homeGoals = 0, awayGoals = 0)),
                memberId1 to listOf(testPrediction(match2, homeGoals = 2, awayGoals = 0)),
                memberId1 to listOf(testPrediction(match3, homeGoals = 2, awayGoals = 0)),
                memberId1 to listOf(testPrediction(match4, homeGoals = 1, awayGoals = 0)),
                memberId2 to listOf(testPrediction(match1, homeGoals = 5, awayGoals = 0)),
                memberId2 to listOf(testPrediction(match2, homeGoals = 2, awayGoals = 0)),
                memberId2 to listOf(testPrediction(match3, homeGoals = 0, awayGoals = 0)),
                memberId2 to listOf(testPrediction(match4, homeGoals = 1, awayGoals = 0)),
            )

            val newRank = PredictionsEngine.updateStandings(members, predictions)
            assertEquals(member1.id, member2.id)
            assertEquals(member2.id, newRank[0].id)
            assertEquals(1, newRank[0].rank)
            assertEquals(member1.id, newRank[1].id)
            assertEquals(2, newRank[1].rank)
        }

        @Test
        fun `updateStandings returns the new members ranked by points (fourth dedup, joined-at)`() {
            val match1 = testMatch(homeGoals = 5, awayGoals = 0)
            val match2 = testMatch(homeGoals = 2, awayGoals = 0)
            val match3 = testMatch(homeGoals = 2, awayGoals = 0)
            val predictions = mapOf(
                memberId1 to listOf(testPrediction(match1, homeGoals = 5, awayGoals = 0)),
                memberId1 to listOf(testPrediction(match2, homeGoals = 2, awayGoals = 0)),
                memberId1 to listOf(testPrediction(match3, homeGoals = 2, awayGoals = 0)),
                memberId2 to listOf(testPrediction(match1, homeGoals = 5, awayGoals = 0)),
                memberId2 to listOf(testPrediction(match2, homeGoals = 2, awayGoals = 0)),
                memberId2 to listOf(testPrediction(match3, homeGoals = 2, awayGoals = 0)),
            )

            val newRank = PredictionsEngine.updateStandings(members, predictions)
            assertEquals(member1.id, member2.id)
            assertEquals(member2.id, newRank[0].id)
            assertEquals(1, newRank[0].rank)
            assertEquals(member1.id, newRank[1].id)
            assertEquals(2, newRank[1].rank)
        }
    }
}
