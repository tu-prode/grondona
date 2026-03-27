package com.grondona.model.dto.response

import com.grondona.model.Group
import com.grondona.model.Prediction
import com.grondona.model.PredictionView
import java.util.UUID

data class PredictionScoreResponse(
    val homeGoals: Int,
    val awayGoals: Int,
) {
    companion object {
        fun from(prediction: Prediction): PredictionScoreResponse = PredictionScoreResponse(
            homeGoals = prediction.homeGoals,
            awayGoals = prediction.awayGoals,
        )
    }
}

data class PredictionResponse(
    val id: UUID?,
    val user: UserResponse,
    val match: MatchResponse,
    val predictedScore: PredictionScoreResponse?
) {
    companion object {
        fun from(prediction: Prediction): PredictionResponse = PredictionResponse(
            id = prediction.id,
            user = UserResponse.from(prediction.user),
            match = MatchResponse.from(prediction.match),
            predictedScore = PredictionScoreResponse.from(prediction)
        )

        fun fromPredictionView(view: PredictionView): PredictionResponse = PredictionResponse(
            id = view.id!!,
            user = UserResponse.from(view.user),
            match = MatchResponse.from(view.match),
            predictedScore = view.prediction?.let(PredictionScoreResponse::from)
        )
    }
}

data class GroupPredictionsResponse(
    val groupId: UUID,
    val groupName: String,
    val predictions: List<PredictionResponse>
) {
    companion object {
        fun fromPrediction(group: Group, predictions: List<Prediction>): GroupPredictionsResponse = GroupPredictionsResponse(
            groupId = group.id!!,
            groupName = group.name,
            predictions = predictions.map(PredictionResponse::from),
        )

        fun fromPredictionView(group: Group, predictions: List<PredictionView>): GroupPredictionsResponse = GroupPredictionsResponse(
            groupId = group.id!!,
            groupName = group.name,
            predictions = predictions.map(PredictionResponse::fromPredictionView),
        )
    }
}
