package com.grondona.model.dto.request

import com.grondona.model.TournamentStatus
import javax.validation.constraints.NotBlank

data class CreateTournamentRequest(
    @field:NotBlank(message = "Name is required")
    val name: String,

    val status: TournamentStatus? = TournamentStatus.NOT_STARTED
)
