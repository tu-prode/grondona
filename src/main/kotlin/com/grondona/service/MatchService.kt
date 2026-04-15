package com.grondona.service

import com.grondona.client.MatchClient
import com.grondona.exception.BadRequestException
import com.grondona.model.Match
import com.grondona.model.MatchStatus
import com.grondona.model.MatchPrediction
import com.grondona.model.PredictionStatus
import com.grondona.model.TournamentStatus
import com.grondona.repository.MatchRepository
import com.grondona.repository.MembershipRepository
import com.grondona.repository.MatchPredictionRepository
import com.grondona.repository.TournamentRepository
import com.grondona.utils.PointsEngine
import com.grondona.utils.WorldCupEngine
import java.util.UUID
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class MatchService(
    private val matchClient: MatchClient,
    private val matchRepository: MatchRepository,
    private val membershipRepository: MembershipRepository,
    private val tournamentRepository: TournamentRepository,
    private val predictionRepository: MatchPredictionRepository,
) {

    companion object {
        private val logger = LoggerFactory.getLogger(MatchService::class.java)
    }

    @Transactional
    fun updateMatchesStatuses(tournamentId: UUID) {
        if (tournamentId != WorldCupEngine.SYSTEM_TOURNAMENT_ID) {
            logger.warn("Currently the app only supports World Cup matches, with id={}", tournamentId)
            throw BadRequestException("Tournament not supported")
        }

        logger.trace("Fetching matches to update for tournament={}", tournamentId)
        val apiMatches = matchClient.getMatches(tournamentId)
        logger.trace("API matches retrieved={}", apiMatches.size)
        val systemMatches = matchRepository.findAllByTournamentIdAndStatusIn(
            tournamentId, listOf(MatchStatus.NOT_STARTED, MatchStatus.IN_PROGRESS),
        )
        logger.trace("System matches retrieved={}", systemMatches.size)

        val (matchesToUpdate, anyJustFinished) = apiMatches.mapNotNull { it.toMatchUpdated(systemMatches) }.let {
            val updatedMatches = it.map { (match, _) -> match }
            val statusUpdate = it.any { (_, status) -> status }
            updatedMatches to statusUpdate
        }
        if (matchesToUpdate.isNotEmpty()) {
            // TODO: Extract all this behavior to the WorldCupEngine
            val tournament = tournamentRepository.findById(tournamentId).orElseThrow {
                logger.error("Tournament={} not found in DB", tournamentId)
                BadRequestException("Tournament not found")
            }

            // TODO: Use the WorldCupEngine.updateTournament()
            if (tournament.status == TournamentStatus.NOT_STARTED) {
                logger.info("Setting tournament={} as STARTED", tournamentId)
                tournament.status = TournamentStatus.IN_PROGRESS
                tournamentRepository.save(tournament)
            }

            logger.debug("Matches to update in DB={}", matchesToUpdate.size)
            matchRepository.saveAll(matchesToUpdate)
        }

        if (anyJustFinished) {
            updateStandings(matchesToUpdate)
        }
    }

    fun updateStandings(matchesToUpdate: List<Match>) {
        val updatedMatchesIds = matchesToUpdate.map { it.id!! }
        var predictionsToUpdate =
            predictionRepository.findByStatusAndMatchIdIn(PredictionStatus.PENDING, updatedMatchesIds)
        predictionsToUpdate = checkCompletedPredictions(predictionsToUpdate)
        if (predictionsToUpdate.isNotEmpty()) {
            logger.debug("Predictions to update in DB={}", matchesToUpdate.size)
            predictionRepository.saveAll(predictionsToUpdate)
        }

        predictionsToUpdate.groupBy { it.group }.forEach { (group, groupPredictions) ->
            var members = membershipRepository.findByGroupId(group.id!!)
            val newPredictions = groupPredictions.groupBy { it.user.id!! }.mapValues { (_, userPredictions) ->
                val matchPredictions = userPredictions.groupBy { it.match.id }
                matchesToUpdate.map { match -> matchPredictions[match.id!!]?.firstOrNull() }
            }

            members = PointsEngine.updateStandings(members, newPredictions)
            logger.debug("Group={} new standings saved", group.id)
            membershipRepository.saveAll(members)
        }
    }

    fun checkCompletedPredictions(predictions: List<MatchPrediction>): List<MatchPrediction> =
        predictions.toMutableList().map { prediction ->
            if (prediction.status == PredictionStatus.PENDING && prediction.match.status == MatchStatus.FINISHED) {
                val matchScore = prediction.match.score()
                if (matchScore == null) {
                    logger.error("Match with id={} has no goals submitted but status FINISHED", prediction.match.id)
                } else {
                    val predictionScore = prediction.score()

                    when {
                        matchScore == predictionScore && matchScore.goals() >= 5 -> prediction.status =
                            PredictionStatus.BONUS

                        matchScore == predictionScore -> prediction.status = PredictionStatus.CORRECT
                        matchScore.outcome() == predictionScore.outcome() -> prediction.status =
                            PredictionStatus.PARTIAL

                        else -> prediction.status = PredictionStatus.INCORRECT
                    }
                }
            }

            prediction
        }

    @Transactional
    fun updateMatchesQuotas(tournamentId: UUID) {
        logger.trace("Starting matches polling")

        if (tournamentId != WorldCupEngine.SYSTEM_TOURNAMENT_ID) {
            logger.error("Currently the app only supports World Cup matches, with id={}", tournamentId)
            throw BadRequestException("Tournament not supported")
        }

        logger.trace("Fetching matches to update quota for tournament={}", tournamentId)
        val apiMatches = matchClient.getMatches(tournamentId)
        logger.trace("API matches retrieved={}", apiMatches.size)
        val systemMatches = matchRepository.findAllByTournamentIdAndStatusIn(
            tournamentId, listOf(MatchStatus.NOT_STARTED),
        )
        logger.trace("System matches retrieved={}", systemMatches.size)

        val matchesToUpdate = apiMatches.mapNotNull { it.toQuotasUpdated(systemMatches) }
        if (matchesToUpdate.isNotEmpty()) {
            logger.debug("Matches to update in DB={}", matchesToUpdate.size)
            matchRepository.saveAll(matchesToUpdate)
        }
    }
}
