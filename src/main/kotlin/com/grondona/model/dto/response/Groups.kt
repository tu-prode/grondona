package com.grondona.model.dto.response

import com.fasterxml.jackson.annotation.JsonProperty
import com.grondona.model.Group
import com.grondona.model.PredictionStatus
import com.grondona.model.Standing
import java.util.UUID

data class StandingResponse(
    val userId: UUID,
    val username: String,
    val points: Float,
    val rank: Int,
    val lastPredictions: List<PredictionStatus>
)

data class GroupResponse(
    val id: UUID,
    val tournamentId: UUID,
    val name: String,
    @get:JsonProperty("private")
    val isPrivate: Boolean,
    val maxMembers: Int,
    val standings: List<StandingResponse> = emptyList()
) {
    companion object {
        fun from(group: Group): GroupResponse = GroupResponse(
            id = group.id!!,
            tournamentId = group.tournament.id!!,
            name = group.name,
            isPrivate = group.isPrivate,
            maxMembers = group.maxMembers,
            standings = emptyList(),
        )

        fun from(group: Group, standings: List<Standing>): GroupResponse = GroupResponse(
            id = group.id!!,
            tournamentId = group.tournament.id!!,
            name = group.name,
            isPrivate = group.isPrivate,
            maxMembers = group.maxMembers,
            standings = standings.map { standing ->
                StandingResponse(
                    userId = standing.user.id!!,
                    username = standing.user.username,
                    points = standing.points,
                    rank = standing.rank,
                    lastPredictions = standing.lastPredictions,
                )
            }
        )
    }
}
