package com.grondona.utils

import com.grondona.model.MatchStatus
import com.grondona.model.Prediction
import com.grondona.model.PredictionStatus
import com.grondona.model.Score
import org.slf4j.LoggerFactory

object PredictionEngine {

    private const val POINTS_PARTIAL = 1
    private const val EXTRA_POINTS_CORRECT = 3
    private const val EXTRA_POINTS_FIVE_GOALS = 2
    private val logger = LoggerFactory.getLogger(PredictionEngine::class.java)

    private enum class MatchOutcome {
        HOME, TIE, AWAY
    }

    private fun outcome(score: Score) = when {
        score.homeGoals > score.awayGoals -> MatchOutcome.HOME
        score.homeGoals < score.awayGoals -> MatchOutcome.AWAY
        else -> MatchOutcome.TIE
    }

    fun check(predictions: List<Prediction>): List<Prediction> =
        predictions.toMutableList().map { prediction ->
            if (prediction.status == PredictionStatus.PENDING && prediction.match.status == MatchStatus.FINISHED) {
                val matchScore = prediction.match.score()
                if (matchScore == null) {
                    logger.error("Match with id={} has no goals submitted but status FINISHED", prediction.match.id)
                } else {
                    val predictionScore = prediction.score()

                    when {
                        matchScore == predictionScore -> prediction.status = PredictionStatus.CORRECT
                        outcome(matchScore) == outcome(predictionScore) -> prediction.status = PredictionStatus.PARTIAL
                        else -> prediction.status = PredictionStatus.INCORRECT
                    }
                }
            }

            prediction
        }

    fun points(predictions: List<Prediction>): Float =
        predictions.map { prediction ->
            var points = 0f

            val matchScore = prediction.match.score()
            if (matchScore == null) {
                logger.error("Match with id={} has no goals submitted but status FINISHED", prediction.match.id)
            } else {
                val predictionScore = prediction.score()
                if (outcome(matchScore) == outcome(predictionScore)) {
                    points += POINTS_PARTIAL
                }

                if (matchScore == predictionScore) {
                    points += EXTRA_POINTS_CORRECT
                }

                if (matchScore.goals() >= 5) {
                    points += EXTRA_POINTS_FIVE_GOALS
                }

                points += when (outcome(matchScore)) {
                    MatchOutcome.HOME -> prediction.match.homeQuota
                    MatchOutcome.TIE -> prediction.match.tieQuota
                    MatchOutcome.AWAY -> prediction.match.awayQuota
                }
            }

            points
        }.sum().round()
}
