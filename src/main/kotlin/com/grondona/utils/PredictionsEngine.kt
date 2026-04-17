package com.grondona.utils

import com.grondona.model.GroupUser
import com.grondona.model.MatchOutcome
import com.grondona.model.MatchPrediction
import com.grondona.model.PredictionStatus
import org.slf4j.LoggerFactory
import java.util.UUID

object PredictionsEngine {

    private const val POINTS_PARTIAL = 1
    private const val POINTS_CORRECT = 3
    private const val POINTS_BONUS = 5
    private val logger = LoggerFactory.getLogger(PredictionsEngine::class.java)

    fun checkPredictions(predictions: List<MatchPrediction>): List<MatchPrediction> =
        predictions.toMutableList().map { prediction ->
            val matchScore = prediction.match.score()
            if (matchScore == null) {
                logger.error("Trying to calculate the PredictionStatus of match with id={}, but has no goals submitted", prediction.match.id)
            } else {
                val predictionScore = prediction.score()

                when {
                    matchScore == predictionScore && matchScore.goals() >= 5 -> prediction.status = PredictionStatus.BONUS
                    matchScore == predictionScore -> prediction.status = PredictionStatus.CORRECT
                    matchScore.outcome() == predictionScore.outcome() -> prediction.status = PredictionStatus.PARTIAL
                    else -> prediction.status = PredictionStatus.INCORRECT
                }
            }

            prediction
        }

    fun updateStandings(members: List<GroupUser>, newPredictions: Map<UUID, List<MatchPrediction?>>): List<GroupUser> =
        members.map { member ->
            val matchesApplied: MutableSet<UUID> = mutableSetOf()
            newPredictions[member.user.id].orEmpty().also {
                if (it.isEmpty()) {
                    logger.error("No predictions for user={} in group={}", member.user, member.group)
                }
            }.forEach { prediction ->
                if (prediction == null) {
                    member.lastPredictions += PredictionStatus.MISSING
                } else {
                    if (!matchesApplied.contains(prediction.match.id)) {
                        matchesApplied.add(prediction.match.id!!)
                        member.lastPredictions += prediction.status
                        when (prediction.status) {
                            PredictionStatus.BONUS -> member.amountBonus++
                            PredictionStatus.CORRECT -> member.amountCorrect++
                            PredictionStatus.PARTIAL -> member.amountPartial++
                            else -> {}
                        }

                        member.points += points(prediction)
                    }
                }
            }

            member.lastPredictions = member.lastPredictions.takeLast(5)
            member
        }.sortedWith(
            compareByDescending<GroupUser> { it.points }
                .thenByDescending { it.amountBonus + it.amountCorrect + it.amountPartial }
                .thenByDescending { it.amountBonus + it.amountCorrect }
                .thenByDescending { it.amountBonus }
                .thenBy { it.joinedAt }
        ).mapIndexed { index, member -> member.rank = index + 1; member }

    internal fun points(prediction: MatchPrediction): Float {
        var points = 0f
        val matchScore = prediction.match.score()
        if (matchScore == null) {
            logger.error(
                "Match with id={} has no goals submitted but status FINISHED",
                prediction.match.id
            )
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

        return points.round()
    }
}
