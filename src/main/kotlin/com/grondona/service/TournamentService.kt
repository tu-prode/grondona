package com.grondona.service

import com.grondona.exception.ConflictException
import com.grondona.exception.NotFoundException
import com.grondona.model.Tournament
import com.grondona.model.TournamentStatus
import com.grondona.model.dto.request.CreateTournamentRequest
import com.grondona.model.dto.request.UpdateTournamentRequest
import com.grondona.model.dto.response.TournamentMatchesResponse
import com.grondona.model.dto.response.TournamentResponse
import com.grondona.repository.MatchRepository
import com.grondona.repository.TournamentRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.UUID

@Service
class TournamentService(
    private val matchRepository: MatchRepository,
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
        return TournamentResponse.from(savedTournament)
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
        tournament.updatedAt = LocalDateTime.now()

        val savedGroup = tournamentRepository.save(tournament)
        logger.info("Tournament updated successfully: id={}, name='{}'", savedGroup.id, savedGroup.name)
        return TournamentResponse.from(savedGroup)
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
        return TournamentResponse.from(tournament)
    }

    fun getTournamentMatches(tournamentId: UUID, past: Int?, next: Int?, live: Int?): TournamentMatchesResponse {
        logger.info("Fetching matches for tournament id={}", tournamentId)

        val tournament = tournamentRepository.findById(tournamentId).orElseThrow {
            logger.warn("Tournament not found: id={}", tournamentId)
            NotFoundException("Tournament not found")
        }

        val matches = matchRepository.findByTournamentIdOrderByStartedAt(tournamentId)
        logger.info("Tournament matches fetched successfully: id={}, matches='{}'", tournamentId, matches.size)
        return TournamentMatchesResponse.from(tournament, matches, past, next, live)
    }
}
