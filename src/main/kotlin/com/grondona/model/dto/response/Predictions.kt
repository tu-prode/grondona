package com.grondona.model.dto.response

import com.grondona.model.Group
import com.grondona.model.Prediction
import com.grondona.model.Score
import java.util.UUID

data class PredictionScoreResponse(
    val homeGoals: Int,
    val awayGoals: Int,
) {
    companion object {
        fun from(score: Score): PredictionScoreResponse = PredictionScoreResponse(
            homeGoals = score.homeGoals,
            awayGoals = score.awayGoals,
        )
    }
}

data class PredictionResponse(
    val id: UUID,
    val user: UserResponse,
    val match: MatchResponse,
    val predictedScore: PredictionScoreResponse?
) {
    companion object {
        fun from(prediction: Prediction): PredictionResponse = PredictionResponse(
            id = prediction.id!!,
            user = UserResponse.from(prediction.user),
            match = MatchResponse.from(prediction.match),
            predictedScore = prediction.score()?.let(PredictionScoreResponse::from)
        )
    }
}

data class GroupPredictionsResponse(
    val groupId: UUID,
    val groupName: String,
    val predictions: List<PredictionResponse>
) {
    companion object {
        fun from(group: Group, predictions: List<Prediction>): GroupPredictionsResponse = GroupPredictionsResponse(
            groupId = group.id!!,
            groupName = group.name,
            predictions = predictions.map(PredictionResponse::from),
        )
    }
}
