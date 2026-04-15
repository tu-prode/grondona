package com.grondona.controller

import com.grondona.exception.UnauthorizedException
import com.grondona.model.dto.request.SubmitAwardPredictionRequest
import com.grondona.model.dto.request.SubmitBulkMatchPredictionsRequest
import com.grondona.model.dto.request.SubmitMatchPredictionRequest
import com.grondona.model.dto.response.AwardPredictionsResponse
import com.grondona.model.dto.response.GroupAwardPredictionsResponse
import com.grondona.model.dto.response.GroupMatchPredictionsResponse
import com.grondona.model.dto.response.MatchPredictionResponse
import com.grondona.security.JwtUserPrincipal
import com.grondona.service.PredictionService
import jakarta.validation.Valid
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/tournaments/{tournamentId}/groups/{groupId}/predictions")
class PredictionController(
    private val predictionsService: PredictionService
) {

    companion object {
        private val logger = LoggerFactory.getLogger(GroupController::class.java)
    }

    @PostMapping("/matches/{matchId}")
    fun submitSingleGroupMatchPredictions(
        @AuthenticationPrincipal principal: JwtUserPrincipal?,
        @PathVariable groupId: UUID, @PathVariable tournamentId: UUID, @PathVariable matchId: UUID,
        @Valid @RequestBody request: SubmitMatchPredictionRequest
    ): ResponseEntity<MatchPredictionResponse> {
        val userId = principal?.userId ?: throw UnauthorizedException("Authentication required")

        logger.info("POST /api/{}/tournaments/groups/{}/predictions/matches/{} - userId={}", tournamentId, groupId, userId, matchId)
        val response = predictionsService.submitSingleMatchPrediction(userId, groupId, request)
        logger.info("POST /api/{}/tournaments/groups/{}/predictions/matches/{} - userId={} - Prediction stored successfully", tournamentId, groupId, userId, matchId)

        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }

    @GetMapping("/matches/{matchId}")
    fun getSingleGroupMatchPredictions(
        @AuthenticationPrincipal principal: JwtUserPrincipal?,
        @PathVariable groupId: UUID, @PathVariable tournamentId: UUID, @PathVariable matchId: UUID,
    ): ResponseEntity<GroupMatchPredictionsResponse> {
        val userId = principal?.userId ?: throw UnauthorizedException("Authentication required")

        logger.info("GET /api/{}/tournaments/groups/{}/predictions/matches/{} - userId={}", tournamentId, groupId, userId, matchId)
        val response = predictionsService.getSingleMatchPredictionsForGroup(userId, groupId, matchId)
        logger.info("GET /api/{}/tournaments/groups/{}/predictions/matches/{} - userId={} - Predictions retrieved: {}", tournamentId, groupId, userId, matchId, response.predictions.size)

        return ResponseEntity.status(HttpStatus.OK).body(response)
    }

    @PostMapping("/matches")
    fun submitGroupBulkMatchPredictions(
        @AuthenticationPrincipal principal: JwtUserPrincipal?,
        @PathVariable groupId: UUID, @PathVariable tournamentId: UUID,
        @Valid @RequestBody request: SubmitBulkMatchPredictionsRequest
    ): ResponseEntity<GroupMatchPredictionsResponse> {
        val userId = principal?.userId ?: throw UnauthorizedException("Authentication required")

        logger.info("POST /api/{}/tournaments/groups/{}/predictions - userId={}", tournamentId, groupId, userId)
        val response = predictionsService.submitMatchPredictions(userId, groupId, request)
        logger.info("POST /api/{}/tournaments/groups/{}/predictions - userId={} - Predictions stored successfully: {}", tournamentId, groupId, userId, response.predictions.size)

        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }

    @GetMapping("/matches")
    fun getGroupMatchPredictions(
        @AuthenticationPrincipal principal: JwtUserPrincipal?,
        @PathVariable groupId: UUID, @PathVariable tournamentId: UUID,
    ): ResponseEntity<GroupMatchPredictionsResponse> {
        val userId = principal?.userId ?: throw UnauthorizedException("Authentication required")

        logger.info("GET /api/{}/tournaments/groups/{}/predictions/matches - userId={}", tournamentId, groupId, userId)
        val response = predictionsService.getMatchPredictionsForGroup(userId, groupId)
        logger.info("GET /api/{}/tournaments/groups/{}/predictions/matches - userId={} - Predictions retrieved: {}", tournamentId, groupId, userId, response.predictions.size)

        return ResponseEntity.status(HttpStatus.OK).body(response)
    }

    @GetMapping("/matches/me")
    fun getMyGroupMatchPredictions(
        @AuthenticationPrincipal principal: JwtUserPrincipal?,
        @PathVariable groupId: UUID, @PathVariable tournamentId: UUID,
    ): ResponseEntity<GroupMatchPredictionsResponse> {
        val userId = principal?.userId ?: throw UnauthorizedException("Authentication required")

        logger.info("GET /api/tournaments/{}/groups/{}/predictions/matches/me - userId={}", tournamentId, groupId, userId)
        val response = predictionsService.getUserMatchPredictionsForGroup(userId, groupId)
        logger.info("GET /api/tournaments/{}/groups/{}/predictions/matches/me - userId={} - Predictions retrieved: {}", tournamentId, groupId, userId, response.predictions.size)

        return ResponseEntity.status(HttpStatus.OK).body(response)
    }

    @PostMapping("/awards")
    fun submitGroupAwardPredictions(
        @AuthenticationPrincipal principal: JwtUserPrincipal?,
        @PathVariable groupId: UUID, @PathVariable tournamentId: UUID,
        @Valid @RequestBody request: SubmitAwardPredictionRequest
    ): ResponseEntity<AwardPredictionsResponse> {
        val userId = principal?.userId ?: throw UnauthorizedException("Authentication required")

        logger.info("POST /api/{}/tournaments/groups/{}/predictions/awards - userId={}", tournamentId, groupId, userId)
        val response = predictionsService.submitAwardPredictions(userId, groupId, request)
        logger.info("POST /api/{}/tournaments/groups/{}/predictions/awards - userId={} - Award predictions stored successfully", tournamentId, groupId, userId)

        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }

    @GetMapping("/awards")
    fun getGroupAwardPredictions(
        @AuthenticationPrincipal principal: JwtUserPrincipal?,
        @PathVariable groupId: UUID, @PathVariable tournamentId: UUID,
    ): ResponseEntity<GroupAwardPredictionsResponse> {
        val userId = principal?.userId ?: throw UnauthorizedException("Authentication required")

        logger.info("GET /api/{}/tournaments/groups/{}/predictions/awards - userId={}", tournamentId, groupId, userId)
        val response = predictionsService.getAwardPredictionsForGroup(userId, groupId, tournamentId)
        logger.info("GET /api/{}/tournaments/groups/{}/predictions/awards - userId={} - Award predictions retrieved: {}", tournamentId, groupId, userId, response.predictions.size)

        return ResponseEntity.status(HttpStatus.OK).body(response)
    }

    @GetMapping("/awards/me")
    fun getMyGroupAwardPredictions(
        @AuthenticationPrincipal principal: JwtUserPrincipal?,
        @PathVariable groupId: UUID, @PathVariable tournamentId: UUID,
    ): ResponseEntity<AwardPredictionsResponse> {
        val userId = principal?.userId ?: throw UnauthorizedException("Authentication required")

        logger.info("GET /api/{}/tournaments/groups/{}/predictions/awards/me - userId={}", tournamentId, groupId, userId)
        val response = predictionsService.getUserAwardPredictionsForGroup(userId, groupId)
        logger.info("GET /api/{}/tournaments/groups/{}/predictions/awards/me - userId={} - Award predictions retrieved", tournamentId, groupId, userId)

        return ResponseEntity.status(HttpStatus.OK).body(response)
    }

}
