package com.grondona.model.dto.request

import java.util.UUID
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.PositiveOrZero

data class SubmitMatchPredictionRequest(
    @field:NotNull(message = "Match ID is required")
    val matchId: UUID,

    @field:PositiveOrZero(message = "Home goals must be at least zero")
    val homeGoals: Int,

    @field:PositiveOrZero(message = "Away goals must be at least zero")
    val awayGoals: Int
)

data class SubmitBulkMatchPredictionsRequest(
    @field:NotEmpty(message = "Expected at least one prediction")
    val predictions: List<SubmitMatchPredictionRequest> = emptyList()
)

data class SubmitAwardPredictionRequest(
    val champions: List<UUID>,
    val topScorers: List<UUID>,
    val bestPlayers: List<UUID>,
    val bestGoalkeepers: List<UUID>,
    val bestYoungPlayers: List<UUID>,
)
