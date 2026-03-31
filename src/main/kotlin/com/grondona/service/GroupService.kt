package com.grondona.service

import com.grondona.exception.ConflictException
import com.grondona.exception.NotFoundException
import com.grondona.model.Group
import com.grondona.model.GroupUser
import com.grondona.model.MatchStatus
import com.grondona.model.PredictionStatus
import com.grondona.model.Standing
import com.grondona.model.Tournament
import com.grondona.model.User
import com.grondona.model.dto.request.CreateGroupRequest
import com.grondona.model.dto.request.UpdateGroupRequest
import com.grondona.model.dto.response.GroupResponse
import com.grondona.repository.GroupRepository
import com.grondona.repository.MatchRepository
import com.grondona.repository.MembershipRepository
import com.grondona.repository.PredictionRepository
import com.grondona.repository.TournamentRepository
import com.grondona.utils.PredictionCalculator
import jakarta.persistence.criteria.JoinType
import jakarta.persistence.criteria.Predicate
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.UUID

@Service
class GroupService(
    private val groupRepository: GroupRepository,
    private val matchRepository: MatchRepository,
    private val membershipRepository: MembershipRepository,
    private val tournamentRepository: TournamentRepository,
    private val predictionRepository: PredictionRepository,
) {

    companion object {
        private val logger = LoggerFactory.getLogger(GroupService::class.java)
    }

    @Transactional
    fun createGroup(tournamentId: UUID, request: CreateGroupRequest): GroupResponse {
        logger.info(
            "Creating group with name='{}', private={}, maxMembers={}, at tournament={}",
            request.name,
            request.isPrivate,
            request.maxMembers,
            tournamentId
        )

        if (groupRepository.existsByName(request.name)) {
            logger.warn("Group creation failed: name '{}' already exists", request.name)
            throw ConflictException(message = "Group name already exists", field = "name", rejectedValue = request.name)
        }

        val tournament = tournamentRepository.findById(tournamentId).orElseThrow {
            logger.warn("Group creation failed: tournament '{}' not found", tournamentId)
            throw NotFoundException(message = "Tournament not found")
        }

        val group = Group(
            name = request.name,
            tournament = tournament,
            isPrivate = request.isPrivate,
            maxMembers = request.maxMembers,
            createdAt = LocalDateTime.now(),
        )

        val savedGroup = groupRepository.save(group)
        logger.info("Group created successfully: id={}, name='{}'", savedGroup.id, savedGroup.name)
        return GroupResponse.from(savedGroup)
    }

    @Transactional
    fun updateGroup(groupId: UUID, request: UpdateGroupRequest): GroupResponse {
        logger.info("Updating group id={} with {}", groupId, request)

        val group = groupRepository.findById(groupId).orElseThrow {
            logger.warn("Group not found: id={}", groupId)
            NotFoundException("Group not found")
        }

        request.name?.let { newName ->
            if (newName != group.name && groupRepository.existsByName(newName)) {
                logger.warn("Group update failed: name '{}' already exists", newName)
                throw ConflictException(message = "Group name already exists", field = "name", rejectedValue = newName)
            }
            group.name = newName
        }

        request.isPrivate?.let { group.isPrivate = it }
        request.maxMembers?.let { group.maxMembers = it }

        group.updatedAt = LocalDateTime.now()

        val savedGroup = groupRepository.save(group)
        logger.info("Group updated successfully: id={}, name='{}'", savedGroup.id, savedGroup.name)
        return GroupResponse.from(savedGroup)
    }

    @Transactional
    fun deleteGroup(groupId: UUID) {
        logger.info("Deleting group id={}", groupId)

        val group = groupRepository.findById(groupId).orElseThrow {
            logger.warn("Group not found for deletion: id={}", groupId)
            NotFoundException("Group not found")
        }

        groupRepository.delete(group)
        logger.info("Group deleted successfully: id={}, name='{}'", groupId, group.name)
    }

    fun getGroupById(groupId: UUID, withStandings: Boolean = false): GroupResponse {
        logger.info("Fetching group id={}", groupId)

        val group = groupRepository.findById(groupId).orElseThrow {
            logger.warn("Group not found: id={}", groupId)
            NotFoundException("Group not found")
        }

        logger.info("Group fetched successfully: id={}, name='{}'", group.id, group.name)

        if (withStandings) {
            return GroupResponse.from(group, calculateStandings(group))
        }

        return GroupResponse.from(group)
    }

    // TODO: Here we can implement the last-check logic. Currently we'll recalculate points every time.
    fun calculateStandings(group: Group): List<Standing> {
        logger.info("Retrieving past matches for group={}", group.id!!)
        val matches = matchRepository.findByTournamentIdAndStatusOrderByStartedAt(group.tournament.id!!, MatchStatus.FINISHED)
        logger.debug("Matches to calculate predictions: {}", matches.size)

        logger.info("Checking prediction statuses for group={}", group.id)
        var predictions = predictionRepository.findByGroupIdAndMatchIdIn(group.id, matches.map { it.id!! })
        predictions = PredictionCalculator.check(predictions)
        predictions = predictionRepository.saveAll(predictions)
        logger.debug("Predictions retrieved: {}", predictions.size)

        // List of predictions indexed by user-id and match-id
        val predictionsIndexed = predictions.groupBy { it.user.id!! }
            .mapValues { (_, predictions) ->
                predictions.groupBy { it.match.id!! }
                    // Within a group, there's only one prediction per user, per match.
                    .mapValues { (_, predictions) -> predictions[0] }
            }

        logger.info("Checking members for group={}", group.id)
        val groupMembers = membershipRepository.findByGroupId(group.id)
        logger.debug("Members retrieved: {}", groupMembers.size)

        val memberStandings = groupMembers.groupBy { it.user }
            .mapValues { (user, _) ->
                val userPredictions = predictionsIndexed[user.id!!]
                matches.map { userPredictions?.get(it.id!!) }
            }
            .map { (user, predictions) ->
                Standing(
                    rank = 0,
                    user = user,
                    points = PredictionCalculator.points(predictions.filterNotNull()),
                    lastPredictions = predictions.map { it?.status ?: PredictionStatus.MISSING }.takeLast(5)
                )
            }
            .sortedByDescending { it.points }
            .mapIndexed { index, standing ->
                Standing(
                    rank = index+1,
                    user = standing.user,
                    points = standing.points,
                    lastPredictions = standing.lastPredictions,
                )
            }
            .groupBy { it.user.id }
            .mapValues { (_, standings) -> standings[0] } // There's only one standing prediction per user.

        logger.info("Updating members rank and points for group={}", group.id)
        groupMembers.forEach {
            val userStanding = memberStandings[it.user.id]
            it.points = userStanding?.points ?: 0f
            it.rank = userStanding?.rank
            it.calculatedAt = LocalDateTime.now()
        }
        membershipRepository.saveAll(groupMembers)

        return memberStandings.map { it.value }.sortedBy { it.rank}
    }

    fun findOtherGroups(userId: UUID, tournamentId: UUID, search: String?, joined: Boolean?): List<GroupResponse> {
        return groupRepository.findAll { root, query, builder ->
            val predicates = mutableListOf<Predicate>()

            query.distinct(true)

            // tournament filter
            predicates.add(builder.equal(root.get<Tournament>("tournament").get<UUID>("id"), tournamentId))

            // search filter [optional]
            if (!search.isNullOrBlank()) {
                predicates.add(builder.like(builder.lower(root.get("name")), "%${search.lowercase()}%"))
            }

            // joined filter [optional]
            if (joined != null) {
                val join = root.join<Group, GroupUser>("groupUsers", JoinType.LEFT)

                join.on(
                    builder.equal(
                        join
                            .get<User>(GroupUser::user.name)
                            .get<UUID>(User::id.name),
                        userId
                    )
                )

                if (joined) {
                    predicates.add(builder.isNotNull(join.get<Long>("id")))
                } else {
                    predicates.add(builder.isNull(join.get<Long>("id")))
                }
            }

            builder.and(*predicates.toTypedArray())
        }.map(GroupResponse::from)
    }
}
