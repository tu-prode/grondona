package com.grondona.model.dto

import com.grondona.model.TournamentStatus
import javax.validation.constraints.Email
import javax.validation.constraints.NotBlank
import javax.validation.constraints.Size

data class CreateTournamentRequest(
    @field:NotBlank(message = "Name is required")
    val name: String,

    val status: TournamentStatus? = TournamentStatus.NOT_STARTED
)
