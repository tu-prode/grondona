package com.grondona.service

import com.grondona.exception.BadRequestException
import com.grondona.exception.ForbiddenException
import com.grondona.exception.NotFoundException
import com.grondona.model.Group
import com.grondona.model.GroupUser
import com.grondona.model.Match
import com.grondona.model.MatchStatus
import com.grondona.model.Prediction
import com.grondona.model.PredictionStatus
import com.grondona.model.Standing
import com.grondona.model.dto.request.SubmitPredictionRequest
import com.grondona.model.dto.request.SubmitBulkPredictionsRequest
import com.grondona.model.dto.response.GroupPredictionsResponse
import com.grondona.model.dto.response.PredictionResponse
import com.grondona.repository.GroupRepository
import com.grondona.repository.MembershipRepository
import com.grondona.repository.MatchRepository
import com.grondona.repository.PredictionRepository
import com.grondona.repository.UserRepository
import com.grondona.utils.PredictionEngine
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
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

        fun canSubmit(match: Match): Boolean =
            match.startedAt != null && match.startedAt!! > LocalDateTime.now().plus(15, ChronoUnit.MINUTES)
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

        val predictions = predictionRepository.findGroupPredictionsForMatch(groupId, matchId)
        return GroupPredictionsResponse.fromPredictionView(group, predictions)
    }

    fun calculateStandings(group: Group): List<Standing> {
        logger.info("Retrieving past matches for group={}", group.id!!)
        val matches = matchRepository.findByTournamentIdAndStatusOrderByStartedAt(group.tournament.id!!, MatchStatus.FINISHED)
        logger.debug("Matches to calculate predictions: {}", matches.size)

        logger.info("Checking members for group={}", group.id)
        val groupMembers = membershipRepository.findByGroupId(group.id)
        logger.debug("Members retrieved: {}", groupMembers.size)

        val memberStandings = if (matches.isEmpty()) {
            emptyStandings(groupMembers)
        } else {
            val lastMatchFinishedAt = matches.last().finishedAt ?: LocalDateTime.now().also {
                logger.warn("No finished-at timestamp for match={} with status FINISHED", matches.last().id)
            }

            if (groupMembers.any { it.calculatedAt?.isBefore(lastMatchFinishedAt) ?: true }) {
                newStandings(group, groupMembers, matches)
            } else {
                // If there are no new games updated since last time points were calculated, we'll return the same rank.
                return groupMembers.map {
                    Standing(rank = it.rank!!, user = it.user, points = it.points, lastPredictions = it.lastPredictions)
                }.sortedBy { it.rank }
            }
        }

        logger.info("Updating members rank and points for group={}", group.id)
        groupMembers.forEach {
            val userStanding = memberStandings[it.user.id]
            it.points = userStanding?.points ?: 0f
            it.rank = userStanding?.rank
            it.calculatedAt = LocalDateTime.now()
            it.lastPredictions = userStanding?.lastPredictions ?: emptyList()
        }
        membershipRepository.saveAll(groupMembers)

        return memberStandings.map { it.value }.sortedBy { it.rank }
    }

    private fun emptyStandings(members: List<GroupUser>): Map<UUID, Standing> = members
        .sortedBy { it.joinedAt }
        .mapIndexed { index, member ->
            Standing(
                rank = index + 1,
                user = member.user,
                points = 0f,
                lastPredictions = emptyList(),
            )
        }
        .groupBy { it.user.id!! }
        .mapValues { (_, standings) -> standings[0] } // There's only one standing prediction per user.

    private fun newStandings(group: Group, members: List<GroupUser>, matches: List<Match>): Map<UUID, Standing> {
        logger.info("Checking prediction statuses for group={}", group.id)
        var predictions = predictionRepository.findByGroupIdAndMatchIdIn(group.id!!, matches.map { it.id!! })
        predictions = PredictionEngine.check(predictions)
        predictions = predictionRepository.saveAll(predictions)
        logger.debug("Predictions retrieved: {}", predictions.size)

        // List of predictions indexed by user-id and match-id
        val predictionsIndexed = predictions.groupBy { it.user.id!! }
            .mapValues { (_, predictions) ->
                predictions.groupBy { it.match.id!! }
                    // Within a group, there's only one prediction per user, per match.
                    .mapValues { (_, predictions) -> predictions[0] }
            }

        return members.groupBy { it.user }
            .mapValues { (user, _) ->
                val userPredictions = predictionsIndexed[user.id!!]
                matches.map { userPredictions?.get(it.id!!) }
            }
            .map { (user, predictions) ->
                Standing(
                    rank = 0,
                    user = user,
                    points = PredictionEngine.points(predictions.filterNotNull()),
                    lastPredictions = predictions.map { it?.status ?: PredictionStatus.MISSING }.takeLast(5)
                )
            }
            .sortedByDescending { it.points }
            .mapIndexed { index, standing ->
                Standing(
                    rank = index + 1,
                    user = standing.user,
                    points = standing.points,
                    lastPredictions = standing.lastPredictions,
                )
            }
            .groupBy { it.user.id!! }
            .mapValues { (_, standings) -> standings[0] } // There's only one standing prediction per user.
    }
}
