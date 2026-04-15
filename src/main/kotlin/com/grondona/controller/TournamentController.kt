package com.grondona.controller

import com.grondona.exception.ForbiddenException
import com.grondona.exception.UnauthorizedException
import com.grondona.model.dto.request.CreateTournamentRequest
import com.grondona.model.dto.request.UpdateTournamentRequest
import com.grondona.model.dto.response.TournamentMatchesResponse
import com.grondona.model.dto.response.TournamentPlayersResponse
import com.grondona.model.dto.response.TournamentResponse
import com.grondona.model.dto.response.TournamentTeamsResponse
import com.grondona.security.JwtUserPrincipal
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
    private val tournamentService: TournamentService,
) {

    companion object {
        private val logger = LoggerFactory.getLogger(TournamentController::class.java)
    }

    fun <T> withSuperuserValidation(principal: JwtUserPrincipal?, callback: () -> ResponseEntity<T>): ResponseEntity<T> {
        val userId = principal?.userId ?: throw UnauthorizedException("Authentication required")

        if (userService.hasAdminAccess(userId)) {
            logger.info("User {} performing a SUPERADMIN operation", userId)
            return callback()
        } else {
            logger.warn("User {} trying to perform a superuser operation", userId)
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

    @GetMapping("/{tournamentId}")
    fun getTournament(@PathVariable tournamentId: UUID): ResponseEntity<TournamentResponse> {
        logger.info("GET /api/tournaments/{} - Fetching tournament", tournamentId)
        val response = tournamentService.getTournamentById(tournamentId)
        return ResponseEntity.ok(response)
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
}
