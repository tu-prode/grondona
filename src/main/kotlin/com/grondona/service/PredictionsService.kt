package com.grondona.service

import com.grondona.exception.BadRequestException
import com.grondona.exception.ForbiddenException
import com.grondona.exception.NotFoundException
import com.grondona.model.Match
import com.grondona.model.Prediction
import com.grondona.model.dto.request.SubmitPredictionRequest
import com.grondona.model.dto.request.SubmitBulkPredictionsRequest
import com.grondona.model.dto.response.GroupPredictionsResponse
import com.grondona.model.dto.response.PredictionResponse
import com.grondona.repository.GroupRepository
import com.grondona.repository.GroupUserRepository
import com.grondona.repository.MatchRepository
import com.grondona.repository.PredictionRepository
import com.grondona.repository.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID

@Service
class PredictionsService(
    private val userRepository: UserRepository,
    private val groupRepository: GroupRepository,
    private val matchRepository: MatchRepository,
    private val groupUserRepository: GroupUserRepository,
    private val predictionRepository: PredictionRepository,
) {

    companion object {
        private val logger = LoggerFactory.getLogger(PredictionsService::class.java)

        fun canSubmit(match: Match): Boolean =
            match.startedAt != null && match.startedAt!! > LocalDateTime.now().plus(15, ChronoUnit.MINUTES)
    }

    @Transactional
    fun submitPrediction(userId: UUID, groupId: UUID, request: SubmitPredictionRequest): PredictionResponse {
        logger.info("Submitting prediction for user={}, match={} at group={}", userId, request.matchId, groupId)

        val user = userRepository.findById(userId).orElseThrow { NotFoundException("User not found") }
        val group = groupRepository.findById(groupId).orElseThrow { NotFoundException("Group not found") }

        if (!groupUserRepository.existsByUserIdAndGroupId(userId, groupId)) {
            logger.warn("User={} trying to submit a prediction to the group={}, but doesn't belong to", userId, groupId)
            throw ForbiddenException("User doesn't belong to the group")
        }

        val prediction = Prediction(
            user = user,
            group = group,
            homeGoals = request.homeGoals,
            awayGoals = request.awayGoals,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now(),
            match = matchRepository.findById(request.matchId).orElseThrow { NotFoundException("Match not found") },
        )

        if (!canSubmit(prediction.match)) {
            logger.warn("Trying to submit a prediction for a match that is locked, user={}, match={} at group={}", userId, request.matchId, groupId)
            throw BadRequestException(message = "Cannot submit predictions for this match")
        }

        predictionRepository.save(prediction)
        return PredictionResponse.from(prediction)
    }

    @Transactional
    fun submitBulkPredictions(userId: UUID, groupId: UUID, request: SubmitBulkPredictionsRequest): GroupPredictionsResponse {
        logger.info("Submitting {} predictions for user={} at group={}", request.predictions.size, userId, groupId)

        val user = userRepository.findById(userId).orElseThrow { NotFoundException("User not found") }
        val group = groupRepository.findById(groupId).orElseThrow { NotFoundException("Group not found") }

        if (!groupUserRepository.existsByUserIdAndGroupId(userId, groupId)) {
            logger.warn("User={} trying to submit predictions to the group={}, but doesn't belong to", userId, groupId)
            throw ForbiddenException("User doesn't belong to the group")
        }

        val predictions = request.predictions.map { prediction ->
            Prediction(
                user = user,
                group = group,
                homeGoals = prediction.homeGoals,
                awayGoals = prediction.awayGoals,
                createdAt = LocalDateTime.now(),
                updatedAt = LocalDateTime.now(),
                match = matchRepository.findById(prediction.matchId)
                    .orElseThrow { NotFoundException("Match not found") },
            )
        }.filter { canSubmit(it.match) }

        predictionRepository.saveAll(predictions)
        return GroupPredictionsResponse.from(group, predictions)
    }

    fun getUserPredictions(userId: UUID, groupId: UUID): GroupPredictionsResponse {
        logger.info("Fetching predictions for user={} at group={}", userId, groupId)

        val group = groupRepository.findById(groupId).orElseThrow { NotFoundException("Group not found") }
        val predictions = predictionRepository.findAllByUserIdAndGroupIdOrderByMatchStartedAt(userId, groupId)

        return GroupPredictionsResponse.from(group, predictions)
    }

    fun getMatchPredictions(userId: UUID, groupId: UUID, matchId: UUID): GroupPredictionsResponse {
        logger.info("Fetching predictions for match={} at group={}, by user={}", matchId, groupId, userId)

        val group = groupRepository.findById(groupId).orElseThrow { NotFoundException("Group not found") }
        if (!groupUserRepository.existsByUserIdAndGroupId(userId, groupId)) {
            logger.warn("User={} trying fetch predictions from the group={}, but doesn't belong to", userId, groupId)
            throw ForbiddenException("User doesn't belong to the group")
        }

        val match = matchRepository.findById(matchId).orElseThrow { NotFoundException("Match not found") }
        if (canSubmit(match)) {
            logger.warn("User={} trying fetch predictions for the match={} at group={}, but it's not locked", userId, matchId, groupId)
            throw BadRequestException("Match is still open")
        }

        val predictions = predictionRepository.findGroupPredictionsForMatch(groupId, matchId)
        return GroupPredictionsResponse.from(group, predictions)
    }
}
