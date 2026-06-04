package com.grondona.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.grondona.exception.ExternalServiceException
import com.grondona.utils.oddsToQuota
import com.grondona.utils.similar
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
    val startedAt: ZonedDateTime? = null,
    val finishedAt: ZonedDateTime? = null,
) {
    fun toNewMatch(tournament: Tournament, tournamentTeams: Map<String, Team>): Match {
        if (startedAt == null) {
            throw ExternalServiceException(message = "Missing required parameters from the external service: startedAt=$startedAt")
        }

        val homeTeam = tournamentTeams[home] ?: run {
            throw ExternalServiceException(message = "Home team not found in the DB: home=$home")
        }

        val awayTeam = tournamentTeams[away] ?: run {
            throw ExternalServiceException(message = "Away team not found in the DB: away=$away")
        }

        return Match(
            code = "XX",
            tournament = tournament,
            homeTeam = homeTeam,
            awayTeam = awayTeam,
            stage = stage,
            group = group,
            status = MatchStatus.NOT_STARTED,
            startedAt = startedAt,
        )
    }

    fun toExistingMatch(matches: List<Match>): Match? =
        matches.firstOrNull { systemMatch -> stage == systemMatch.stage && systemMatch.homeTeam.code == home && systemMatch.awayTeam.code == away }
            ?.copy(
                homeGoals = homeGoals, awayGoals = awayGoals, homePenalties = homePenalties, awayPenalties = awayPenalties,
                status = status, substatus = substatus, finishedAt = if (status == MatchStatus.FINISHED) finishedAt ?: ZonedDateTime.now() else null,
            )
}

// Case data class extracted from the External client.
@JsonIgnoreProperties(ignoreUnknown = true)
data class ExternalOdds(
    val homeKey: String,
    val awayKey: String,
    val homeOdds: Float,
    val drawOdds: Float,
    val awayOdds: Float,
    val startedAt: ZonedDateTime,
) {
    fun toMatchUpdated(matches: List<Match>, tournamentTeams: Map<String, Team>): Match? {
        val home = tournamentTeams[homeKey]?.code  ?: run {
            throw ExternalServiceException(message = "Home team not found in the DB: home=$homeKey")
        }

        val away = tournamentTeams[awayKey]?.code ?: run {
            throw ExternalServiceException(message = "Away team not found in the DB: away=$awayKey")
        }

        return matches.firstOrNull { match -> match.startedAt.similar(startedAt) && match.homeTeam.code == home && match.awayTeam.code == away }
            ?.copy(homeQuota = homeOdds.oddsToQuota(), drawQuota = drawOdds.oddsToQuota(), awayQuota = awayOdds.oddsToQuota())
    }
}
