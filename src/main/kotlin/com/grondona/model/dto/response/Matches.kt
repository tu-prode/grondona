package com.grondona.model.dto.response

import com.grondona.model.Match
import com.grondona.model.MatchStatus
import com.grondona.model.Tournament
import java.time.LocalDateTime
import java.util.UUID
import kotlin.math.min

data class MatchResponse(
    val id: UUID,
    val code: String,
    val homeTeam: TeamResponse,
    val awayTeam: TeamResponse,
    val homeQuota: Float,
    val awayQuota: Float,
    val tieQuota: Float,
    val status: MatchStatus,
    val substatus: String?,
    val startedAt: LocalDateTime?,
    val finishedAt: LocalDateTime?,
    val homeGoals: Int?,
    val awayGoals: Int?,
    val homePenalties: Int?,
    val awayPenalties: Int?,
) {
    companion object {
        fun from(match: Match): MatchResponse = MatchResponse(
            id = match.id!!,
            code = match.code,
            homeTeam = TeamResponse.from(match.homeTeam),
            awayTeam = TeamResponse.from(match.awayTeam),
            homeQuota = match.homeQuota,
            awayQuota = match.awayQuota,
            tieQuota = match.tieQuota,
            status = match.status,
            substatus = match.substatus,
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
    val tournamentId: UUID,
    val tournamentName: String,
    val pastMatches: List<MatchResponse>,
    val liveMatches: List<MatchResponse>,
    val nextMatches: List<MatchResponse>,
) {
    companion object {
        fun from(tournament: Tournament, matches: List<Match>, past: Int?, next: Int?, live: Int?): TournamentMatchesResponse {
            var pastMatches = matches.filter { it.status == MatchStatus.FINISHED }.map(MatchResponse::from)
            var liveMatches = matches.filter { it.status == MatchStatus.IN_PROGRESS }.map(MatchResponse::from)
            var nextMatches = matches.filter { it.status == MatchStatus.NOT_STARTED }.map(MatchResponse::from)

            pastMatches = past?.let { pastMatches.subList(0, min(it, pastMatches.lastIndex)) } ?: pastMatches
            liveMatches = live?.let { liveMatches.subList(0, min(it, liveMatches.lastIndex)) } ?: liveMatches
            nextMatches = next?.let { nextMatches.subList(0, min(it, nextMatches.lastIndex)) } ?: nextMatches
            return TournamentMatchesResponse(
                tournamentId = tournament.id!!,
                tournamentName = tournament.name,
                pastMatches = pastMatches,
                liveMatches = liveMatches,
                nextMatches = nextMatches,
            )
        }
    }
}
