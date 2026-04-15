package com.grondona.model.dto.response

import com.grondona.model.Player
import com.grondona.model.PlayerPosition
import java.time.LocalDate
import java.util.UUID

data class PlayerResponse(
    val id: UUID,
    val name: String,
    val team: TeamResponse,
    val position: PlayerPosition,
    val birthdate: LocalDate,
) {
    companion object {
        fun from(player: Player): PlayerResponse = PlayerResponse(
            id = player.id!!,
            name = player.name,
            team = TeamResponse.from(player.team),
            position = player.position,
            birthdate = player.birthdate
        )
    }
}

data class TournamentPlayersResponse(
    val players: List<PlayerResponse>,
) {
    companion object {
        fun from(players: List<Player>) = TournamentPlayersResponse(
            players = players.map(PlayerResponse::from)
        )
    }
}
