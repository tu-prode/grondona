package com.grondona.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.grondona.service.PredictionService
import com.grondona.utils.oddsToQuota
import java.time.ZonedDateTime

// Data class for matches retrieved from LiveScoreAPI: https://live-score-api.com/documentation
@JsonIgnoreProperties(ignoreUnknown = true)
data class ExternalMatch(
    val code: String,
    val home: String,
    val away: String,
    val homeGoals: Int,
    val awayGoals: Int,
    val homePenalties: Int? = null,
    val awayPenalties: Int? = null,
    val minutes: Int,
    val half: Int,
    val status: String,
    val homeOdds: Float = 1f,
    val drawOdds: Float = 1f,
    val awayOdds: Float = 1f,
    val startedAt: ZonedDateTime,
    val endedAt: ZonedDateTime? = null,
) {
    private enum class Status { TO_START, IN_PLAY, HALF_TIME, PENALTIES, COMPLETED }

    fun toNewMatch(tournament: Tournament, availableTeams: Map<String, Team>): Match {
        val homeTeam = availableTeams[home]!!
        val awayTeam = availableTeams[away]!!

        return Match(
            code = code,
            tournament = tournament,
            homeTeam = homeTeam,
            awayTeam = awayTeam,
            homeQuota = homeOdds.oddsToQuota(),
            drawQuota = drawOdds.oddsToQuota(),
            awayQuota = awayOdds.oddsToQuota(),
            status = MatchStatus.NOT_STARTED,
            startedAt = startedAt
        )
    }

    fun toSystemMatch(matches: List<Match>): Match? =
        matches.takeIf { status != Status.TO_START.name }
            ?.filter { it.status != MatchStatus.FINISHED }
            ?.firstOrNull { it.homeTeam.code == home && it.awayTeam.code == away }?.let {
                var newHomeGoals = it.homeGoals
                var newAwayGoals = it.awayGoals
                var newHomePenalties = it.homePenalties
                var newAwayPenalties = it.awayPenalties
                var newStatus = it.status
                var newSubstatus = it.substatus
                var newFinishedAt = it.finishedAt

                when (status) {
                    Status.IN_PLAY.name -> {
                        newHomeGoals = homeGoals
                        newAwayGoals = awayGoals
                        newStatus = MatchStatus.IN_PROGRESS
                        newSubstatus = when {
                            half == 1 && minutes <= 45 -> "$minutes' PT"
                            half == 1 && minutes > 45 -> "45+${minutes - 45}' PT"
                            half == 2 && minutes <= 90 -> "${minutes - 45}' ST"
                            half == 2 && minutes > 90 -> "45+${minutes - 90}' ST"
                            half == 3 && minutes <= 105 -> "${minutes - 90}' PTE"
                            half == 3 && minutes > 105 -> "15+${minutes - 105}' PTE"
                            half == 4 && minutes <= 120 -> "${minutes - 105}' STE"
                            half == 4 && minutes > 120 -> "15+${minutes - 120}' STE"
                            else -> null
                        }
                    }

                    Status.HALF_TIME.name -> {
                        newHomeGoals = homeGoals
                        newAwayGoals = awayGoals
                        newSubstatus = "ET"
                    }

                    Status.PENALTIES.name -> {
                        newHomeGoals = homeGoals
                        newAwayGoals = awayGoals
                        newHomePenalties = homePenalties
                        newAwayPenalties = awayPenalties
                        newSubstatus = "PEN"
                    }

                    Status.COMPLETED.name -> {
                        newHomeGoals = homeGoals
                        newAwayGoals = awayGoals
                        newHomePenalties = homePenalties
                        newAwayPenalties = awayPenalties
                        newStatus = MatchStatus.FINISHED
                        newFinishedAt = endedAt ?: ZonedDateTime.now()
                        newSubstatus = "FIN"
                    }
                }

                it.copy(
                    homeGoals = newHomeGoals, awayGoals = newAwayGoals,
                    homePenalties = newHomePenalties, awayPenalties = newAwayPenalties,
                    status = newStatus, substatus = newSubstatus, finishedAt = newFinishedAt,
                )
            }

    fun toSystemQuotas(matches: List<Match>): Match? =
        matches.filter { it.status == MatchStatus.NOT_STARTED && PredictionService.isMatchUnlocked(it) }
            .firstOrNull { it.homeTeam.name == home && it.awayTeam.name == away }
            ?.takeUnless { status != Status.TO_START.name }?.copy(
                homeQuota = homeOdds.oddsToQuota(),
                drawQuota = drawOdds.oddsToQuota(),
                awayQuota = awayOdds.oddsToQuota(),
            )
}
