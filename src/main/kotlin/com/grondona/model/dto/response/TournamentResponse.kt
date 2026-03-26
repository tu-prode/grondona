package com.grondona.model.dto.response

import com.grondona.model.Tournament
import com.grondona.model.TournamentStatus
import java.util.UUID

data class TournamentResponse(
    val id: UUID,
    val name: String,
    val status: TournamentStatus,
) {
    companion object {
        fun from(tournament: Tournament): TournamentResponse = TournamentResponse(
            id = tournament.id!!,
            name = tournament.name,
            status = tournament.status,
        )
    }
}
