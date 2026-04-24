package com.grondona.service.engine

import com.grondona.exception.GeneralException
import com.grondona.model.ExternalMatch
import com.grondona.model.Match
import com.grondona.model.MatchStatus
import com.grondona.model.Tournament
import com.grondona.model.TournamentStatus
import com.grondona.repository.TeamRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID

@Component
class WorldCupEngine(
    private val teamRepository: TeamRepository,
) : TournamentEngine {

    override val tournamentId: UUID = SYSTEM_TOURNAMENT_ID

    companion object {
        private val logger = LoggerFactory.getLogger(WorldCupEngine::class.java)

        val BEST_YOUNG_PLAYER_DATE_LIMIT = LocalDate.parse("2005-01-01")!!
        const val API_TOURNAMENT_ID: String = "2173492"
        val SYSTEM_TOURNAMENT_ID: UUID = UUID.fromString("28652183-a2d6-4f33-a624-0d24645ce3cd")

        val GS_MATCHES_CODE: List<String> = (1..72).map { it.toString() }
        val RO32_MATCHES_CODE: List<String> = (73..88).map { it.toString() }
        val RO16_MATCHES_CODE: List<String> = (88..96).map { it.toString() }
        val QUARTERFINALS_MATCHES_CODE: List<String> = (96..100).map { it.toString() }
        val SEMIFINALS_MATCHES_CODE: List<String> = (101..102).map { it.toString() }
        val LAST_ROUND_MATCHES_CODE: List<String> = (103..104).map { it.toString() }
        const val FINAL_MATCH_CODE: String = "104"

        var now: LocalDateTime = LocalDateTime.now()

        fun isMatchUnlocked(match: Match) =
            match.startedAt?.isAfter(now.plus(15, ChronoUnit.MINUTES)) ?: true
    }

    override fun calculateNewStatus(tournament: Tournament, matches: List<Match>): TournamentStatus? {
        return when {
            tournament.status == TournamentStatus.NOT_STARTED &&
                    matches.any { it.status != MatchStatus.NOT_STARTED } -> TournamentStatus.IN_PROGRESS

            tournament.status == TournamentStatus.IN_PROGRESS &&
                    matches.firstOrNull { it.code == FINAL_MATCH_CODE }?.status == MatchStatus.FINISHED -> TournamentStatus.IN_PROGRESS

            else -> null
        }
    }

    private fun List<Match>.allMatchesFinished(codes: List<String>): Boolean {
        val matchesPerCode = this.groupBy { it.code }.mapValues { it.value.first() }
        return codes.map { matchesPerCode[it] }.all { it?.status == MatchStatus.FINISHED }
    }

    override fun calculateNewMatches(tournament: Tournament, systemMatches: List<Match>, externalMatches: List<ExternalMatch>): List<Match> {
        return when {
            systemMatches.allMatchesFinished(GS_MATCHES_CODE) && systemMatches.none { it.code in RO32_MATCHES_CODE } -> {
                logger.info("World Cup 2026's GS finished, preparing RO32 matches")
                externalMatches.filter { it.code in RO32_MATCHES_CODE }.prepareNewMatches(tournament).also {
                    logger.info("New matches added to system: {}", it.size)
                }
            }

            systemMatches.allMatchesFinished(RO32_MATCHES_CODE) && systemMatches.none { it.code in RO16_MATCHES_CODE } -> {
                logger.info("World Cup 2026's RO32 finished, preparing RO16 matches")
                externalMatches.filter { it.code in RO16_MATCHES_CODE }.prepareNewMatches(tournament).also {
                    logger.info("New matches added to system: {}", it.size)
                }
            }

            systemMatches.allMatchesFinished(RO16_MATCHES_CODE) && systemMatches.none { it.code in QUARTERFINALS_MATCHES_CODE } -> {
                logger.info("World Cup 2026's RO16 finished, preparing QF matches")
                externalMatches.filter { it.code in QUARTERFINALS_MATCHES_CODE }.prepareNewMatches(tournament).also {
                    logger.info("New matches added to system: {}", it.size)
                }
            }

            systemMatches.allMatchesFinished(QUARTERFINALS_MATCHES_CODE) && systemMatches.none { it.code in SEMIFINALS_MATCHES_CODE } -> {
                logger.info("World Cup 2026's QF finished, preparing SF matches")
                externalMatches.filter { it.code in SEMIFINALS_MATCHES_CODE }.prepareNewMatches(tournament).also {
                    logger.info("New matches added to system: {}", it.size)
                }
            }

            systemMatches.allMatchesFinished(SEMIFINALS_MATCHES_CODE) && systemMatches.none { it.code in LAST_ROUND_MATCHES_CODE } -> {
                logger.info("World Cup 2026's SF finished, preparing F+3P matches")
                externalMatches.filter { it.code in LAST_ROUND_MATCHES_CODE }.prepareNewMatches(tournament).also {
                    logger.info("New matches added to system: {}", it.size)
                }
            }

            else -> emptyList()
        }
    }

    private fun List<ExternalMatch>.prepareNewMatches(tournament: Tournament): List<Match> {
        val countryCodes = this.flatMap { listOf(it.home, it.away) }
        val tournamentTeams = teamRepository.findByTournamentId(tournamentId).associateBy { it.code }
        val availableTeams = countryCodes.map {
            tournamentTeams[it] ?: run {
                logger.error("Trying to create a new match for team={} in tournament={} but it's not in the DB", it, tournamentId)
                throw GeneralException("Team not found")
            }
        }.associateBy { it.code }
        return this.map { it.toNewMatch(tournament, availableTeams) }
    }
}