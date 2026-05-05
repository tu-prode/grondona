package com.grondona.service

import com.grondona.exception.ConflictException
import com.grondona.exception.GeneralException
import com.grondona.exception.NotFoundException
import com.grondona.model.Group
import com.grondona.model.GroupRole
import com.grondona.model.GroupUser
import com.grondona.model.MatchStatus
import com.grondona.model.Standing
import com.grondona.model.Tournament
import com.grondona.model.User
import com.grondona.model.dto.request.CreateGroupRequest
import com.grondona.model.dto.request.UpdateGroupRequest
import com.grondona.model.dto.response.GroupResponse
import com.grondona.repository.GroupRepository
import com.grondona.repository.MatchPredictionRepository
import com.grondona.repository.MembershipRepository
import com.grondona.repository.TournamentRepository
import com.grondona.repository.UserRepository
import com.grondona.service.engine.PredictionsEngine
import jakarta.persistence.criteria.JoinType
import jakarta.persistence.criteria.Predicate
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.UUID

@Service
class GroupService(
    private val userRepository: UserRepository,
    private val groupRepository: GroupRepository,
    private val membershipRepository: MembershipRepository,
    private val tournamentRepository: TournamentRepository,
    private val matchPredictionRepository: MatchPredictionRepository,
) {

    companion object {
        private val logger = LoggerFactory.getLogger(GroupService::class.java)
    }

    @Transactional
    fun createGroup(userId: UUID, tournamentId: UUID, request: CreateGroupRequest): GroupResponse {
        logger.info(
            "Creating group with name='{}', private={}, maxMembers={}, at tournament={}",
            request.name, request.isPrivate, request.maxMembers, tournamentId
        )

        val user = userRepository.findById(userId).orElseThrow {
            logger.warn("Couldn't find user {} for group creation", userId)
            throw GeneralException(message = "Authenticated user not found")
        }

        if (groupRepository.existsByName(request.name)) {
            logger.warn("Group creation failed: name '{}' already exists", request.name)
            throw ConflictException(message = "Group name already exists", field = "name", rejectedValue = request.name)
        }

        val tournament = tournamentRepository.findById(tournamentId).orElseThrow {
            logger.warn("Group creation failed: tournament '{}' not found", tournamentId)
            throw NotFoundException(message = "Tournament not found")
        }

        val group = Group(name = request.name, tournament = tournament, isPrivate = request.isPrivate, maxMembers = request.maxMembers)
        val savedGroup = groupRepository.save(group)

        val membership = GroupUser(user = user, group = group, role = GroupRole.OWNER, joinedAt = LocalDateTime.now())
        membershipRepository.save(membership)

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

        membershipRepository.findEveryGroupUser(groupId).forEach { membershipRepository.delete(it) }
        groupRepository.delete(group)
        logger.info("Group deleted successfully: id={}, name='{}'", groupId, group.name)
    }

    fun getGroupById(groupId: UUID, liveStandings: Boolean = false, omitStandings: Boolean = false): GroupResponse {
        logger.info("Fetching group id={}", groupId)

        val group = groupRepository.findById(groupId).orElseThrow {
            logger.warn("Group not found: id={}", groupId)
            NotFoundException("Group not found")
        }

        logger.info("Group fetched successfully: id={}, name='{}'", group.id, group.name)

        if (omitStandings) {
            return GroupResponse.from(group)
        }

        val groupUsers = membershipRepository.findEveryGroupUser(groupId)
        val members = groupUsers.filter { it.role != GroupRole.CANDIDATE }
        val standings = when {
            // Hasn't started
            members.all { it.rank == null } ->
                members.sortedBy { it.joinedAt }.mapIndexed { index, member ->
                    Standing(rank = index + 1, user = member.user, points = 0f, lastPredictions = emptyList())
                }

            // Live standings
            liveStandings -> matchPredictionRepository.findGroupPredictions(groupId)
                .filter { it.match.status == MatchStatus.IN_PROGRESS }
                .mapNotNull { it.prediction }
                .let { PredictionsEngine.checkMatchPredictions(it) }
                .groupBy { it.user.id!! }
                .let { PredictionsEngine.updateMatchPoints(members, it) }
                .mapIndexed { index, member ->
                    Standing(rank = member.rank ?: index, user = member.user, points = member.points, lastPredictions = member.lastPredictions)
                }

            // Saved standings
            else -> members.sortedWith(
                compareBy<GroupUser> { it.rank == null }.thenBy { it.rank }.thenBy { it.joinedAt }
            ).mapIndexed { index, member ->
                Standing(rank = member.rank ?: index, user = member.user, points = member.points, lastPredictions = member.lastPredictions)
            }
        }

        return GroupResponse.from(group, standings, groupUsers.filter { it.role == GroupRole.CANDIDATE })
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
                val join = root.join<Group, GroupUser>("members", JoinType.LEFT)

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
