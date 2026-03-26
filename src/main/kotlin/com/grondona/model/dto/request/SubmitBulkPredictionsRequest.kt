package com.grondona.model.dto.request

import java.util.UUID
import javax.validation.constraints.NotBlank
import javax.validation.constraints.PositiveOrZero

data class SubimtPredictionRequest(
    @field:NotBlank(message = "Match ID is required")
    val matchId: UUID,

    @field:PositiveOrZero
    @field:NotBlank(message = "Home goals are required")
    val homeGoals: Int,

    @field:PositiveOrZero
    @field:NotBlank(message = "Away goals are required")
    val awayGoals: Int
)

data class SubmitBulkPredictionsRequest(
    val predictions: List<SubimtPredictionRequest> = emptyList()
)
