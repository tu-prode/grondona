package com.grondona.model.dto.response

import com.grondona.model.AwardPrediction
import com.grondona.model.AwardPredictionView
import com.grondona.model.AwardType
import com.grondona.model.Group
import com.grondona.model.MatchPrediction
import com.grondona.model.PredictionStatus
import com.grondona.model.MatchPredictionView
import com.grondona.model.User
import java.util.UUID

data class ScorePredictionResponse(
    val homeGoals: Int,
    val awayGoals: Int,
    val status: PredictionStatus,
) {
    companion object {
        fun from(prediction: MatchPrediction): ScorePredictionResponse = ScorePredictionResponse(
            homeGoals = prediction.homeGoals,
            awayGoals = prediction.awayGoals,
            status = prediction.status,
        )
    }
}

data class MatchPredictionResponse(
    val id: UUID?,
    val user: UserResponse,
    val match: MatchResponse,
    val prediction: ScorePredictionResponse?
) {
    companion object {
        fun from(prediction: MatchPrediction): MatchPredictionResponse = MatchPredictionResponse(
            id = prediction.id,
            user = UserResponse.from(prediction.user),
            match = MatchResponse.from(prediction.match),
            prediction = ScorePredictionResponse.from(prediction)
        )

        fun fromPredictionView(view: MatchPredictionView): MatchPredictionResponse = MatchPredictionResponse(
            id = view.id,
            user = UserResponse.from(view.user),
            match = MatchResponse.from(view.match),
            prediction = view.prediction?.let(ScorePredictionResponse::from)
        )
    }
}

data class GroupMatchPredictionsResponse(
    val groupId: UUID,
    val groupName: String,
    val predictions: List<MatchPredictionResponse>
) {
    companion object {
        fun fromPredictions(group: Group, predictions: List<MatchPrediction>): GroupMatchPredictionsResponse =
            GroupMatchPredictionsResponse(
                groupId = group.id!!,
                groupName = group.name,
                predictions = predictions.map(MatchPredictionResponse::from),
            )

        fun fromMatchPredictionViews(
            group: Group,
            predictions: List<MatchPredictionView>
        ): GroupMatchPredictionsResponse =
            GroupMatchPredictionsResponse(
                groupId = group.id!!,
                groupName = group.name,
                predictions = predictions.map(MatchPredictionResponse::fromPredictionView),
            )
    }
}

data class AwardPredictionsResponse(
    val user: UserResponse,
    val champions: List<TeamResponse> = emptyList(),
    val topScorers: List<PlayerResponse> = emptyList(),
    val bestPlayers: List<PlayerResponse> = emptyList(),
    val bestGoalkeepers: List<PlayerResponse> = emptyList(),
    val bestYoungPlayers: List<PlayerResponse> = emptyList(),
) {
    companion object {
        fun fromAwardPredictions(user: User, awardPredictions: List<AwardPrediction>): AwardPredictionsResponse =
            AwardPredictionsResponse(
                user = UserResponse.from(user),
                champions = awardPredictions.filter { it.awardType == AwardType.CHAMPION }
                    .mapNotNull { award -> award.team?.let { TeamResponse.from(it) } },
                topScorers = awardPredictions.filter { it.awardType == AwardType.TOP_SCORER }
                    .mapNotNull { award -> award.player?.let { PlayerResponse.from(it) } },
                bestPlayers = awardPredictions.filter { it.awardType == AwardType.BEST_PLAYER }
                    .mapNotNull { award -> award.player?.let { PlayerResponse.from(it) } },
                bestGoalkeepers = awardPredictions.filter { it.awardType == AwardType.BEST_GOALKEEPER }
                    .mapNotNull { award -> award.player?.let { PlayerResponse.from(it) } },
                bestYoungPlayers = awardPredictions.filter { it.awardType == AwardType.BEST_YOUNG_PLAYER }
                    .mapNotNull { award -> award.player?.let { PlayerResponse.from(it) } },
            )

        fun fromAwardPredictionsViews(user: User, awardPredictions: List<AwardPredictionView>): AwardPredictionsResponse =
            AwardPredictionsResponse(
                user = UserResponse.from(user),
                champions = awardPredictions.filter { it.awardPrediction?.awardType == AwardType.CHAMPION }
                    .mapNotNull { award -> award.awardPrediction?.team?.let { TeamResponse.from(it) } },
                topScorers = awardPredictions.filter { it.awardPrediction?.awardType == AwardType.TOP_SCORER }
                    .mapNotNull { award -> award.awardPrediction?.player?.let { PlayerResponse.from(it) } },
                bestPlayers = awardPredictions.filter { it.awardPrediction?.awardType == AwardType.BEST_PLAYER }
                    .mapNotNull { award -> award.awardPrediction?.player?.let { PlayerResponse.from(it) } },
                bestGoalkeepers = awardPredictions.filter { it.awardPrediction?.awardType == AwardType.BEST_GOALKEEPER }
                    .mapNotNull { award -> award.awardPrediction?.player?.let { PlayerResponse.from(it) } },
                bestYoungPlayers = awardPredictions.filter { it.awardPrediction?.awardType == AwardType.BEST_YOUNG_PLAYER }
                    .mapNotNull { award -> award.awardPrediction?.player?.let { PlayerResponse.from(it) } },
            )
    }
}

data class GroupAwardPredictionsResponse(
    val groupId: UUID,
    val groupName: String,
    val predictions: List<AwardPredictionsResponse>
) {
    companion object {
        fun fromAwardPredictionsViews(group: Group, awardPredictions: List<AwardPredictionView>): GroupAwardPredictionsResponse =
            awardPredictions.groupBy { it.user }
                .mapValues { AwardPredictionsResponse.fromAwardPredictionsViews(it.key, it.value) }
                .let { GroupAwardPredictionsResponse(groupId = group.id!!, groupName = group.name, predictions = it.values.toList()) }
    }
}
