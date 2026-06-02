package com.grondona.model.dto.response

import com.fasterxml.jackson.annotation.JsonInclude
import com.grondona.model.Match
import com.grondona.model.MatchGroup
import com.grondona.model.MatchStage
import com.grondona.model.MatchStatus
import com.grondona.model.Tournament
import java.time.ZonedDateTime
import java.util.UUID
import kotlin.math.min

data class MatchResponse(
    val id: UUID,
    val code: String,
    val homeTeam: TeamResponse,
    val awayTeam: TeamResponse,
    val stage: MatchStage,
    val group: MatchGroup? = null,
    val homeQuota: Float,
    val awayQuota: Float,
    val drawQuota: Float,
    val status: MatchStatus,
    val substatus: String?,
    val startedAt: ZonedDateTime?,
    val finishedAt: ZonedDateTime?,
    val homeGoals: Int?,
    val awayGoals: Int?,
    val homePenalties: Int?,
    val awayPenalties: Int?,
    val hasMultiplier: Boolean,
) {
    companion object {
        fun from(match: Match): MatchResponse = MatchResponse(
            id = match.id!!,
            code = match.code,
            homeTeam = TeamResponse.from(match.homeTeam),
            awayTeam = TeamResponse.from(match.awayTeam),
            stage = match.stage,
            group = match.group,
            homeQuota = match.homeQuota,
            awayQuota = match.awayQuota,
            drawQuota = match.drawQuota,
            status = match.status,
            substatus = match.substatus,
            startedAt = match.startedAt,
            finishedAt = match.finishedAt,
            homeGoals = match.homeGoals,
            awayGoals = match.awayGoals,
            homePenalties = match.homePenalties,
            awayPenalties = match.awayPenalties,
            hasMultiplier = match.hasMultiplier,
        )
    }
}

data class TournamentMatchesResponse(
    val tournamentId: UUID,
    val tournamentName: String,
    @JsonInclude(JsonInclude.Include.ALWAYS)
    val pastMatches: List<MatchResponse> = emptyList(),
    val totalPastMatches: Int = 0,
    @JsonInclude(JsonInclude.Include.ALWAYS)
    val liveMatches: List<MatchResponse> = emptyList(),
    val totalLiveMatches: Int = 0,
    @JsonInclude(JsonInclude.Include.ALWAYS)
    val nextMatches: List<MatchResponse> = emptyList(),
    val totalNextMatches: Int = 0,
) {
    companion object {
        fun from(tournament: Tournament, matches: List<Match>, past: Int? = null, next: Int? = null, live: Int? = null): TournamentMatchesResponse {
            var pastMatches = matches.filter { it.status == MatchStatus.FINISHED }.map(MatchResponse::from).sortedByDescending { it.startedAt }
            var liveMatches = matches.filter { it.status == MatchStatus.IN_PROGRESS }.map(MatchResponse::from)
            var nextMatches = matches.filter { it.status == MatchStatus.NOT_STARTED }.map(MatchResponse::from)

            pastMatches = past?.takeIf { pastMatches.isNotEmpty() }?.let { pastMatches.subList(0, min(it, pastMatches.lastIndex)) } ?: pastMatches
            liveMatches = live?.takeIf { liveMatches.isNotEmpty() }?.let { liveMatches.subList(0, min(it, liveMatches.lastIndex)) } ?: liveMatches
            nextMatches = next?.takeIf { nextMatches.isNotEmpty() }?.let { nextMatches.subList(0, min(it, nextMatches.lastIndex)) } ?: nextMatches
            return TournamentMatchesResponse(
                tournamentId = tournament.id,
                tournamentName = tournament.name,
                pastMatches = pastMatches,
                totalPastMatches = pastMatches.size,
                liveMatches = liveMatches,
                totalLiveMatches = liveMatches.size,
                nextMatches = nextMatches,
                totalNextMatches = nextMatches.size,
            )
        }
    }
}

data class SimpleMatchesResponse(
    val tournamentId: UUID,
    val tournamentName: String,
    val matches: List<MatchResponse>
) {
    companion object {
        fun from(tournament: Tournament, matches: List<Match>) = SimpleMatchesResponse(
            tournamentId = tournament.id,
            tournamentName = tournament.name,
            matches = matches.map(MatchResponse::from),
        )
    }
}
