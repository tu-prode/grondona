package com.grondona.service

import com.grondona.exception.BadRequestException
import com.grondona.exception.NotFoundException
import com.grondona.model.GroupUser
import com.grondona.model.dto.UserGroupResponse
import com.grondona.repository.GroupRepository
import com.grondona.repository.GroupUserRepository
import com.grondona.repository.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class GroupMembershipService(
    private val groupRepository: GroupRepository,
    private val userRepository: UserRepository,
    private val groupUserRepository: GroupUserRepository
) {

    companion object {
        private val logger = LoggerFactory.getLogger(GroupMembershipService::class.java)
    }

    @Transactional
    fun joinGroup(userId: UUID, groupId: UUID) {
        logger.info("User {} attempting to join group {}", userId, groupId)

        val group = groupRepository.findById(groupId).orElseThrow {
            logger.warn("Join failed: group {} not found", groupId)
            NotFoundException("Grupo no encontrado")
        }

        val user = userRepository.findById(userId).orElseThrow {
            logger.warn("Join failed: user {} not found", userId)
            NotFoundException("Usuario no encontrado")
        }

        if (groupUserRepository.existsByUser_IdAndGroup_Id(userId, groupId)) {
            logger.warn("Join failed: user {} is already a member of group {}", userId, groupId)
            throw BadRequestException("Ya eres miembro de este grupo")
        }

        val memberCount = groupUserRepository.countByGroup_Id(groupId)
        if (memberCount >= group.maxMembers) {
            logger.warn("Join failed: group {} is full ({}/{})", groupId, memberCount, group.maxMembers)
            throw BadRequestException("El grupo está lleno")
        }

        val membership = GroupUser(user = user, group = group)
        groupUserRepository.save(membership)

        logger.info("User {} joined group '{}' successfully ({}/{} members)", userId, group.name, memberCount + 1, group.maxMembers)
    }

    @Transactional
    fun leaveGroup(userId: UUID, groupId: UUID) {
        logger.info("User {} attempting to leave group {}", userId, groupId)

        groupRepository.findById(groupId).orElseThrow {
            logger.warn("Leave failed: group {} not found", groupId)
            NotFoundException("Grupo no encontrado")
        }

        val membership = groupUserRepository.findByUser_IdAndGroup_Id(userId, groupId).orElseThrow {
            logger.warn("Leave failed: user {} is not a member of group {}", userId, groupId)
            NotFoundException("No eres miembro de este grupo")
        }

        groupUserRepository.delete(membership)
        logger.info("User {} left group {} successfully", userId, groupId)
    }

    @Transactional(readOnly = true)
    fun getMyGroups(userId: UUID): List<UserGroupResponse> {
        logger.info("Fetching groups for user {}", userId)
        val groups = groupUserRepository.findUserGroupsWithMemberCount(userId)
        logger.info("User {} belongs to {} groups", userId, groups.size)
        return groups
    }
}
