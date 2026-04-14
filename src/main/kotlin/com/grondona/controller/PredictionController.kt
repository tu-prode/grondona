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
    fun submitPredictions(
        @AuthenticationPrincipal principal: JwtUserPrincipal?,
        @PathVariable groupId: UUID, @PathVariable tournamentId: UUID, @PathVariable matchId: UUID,
        @Valid @RequestBody request: SubmitMatchPredictionRequest
    ): ResponseEntity<MatchPredictionResponse> {
        val userId = principal?.userId ?: throw UnauthorizedException("Authentication required")

        logger.info("POST /api/groups/{}/tournaments/{}/predictions/matches/{} - userId={}", groupId, tournamentId, userId, matchId)
        val response = predictionsService.submitPrediction(userId, groupId, request)
        logger.info("POST /api/groups/{}/tournaments/{}/predictions/matches/{} - userId={} - Prediction stored successfully", groupId, tournamentId, userId, matchId)

        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }

    @GetMapping("/matches/{matchId}")
    fun getGroupMatchPredictions(
        @AuthenticationPrincipal principal: JwtUserPrincipal?,
        @PathVariable groupId: UUID, @PathVariable tournamentId: UUID, @PathVariable matchId: UUID,
    ): ResponseEntity<GroupMatchPredictionsResponse> {
        val userId = principal?.userId ?: throw UnauthorizedException("Authentication required")

        logger.info("GET /api/groups/{}/tournaments/{}/predictions/matches/{} - userId={}", groupId, tournamentId, userId, matchId)
        val response = predictionsService.getGroupMatchPredictions(userId, groupId, matchId)
        logger.info("GET /api/groups/{}/tournaments/{}/predictions/matches/{} - userId={} - Predictions retrieved: {}", groupId, tournamentId, userId, matchId, response.predictions.size)

        return ResponseEntity.status(HttpStatus.OK).body(response)
    }

    @PostMapping("/matches")
    fun submitBulkPredictions(
        @AuthenticationPrincipal principal: JwtUserPrincipal?,
        @PathVariable groupId: UUID, @PathVariable tournamentId: UUID,
        @Valid @RequestBody request: SubmitBulkMatchPredictionsRequest
    ): ResponseEntity<GroupMatchPredictionsResponse> {
        val userId = principal?.userId ?: throw UnauthorizedException("Authentication required")

        logger.info("POST /api/groups/{}/tournaments/{}/predictions - userId={}", groupId, tournamentId, userId)
        val response = predictionsService.submitBulkPredictions(userId, groupId, request)
        logger.info("POST /api/groups/{}/tournaments/{}/predictions - userId={} - Predictions stored successfully: {}", groupId, tournamentId, userId, response.predictions.size)

        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }

    @GetMapping("/matches")
    fun getGroupUserPredictions(
        @AuthenticationPrincipal principal: JwtUserPrincipal?,
        @PathVariable groupId: UUID, @PathVariable tournamentId: UUID,
    ): ResponseEntity<GroupMatchPredictionsResponse> {
        val userId = principal?.userId ?: throw UnauthorizedException("Authentication required")

        logger.info("GET /api/groups/{}/tournaments/{}/predictions/matches - userId={}", groupId, tournamentId, userId)
        val response = predictionsService.getGroupUserPredictions(userId, groupId)
        logger.info("GET /api/groups/{}/tournaments/{}/predictions/matches - userId={} - Predictions stored successfully: {}", groupId, tournamentId, userId, response.predictions.size)

        return ResponseEntity.status(HttpStatus.OK).body(response)
    }

    @PostMapping("/awards")
    fun submitAwardPredictions(
        @AuthenticationPrincipal principal: JwtUserPrincipal?,
        @PathVariable groupId: UUID, @PathVariable tournamentId: UUID,
        @Valid @RequestBody request: SubmitAwardPredictionRequest
    ): ResponseEntity<AwardPredictionsResponse> {
        val userId = principal?.userId ?: throw UnauthorizedException("Authentication required")

        logger.info("POST /api/groups/{}/tournaments/{}/predictions/awards - userId={}", groupId, tournamentId, userId)
        val response = predictionsService.submitAwardPredictions(userId, groupId, request)
        logger.info("POST /api/groups/{}/tournaments/{}/predictions/awards - userId={} - Award predictions stored successfully", groupId, tournamentId, userId)

        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }

    @GetMapping("/awards")
    fun getGroupAwardPredictions(
        @AuthenticationPrincipal principal: JwtUserPrincipal?,
        @PathVariable groupId: UUID, @PathVariable tournamentId: UUID,
    ): ResponseEntity<GroupAwardPredictionsResponse> {
        val userId = principal?.userId ?: throw UnauthorizedException("Authentication required")

        logger.info("GET /api/groups/{}/tournaments/{}/predictions/awards - userId={}", groupId, tournamentId, userId)
        val response = predictionsService.getAwardPredictions(userId, tournamentId, groupId)
        logger.info("GET /api/groups/{}/tournaments/{}/predictions/awards - userId={} - Award predictions retrieved: {}", groupId, tournamentId, userId, response.others.size + 1)

        return ResponseEntity.status(HttpStatus.OK).body(response)
    }

}
