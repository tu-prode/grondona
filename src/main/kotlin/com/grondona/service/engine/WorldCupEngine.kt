package com.grondona.service.engine

import com.grondona.model.ExternalMatch
import com.grondona.model.Match
import com.grondona.model.MatchStatus
import com.grondona.model.TournamentStatus
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.util.UUID

@Component
object WorldCupEngine : TournamentEngine {

    private val logger = LoggerFactory.getLogger(WorldCupEngine::class.java)

    val BEST_YOUNG_PLAYER_DATE_LIMIT: LocalDate = LocalDate.parse("2005-01-01")
    const val API_TOURNAMENT_ID: String = "2173492"
    val SYSTEM_TOURNAMENT_ID: UUID = UUID.fromString("28652183-a2d6-4f33-a624-0d24645ce3cd")

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