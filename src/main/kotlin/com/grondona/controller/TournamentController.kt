package com.grondona.controller

import com.grondona.exception.ForbiddenException
import com.grondona.exception.UnauthorizedException
import com.grondona.model.dto.request.CreateMatchRequest
import com.grondona.model.dto.request.CreatePlayerRequest
import com.grondona.model.dto.request.CreateTeamRequest
import com.grondona.model.dto.request.CreateTournamentRequest
import com.grondona.model.dto.request.UpdateTournamentRequest
import com.grondona.model.dto.response.MatchResponse
import com.grondona.model.dto.response.PlayerResponse
import com.grondona.model.dto.response.TeamResponse
import com.grondona.model.dto.response.TournamentMatchesResponse
import com.grondona.model.dto.response.TournamentPlayersResponse
import com.grondona.model.dto.response.TournamentResponse
import com.grondona.model.dto.response.TournamentTeamsResponse
import com.grondona.security.JwtUserPrincipal
import com.grondona.service.PredictionService
import com.grondona.service.TournamentService
import com.grondona.service.UserService
import jakarta.validation.Valid
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/tournaments")
class TournamentController(
    private val userService: UserService,
    private val predictionService: PredictionService,
    private val tournamentService: TournamentService,
) {

    companion object {
        private val logger = LoggerFactory.getLogger(TournamentController::class.java)
    }

    @GetMapping("/{tournamentId}")
    fun getTournament(@PathVariable tournamentId: UUID): ResponseEntity<TournamentResponse> {
        logger.info("GET /api/tournaments/{} - Fetching tournament", tournamentId)
        val response = tournamentService.getTournamentById(tournamentId)
        return ResponseEntity.ok(response)
    }

    @GetMapping("/{tournamentId}/matches")
    fun getTournamentMatches(
        @PathVariable tournamentId: UUID,
        @RequestParam(required = false) past: Int?,
        @RequestParam(required = false) live: Int?,
        @RequestParam(required = false) next: Int?
    ): ResponseEntity<TournamentMatchesResponse> {
        logger.info("GET /api/tournaments/{}/matches - Fetching tournament matches, past={}, live={}, next={}", tournamentId, past, live, next)
        val response = tournamentService.getTournamentMatches(tournamentId, past, next, live)
        return ResponseEntity.ok(response)
    }

    @GetMapping("/{tournamentId}/teams")
    fun getTournamentTeams(
        @PathVariable tournamentId: UUID
    ): ResponseEntity<TournamentTeamsResponse> {
        logger.info("GET /api/tournaments/{}/teams - Fetching tournament teams", tournamentId)
        val response = tournamentService.getTournamentTeams(tournamentId)
        return ResponseEntity.ok(response)
    }

    @GetMapping("/{tournamentId}/players")
    fun getTournamentPlayers(
        @PathVariable tournamentId: UUID,
        @RequestParam(required = false) country: String?,
        @RequestParam(required = false) goalkeeper: Boolean?,
        @RequestParam(required = false) u21: Boolean?,
    ): ResponseEntity<TournamentPlayersResponse> {
        logger.info("GET /api/tournaments/{}/players - Fetching tournament players, country={}, goalkeeper={}, u21={}", tournamentId, country, goalkeeper, u21)
        val response = tournamentService.getTournamentPlayers(tournamentId, country, goalkeeper, u21)
        return ResponseEntity.ok(response)
    }

    //// ADMIN ENDPOINTS

    fun <T> withSuperuserValidation(principal: JwtUserPrincipal?, callback: () -> ResponseEntity<T>): ResponseEntity<T> {
        val userId = principal?.userId ?: throw UnauthorizedException("Authentication required")

        if (userService.hasAdminAccess(userId)) {
            logger.info("User={} performing a SUPERUSER operation", userId)
            return callback()
        } else {
            logger.warn("User={} trying to perform a SUPERUSER operation but has no access", userId)
            throw ForbiddenException("User has no access to perform this operation")
        }
    }

    @PostMapping
    fun createTournament(
        @AuthenticationPrincipal principal: JwtUserPrincipal?,
        @Valid @RequestBody request: CreateTournamentRequest,
    ): ResponseEntity<TournamentResponse> {
        return withSuperuserValidation(principal) {
            logger.info("POST /api/tournaments - Creating tournament: name='{}'", request.name)
            val response = tournamentService.createTournament(request)
            logger.info("POST /api/tournaments - Tournament created: id={}, name='{}'", response.id, response.name)
            ResponseEntity.status(HttpStatus.CREATED).body(response)
        }
    }

    @PatchMapping("/{tournamentId}")
    fun updateTournament(
        @AuthenticationPrincipal principal: JwtUserPrincipal?,
        @Valid @RequestBody request: UpdateTournamentRequest,
        @PathVariable tournamentId: UUID
    ): ResponseEntity<TournamentResponse> {
        return withSuperuserValidation(principal) {
            logger.info("PATCH /api/tournaments - Updating tournament: id='{}'", tournamentId)
            val response = tournamentService.updateTournament(tournamentId, request)
            logger.info("PATCH /api/tournaments - Tournament updated: id={}", tournamentId)
            ResponseEntity.ok(response)
        }
    }

    @DeleteMapping("/{tournamentId}")
    fun deleteTournament(
        @AuthenticationPrincipal principal: JwtUserPrincipal?,
        @PathVariable tournamentId: UUID
    ): ResponseEntity<Void> {
        return withSuperuserValidation(principal) {
            logger.info("DELETE /api/tournaments/{} - Deleting tournament", tournamentId)
            tournamentService.deleteTournament(tournamentId)
            logger.info("DELETE /api/tournaments/{} - Tournament deleted", tournamentId)
            ResponseEntity.noContent().build()
        }
    }

    @PostMapping("/{tournamentId}/matches")
    fun createTournamentMatch(
        @AuthenticationPrincipal principal: JwtUserPrincipal?,
        @PathVariable tournamentId: UUID,
        @Valid @RequestBody request: CreateMatchRequest,
    ): ResponseEntity<MatchResponse> {
        return withSuperuserValidation(principal) {
            logger.info("POST /api/tournaments/{}/matches - Creating match: code='{}'", tournamentId, request.code)
            val response = tournamentService.createTournamentMatch(tournamentId, request)
            logger.info("POST /api/tournaments/{}/matches - Match created: code='{}', id={}", tournamentId, response.code, response.id)
            ResponseEntity.status(HttpStatus.CREATED).body(response)
        }
    }

    @PostMapping("/{tournamentId}/teams")
    fun createTournamentTeam(
        @AuthenticationPrincipal principal: JwtUserPrincipal?,
        @PathVariable tournamentId: UUID,
        @Valid @RequestBody request: CreateTeamRequest,
    ): ResponseEntity<TeamResponse> {
        return withSuperuserValidation(principal) {
            logger.info("POST /api/tournaments/{}/teams - Creating team: code='{}'", tournamentId, request.code)
            val response = tournamentService.createTournamentTeam(tournamentId, request)
            logger.info("POST /api/tournaments/{}/teams - Team created: code='{}', id={}", tournamentId, response.code, response.id)
            ResponseEntity.status(HttpStatus.CREATED).body(response)
        }
    }

    @PostMapping("/{tournamentId}/players")
    fun createTournamentPlayer(
        @AuthenticationPrincipal principal: JwtUserPrincipal?,
        @PathVariable tournamentId: UUID,
        @Valid @RequestBody request: CreatePlayerRequest,
    ): ResponseEntity<PlayerResponse> {
        return withSuperuserValidation(principal) {
            logger.info("POST /api/tournaments/{}/matches - Creating player: name='{}', team={}", tournamentId, request.name, request.team)
            val response = tournamentService.createTournamentPlayer(tournamentId, request)
            logger.info("POST /api/tournaments/{}/matches - Player created: name='{}', team={}, id={}", tournamentId, response.name, response.team.id, response.id)
            ResponseEntity.status(HttpStatus.CREATED).body(response)
        }
    }

    @PutMapping("/{tournamentId}/points")
    fun recalculateTournamentPoints(
        @AuthenticationPrincipal principal: JwtUserPrincipal?,
        @PathVariable tournamentId: UUID,
    ): ResponseEntity<Void> {
        return withSuperuserValidation(principal) {
            logger.info("PUT /api/tournaments/{}/points - Triggering points recalculation", tournamentId)
            predictionService.recalculatePoints(tournamentId)
            logger.info("PUT /api/tournaments/{}/points - Points recalculated", tournamentId)
            ResponseEntity.noContent().build()
        }
    }
}
