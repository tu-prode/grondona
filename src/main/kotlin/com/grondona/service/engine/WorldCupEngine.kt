package com.grondona.service.engine

import com.grondona.model.ExternalMatch
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

    internal val GS_MATCHES_CODE: List<String> = (1..72).map { it.toString() }
    internal val RO32_MATCHES_CODE: List<String> = (73..88).map { it.toString() }
    internal val RO16_MATCHES_CODE: List<String> = (89..96).map { it.toString() }
    internal val QUARTERFINALS_MATCHES_CODE: List<String> = (97..100).map { it.toString() }
    internal val SEMIFINALS_MATCHES_CODE: List<String> = (101..102).map { it.toString() }
    internal val LAST_ROUND_MATCHES_CODE: List<String> = (103..104).map { it.toString() }
    internal const val FINAL_MATCH_CODE: String = "104"

    override val tournamentId: UUID = SYSTEM_TOURNAMENT_ID

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

    internal fun List<Match>.allMatchesFinished(codes: List<String>): Boolean {
        val matchesPerCode = this.groupBy { it.code }.mapValues { it.value.first() }
        return codes.map { matchesPerCode[it] }.all { it?.status == MatchStatus.FINISHED }
    }

    override fun calculateNewMatches(systemMatches: List<Match>, externalMatches: List<ExternalMatch>): List<Match> {
        return when {
            systemMatches.allMatchesFinished(GS_MATCHES_CODE) && systemMatches.none { it.code in RO32_MATCHES_CODE } -> {
                logger.info("World Cup 2026's GS finished, preparing RO32 matches")
                val availableTeams = gatherTeamsByCode(systemMatches)
                val tournament = systemMatches.firstOrNull()?.tournament ?: return emptyList()
                externalMatches.filter { it.code in RO32_MATCHES_CODE }.map { it.toNewMatch(tournament, availableTeams) }.also {
                    logger.info("New matches added to system: {}", it.size)
                }
            }

            systemMatches.allMatchesFinished(RO32_MATCHES_CODE) && systemMatches.none { it.code in RO16_MATCHES_CODE } -> {
                logger.info("World Cup 2026's RO32 finished, preparing RO16 matches")
                val availableTeams = gatherTeamsByCode(systemMatches)
                val tournament = systemMatches.firstOrNull()?.tournament ?: return emptyList()
                externalMatches.filter { it.code in RO16_MATCHES_CODE }.map { it.toNewMatch(tournament, availableTeams) }.also {
                    logger.info("New matches added to system: {}", it.size)
                }
            }

            systemMatches.allMatchesFinished(RO16_MATCHES_CODE) && systemMatches.none { it.code in QUARTERFINALS_MATCHES_CODE } -> {
                logger.info("World Cup 2026's RO16 finished, preparing QF matches")
                val availableTeams = gatherTeamsByCode(systemMatches)
                val tournament = systemMatches.firstOrNull()?.tournament ?: return emptyList()
                externalMatches.filter { it.code in QUARTERFINALS_MATCHES_CODE }.map { it.toNewMatch(tournament, availableTeams) }.also {
                    logger.info("New matches added to system: {}", it.size)
                }
            }

            systemMatches.allMatchesFinished(QUARTERFINALS_MATCHES_CODE) && systemMatches.none { it.code in SEMIFINALS_MATCHES_CODE } -> {
                logger.info("World Cup 2026's QF finished, preparing SF matches")
                val availableTeams = gatherTeamsByCode(systemMatches)
                val tournament = systemMatches.firstOrNull()?.tournament ?: return emptyList()
                externalMatches.filter { it.code in SEMIFINALS_MATCHES_CODE }.map { it.toNewMatch(tournament, availableTeams) }.also {
                    logger.info("New matches added to system: {}", it.size)
                }
            }

            systemMatches.allMatchesFinished(SEMIFINALS_MATCHES_CODE) && systemMatches.none { it.code in LAST_ROUND_MATCHES_CODE } -> {
                logger.info("World Cup 2026's SF finished, preparing F+3P matches")
                val availableTeams = gatherTeamsByCode(systemMatches)
                val tournament = systemMatches.firstOrNull()?.tournament ?: return emptyList()
                externalMatches.filter { it.code in LAST_ROUND_MATCHES_CODE }.map { it.toNewMatch(tournament, availableTeams) }.also {
                    logger.info("New matches added to system: {}", it.size)
                }
            }

            else -> emptyList()
        }
    }

    internal fun gatherTeamsByCode(matches: List<Match>) =
        matches.flatMap { listOf(it.homeTeam, it.awayTeam) }.distinct().associateBy { it.code }



}