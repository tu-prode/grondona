package com.grondona.service

import com.grondona.exception.ConflictException
import com.grondona.exception.NotFoundException
import com.grondona.model.Group
import com.grondona.model.GroupUser
import com.grondona.model.dto.*
import com.grondona.repository.GroupRepository
import jakarta.persistence.criteria.JoinType
import jakarta.persistence.criteria.Predicate
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.UUID

@Service
class GroupService(private val groupRepository: GroupRepository) {

    companion object {
        private val logger = LoggerFactory.getLogger(GroupService::class.java)
    }

    @Transactional
    fun createGroup(request: CreateGroupRequest): GroupResponse {
        logger.info("Creating group with name='{}', private={}, maxMembers={}", request.name, request.isPrivate, request.maxMembers)

        if (groupRepository.existsByName(request.name)) {
            logger.warn("Group creation failed: name '{}' already exists", request.name)
            throw ConflictException(message = "Group name already exists", field = "name", rejectedValue = request.name)
        }

        val group = Group(
            name = request.name,
            isPrivate = request.isPrivate,
            maxMembers = request.maxMembers
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

    fun getGroupById(groupId: UUID): GroupResponse {
        logger.info("Fetching group id={}", groupId)

        val group = groupRepository.findById(groupId).orElseThrow {
            logger.warn("Group not found: id={}", groupId)
            NotFoundException("Group not found")
        }

        logger.info("Group fetched successfully: id={}, name='{}'", group.id, group.name)
        return GroupResponse.from(group)
    }

    fun findGroups(search: String?, joined: Boolean?): List<GroupResponse> {
        return groupRepository.findAll { root, query, builder ->
            val predicates = mutableListOf<Predicate>()

            query.distinct(true)

            // 🔎 search filter
            if (!search.isNullOrBlank()) {
                predicates.add(builder.like(builder.lower(root.get("name")), "%${search.lowercase()}%"))
            }

            // 👥 joined filter
            if (joined != null) {
                val join = root.join<Group, GroupUser>("groupUsers", JoinType.LEFT)

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
