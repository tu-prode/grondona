package com.grondona.controller

import com.grondona.exception.UnauthorizedException
import com.grondona.model.dto.request.SubmitBulkPredictionsRequest
import com.grondona.model.dto.request.SubmitPredictionRequest
import com.grondona.model.dto.response.GroupPredictionsResponse
import com.grondona.model.dto.response.PredictionResponse
import com.grondona.security.JwtUserPrincipal
import com.grondona.service.PredictionsService
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
    private val predictionsService: PredictionsService
) {

    companion object {
        private val logger = LoggerFactory.getLogger(GroupController::class.java)
    }

    @PostMapping("/matches/{matchId}")
    fun submitPredictions(
        @AuthenticationPrincipal principal: JwtUserPrincipal?,
        @PathVariable groupId: UUID, @PathVariable tournamentId: UUID, @PathVariable matchId: UUID,
        @Valid @RequestBody request: SubmitPredictionRequest
    ): ResponseEntity<PredictionResponse> {
        val userId = principal?.userId ?: throw UnauthorizedException("Authentication required")

        logger.info("POST /api/groups/{}/tournaments/{}/predictions/matches/{} - userId={}", groupId, tournamentId, userId, matchId)
        val response = predictionsService.submitPrediction(userId, groupId, request)
        logger.info("POST /api/groups/{}/tournaments/{}/predictions/matches/{} - userId={} - Prediction stored successfully", groupId, tournamentId, userId, matchId)

        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }

    @GetMapping("/matches/{matchId}")
    fun getMatchPredictions(
        @AuthenticationPrincipal principal: JwtUserPrincipal?,
        @PathVariable groupId: UUID, @PathVariable tournamentId: UUID, @PathVariable matchId: UUID,
    ): ResponseEntity<GroupPredictionsResponse> {
        val userId = principal?.userId ?: throw UnauthorizedException("Authentication required")

        logger.info("GET /api/groups/{}/tournaments/{}/predictions/matches/{} - userId={}", groupId, tournamentId, userId, matchId)
        val response = predictionsService.getMatchPredictions(userId, groupId, matchId)
        logger.info("GET /api/groups/{}/tournaments/{}/predictions/matches/{} - userId={} - Predictions retrieved: {}", groupId, tournamentId, userId, matchId, response.predictions.size)

        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }

    @PostMapping
    fun submitBulkPredictions(
        @AuthenticationPrincipal principal: JwtUserPrincipal?,
        @PathVariable groupId: UUID, @PathVariable tournamentId: UUID,
        @Valid @RequestBody request: SubmitBulkPredictionsRequest
    ): ResponseEntity<GroupPredictionsResponse> {
        val userId = principal?.userId ?: throw UnauthorizedException("Authentication required")

        logger.info("POST /api/groups/{}/tournaments/{}/predictions - userId={}", groupId, tournamentId, userId)
        val response = predictionsService.submitBulkPredictions(userId, groupId, request)
        logger.info("POST /api/groups/{}/tournaments/{}/predictions - userId={} - Predictions stored successfully: {}", groupId, tournamentId, userId, response.predictions.size)

        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }

    @GetMapping
    fun getUserPredictions(
        @AuthenticationPrincipal principal: JwtUserPrincipal?,
        @PathVariable groupId: UUID, @PathVariable tournamentId: UUID,
    ): ResponseEntity<GroupPredictionsResponse> {
        val userId = principal?.userId ?: throw UnauthorizedException("Authentication required")

        logger.info("GET /api/groups/{}/tournaments/{}/predictions - userId={}", groupId, tournamentId, userId)
        val response = predictionsService.getUserPredictions(userId, groupId)
        logger.info("GET /api/groups/{}/tournaments/{}/predictions - userId={} - Predictions stored successfully: {}", groupId, tournamentId, userId, response.predictions.size)

        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }

}
