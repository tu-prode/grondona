package com.grondona.service

import com.grondona.exception.BadRequestException
import com.grondona.exception.ConflictException
import com.grondona.exception.NotFoundException
import com.grondona.model.AwardPrediction
import com.grondona.model.ExtendedAwards
import com.grondona.model.Match
import com.grondona.model.Player
import com.grondona.model.PredictionStatus
import com.grondona.model.Team
import com.grondona.model.Tournament
import com.grondona.model.TournamentStatus
import com.grondona.model.dto.request.CreateMatchesRequest
import com.grondona.model.dto.request.CreatePlayerRequest
import com.grondona.model.dto.request.CreateTeamRequest
import com.grondona.model.dto.request.CreateTournamentRequest
import com.grondona.model.dto.request.UpdateTournamentRequest
import com.grondona.model.dto.response.CreatedMatchesResponse
import com.grondona.model.dto.response.PlayerResponse
import com.grondona.model.dto.response.TeamResponse
import com.grondona.model.dto.response.TournamentMatchesResponse
import com.grondona.model.dto.response.TournamentPlayersResponse
import com.grondona.model.dto.response.TournamentResponse
import com.grondona.model.dto.response.TournamentTeamsResponse
import com.grondona.repository.AwardPredictionRepository
import com.grondona.repository.MatchRepository
import com.grondona.repository.MembershipRepository
import com.grondona.repository.PlayerRepository
import com.grondona.repository.TeamRepository
import com.grondona.repository.TournamentRepository
import com.grondona.service.engine.PredictionsEngine
import com.grondona.service.engine.WorldCupEngine
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.UUID
import kotlin.String
import kotlin.collections.component1
import kotlin.collections.component2

@Service
class TournamentService(
    private val teamRepository: TeamRepository,
    private val matchRepository: MatchRepository,
    private val playerRepository: PlayerRepository,
    private val tournamentRepository: TournamentRepository,
    private val membershipRepository: MembershipRepository,
    private val awardPredictionRepository: AwardPredictionRepository,
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

        if (request.name != null) {
            if (request.name != tournament.name && tournamentRepository.existsByName(request.name)) {
                logger.warn("Tournament update failed: name '{}' already exists", request.name)
                throw ConflictException(message = "Tournament name already exists", field = "name", rejectedValue = request.name)
            }
        }

        if (request.awards != null && (request.status ?: tournament.status) != TournamentStatus.FINISHED) {
            logger.warn("Tournament update failed: cannot set awards for a non-finished tournament")
            throw BadRequestException(message = "Setting awards for a non-finished tournament")
        }

        val tournamentToSave = tournament.copy(
            name = request.name ?: tournament.name,
            status = request.status ?: tournament.status,
            awards = request.awards ?: tournament.awards,
            updatedAt = LocalDateTime.now(),
        )

        val savedTournament = tournamentRepository.save(tournamentToSave)
        logger.info("Tournament updated successfully: id={}, name='{}'", savedTournament.id, savedTournament.name)

        if (savedTournament.awards != null) {
            logger.info("Running the awards predictions check for tournament={}", savedTournament.id)
            updateAwardPredictionsPoints(savedTournament)
        }

        return TournamentResponse.from(savedTournament, checkAwards(tournament))
    }

    fun updateAwardPredictionsPoints(tournament: Tournament) {
        var predictionsToUpdate = awardPredictionRepository.findByTournamentId(tournament.id!!)
        predictionsToUpdate = checkAwardPredictions(predictionsToUpdate)
        if (predictionsToUpdate.isNotEmpty()) {
            logger.debug("Award predictions to update in DB={}", predictionsToUpdate.size)
            awardPredictionRepository.saveAll(predictionsToUpdate)
        }

        predictionsToUpdate.groupBy { it.group }.forEach { (group, groupPredictions) ->
            var members = membershipRepository.findMembers(group.id!!)
            val newPredictions = groupPredictions.groupBy { it.user.id!! }
            members = PredictionsEngine.updateAwardPoints(members, newPredictions)
            logger.debug("Group={} new standings saved, after applying awards points", group.id)
            membershipRepository.saveAll(members)
        }
    }

    fun checkAwardPredictions(predictions: List<AwardPrediction>): List<AwardPrediction> =
        PredictionsEngine.checkAwardPredictions(predictions.filter {
            it.status == PredictionStatus.PENDING && it.group.tournament.status == TournamentStatus.FINISHED
        })

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

        tournamentRepository.findById(tournamentId).orElseThrow {
            logger.warn("Tournament not found id={}", tournamentId)
            NotFoundException("Tournament not found")
        }

        val teams = teamRepository.findByTournamentId(tournamentId)
        logger.info("Tournament teams fetched successfully id={}, amount of teams={}", tournamentId, teams.size)
        return TournamentTeamsResponse.from(teams)
    }

    fun getTournamentPlayers(tournamentId: UUID, country: String?, isGoalkeeper: Boolean?, isU21: Boolean?): TournamentPlayersResponse {
        logger.info("Fetching players for tournament id={}", tournamentId)

        tournamentRepository.findById(tournamentId).orElseThrow {
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

    @Transactional
    fun createTournamentMatches(tournamentId: UUID, request: CreateMatchesRequest): CreatedMatchesResponse {
        logger.info("Creating match for tournament id={}", tournamentId)

        val tournament = tournamentRepository.findById(tournamentId).orElseThrow {
            logger.warn("Tournament not found id={}", tournamentId)
            NotFoundException("Tournament not found")
        }

        val matchesCodes = request.matches.map { it.code }
        val existingCodes = matchRepository.findByCodes(tournamentId, matchesCodes).map { it.code }
        if (existingCodes.isNotEmpty()) {
            logger.error("Trying to create matches for tournament={} with existing codes: {}", tournamentId, existingCodes)
            throw ConflictException("Some codes are already registered", field = "code", rejectedValue = existingCodes.sorted().joinToString(","))
        }

        val teamsPerId = request.matches
            .map { setOf(it.homeTeam, it.awayTeam) }
            .reduce { accList, newList -> accList + newList }
            .let { ids -> teamRepository.findAllById(ids) }
            .groupBy { it.id!! }
            .mapValues { (_, teams) -> teams.first() }

        val matchesToSave = request.matches.map { matchReq ->
            val homeTeam: Team = teamsPerId[matchReq.homeTeam] ?: run {
                logger.warn("Team not found id={}", matchReq.homeTeam)
                throw NotFoundException("Home team not found")
            }

            val awayTeam = teamsPerId[matchReq.homeTeam] ?: run {
                logger.warn("Team not found id={}", matchReq.awayTeam)
                throw NotFoundException("Away team not found")
            }

            Match(
                code = matchReq.code, tournament = tournament,
                homeTeam = homeTeam, awayTeam = awayTeam,
                startedAt = matchReq.startedAt, hasMultiplier = matchReq.hasMultiplier ?: false,
            )
        }

        matchRepository.saveAll(matchesToSave)
        logger.info("Matches created successfully for tournament={}: {}", tournamentId, matchesToSave.size)
        return CreatedMatchesResponse.from(tournament, matchesToSave)
    }

    @Transactional
    fun createTournamentTeam(tournamentId: UUID, request: CreateTeamRequest): TeamResponse {
        logger.info("Creating team for tournament id={}", tournamentId)

        val tournament = tournamentRepository.findById(tournamentId).orElseThrow {
            logger.warn("Tournament not found id={}", tournamentId)
            NotFoundException("Tournament not found")
        }

        val team = Team(
            tournament = tournament,
            name = request.name,
            code = request.code,
            icon = request.icon,
        )

        teamRepository.save(team)
        logger.info("Team created successfully with id={}, for tournament={}", team.id, tournamentId)
        return TeamResponse.from(team)
    }

    @Transactional
    fun createTournamentPlayer(tournamentId: UUID, request: CreatePlayerRequest): PlayerResponse {
        logger.info("Creating player for tournament id={}", tournamentId)

        tournamentRepository.findById(tournamentId).orElseThrow {
            logger.warn("Tournament not found id={}", tournamentId)
            NotFoundException("Tournament not found")
        }

        val team = teamRepository.findById(request.team).orElseThrow {
            logger.warn("Team not found id={}", request.team)
            NotFoundException("Team not found")
        }

        val player = Player(
            team = team,
            name = request.name,
            position = request.position,
            birthdate = request.birthdate,
        )

        playerRepository.save(player)
        logger.info("Player created successfully with id={}, for team={} and tournament={}", player.id, request.team, tournamentId)
        return PlayerResponse.from(player)
    }
}
