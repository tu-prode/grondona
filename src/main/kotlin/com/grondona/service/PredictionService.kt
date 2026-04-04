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
import com.grondona.repository.MembershipRepository
import com.grondona.repository.MatchRepository
import com.grondona.repository.PredictionRepository
import com.grondona.repository.UserRepository
import com.grondona.utils.WorldCupEngine
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.UUID

@Service
class PredictionService(
    private val userRepository: UserRepository,
    private val groupRepository: GroupRepository,
    private val matchRepository: MatchRepository,
    private val membershipRepository: MembershipRepository,
    private val predictionRepository: PredictionRepository,
) {

    companion object {
        private val logger = LoggerFactory.getLogger(PredictionService::class.java)

        fun canSubmit(match: Match): Boolean = when (match.tournament.id) {
            WorldCupEngine.SYSTEM_TOURNAMENT_ID -> WorldCupEngine.isMatchUnlocked(match)
            else -> throw NotFoundException("Tournament not support")
        }
    }

    @Transactional
    fun submitPrediction(userId: UUID, groupId: UUID, request: SubmitPredictionRequest): PredictionResponse {
        logger.info("Submitting prediction for user={}, match={} at group={}", userId, request.matchId, groupId)

        val user = userRepository.findById(userId).orElseThrow { NotFoundException("User not found") }
        val group = groupRepository.findById(groupId).orElseThrow { NotFoundException("Group not found") }

        if (!membershipRepository.existsByUserIdAndGroupId(userId, groupId)) {
            logger.warn("User={} trying to submit a prediction to the group={}, but doesn't belong to", userId, groupId)
            throw ForbiddenException("User doesn't belong to the group")
        }

        var prediction = Prediction(
            user = user,
            group = group,
            homeGoals = request.homeGoals,
            awayGoals = request.awayGoals,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now(),
            match = matchRepository.findById(request.matchId).orElseThrow { NotFoundException("Match not found") },
        )

        if (!canSubmit(prediction.match)) {
            logger.warn(
                "Trying to submit a prediction for a match that is locked, user={}, match={} at group={}",
                userId,
                request.matchId,
                groupId
            )
            throw BadRequestException(message = "Cannot submit predictions for this match")
        }

        prediction = predictionRepository.upsert(prediction)
        return PredictionResponse.from(prediction)
    }

    @Transactional
    fun submitBulkPredictions(
        userId: UUID,
        groupId: UUID,
        request: SubmitBulkPredictionsRequest
    ): GroupPredictionsResponse {
        logger.info("Submitting {} predictions for user={} at group={}", request.predictions.size, userId, groupId)

        val user = userRepository.findById(userId).orElseThrow { NotFoundException("User not found") }
        val group = groupRepository.findById(groupId).orElseThrow { NotFoundException("Group not found") }

        if (!membershipRepository.existsByUserIdAndGroupId(userId, groupId)) {
            logger.warn("User={} trying to submit predictions to the group={}, but doesn't belong to", userId, groupId)
            throw ForbiddenException("User doesn't belong to the group")
        }

        var predictions = request.predictions.map { prediction ->
            Prediction(
                user = user,
                group = group,
                homeGoals = prediction.homeGoals,
                awayGoals = prediction.awayGoals,
                match = matchRepository.findById(prediction.matchId)
                    .orElseThrow { NotFoundException("Match not found") },
            )
        }.filter {
            if (canSubmit(it.match)) true else {
                logger.warn(
                    "User={} trying to submit predictions for match={}, but it's locked",
                    userId,
                    it.match.id
                ); false
            }
        }


        predictions = predictionRepository.upsertAll(predictions)
        return GroupPredictionsResponse.fromPrediction(group, predictions)
    }

    fun getGroupUserPredictions(userId: UUID, groupId: UUID): GroupPredictionsResponse {
        logger.info("Fetching predictions for user={} at group={}", userId, groupId)

        val user = userRepository.findById(userId).orElseThrow { NotFoundException("User not found") }
        val group = groupRepository.findById(groupId).orElseThrow { NotFoundException("Group not found") }
        if (!membershipRepository.existsByUserIdAndGroupId(userId, groupId)) {
            logger.warn("User={} trying fetch predictions from the group={}, but doesn't belong to", userId, groupId)
            throw ForbiddenException("User doesn't belong to the group")
        }

        val predictions = predictionRepository.findGroupPredictionsForUser(groupId, userId)
        return GroupPredictionsResponse.fromPredictionView(group, predictions)
    }

    fun getGroupMatchPredictions(userId: UUID, groupId: UUID, matchId: UUID): GroupPredictionsResponse {
        logger.info("Fetching predictions for match={} at group={}, by user={}", matchId, groupId, userId)

        val group = groupRepository.findById(groupId).orElseThrow { NotFoundException("Group not found") }
        if (!membershipRepository.existsByUserIdAndGroupId(userId, groupId)) {
            logger.warn("User={} trying fetch predictions from the group={}, but doesn't belong to", userId, groupId)
            throw ForbiddenException("User doesn't belong to the group")
        }

        val match = matchRepository.findById(matchId).orElseThrow { NotFoundException("Match not found") }
        if (canSubmit(match)) {
            logger.warn(
                "User={} trying fetch predictions for the match={} at group={}, but it's not locked",
                userId,
                matchId,
                groupId
            )
            throw BadRequestException("Match is still open")
        }

        val predictionViews = predictionRepository.findGroupPredictionsForMatch(groupId, matchId)
        return GroupPredictionsResponse.fromPredictionView(group, predictionViews)
    }
}
