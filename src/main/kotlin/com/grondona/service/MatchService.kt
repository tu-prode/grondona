package com.grondona.service

import com.grondona.client.MatchClient
import com.grondona.client.OddsClient
import com.grondona.exception.BadRequestException
import com.grondona.model.Match
import com.grondona.model.MatchStatus
import com.grondona.model.MatchPrediction
import com.grondona.model.PredictionStatus
import com.grondona.repository.MatchRepository
import com.grondona.repository.MembershipRepository
import com.grondona.repository.MatchPredictionRepository
import com.grondona.repository.TeamRepository
import com.grondona.repository.TournamentRepository
import com.grondona.service.engine.TournamentEngine
import com.grondona.service.engine.PredictionsEngine
import com.grondona.utils.consolidateGroupMatchPredictions
import java.util.UUID
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class MatchService(
    private val oddsClient: OddsClient,
    private val matchClient: MatchClient,
    private val teamRepository: TeamRepository,
    private val matchRepository: MatchRepository,
    private val membershipRepository: MembershipRepository,
    private val tournamentRepository: TournamentRepository,
    private val matchPredictionRepository: MatchPredictionRepository,
    private val enginesList: List<TournamentEngine>,
    @Value("\${external.api.matches.with-new-matches}")
    private val prepareNewMatches: Boolean
) {

    companion object {
        private val logger = LoggerFactory.getLogger(MatchService::class.java)

        fun extractMatchesToUpdateStatus(matchesFromDB: List<Match>, matchesFromAPI: List<Match>): List<Match> {
            val apiMatchesById = matchesFromAPI.filter { it.id != null }.associateBy { it.id!! }
            return matchesFromDB.mapNotNull { dbMatch ->
                apiMatchesById[dbMatch.id]?.let { apiMatch ->
                    if (apiMatch.status != MatchStatus.NOT_STARTED && dbMatch.status != MatchStatus.FINISHED) {
                        dbMatch.copy(
                            status = apiMatch.status, substatus = apiMatch.substatus, finishedAt = apiMatch.finishedAt,
                            homeGoals = apiMatch.homeGoals, awayGoals = apiMatch.awayGoals,
                            homePenalties = apiMatch.homePenalties, awayPenalties = apiMatch.awayPenalties,
                        )
                    } else null
                }
            }
        }

        fun extractMatchesToUpdateQuotas(matchesFromDB: List<Match>, matchesFromAPI: List<Match>): List<Match> {
            val apiMatchesById = matchesFromAPI.filter { it.id != null }.associateBy { it.id!! }
            return matchesFromDB.mapNotNull { dbMatch ->
                apiMatchesById[dbMatch.id]?.let { apiMatch ->
                    if (apiMatch.status == MatchStatus.NOT_STARTED && PredictionService.isMatchUnlocked(dbMatch)) {
                        dbMatch.copy(homeQuota = apiMatch.homeQuota, drawQuota = apiMatch.drawQuota, awayQuota = apiMatch.awayQuota)
                    } else null
                }
            }
        }
    }

    private val engines: Map<UUID, TournamentEngine> = enginesList.associateBy { it.tournamentId }

    @Transactional
    fun updateMatchesQuotas(tournamentId: UUID) {
        logger.trace("Starting matches polling")

        engines[tournamentId] ?: run {
            logger.warn("Trying to get matches statuses for tournament={}, currently not supported", tournamentId)
            throw BadRequestException("Tournament not supported")
        }

        logger.debug("Fetching odds to update quota for tournament={}", tournamentId)
        val externalOdds = oddsClient.getOdds(tournamentId)
        logger.debug("API odds retrieved for quotas updates={}", externalOdds.size)
        val matchesFromDB = matchRepository.findByTournamentIdAndStatus(tournamentId, MatchStatus.NOT_STARTED)
        logger.debug("System matches retrieved for quotas updates={}", matchesFromDB.size)

        val tournamentTeams = teamRepository.findByTournamentId(tournamentId).associateBy { it.englishKey }
        val matchesFromAPI = externalOdds.mapNotNull { it.toMatchUpdated(matchesFromDB, tournamentTeams) }
        val matchesToUpdate = extractMatchesToUpdateQuotas(matchesFromDB, matchesFromAPI)

        if (matchesToUpdate.isNotEmpty()) {
            logger.debug("Matches to apply quotas update in DB={}", matchesToUpdate.size)
            matchRepository.saveAll(matchesToUpdate)
        }
    }

    @Transactional
    fun updateMatchesStatuses(tournamentId: UUID): List<Match> {
        val tournamentEngine = engines[tournamentId] ?: run {
            logger.warn("Trying to get matches statuses for tournament={}, currently not supported", tournamentId)
            throw BadRequestException("Tournament not supported")
        }

        logger.trace("Fetching matches to update for tournament={}", tournamentId)
        val externalMatches = matchClient.getMatches(tournamentId)
        logger.trace("API matches retrieved for status updates={}", externalMatches.size)
        val matchesFromDB = matchRepository.findByTournamentId(tournamentId)
        logger.trace("System matches retrieved for status updates={}", matchesFromDB.size)

        val tournament = tournamentRepository.findById(tournamentId).orElseThrow {
            logger.error("Tournament={} not found in DB", tournamentId)
            BadRequestException("Tournament not found")
        }
        val tournamentTeams = teamRepository.findByTournamentId(tournamentId)
        val tournamentTeamsByCode = tournamentTeams.associateBy { it.code }

        val matchesFromAPI = externalMatches.map { it.toExistingMatch(matchesFromDB) ?: it.toNewMatch(tournament, tournamentTeamsByCode) }
        val matchesToUpdate = extractMatchesToUpdateStatus(matchesFromDB, matchesFromAPI)
        var matchesToCreate = matchesFromAPI.filter { it.id == null }

        if (matchesToCreate.isNotEmpty()) {
            logger.info("About to create {} new matches, fetching odds to populate quotas", matchesToCreate.size)
            val externalOdds = oddsClient.getOdds(tournamentId)
            val tournamentTeamsByKey = tournamentTeams.associateBy { it.englishKey }
            val newQuotedMatches = externalOdds.mapNotNull { it.toMatchUpdated(matchesToCreate, tournamentTeamsByKey) }
            logger.info("Retrieved quotas for {} new matches", newQuotedMatches.size)
            matchesToCreate = consolidateMatches(newQuotedMatches, matchesToCreate)
        }

        val newTournamentStatus = tournamentEngine.calculateTournamentStatus(matchesToUpdate)
        newTournamentStatus?.let {
            logger.debug("Setting tournament={} status as {} in DB", tournament.id, newTournamentStatus)
            tournamentRepository.save(tournament.copy(status = newTournamentStatus))
        }

        val matchesToSave = matchesToUpdate + if (prepareNewMatches && matchesToCreate.isNotEmpty())
            tournamentEngine.generateMatchesCodes(matchesToCreate) else emptyList()
        if (matchesToSave.isNotEmpty()) {
            logger.debug("Matches to apply status update in DB={}", matchesToSave.size)
            matchRepository.saveAll(matchesToSave)
        }

        val anyJustFinished = matchesToUpdate.any { it.status == MatchStatus.FINISHED }
        if (anyJustFinished) {
            updateMatchPredictionsPoints(matchesToUpdate)
        }

        return matchesToSave
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
            var members = membershipRepository.findMembers(group.id!!)
            val newPredictions = groupPredictions.consolidateGroupMatchPredictions(matchesToUpdate)

            members = PredictionsEngine.updateMatchPoints(members, newPredictions)
            logger.debug("Group={} new standings saved, after applying matches points", group.id)
            membershipRepository.saveAll(members)
        }
    }

    fun checkMatchPredictions(predictions: List<MatchPrediction>): List<MatchPrediction> =
        PredictionsEngine.checkMatchPredictions(predictions.filter {
            it.status == PredictionStatus.PENDING && it.match.status == MatchStatus.FINISHED
        })

    fun consolidateMatches(updatedMatches: List<Match>, outdatedMatches: List<Match>): List<Match> {
        val updatesMatchesMap = updatedMatches.associateBy { it.id }
        return outdatedMatches.map { updatesMatchesMap[it.id] ?: it }
    }
}
