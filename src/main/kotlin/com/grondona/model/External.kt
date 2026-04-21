package com.grondona.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.grondona.service.engine.WorldCupEngine
import com.grondona.utils.round
import java.time.LocalDateTime
import kotlin.math.log

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
    val startedAt: LocalDateTime,
    val endedAt: LocalDateTime? = null,
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
            homeQuota = oddsToQuota(homeOdds),
            drawQuota = oddsToQuota(drawOdds),
            awayQuota = oddsToQuota(awayOdds),
            status = MatchStatus.NOT_STARTED,
            startedAt = startedAt
        )
    }

    fun toMatchUpdated(matches: List<Match>): Pair<Match, Boolean>? {
        var changedToFinished = false
        val match = matches.takeIf { status != Status.TO_START.name }
            ?.filter { it.status != MatchStatus.FINISHED }
            ?.firstOrNull { it.homeTeam.code == home && it.awayTeam.code == away }?.also {
                when (status) {
                    Status.IN_PLAY.name -> {
                        it.homeGoals = homeGoals
                        it.awayGoals = awayGoals
                        it.homePenalties = awayPenalties
                        it.awayPenalties = awayPenalties
                        it.status = MatchStatus.IN_PROGRESS
                        it.substatus = when {
                            half == 1 && minutes <= 45 -> "$minutes' PT"
                            half == 1 && minutes > 45 -> "45+${minutes - 45}' PT"
                            half == 2 && minutes <= 90 -> "${minutes - 45}' ST"
                            half == 2 && minutes > 90 -> "45+${minutes - 90}' ST"
                            half == 3 && minutes <= 15 -> "${minutes - 90}' PTE"
                            half == 3 && minutes > 15 -> "15+${minutes - 105}' PTE"
                            half == 4 && minutes <= 15 -> "${minutes - 105}' STE"
                            half == 4 && minutes > 15 -> "15+${minutes - 120}' STE"
                            else -> null
                        }
                    }

                    Status.HALF_TIME.name -> {
                        it.homeGoals = homeGoals
                        it.awayGoals = awayGoals
                        it.homePenalties = awayPenalties
                        it.awayPenalties = awayPenalties
                        it.status = MatchStatus.IN_PROGRESS
                        it.substatus = "ET"
                    }

                    Status.PENALTIES.name -> {
                        it.homeGoals = homeGoals
                        it.awayGoals = awayGoals
                        it.homePenalties = awayPenalties
                        it.awayPenalties = awayPenalties
                        it.status = MatchStatus.IN_PROGRESS
                        it.substatus = "PK"
                    }

                    Status.COMPLETED.name -> {
                        if (it.status != MatchStatus.FINISHED) {
                            changedToFinished = true
                        }
                        it.homeGoals = homeGoals
                        it.awayGoals = awayGoals
                        it.homePenalties = homePenalties
                        it.awayPenalties = awayPenalties
                        it.status = MatchStatus.FINISHED
                        it.substatus = "FT"
                        it.finishedAt = it.finishedAt ?: endedAt ?: LocalDateTime.now()
                    }
                }
            }

        return match?.let { Pair(it, changedToFinished) }
    }

    private fun oddsToQuota(odds: Float) = 1 + 2 * log(odds.toDouble(), 10.0).toFloat().round()

    fun toQuotasUpdated(matches: List<Match>): Match? =
        matches.filter { it.status == MatchStatus.NOT_STARTED && WorldCupEngine.isMatchUnlocked(it) }
            .firstOrNull { it.homeTeam.name == home && it.awayTeam.name == away }?.also {
                when (status) {
                    Status.TO_START.name -> {
                        it.homeQuota = oddsToQuota(homeOdds)
                        it.drawQuota = oddsToQuota(drawOdds)
                        it.awayQuota = oddsToQuota(awayOdds)
                    }
                }
            }
}
