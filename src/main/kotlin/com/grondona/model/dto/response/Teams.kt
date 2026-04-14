package com.grondona.model.dto.response

import com.grondona.model.Team
import java.util.UUID

data class TeamResponse(
    val id: UUID,
    val name: String,
    val code: String,
    val icon: String,
) {
    companion object {
        fun from(team: Team): TeamResponse = TeamResponse(
            id = team.id!!,
            name = team.name,
            code = team.code,
            icon = team.icon,
        )
    }
}

data class TournamentTeamsResponse(
    val teams: List<TeamResponse>,
) {
    companion object {
        fun from(teams: List<Team>) = TournamentTeamsResponse(
            teams = teams.map(TeamResponse::from)
        )
    }
}
