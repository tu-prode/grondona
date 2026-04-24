package com.grondona.service

import com.grondona.exception.BadRequestException
import com.grondona.exception.ForbiddenException
import com.grondona.exception.NotFoundException
import com.grondona.model.AwardPrediction
import com.grondona.model.AwardType
import com.grondona.model.ExtendedAwards
import com.grondona.model.Group
import com.grondona.model.Match
import com.grondona.model.MatchPrediction
import com.grondona.model.TournamentStatus
import com.grondona.model.User
import com.grondona.model.dto.request.SubmitAwardPredictionRequest
import com.grondona.model.dto.request.SubmitMatchPredictionRequest
import com.grondona.model.dto.request.SubmitBulkMatchPredictionsRequest
import com.grondona.model.dto.response.AwardPredictionsResponse
import com.grondona.model.dto.response.GroupAwardPredictionsResponse
import com.grondona.model.dto.response.GroupMatchPredictionsResponse
import com.grondona.model.dto.response.MatchPredictionResponse
import com.grondona.repository.AwardPredictionRepository
import com.grondona.repository.GroupRepository
import com.grondona.repository.MembershipRepository
import com.grondona.repository.MatchRepository
import com.grondona.repository.MatchPredictionRepository
import com.grondona.repository.PlayerRepository
import com.grondona.repository.TeamRepository
import com.grondona.repository.TournamentRepository
import com.grondona.repository.UserRepository
import com.grondona.service.engine.WorldCupEngine
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.UUID

@Service
class PredictionService(
    private val userRepository: UserRepository,
    private val teamRepository: TeamRepository,
    private val groupRepository: GroupRepository,
    private val matchRepository: MatchRepository,
    private val playerRepository: PlayerRepository,
    private val membershipRepository: MembershipRepository,
    private val tournamentRepository: TournamentRepository,
    private val matchPredictionRepository: MatchPredictionRepository,
    private val awardPredictionRepository: AwardPredictionRepository,
) {

    companion object {
        private val logger = LoggerFactory.getLogger(PredictionService::class.java)

        fun canSubmit(match: Match): Boolean = when (match.tournament.id) {
            WorldCupEngine.SYSTEM_TOURNAMENT_ID -> WorldCupEngine.isMatchUnlocked(match)
            else -> throw NotFoundException("Tournament not support")
        }
    }

    internal fun checkMembership(userId: UUID, groupId: UUID): Pair<User, Group> {
        val user = userRepository.findById(userId).orElseThrow { NotFoundException("User not found") }
        val group = groupRepository.findById(groupId).orElseThrow { NotFoundException("Group not found") }

        if (!membershipRepository.existsByUserIdAndGroupId(userId, groupId)) {
            logger.warn("User={} trying to submit a prediction to the group={}, but doesn't belong to", userId, groupId)
            throw ForbiddenException("User doesn't belong to the group")
        }

        return Pair(user, group)
    }

    @Transactional
    fun submitSingleMatchPrediction(
        userId: UUID,
        groupId: UUID,
        request: SubmitMatchPredictionRequest
    ): MatchPredictionResponse {
        logger.info("Submitting prediction for user={}, match={} at group={}", userId, request.matchId, groupId)

        val (user, group) = checkMembership(userId, groupId)
        var prediction = MatchPrediction(
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

        prediction = matchPredictionRepository.upsert(prediction)
        return MatchPredictionResponse.from(prediction)
    }

    @Transactional
    fun submitMatchPredictions(
        userId: UUID,
        groupId: UUID,
        request: SubmitBulkMatchPredictionsRequest
    ): GroupMatchPredictionsResponse {
        logger.info("Submitting {} predictions for user={} at group={}", request.predictions.size, userId, groupId)

        val (user, group) = checkMembership(userId, groupId)
        var predictions = request.predictions.map { prediction ->
            MatchPrediction(
                user = user,
                group = group,
                homeGoals = prediction.homeGoals,
                awayGoals = prediction.awayGoals,
                match = matchRepository.findById(prediction.matchId)
                    .orElseThrow { NotFoundException("Match not found") },
            )
        }.filter {
            if (canSubmit(it.match)) true else {
                logger.warn("User={} trying to submit predictions for match={}, but it's locked", userId, it.match.id); false
            }
        }

        predictions = matchPredictionRepository.upsertAll(predictions)
        return GroupMatchPredictionsResponse.fromPredictions(group, predictions)
    }

    fun getMatchPredictionsForGroup(userId: UUID, groupId: UUID): GroupMatchPredictionsResponse {
        logger.info("Fetching predictions for user={} at group={}", userId, groupId)

        val (_, group) = checkMembership(userId, groupId)
        val predictions = matchPredictionRepository.findGroupPredictions(groupId)
        return GroupMatchPredictionsResponse.fromMatchPredictionViews(group, predictions)
    }

    fun getUserMatchPredictionsForGroup(userId: UUID, groupId: UUID): GroupMatchPredictionsResponse {
        logger.info("Fetching predictions for user={} at group={}", userId, groupId)

        val (_, group) = checkMembership(userId, groupId)
        val predictions = matchPredictionRepository.findGroupPredictionsForUser(groupId, userId)
        return GroupMatchPredictionsResponse.fromMatchPredictionViews(group, predictions)
    }

    fun getSingleMatchPredictionsForGroup(userId: UUID, groupId: UUID, matchId: UUID): GroupMatchPredictionsResponse {
        logger.info("Fetching predictions for match={} at group={}, by user={}", matchId, groupId, userId)

        val (_, group) = checkMembership(userId, groupId)
        val match = matchRepository.findById(matchId).orElseThrow { NotFoundException("Match not found") }
        if (canSubmit(match)) {
            logger.warn("User={} trying fetch predictions for the match={} at group={}, but it's not locked", userId, matchId, groupId)
            throw BadRequestException("Match is still open")
        }

        val predictionViews = matchPredictionRepository.findGroupPredictionsForMatch(groupId, matchId)
        return GroupMatchPredictionsResponse.fromMatchPredictionViews(group, predictionViews)
    }

    @Transactional
    fun submitAwardPredictions(
        userId: UUID, groupId: UUID, tournamentId: UUID,
        awardPredictions: SubmitAwardPredictionRequest,
    ): AwardPredictionsResponse {
        logger.info("Submitting award predictions for user={} at group={}", userId, groupId)

        val (user, group) = checkMembership(userId, groupId)
        val tournament = tournamentRepository.findById(tournamentId).orElseThrow { NotFoundException("Tournament not found") }
        if (tournament.status == TournamentStatus.IN_PROGRESS) {
            logger.warn("User={} trying to submit award predictions for the tournament={} at group={}, but it has already started", userId, tournamentId, groupId)
            throw BadRequestException("Tournament has already started")
        }

        when {
            awardPredictions.champions.size > 2 -> {
                logger.warn("User={} trying to submit {} options for tournament champion", userId, awardPredictions.champions.size)
                throw BadRequestException("Invalid amount of awards")
            }
            awardPredictions.topScorers.size > 3 -> {
                logger.warn("User={} trying to submit {} options for tournament top scorer", userId, awardPredictions.topScorers.size)
                throw BadRequestException("Invalid amount of awards")
            }
            awardPredictions.bestPlayers.size > 3 -> {
                logger.warn("User={} trying to submit {} options for tournament best player", userId, awardPredictions.bestPlayers.size)
                throw BadRequestException("Invalid amount of awards")
            }
            awardPredictions.bestGoalkeepers.size > 3 -> {
                logger.warn("User={} trying to submit {} options for tournament best goalkeeper", userId, awardPredictions.bestGoalkeepers.size)
                throw BadRequestException("Invalid amount of awards")
            }
            awardPredictions.bestYoungPlayers.size > 3 -> {
                logger.warn("User={} trying to submit {} options for tournament best young player", userId, awardPredictions.bestYoungPlayers.size)
                throw BadRequestException("Invalid amount of awards")
            }
        }

        var predictions = awardPredictions.champions.map {
            AwardPrediction(user = user, group = group, awardType = AwardType.CHAMPION, team = teamRepository.getReferenceById(it))
        } + awardPredictions.topScorers.map {
            AwardPrediction(user = user, group = group, awardType = AwardType.TOP_SCORER, player = playerRepository.getReferenceById(it))
        } + awardPredictions.bestPlayers.map {
            AwardPrediction(user = user, group = group, awardType = AwardType.BEST_PLAYER, player = playerRepository.getReferenceById(it))
        } + awardPredictions.bestGoalkeepers.map {
            AwardPrediction(user = user, group = group, awardType = AwardType.BEST_GOALKEEPER, player = playerRepository.getReferenceById(it))
        } + awardPredictions.bestYoungPlayers.map {
            AwardPrediction(user = user, group = group, awardType = AwardType.BEST_YOUNG_PLAYER, player = playerRepository.getReferenceById(it))
        }

        val deletedAwards = awardPredictionRepository.deleteByUserId(userId)
        logger.debug("Deleted {} previous awards from user={}", deletedAwards, userId)
        predictions = awardPredictionRepository.saveAll(predictions)
        return AwardPredictionsResponse.fromAwardPredictions(user, predictions)
    }

    fun getAwardPredictionsForGroup(userId: UUID, groupId: UUID, tournamentId: UUID): GroupAwardPredictionsResponse {
        logger.info("Fetching award predictions for group={}, by user={}", groupId, userId)

        val (_, group) = checkMembership(userId, groupId)
        val tournament = tournamentRepository.findById(tournamentId).orElseThrow { NotFoundException("Tournament not found") }
        if (tournament.status == TournamentStatus.NOT_STARTED) {
            logger.warn("User={} trying fetch award predictions for the tournament={} at group={}, but it hasn't started yet", userId, tournamentId, groupId)
            throw BadRequestException("Tournament hasn't started yet")
        }

        val awards = tournament.awards?.let {
            ExtendedAwards(
                champion = teamRepository.findById(it.champion).orElseThrow { NotFoundException("Champion not found") },
                topScorer = playerRepository.findById(it.topScorer).orElseThrow { NotFoundException("Top scorer not found") },
                bestPlayer = playerRepository.findById(it.bestPlayer).orElseThrow { NotFoundException("Best player not found") },
                bestGoalkeeper = playerRepository.findById(it.bestGoalkeeper).orElseThrow { NotFoundException("Best goalkeeper not found") },
                bestYoungPlayer = playerRepository.findById(it.bestYoungPlayer).orElseThrow { NotFoundException("Best young player not found") },
            )
        }.takeIf { tournament.status == TournamentStatus.FINISHED }

        val predictions = awardPredictionRepository.findGroupAwardPredictions(group.id!!)
        return GroupAwardPredictionsResponse.fromAwardPredictionsViews(group, predictions, awards)
    }

    fun getUserAwardPredictionsForGroup(userId: UUID, groupId: UUID): AwardPredictionsResponse {
        logger.info("Fetching award predictions for group={}, by user={}", groupId, userId)

        val (user, group) = checkMembership(userId, groupId)
        val predictions = awardPredictionRepository.findByUserIdAndGroupId(user.id!!, group.id!!)

        return AwardPredictionsResponse.fromAwardPredictions(user, predictions)
    }
}
