package com.grondona.service

import com.grondona.exception.ConflictException
import com.grondona.exception.NotFoundException
import com.grondona.model.Tournament
import com.grondona.model.TournamentStatus
import com.grondona.model.dto.request.UpdateTournamentRequest
import com.grondona.model.dto.request.SubmitBulkPredictionsRequest
import com.grondona.model.dto.response.TournamentMatchesResponse
import com.grondona.model.dto.response.TournamentResponse
import com.grondona.model.dto.response.UserPredictionsResponse
import com.grondona.repository.MatchRepository
import com.grondona.repository.PredictionRepository
import com.grondona.repository.TournamentRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.UUID

@Service
class PredictionsService(
    private val predictionRepository: PredictionRepository,
) {

    companion object {
        private val logger = LoggerFactory.getLogger(PredictionsService::class.java)
    }

    @Transactional
    fun submitPredictions(userId: UUID, groupId: UUID, request: SubmitBulkPredictionsRequest): List<UserPredictionsResponse> {
        logger.info("Submitting {} predictions for user={} at group={}", request.predictions.size, userId, groupId)
        return emptyList()
    }
}
