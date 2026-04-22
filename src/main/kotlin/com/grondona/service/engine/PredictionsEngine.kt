package com.grondona.service.engine

import com.grondona.model.AwardPrediction
import com.grondona.model.AwardType
import com.grondona.model.GroupUser
import com.grondona.model.MatchOutcome
import com.grondona.model.MatchPrediction
import com.grondona.model.PredictionStatus
import com.grondona.model.TournamentStatus
import com.grondona.utils.round
import org.slf4j.LoggerFactory
import java.util.UUID
import kotlin.collections.get
import kotlin.collections.plus

object PredictionsEngine {

    private const val POINTS_PARTIAL = 1
    private const val POINTS_CORRECT = 3
    private const val POINTS_BONUS = 5

    private const val POINTS_SINGLE_CHAMPION = 10
    private const val POINTS_DOUBLE_CHAMPION = 5
    private const val POINTS_SINGLE_PLAYER = 12
    private const val POINTS_DOUBLE_PLAYER = 8
    private const val POINTS_TRIPLE_PLAYER = 4

    private val logger = LoggerFactory.getLogger(PredictionsEngine::class.java)

    private fun awardStatus(isCorrect: Boolean) = if (isCorrect) PredictionStatus.CORRECT else PredictionStatus.INCORRECT

    fun checkMatchPredictions(predictions: List<MatchPrediction>): List<MatchPrediction> =
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

    fun checkAwardPredictions(predictions: List<AwardPrediction>): List<AwardPrediction> {
        if (predictions.isEmpty() || predictions[0].group.tournament.status != TournamentStatus.FINISHED) {
            return emptyList()
        }

        val awards = predictions[0].group.tournament.awards
        return awards?.let {
            predictions.toMutableList().map { prediction ->
                when (prediction.awardType) {
                    AwardType.CHAMPION -> prediction.status = awardStatus(awards.champion == prediction.team!!.id)
                    AwardType.TOP_SCORER -> prediction.status = awardStatus(awards.topScorer == prediction.player!!.id)
                    AwardType.BEST_PLAYER -> prediction.status = awardStatus(awards.bestPlayer == prediction.player!!.id)
                    AwardType.BEST_GOALKEEPER -> prediction.status = awardStatus(awards.bestGoalkeeper == prediction.player!!.id)
                    AwardType.BEST_YOUNG_PLAYER -> prediction.status = awardStatus(awards.bestYoungPlayer == prediction.player!!.id)
                }
                prediction
            }
        } ?: emptyList()
    }

    fun updateMatchPoints(members: List<GroupUser>, newPredictions: Map<UUID, List<MatchPrediction?>>): List<GroupUser> =
        members.map { member ->
            val matchesApplied: MutableSet<UUID> = mutableSetOf()
            newPredictions[member.user.id].orEmpty().also {
                if (it.isEmpty()) {
                    logger.error("No match predictions for user={} in group={}", member.user, member.group)
                }
            }.forEach { prediction ->
                if (prediction == null) {
                    member.lastPredictions + PredictionStatus.MISSING
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

                        member.points += matchPoints(prediction)
                    }
                }
            }

            member.lastPredictions = member.lastPredictions.takeLast(5)
            member
        }.rank()

    fun updateAwardPoints(members: List<GroupUser>, predictions: Map<UUID, List<AwardPrediction>>): List<GroupUser> =
        members.map { member ->
            if (member.group.tournament.status != TournamentStatus.FINISHED) {
                val memberPredictions = predictions[member.user.id].orEmpty()
                if (memberPredictions.isEmpty()) {
                    logger.debug("No awards predictions for user={} in group={}", member.user, member.group)
                }
                member.points += awardPoints(memberPredictions)
            }
            member
        }.rank()

    internal fun matchPoints(prediction: MatchPrediction): Float {
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
                    MatchOutcome.TIE -> prediction.match.drawQuota
                    MatchOutcome.AWAY -> prediction.match.awayQuota
                }
            }
        }

        return points.round()
    }

    internal fun awardPoints(predictions: List<AwardPrediction>): Float {
        var points = 0f
        if (predictions.isEmpty()) {
            return points
        }

        val predictedChampions = predictions.filter { it.awardType == AwardType.CHAMPION }
        val predictedTopScorers = predictions.filter { it.awardType == AwardType.TOP_SCORER }
        val predictedBestPlayer = predictions.filter { it.awardType == AwardType.BEST_PLAYER }
        val predictedBestGoalkeepers = predictions.filter { it.awardType == AwardType.BEST_GOALKEEPER }
        val predictedBestYoungPlayer = predictions.filter { it.awardType == AwardType.BEST_YOUNG_PLAYER }

        if (predictedChampions.any { it.status == PredictionStatus.CORRECT }) {
            when (predictedChampions.size) {
                1 -> points += POINTS_SINGLE_CHAMPION
                2 -> points += POINTS_DOUBLE_CHAMPION
            }
        }

        if (predictedTopScorers.any { it.status == PredictionStatus.CORRECT }) {
            when (predictedTopScorers.size) {
                1 -> points += POINTS_SINGLE_PLAYER
                2 -> points += POINTS_DOUBLE_PLAYER
                3 -> points += POINTS_TRIPLE_PLAYER
            }
        }

        if (predictedBestPlayer.any { it.status == PredictionStatus.CORRECT }) {
            when (predictedBestPlayer.size) {
                1 -> points += POINTS_SINGLE_PLAYER
                2 -> points += POINTS_DOUBLE_PLAYER
                3 -> points += POINTS_TRIPLE_PLAYER
            }
        }

        if (predictedBestGoalkeepers.any { it.status == PredictionStatus.CORRECT }) {
            when (predictedBestGoalkeepers.size) {
                1 -> points += POINTS_SINGLE_PLAYER
                2 -> points += POINTS_DOUBLE_PLAYER
                3 -> points += POINTS_TRIPLE_PLAYER
            }
        }

        if (predictedBestYoungPlayer.any { it.status == PredictionStatus.CORRECT }) {
            when (predictedBestYoungPlayer.size) {
                1 -> points += POINTS_SINGLE_PLAYER
                2 -> points += POINTS_DOUBLE_PLAYER
                3 -> points += POINTS_TRIPLE_PLAYER
            }
        }

        return points.round()
    }

    fun List<GroupUser>.rank() = this.sortedWith(
        compareByDescending<GroupUser> { it.points }
            .thenByDescending { it.amountBonus + it.amountCorrect + it.amountPartial }
            .thenByDescending { it.amountBonus + it.amountCorrect }
            .thenByDescending { it.amountBonus }
            .thenBy { it.joinedAt }
    ).mapIndexed { index, member -> member.rank = index + 1; member }
}