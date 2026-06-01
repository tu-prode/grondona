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
    val stage: MatchStage,
    val group: MatchGroup? = null,
    val substatus: String? = null,
    val homePenalties: Int? = null,
    val awayPenalties: Int? = null,
    val homeOdds: Float? = null,
    val drawOdds: Float? = null,
    val awayOdds: Float? = null,
    val startedAt: ZonedDateTime? = null,
    val finishedAt: ZonedDateTime? = null,
) {
    fun toNewMatch(tournament: Tournament, tournamentTeams: Map<String, Team>): Match {
        if (startedAt == null) {
            throw ExternalServiceException(message = "Missing required parameters from the external service: startedAt=$startedAt")
        }

        val homeTeam = tournamentTeams[home]!!
        val awayTeam = tournamentTeams[away]!!
        return Match(
            code = "XX",
            tournament = tournament,
            homeTeam = homeTeam,
            awayTeam = awayTeam,
            stage = stage,
            group = group,
            homeQuota = homeOdds?.oddsToQuota() ?: 0f,
            drawQuota = drawOdds?.oddsToQuota() ?: 0f,
            awayQuota = awayOdds?.oddsToQuota() ?: 0f,
            status = MatchStatus.NOT_STARTED,
            startedAt = startedAt
        )
    }

    fun toExistingMatch(matches: List<Match>): Match? =
        matches.firstOrNull { systemMatch -> stage == systemMatch.stage && systemMatch.homeTeam.code == home && systemMatch.awayTeam.code == away }
            ?.copy(
                homeGoals = homeGoals, awayGoals = awayGoals, homePenalties = homePenalties, awayPenalties = awayPenalties,
                homeQuota = homeOdds?.oddsToQuota() ?: 0f, drawQuota = drawOdds?.oddsToQuota() ?: 0f, awayQuota = awayOdds?.oddsToQuota() ?: 0f,
                status = status, substatus = substatus, finishedAt = if (status == MatchStatus.FINISHED) finishedAt ?: ZonedDateTime.now() else null,
            )
}
