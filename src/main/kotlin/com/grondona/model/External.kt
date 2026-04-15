package com.grondona.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.grondona.utils.WorldCupEngine
import java.time.LocalDateTime

// Data class for matches retrieved from LiveScoreAPI: https://live-score-api.com/documentation
@JsonIgnoreProperties(ignoreUnknown = true)
data class ExternalMatch(
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
    val tieOdds: Float = 1f,
    val awayOdds: Float = 1f,
    val endedAt: LocalDateTime? = null,
) {
    private enum class Status { TO_START, IN_PLAY, HALF_TIME, COMPLETED }

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
                            else -> null
                        }
                    }

                    Status.HALF_TIME.name -> {
                        it.homeGoals = homeGoals
                        it.awayGoals = awayGoals
                        it.homePenalties = awayPenalties
                        it.awayPenalties = awayPenalties
                        it.status = MatchStatus.IN_PROGRESS
                        it.substatus = "ENTRETIEMPO"
                    }

                    Status.COMPLETED.name -> {
                        if (it.status == MatchStatus.IN_PROGRESS) {
                            changedToFinished = true
                        }
                        it.homeGoals = homeGoals
                        it.awayGoals = awayGoals
                        it.homePenalties = homePenalties
                        it.awayPenalties = awayPenalties
                        it.status = MatchStatus.FINISHED
                        it.substatus = "FINALIZADO"
                        it.finishedAt = it.finishedAt ?: endedAt ?: LocalDateTime.now()
                    }
                }
            }

        return match?.let { Pair(it, changedToFinished) }
    }

    fun toQuotasUpdated(matches: List<Match>): Match? =
        matches.filter { it.status == MatchStatus.NOT_STARTED && WorldCupEngine.isMatchUnlocked(it) }
            .firstOrNull { it.homeTeam.name == home && it.awayTeam.name == away }?.also {
                when (status) {
                    Status.TO_START.name -> {
                        it.homeQuota = homeOdds
                        it.tieQuota = tieOdds
                        it.awayQuota = awayOdds
                    }
                }
            }
}