package com.grondona.utils

import com.grondona.model.MatchOutcome
import com.grondona.model.MatchStatus
import com.grondona.model.Prediction
import com.grondona.model.PredictionStatus
import com.grondona.model.Score
import org.slf4j.LoggerFactory

object PredictionEngine {

    private const val POINTS_PARTIAL = 1
    private const val POINTS_CORRECT = 3
    private const val POINTS_BONUS = 5
    private val logger = LoggerFactory.getLogger(PredictionEngine::class.java)

    fun check(predictions: List<Prediction>): List<Prediction> =
        predictions.toMutableList().map { prediction ->
            if (prediction.status == PredictionStatus.PENDING && prediction.match.status == MatchStatus.FINISHED) {
                val matchScore = prediction.match.score()
                if (matchScore == null) {
                    logger.error("Match with id={} has no goals submitted but status FINISHED", prediction.match.id)
                } else {
                    val predictionScore = prediction.score()

                    when {
                        matchScore == predictionScore && matchScore.goals() >= 5 -> prediction.status = PredictionStatus.BONUS
                        matchScore == predictionScore -> prediction.status = PredictionStatus.CORRECT
                        matchScore.outcome() == predictionScore.outcome() -> prediction.status = PredictionStatus.PARTIAL
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
                points += when (prediction.status) {
                    PredictionStatus.BONUS -> POINTS_BONUS
                    PredictionStatus.CORRECT -> POINTS_CORRECT
                    PredictionStatus.PARTIAL -> POINTS_PARTIAL
                    else -> 0
                }

                val matchOutcome = matchScore.outcome()
                if (matchOutcome == prediction.score().outcome()) {
                    points += when (matchOutcome) {
                        MatchOutcome.HOME -> prediction.match.homeQuota
                        MatchOutcome.TIE -> prediction.match.tieQuota
                        MatchOutcome.AWAY -> prediction.match.awayQuota
                    }
                }
            }

            points
        }.sum().round()
}
