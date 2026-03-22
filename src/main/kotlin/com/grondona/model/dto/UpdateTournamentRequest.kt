package com.grondona.model.dto

import com.grondona.model.TournamentStatus

data class UpdateTournamentRequest(
    val name: String?,

    val status: TournamentStatus? = TournamentStatus.NOT_STARTED
)
