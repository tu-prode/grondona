package com.grondona.model.dto

import com.grondona.model.Match
import com.grondona.model.MatchStatus
import com.grondona.model.Team
import com.grondona.model.Tournament
import com.grondona.model.TournamentStatus
import com.grondona.model.User
import org.springframework.beans.factory.Aware
import java.time.LocalDateTime
import java.util.UUID

data class TournamentMatchResponse(
    val id: UUID,
    val matchKey: String,
    val homeTeam: TeamResponse,
    val awayTeam: TeamResponse,
    val homeQuota: Float,
    val awayQuota: Float,
    val tieQuota: Float,
    val status: MatchStatus,
    val startedAt: LocalDateTime?,
    val finishedAt: LocalDateTime?,
    val homeGoals: Int?,
    val awayGoals: Int?,
    val homePenalties: Int?,
    val awayPenalties: Int?,
) {
    companion object {
        fun from(match: Match): TournamentMatchResponse = TournamentMatchResponse(
            id = match.id!!,
            matchKey = match.matchKey,
            homeTeam = TeamResponse.from(match.homeTeam),
            awayTeam = TeamResponse.from(match.awayTeam),
            homeQuota = match.homeQuota,
            awayQuota = match.awayQuota,
            tieQuota = match.tieQuota,
            status = match.status,
            startedAt = match.startedAt,
            finishedAt = match.finishedAt,
            homeGoals = match.homeGoals,
            awayGoals = match.awayGoals,
            homePenalties = match.homePenalties,
            awayPenalties = match.awayPenalties,
        )
    }
}

data class TournamentMatchesResponse(
    val id: UUID,
    val name: String,
    val matches: List<TournamentMatchResponse>,
) {
    companion object {
        fun from(tournament: Tournament, matches: List<Match>): TournamentMatchesResponse = TournamentMatchesResponse(
            id = tournament.id!!,
            name = tournament.name,
            matches = matches.map(TournamentMatchResponse::from)
        )
    }
}
