package com.grondona.model.dto

import com.grondona.model.TournamentStatus
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class CreateTournamentRequest(
    @field:NotBlank(message = "Name is required")
    val name: String,

    val status: TournamentStatus? = TournamentStatus.NOT_STARTED
)
