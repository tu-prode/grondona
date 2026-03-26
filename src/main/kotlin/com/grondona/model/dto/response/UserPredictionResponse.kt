package com.grondona.model.dto.response

import java.util.UUID

data class UserPredictionResponse(
    val id: UUID,
    val match: MatchResponse,
    val homeTeam: TeamResponse,
    val awayTeam: TeamResponse,
    val homeGoals: Int,
    val awayGoals: Int,
)

data class UserPredictionsResponse(
    val groupId: UUID,
    val groupName: String,
    val tournamentId: UUID,
    val tournamentName: String,
    val predictions: List<UserPredictionResponse>
)
