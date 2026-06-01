package com.grondona.service.engine

import com.grondona.model.Match
import com.grondona.model.MatchStatus
import com.grondona.model.TournamentStatus
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.time.ZonedDateTime
import java.util.UUID

@Component
object WorldCupEngine : TournamentEngine {

    private val logger = LoggerFactory.getLogger(WorldCupEngine::class.java)

    val BEST_YOUNG_PLAYER_DATE_LIMIT: LocalDate = LocalDate.parse("2005-01-01")
    val SYSTEM_TOURNAMENT_ID: UUID = UUID.fromString("28652183-a2d6-4f33-a624-0d24645ce3cd")

    internal const val FINAL_MATCH_CODE: String = "104"
    override val tournamentId: UUID = SYSTEM_TOURNAMENT_ID

    fun calculateKnockoutCode(startedAt: ZonedDateTime): String? =
        when (startedAt.toInstant()) {
            ZonedDateTime.parse("2026-06-28T12:00:00-07:00").toInstant() -> 73
            ZonedDateTime.parse("2026-06-29T16:30:00-04:00").toInstant() -> 74
            ZonedDateTime.parse("2026-06-29T19:00:00-06:00").toInstant() -> 75
            ZonedDateTime.parse("2026-06-29T12:00:00-05:00").toInstant() -> 76
            ZonedDateTime.parse("2026-06-30T17:00:00-04:00").toInstant() -> 77
            ZonedDateTime.parse("2026-06-30T12:00:00-05:00").toInstant() -> 78
            ZonedDateTime.parse("2026-06-30T19:00:00-06:00").toInstant() -> 79
            ZonedDateTime.parse("2026-07-01T12:00:00-04:00").toInstant() -> 80
            ZonedDateTime.parse("2026-07-01T17:00:00-07:00").toInstant() -> 81
            ZonedDateTime.parse("2026-07-01T13:00:00-07:00").toInstant() -> 82
            ZonedDateTime.parse("2026-07-02T19:00:00-04:00").toInstant() -> 83
            ZonedDateTime.parse("2026-07-02T12:00:00-07:00").toInstant() -> 84
            ZonedDateTime.parse("2026-07-02T20:00:00-07:00").toInstant() -> 85
            ZonedDateTime.parse("2026-07-03T18:00:00-04:00").toInstant() -> 86
            ZonedDateTime.parse("2026-07-03T20:30:00-05:00").toInstant() -> 87
            ZonedDateTime.parse("2026-07-03T13:00:00-05:00").toInstant() -> 88
            ZonedDateTime.parse("2026-07-04T17:00:00-04:00").toInstant() -> 89
            ZonedDateTime.parse("2026-07-04T12:00:00-05:00").toInstant() -> 90
            ZonedDateTime.parse("2026-07-05T16:00:00-04:00").toInstant() -> 91
            ZonedDateTime.parse("2026-07-05T18:00:00-06:00").toInstant() -> 92
            ZonedDateTime.parse("2026-07-06T14:00:00-05:00").toInstant() -> 93
            ZonedDateTime.parse("2026-07-06T17:00:00-07:00").toInstant() -> 94
            ZonedDateTime.parse("2026-07-07T12:00:00-04:00").toInstant() -> 95
            ZonedDateTime.parse("2026-07-07T13:00:00-07:00").toInstant() -> 96
            ZonedDateTime.parse("2026-07-09T16:00:00-04:00").toInstant() -> 97
            ZonedDateTime.parse("2026-07-10T12:00:00-07:00").toInstant() -> 98
            ZonedDateTime.parse("2026-07-11T17:00:00-04:00").toInstant() -> 99
            ZonedDateTime.parse("2026-07-11T20:00:00-05:00").toInstant() -> 100
            ZonedDateTime.parse("2026-07-14T14:00:00-05:00").toInstant() -> 101
            ZonedDateTime.parse("2026-07-15T15:00:00-04:00").toInstant() -> 102
            ZonedDateTime.parse("2026-07-18T17:00:00-04:00").toInstant() -> 103
            ZonedDateTime.parse("2026-07-19T15:00:00-04:00").toInstant() -> 104
            else -> null
        }?.toString()

    override fun calculateTournamentStatus(matches: List<Match>): TournamentStatus? {
        val tournament = matches.firstOrNull()?.tournament ?: return null
        return when {
            tournament.status == TournamentStatus.NOT_STARTED &&
                    matches.any { it.status != MatchStatus.NOT_STARTED } -> TournamentStatus.IN_PROGRESS

            tournament.status == TournamentStatus.IN_PROGRESS &&
                    matches.firstOrNull { it.code == FINAL_MATCH_CODE }?.status == MatchStatus.FINISHED -> TournamentStatus.FINISHED

            else -> null
        }
    }

    override fun generateMatchesCodes(newMatches: List<Match>): List<Match> =
        newMatches.mapNotNull {
            calculateKnockoutCode(it.startedAt)?.let { code -> it.copy(code = calculateKnockoutCode(it.startedAt) + code) } ?: run {
                logger.error("Could not find code for match starting on {}", it.startedAt); null
            }
        }

}
