package com.grondona.model.dto.request

import com.grondona.model.TournamentStatus

data class UpdateTournamentRequest(
    val name: String?,

    val status: TournamentStatus? = TournamentStatus.NOT_STARTED
)
