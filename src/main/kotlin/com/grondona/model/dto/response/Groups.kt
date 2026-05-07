package com.grondona.model.dto.response

import com.fasterxml.jackson.annotation.JsonProperty
import com.grondona.model.Group
import com.grondona.model.GroupRole
import com.grondona.model.GroupUser
import com.grondona.model.PredictionStatus
import com.grondona.model.Standing
import com.grondona.model.TournamentStatus
import com.grondona.utils.round
import java.util.UUID

data class StandingResponse(
    val user: UserResponse,
    val points: Float,
    val rank: Int,
    val lastPredictions: List<PredictionStatus>
) {
    companion object {
        fun from(standing: Standing) = StandingResponse(
            user = UserResponse.from(standing.user),
            points = standing.points.round(),
            rank = standing.rank,
            lastPredictions = standing.lastPredictions,
        )
    }
}

data class GroupResponse(
    val id: UUID,
    val tournamentId: UUID,
    val name: String,
    @get:JsonProperty("private")
    val isPrivate: Boolean,
    val maxMembers: Int,
    val totalMembers: Int,
    val hasStarted: Boolean,
    val standings: List<StandingResponse> = emptyList(),
    val candidates: List<UserResponse> = emptyList()
) {
    companion object {
        fun from(group: Group): GroupResponse = GroupResponse(
            id = group.id!!,
            tournamentId = group.tournament.id!!,
            name = group.name,
            isPrivate = group.isPrivate,
            maxMembers = group.maxMembers,
            totalMembers = group.members.filter { it.role != GroupRole.CANDIDATE }.size,
            hasStarted = group.tournament.status != TournamentStatus.NOT_STARTED,
            standings = emptyList(),
        )

        fun from(group: Group, standings: List<Standing>, candidates: List<GroupUser>): GroupResponse = GroupResponse(
            id = group.id!!,
            tournamentId = group.tournament.id!!,
            name = group.name,
            isPrivate = group.isPrivate,
            maxMembers = group.maxMembers,
            totalMembers = group.members.filter { it.role != GroupRole.CANDIDATE }.size,
            hasStarted = group.tournament.status != TournamentStatus.NOT_STARTED,
            standings = standings.map(StandingResponse::from),
            candidates = candidates.map { UserResponse.from(it.user) }
        )
    }
}
