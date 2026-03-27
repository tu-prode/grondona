package com.grondona.model.dto.request

import java.util.UUID
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.PositiveOrZero

data class SubmitPredictionRequest(
    @field:NotNull(message = "Match ID is required")
    val matchId: UUID,

    @field:PositiveOrZero(message = "Home goals must be at least zero")
    val homeGoals: Int,

    @field:PositiveOrZero(message = "Away goals must be at least zero")
    val awayGoals: Int
)

data class SubmitBulkPredictionsRequest(
    val predictions: List<SubmitPredictionRequest> = emptyList()
)
