package com.grondona.model.dto.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.util.UUID

data class CronRequest(
    @field:NotBlank(message = "API key is required")
    val apiKey: String,

    @field:NotNull(message = "Tournament ID is required")
    val tournamentId: UUID,
)
