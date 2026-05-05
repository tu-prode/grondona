package com.grondona.service

import com.grondona.exception.BadRequestException
import com.grondona.exception.ForbiddenException
import com.grondona.exception.NotFoundException
import com.grondona.model.AwardPrediction
import com.grondona.model.AwardType
import com.grondona.model.ExtendedAwards
import com.grondona.model.Group
import com.grondona.model.GroupUser
import com.grondona.model.Match
import com.grondona.model.MatchPrediction
import com.grondona.model.PlayerPosition
import com.grondona.model.TournamentStatus
import com.grondona.model.User
import com.grondona.model.dto.request.SubmitAwardPredictionRequest
import com.grondona.model.dto.request.SubmitMatchPredictionRequest
import com.grondona.model.dto.request.SubmitBulkMatchPredictionsRequest
import com.grondona.model.dto.response.AwardPredictionsResponse
import com.grondona.model.dto.response.GroupAwardPredictionsResponse
import com.grondona.model.dto.response.GroupMatchPredictionsResponse
import com.grondona.model.dto.response.MatchPredictionResponse
import com.grondona.now
import com.grondona.repository.AwardPredictionRepository
import com.grondona.repository.GroupRepository
import com.grondona.repository.MembershipRepository
import com.grondona.repository.MatchRepository
import com.grondona.repository.MatchPredictionRepository
import com.grondona.repository.PlayerRepository
import com.grondona.repository.TeamRepository
import com.grondona.repository.TournamentRepository
import com.grondona.repository.UserRepository
import com.grondona.service.engine.PredictionsEngine
import com.grondona.service.engine.WorldCupEngine
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
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

        fun isMatchUnlocked(match: Match): Boolean =
            match.startedAt.isAfter(now.plus(15, ChronoUnit.MINUTES))
    }

    internal fun checkMembership(userId: UUID, groupId: UUID): Pair<User, Group> {
        val user = userRepository.findById(userId).orElseThrow { NotFoundException("User not found") }
        val group = groupRepository.findById(groupId).orElseThrow { NotFoundException("Group not found") }

        if (!membershipRepository.isMember(userId, groupId)) {
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
        val match = matchRepository.findById(request.matchId).orElseThrow { NotFoundException("Match not found") }

        if (!isMatchUnlocked(match)) {
            logger.warn("Trying to submit a prediction for a match that is locked, user={}, match={} at group={}", userId, request.matchId, groupId)
            throw BadRequestException(message = "Cannot submit predictions for this match")
        }

        val predictionToSave = MatchPrediction(
            user = user,
            group = group,
            match = match,
            homeGoals = request.homeGoals,
            awayGoals = request.awayGoals,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now(),
        )

        val savedPrediction = if (user.hasUniquePredictions) {
            val predictionsToSave = membershipRepository.findUserGroups(userId).map { predictionToSave.copy(group = it.group) }
            val savedPredictions = matchPredictionRepository.upsertAll(predictionsToSave)
            savedPredictions.first { it.group.id == group.id }
        } else {
            matchPredictionRepository.upsert(predictionToSave)
        }

        return MatchPredictionResponse.from(savedPrediction)
    }

    @Transactional
    fun submitMatchPredictions(
        userId: UUID,
        groupId: UUID,
        request: SubmitBulkMatchPredictionsRequest
    ): GroupMatchPredictionsResponse {
        logger.info("Submitting {} predictions for user={} at group={}", request.predictions.size, userId, groupId)

        val (user, group) = checkMembership(userId, groupId)
        val basePredictions = request.predictions.map { prediction ->
            MatchPrediction(
                user = user,
                group = group,
                homeGoals = prediction.homeGoals,
                awayGoals = prediction.awayGoals,
                match = matchRepository.findById(prediction.matchId).orElseThrow { NotFoundException("Match not found") },
            )
        }.filter {
            if (isMatchUnlocked(it.match)) true else {
                logger.warn("User={} trying to submit predictions for match={}, but it's locked", userId, it.match.id); false
            }
        }

        val predictions = if (user.hasUniquePredictions) {
            val userGroups = membershipRepository.findUserGroups(userId)
            userGroups.flatMap { basePredictions.map { prediction -> prediction.copy(group = it.group) } }
        } else {
            basePredictions
        }

        val savedPredictions = matchPredictionRepository.upsertAll(predictions)
        return GroupMatchPredictionsResponse.fromPredictions(group, savedPredictions.filter { it.group.id == groupId })
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
        if (isMatchUnlocked(match)) {
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
            logger.warn(
                "User={} trying to submit award predictions for the tournament={} at group={}, but it has already started",
                userId, tournamentId, groupId
            )
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

        val chosenGoalkeepers = awardPredictions.bestGoalkeepers.map { goalkeeperId ->
            playerRepository.findById(goalkeeperId).orElseThrow {
                logger.warn("Goalkeeper not found id={}", goalkeeperId)
                NotFoundException("Goalkeeper not found")
            }.also {
                if (it.position != PlayerPosition.GOALKEEPER) {
                    logger.warn("User={} trying to set a non-goalkeeper with id={} as best goalkeeper", userId, goalkeeperId)
                    throw BadRequestException("Player is not suitable for the best goalkeeper award")
                }
            }
        }

        val chosenYoungPlayers = awardPredictions.bestYoungPlayers.map { playerId ->
            playerRepository.findById(playerId).orElseThrow {
                logger.warn("Goalkeeper not found id={}", playerId)
                NotFoundException("Goalkeeper not found")
            }.also {
                if (it.birthdate.isBefore(WorldCupEngine.BEST_YOUNG_PLAYER_DATE_LIMIT)) {
                    logger.warn("User={} trying to set a older player with id={} as best young player", userId, playerId)
                    throw BadRequestException("Player is not suitable for the best young player award")
                }
            }
        }

        val predictions = awardPredictions.champions.map {
            AwardPrediction(user = user, group = group, awardType = AwardType.CHAMPION, team = teamRepository.getReferenceById(it))
        } + awardPredictions.topScorers.map {
            AwardPrediction(user = user, group = group, awardType = AwardType.TOP_SCORER, player = playerRepository.getReferenceById(it))
        } + awardPredictions.bestPlayers.map {
            AwardPrediction(user = user, group = group, awardType = AwardType.BEST_PLAYER, player = playerRepository.getReferenceById(it))
        } + chosenGoalkeepers.map {
            AwardPrediction(user = user, group = group, awardType = AwardType.BEST_GOALKEEPER, player = it)
        } + chosenYoungPlayers.map {
            AwardPrediction(user = user, group = group, awardType = AwardType.BEST_YOUNG_PLAYER, player = it)
        }

        val savedPredictions = if (user.hasUniquePredictions) {
            val userGroups = membershipRepository.findUserGroups(userId)
            val userGroupsIds = userGroups.map { it.group.id!! }
            val deletedAwards = awardPredictionRepository.deleteAwardPredictionsForMultipleGroups(userId, userGroupsIds)
            logger.debug("Deleted {} previous awards from user={} in {} groups", deletedAwards, userId, userGroups.size)
            val predictionsToSave = userGroups.flatMap { predictions.map { prediction -> prediction.copy(group = it.group) } }
            awardPredictionRepository.saveAll(predictionsToSave)
        } else {
            val deletedAwards = awardPredictionRepository.deleteAwardPredictionsForGroup(userId, groupId)
            logger.debug("Deleted {} previous awards from user={} in group={}", deletedAwards, userId, groupId)
            awardPredictionRepository.saveAll(predictions)
        }
        return AwardPredictionsResponse.fromAwardPredictions(user, savedPredictions.filter { it.group.id == groupId })
    }

    fun getAwardPredictionsForGroup(userId: UUID, groupId: UUID, tournamentId: UUID): GroupAwardPredictionsResponse {
        logger.info("Fetching award predictions for group={}, by user={}", groupId, userId)

        val (_, group) = checkMembership(userId, groupId)
        val tournament = tournamentRepository.findById(tournamentId).orElseThrow { NotFoundException("Tournament not found") }
        if (tournament.status == TournamentStatus.NOT_STARTED) {
            logger.warn(
                "User={} trying fetch award predictions for the tournament={} at group={}, but it hasn't started yet",
                userId, tournamentId, groupId
            )
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

    @Transactional
    fun clonePredictions(userId: UUID, masterGroupId: UUID) {
        logger.info("Cloning predictions for user={} using master={}", userId, masterGroupId)

        val userGroups = membershipRepository.findUserGroups(userId)
        val userGroupsIds = userGroups.map { it.group.id!! }
        if (!userGroupsIds.contains(masterGroupId)) {
            logger.error("User={} trying to clone predictions from group={}, but it does not belong to it", userId, masterGroupId)
            throw BadRequestException("Invalid group for cloning predictions")
        }

        val otherGroupsIds = userGroupsIds.filter { it != masterGroupId }
        val masterGroupMatchPredictions = matchPredictionRepository.findByUserIdAndGroupId(userId, masterGroupId)
        val matchPredictionsToClone = userGroups
            .filter { it.group.id in otherGroupsIds }
            .flatMap { membership -> masterGroupMatchPredictions.map { it.copy(id = null, group = membership.group) } }
        matchPredictionRepository.upsertAll(matchPredictionsToClone)

        val masterGroupAwardPredictions = awardPredictionRepository.findByUserIdAndGroupId(userId, masterGroupId)
        awardPredictionRepository.deleteAwardPredictionsForMultipleGroups(userId, otherGroupsIds)
        val awardPredictionsToClone = userGroups
            .filter { it.group.id in otherGroupsIds }
            .flatMap { membership -> masterGroupAwardPredictions.map { it.copy(id = null, group = membership.group) } }
        awardPredictionRepository.saveAll(awardPredictionsToClone)
        logger.info("User={} cloned predictions from group={} to other {} groups", userId, masterGroupId, otherGroupsIds.size)
    }

    @Transactional
    fun recalculatePoints(tournamentId: UUID) {
        logger.info("Fetching members and predictions for tournament={} to recalculate points", tournamentId)

        val members = membershipRepository.findByTournamentId(tournamentId)
        val matchPredictions = matchPredictionRepository.findByTournamentId(tournamentId)
        val awardPredictions = awardPredictionRepository.findByTournamentId(tournamentId)

        val groupMembers = members.groupBy { it.group.id!! }
        val matchPredictionsPerGroup = matchPredictions.groupBy { it.group.id!! }
        val awardPredictionsPerGroup = awardPredictions.groupBy { it.group.id!! }

        val membersToSave = mutableListOf<GroupUser>()
        val matchPredictionsToSave = mutableListOf<MatchPrediction>()
        val awardPredictionsToSave = mutableListOf<AwardPrediction>()

        groupMembers.forEach { (groupId, members) ->
            logger.info("Recalculating points for group={} in tournament={}", groupId, tournamentId)
            var recalculatedMembers = members.map { it.clear() }
            val groupMatchPredictions = matchPredictionsPerGroup[groupId] ?: emptyList()
            val newGroupMatchPredictions = PredictionsEngine.checkMatchPredictions(groupMatchPredictions)

            val updatedGroupMatchPredictions = groupMatchPredictions.zip(newGroupMatchPredictions)
                .filter { (old, new) -> old != new }.map { it.second }
            matchPredictionsToSave.addAll(updatedGroupMatchPredictions)

            val groupMatchPredictionsPerUser = groupMatchPredictions.groupBy { it.user.id!! }
            recalculatedMembers = PredictionsEngine.updateMatchPoints(recalculatedMembers, groupMatchPredictionsPerUser)

            val groupAwardPredictions = awardPredictionsPerGroup[groupId] ?: emptyList()
            val newGroupAwardPredictions = PredictionsEngine.checkAwardPredictions(groupAwardPredictions)

            val updatedGroupAwardPredictions = groupAwardPredictions.zip(newGroupAwardPredictions)
                .filter { (old, new) -> old != new }.map { it.second }
            awardPredictionsToSave.addAll(updatedGroupAwardPredictions)

            val groupAwardPredictionsPerUser = groupAwardPredictions.groupBy { it.user.id!! }
            recalculatedMembers = PredictionsEngine.updateAwardPoints(recalculatedMembers, groupAwardPredictionsPerUser)

            val updatedMembers = members.zip(recalculatedMembers).filter { (old, new) -> old != new }.map { it.second }
            membersToSave.addAll(updatedMembers)

            logger.info("Updated elements for group={} in tournament={}, match-predictions={} award-predictions={} members={}",
                groupId, tournamentId, updatedGroupMatchPredictions.size, updatedGroupAwardPredictions.size, updatedMembers.size)
        }

        membershipRepository.saveAll(membersToSave)
        logger.info("Total members updated: {}", membersToSave.size)
        matchPredictionRepository.saveAll(matchPredictionsToSave)
        logger.info("Total match predictions updated: {}", matchPredictionsToSave.size)
        awardPredictionRepository.saveAll(awardPredictionsToSave)
        logger.info("Total award predictions updated: {}", awardPredictionsToSave.size)
    }
}
