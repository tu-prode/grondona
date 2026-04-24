package com.grondona.service

import com.grondona.exception.BadRequestException
import com.grondona.exception.ConflictException
import com.grondona.exception.NotFoundException
import com.grondona.model.ExtendedAwards
import com.grondona.model.Tournament
import com.grondona.model.TournamentStatus
import com.grondona.model.dto.request.CreateTournamentRequest
import com.grondona.model.dto.request.UpdateTournamentRequest
import com.grondona.model.dto.response.TournamentMatchesResponse
import com.grondona.model.dto.response.TournamentPlayersResponse
import com.grondona.model.dto.response.TournamentResponse
import com.grondona.model.dto.response.TournamentTeamsResponse
import com.grondona.repository.MatchRepository
import com.grondona.repository.PlayerRepository
import com.grondona.repository.TeamRepository
import com.grondona.repository.TournamentRepository
import com.grondona.service.engine.WorldCupEngine
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.UUID

@Service
class TournamentService(
    private val teamRepository: TeamRepository,
    private val matchRepository: MatchRepository,
    private val playerRepository: PlayerRepository,
    private val tournamentRepository: TournamentRepository,
) {

    companion object {
        private val logger = LoggerFactory.getLogger(TournamentService::class.java)
    }

    @Transactional
    fun createTournament(request: CreateTournamentRequest): TournamentResponse {
        logger.info("Creating tournament with name='{}', status={}", request.name, request.status)

        if (tournamentRepository.existsByName(request.name)) {
            logger.warn("Tournament creation failed: name '{}' already exists", request.name)
            throw ConflictException(message = "Tournament name already exists", field = "name", rejectedValue = request.name)
        }

        val tournament = Tournament(
            name = request.name,
            status = request.status ?: TournamentStatus.NOT_STARTED,
            createdAt = LocalDateTime.now(),
        )

        val savedTournament = tournamentRepository.save(tournament)
        logger.info("Tournament created successfully: id={}, name='{}'", savedTournament.id, savedTournament.name)
        return TournamentResponse.from(savedTournament, checkAwards(tournament))
    }

    @Transactional
    fun updateTournament(tournamentId: UUID, request: UpdateTournamentRequest): TournamentResponse {
        logger.info("Updating tournament id={} with {}", tournamentId, request)

        val tournament = tournamentRepository.findById(tournamentId).orElseThrow {
            logger.warn("Tournament not found: id={}", tournamentId)
            NotFoundException("Tournament not found")
        }

        request.name?.let { newName ->
            if (newName != tournament.name && tournamentRepository.existsByName(newName)) {
                logger.warn("Tournament update failed: name '{}' already exists", newName)
                throw ConflictException(message = "Tournament name already exists", field = "name", rejectedValue = newName)
            }
            tournament.name = newName
        }

        request.status?.let { newStatus -> tournament.status = newStatus }
        request.awards?.let { newAwards ->
            if (tournament.status == TournamentStatus.FINISHED) {
                tournament.awards = newAwards
            } else {
                logger.warn("Tournament update failed: cannot set awards for a non-finished tournament")
                throw BadRequestException(message = "Setting awards for a non-finished tournament")
            }
        }
        tournament.updatedAt = LocalDateTime.now()

        val savedTournament = tournamentRepository.save(tournament)
        logger.info("Tournament updated successfully: id={}, name='{}'", savedTournament.id, savedTournament.name)
        return TournamentResponse.from(savedTournament, checkAwards(tournament))
    }

    @Transactional
    fun deleteTournament(tournamentId: UUID) {
        logger.info("Deleting tournament id={}", tournamentId)

        val group = tournamentRepository.findById(tournamentId).orElseThrow {
            logger.warn("Tournament not found for deletion: id={}", tournamentId)
            NotFoundException("Tournament not found")
        }

        tournamentRepository.delete(group)
        logger.info("Tournament deleted successfully: id={}, name='{}'", tournamentId, group.name)
    }

    fun getTournamentById(tournamentId: UUID): TournamentResponse {
        logger.info("Fetching tournament id={}", tournamentId)

        val tournament = tournamentRepository.findById(tournamentId).orElseThrow {
            logger.warn("Tournament not found: id={}", tournamentId)
            NotFoundException("Tournament not found")
        }

        logger.info("Tournament fetched successfully: id={}, name='{}'", tournament.id, tournament.name)
        return TournamentResponse.from(tournament, checkAwards(tournament))
    }

    fun getTournamentMatches(tournamentId: UUID, past: Int?, next: Int?, live: Int?): TournamentMatchesResponse {
        logger.info("Fetching matches for tournament id={}", tournamentId)

        val tournament = tournamentRepository.findById(tournamentId).orElseThrow {
            logger.warn("Tournament not found id={}", tournamentId)
            NotFoundException("Tournament not found")
        }

        val matches = matchRepository.findByTournamentIdOrderByStartedAt(tournamentId)
        logger.info("Tournament matches fetched successfully id={}, amount of matches={}", tournamentId, matches.size)
        return TournamentMatchesResponse.from(tournament, matches, past, next, live)
    }

    fun getTournamentTeams(tournamentId: UUID): TournamentTeamsResponse {
        logger.info("Fetching teams for tournament id={}", tournamentId)

        val tournament = tournamentRepository.findById(tournamentId).orElseThrow {
            logger.warn("Tournament not found id={}", tournamentId)
            NotFoundException("Tournament not found")
        }

        val teams = teamRepository.findByTournamentId(tournamentId)
        logger.info("Tournament teams fetched successfully id={}, amount of teams={}", tournamentId, teams.size)
        return TournamentTeamsResponse.from(teams)
    }

    fun getTournamentPlayers(tournamentId: UUID, country: String?, isGoalkeeper: Boolean?, isU21: Boolean?): TournamentPlayersResponse {
        logger.info("Fetching players for tournament id={}", tournamentId)

        val tournament = tournamentRepository.findById(tournamentId).orElseThrow {
            logger.warn("Tournament not found id={}", tournamentId)
            NotFoundException("Tournament not found")
        }

        val players = playerRepository.findTournamentPlayers(
            tournamentId, country, isGoalkeeper, WorldCupEngine.BEST_YOUNG_PLAYER_DATE_LIMIT.takeIf { isU21 ?: false },
        )
        logger.info("Tournament players fetched successfully id={}, amount of players={}", tournamentId, players.size)
        return TournamentPlayersResponse.from(players)
    }

    fun checkAwards(tournament: Tournament): ExtendedAwards? =
        tournament.awards?.let {
            ExtendedAwards(
                champion = teamRepository.findById(it.champion).orElseThrow { NotFoundException("Champion not found") },
                topScorer = playerRepository.findById(it.topScorer).orElseThrow { NotFoundException("Top scorer not found") },
                bestPlayer = playerRepository.findById(it.bestPlayer).orElseThrow { NotFoundException("Best player not found") },
                bestGoalkeeper = playerRepository.findById(it.bestGoalkeeper).orElseThrow { NotFoundException("Best goalkeeper not found") },
                bestYoungPlayer = playerRepository.findById(it.bestYoungPlayer).orElseThrow { NotFoundException("Best young player not found") },
            )
        }.takeIf { tournament.status == TournamentStatus.FINISHED }
}
