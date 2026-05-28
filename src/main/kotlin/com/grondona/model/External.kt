package com.grondona.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.grondona.exception.ExternalServiceException
import com.grondona.service.PredictionService
import com.grondona.utils.oddsToQuota
import java.time.ZonedDateTime

// Case data class extracted from the External client.
@JsonIgnoreProperties(ignoreUnknown = true)
data class ExternalMatch(
    val home: String,
    val away: String,
    val homeGoals: Int,
    val awayGoals: Int,
    val status: MatchStatus,
    val substatus: String? = null,
    val code: String? = null,
    val homePenalties: Int? = null,
    val awayPenalties: Int? = null,
    val homeOdds: Float? = null,
    val drawOdds: Float? = null,
    val awayOdds: Float? = null,
    val startedAt: ZonedDateTime? = null,
    val finishedAt: ZonedDateTime? = null,
) {
    fun toNewMatch(tournament: Tournament, availableTeams: Map<String, Team>): Match {
        if (code == null || startedAt == null) {
            throw ExternalServiceException(message = "Missing required parameters from the external service: code=$code, startedAt=$startedAt")
        }

        val homeTeam = availableTeams[home]!!
        val awayTeam = availableTeams[away]!!
        return Match(
            code = code,
            tournament = tournament,
            homeTeam = homeTeam,
            awayTeam = awayTeam,
            homeQuota = homeOdds?.oddsToQuota() ?: 0f,
            drawQuota = drawOdds?.oddsToQuota() ?: 0f,
            awayQuota = awayOdds?.oddsToQuota() ?: 0f,
            status = MatchStatus.NOT_STARTED,
            startedAt = startedAt
        )
    }

    fun toMatchUpdated(matches: List<Match>): Match? =
        matches.takeIf { status != MatchStatus.NOT_STARTED }
            ?.filter { systemMatch -> systemMatch.status != MatchStatus.FINISHED }
            ?.firstOrNull { systemMatch -> systemMatch.homeTeam.code == home && systemMatch.awayTeam.code == away }
            ?.copy(
                homeGoals = homeGoals, awayGoals = awayGoals, homePenalties = homePenalties, awayPenalties = awayPenalties,
                status = status, substatus = substatus, finishedAt = if (status == MatchStatus.FINISHED) finishedAt ?: ZonedDateTime.now() else null,
            )

    fun toQuotasUpdated(matches: List<Match>): Match? =
        if (homeOdds == null || drawOdds == null || awayOdds == null) {
            throw ExternalServiceException(message = "Missing required parameters from the external service: homeOdds=$homeOdds, drawOdds=$drawOdds, awayOdds=$awayOdds")
        } else {
            matches.filter { systemMatch -> systemMatch.status == MatchStatus.NOT_STARTED && PredictionService.isMatchUnlocked(systemMatch) }
                .firstOrNull { it.homeTeam.name == home && it.awayTeam.name == away }
                ?.takeIf { status == MatchStatus.NOT_STARTED }?.copy(
                    homeQuota = homeOdds.oddsToQuota(),
                    drawQuota = drawOdds.oddsToQuota(),
                    awayQuota = awayOdds.oddsToQuota(),
                )
        }
}
