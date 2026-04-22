package com.grondona.service

import com.grondona.client.MatchClient
import com.grondona.exception.BadRequestException
import com.grondona.model.AwardPrediction
import com.grondona.model.Match
import com.grondona.model.MatchStatus
import com.grondona.model.MatchPrediction
import com.grondona.model.PredictionStatus
import com.grondona.model.Tournament
import com.grondona.model.TournamentStatus
import com.grondona.repository.AwardPredictionRepository
import com.grondona.repository.MatchRepository
import com.grondona.repository.MembershipRepository
import com.grondona.repository.MatchPredictionRepository
import com.grondona.repository.TournamentRepository
import com.grondona.service.engine.TournamentEngine
import com.grondona.service.engine.PredictionsEngine
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
    private val matchPredictionRepository: MatchPredictionRepository,
    private val awardPredictionRepository: AwardPredictionRepository,
    // Engines
    private val enginesList: List<TournamentEngine>
) {

    companion object {
        private val logger = LoggerFactory.getLogger(MatchService::class.java)
    }

    private val engines: Map<UUID, TournamentEngine> = enginesList.associateBy { it.tournamentId }

    @Transactional
    fun updateMatchesStatuses(tournamentId: UUID): List<Match> {
        val tournamentEngine = engines[tournamentId] ?: run {
            logger.warn("Trying to get matches statuses for tournament={}, currently not supported", tournamentId)
            throw BadRequestException("Tournament not supported")
        }

        logger.trace("Fetching matches to update for tournament={}", tournamentId)
        val apiMatches = matchClient.getMatches(tournamentId)
        logger.trace("API matches retrieved={}", apiMatches.size)
        val systemMatches = matchRepository.findByTournamentId(tournamentId)
        logger.trace("System matches retrieved={}", systemMatches.size)

        val (matchesToUpdate, anyJustFinished) = apiMatches.mapNotNull { it.toMatchUpdated(systemMatches) }.let {
            val updatedMatches = it.map { (match, _) -> match }
            val statusUpdate = it.any { (_, status) -> status }
            updatedMatches to statusUpdate
        }

        val consolidatedMatches = consolidateMatches(matchesToUpdate, systemMatches)
        val tournament = tournamentRepository.findById(tournamentId).orElseThrow {
            logger.error("Tournament={} not found in DB", tournamentId)
            BadRequestException("Tournament not found")
        }

        val newTournamentStatus = tournamentEngine.calculateNewStatus(tournament, consolidatedMatches)
        newTournamentStatus?.let {
            tournament.status = newTournamentStatus
            logger.debug("Setting tournament={} status as {} in DB", tournament.id, newTournamentStatus)
            tournamentRepository.save(tournament)
        }

        val newMatches = tournamentEngine.calculateNewMatches(tournament, consolidatedMatches, apiMatches)
        val matchesToSave = matchesToUpdate + newMatches
        if (matchesToSave.isNotEmpty()) {
            logger.debug("Matches to store in DB={}", matchesToSave.size)
            matchRepository.saveAll(matchesToSave)
        }

        if (anyJustFinished) {
            updateMatchPredictionsPoints(matchesToUpdate)
        }

        if (newTournamentStatus == TournamentStatus.FINISHED) {
            updateAwardPredictionsPoints(tournament)
        }

        return matchesToSave
    }

    private fun consolidateMatches(matchesToUpdate: List<Match>, systemMatches: List<Match>): List<Match> {
        val updatedMatchesMap = mutableMapOf<UUID, Match>()
        for (match in matchesToUpdate) {
            updatedMatchesMap[match.id!!] = match
        }

        val consolidatedMatches = mutableListOf<Match>()
        for (match in systemMatches) {
            consolidatedMatches.add(updatedMatchesMap[match.id] ?: match)
        }

        return consolidatedMatches
    }

    fun updateMatchPredictionsPoints(matchesToUpdate: List<Match>) {
        val updatedMatchesIds = matchesToUpdate.map { it.id!! }
        var predictionsToUpdate = matchPredictionRepository.findByStatusAndMatchIdIn(PredictionStatus.PENDING, updatedMatchesIds)
        predictionsToUpdate = checkMatchPredictions(predictionsToUpdate)
        if (predictionsToUpdate.isNotEmpty()) {
            logger.debug("Match predictions to update in DB={}", predictionsToUpdate.size)
            matchPredictionRepository.saveAll(predictionsToUpdate)
        }

        predictionsToUpdate.groupBy { it.group }.forEach { (group, groupPredictions) ->
            var members = membershipRepository.findByGroupId(group.id!!)
            val newPredictions = groupPredictions.groupBy { it.user.id!! }.mapValues { (_, userPredictions) ->
                val matchPredictions = userPredictions.groupBy { it.match.id }
                matchesToUpdate.map { match -> matchPredictions[match.id!!]?.firstOrNull() }
            }

            members = PredictionsEngine.updateMatchPoints(members, newPredictions)
            logger.debug("Group={} new standings saved, after applying matches points", group.id)
            membershipRepository.saveAll(members)
        }
    }

    fun updateAwardPredictionsPoints(tournament: Tournament) {
        var predictionsToUpdate = awardPredictionRepository.findAll()
        predictionsToUpdate = checkAwardPredictions(predictionsToUpdate)
        if (predictionsToUpdate.isNotEmpty()) {
            logger.debug("Award predictions to update in DB={}", predictionsToUpdate.size)
            awardPredictionRepository.saveAll(predictionsToUpdate)
        }

        predictionsToUpdate.groupBy { it.group }.forEach { (group, groupPredictions) ->
            var members = membershipRepository.findByGroupId(group.id!!)
            val newPredictions = groupPredictions.groupBy { it.user.id!! }
            members = PredictionsEngine.updateAwardPoints(members, newPredictions)
            logger.debug("Group={} new standings saved, after applying awards points", group.id)
            membershipRepository.saveAll(members)
        }
    }

    fun checkMatchPredictions(predictions: List<MatchPrediction>): List<MatchPrediction> =
        PredictionsEngine.checkMatchPredictions(predictions.filter {
            it.status == PredictionStatus.PENDING && it.match.status == MatchStatus.FINISHED
        })

    fun checkAwardPredictions(predictions: List<AwardPrediction>): List<AwardPrediction> =
        PredictionsEngine.checkAwardPredictions(predictions.filter {
            it.status == PredictionStatus.PENDING && it.group.tournament.status == TournamentStatus.FINISHED
        })

    @Transactional
    fun updateMatchesQuotas(tournamentId: UUID) {
        logger.trace("Starting matches polling")

        engines[tournamentId] ?: run {
            logger.warn("Trying to get matches statuses for tournament={}, currently not supported", tournamentId)
            throw BadRequestException("Tournament not supported")
        }

        logger.trace("Fetching matches to update quota for tournament={}", tournamentId)
        val apiMatches = matchClient.getMatches(tournamentId)
        logger.trace("API matches retrieved={}", apiMatches.size)
        val systemMatches = matchRepository.findByTournamentIdAndStatus(tournamentId, MatchStatus.NOT_STARTED)
        logger.trace("System matches retrieved={}", systemMatches.size)

        val matchesToUpdate = apiMatches.mapNotNull { it.toQuotasUpdated(systemMatches) }
        if (matchesToUpdate.isNotEmpty()) {
            logger.debug("Matches to update in DB={}", matchesToUpdate.size)
            matchRepository.saveAll(matchesToUpdate)
        }
    }
}
