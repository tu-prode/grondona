package com.grondona.service

import com.grondona.exception.BadRequestException
import com.grondona.exception.NotFoundException
import com.grondona.model.GroupRole
import com.grondona.model.GroupUser
import com.grondona.model.dto.response.MembershipResponse
import com.grondona.model.hasAdminAccess
import com.grondona.repository.GroupRepository
import com.grondona.repository.MembershipRepository
import com.grondona.repository.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.UUID
import kotlin.jvm.optionals.getOrElse

@Service
class MembershipService(
    private val groupRepository: GroupRepository,
    private val userRepository: UserRepository,
    private val membershipRepository: MembershipRepository,
) {

    companion object {
        private val logger = LoggerFactory.getLogger(MembershipService::class.java)
    }

    @Transactional
    fun joinGroup(userId: UUID, groupId: UUID) {
        logger.info("User {} attempting to join group {}", userId, groupId)

        val group = groupRepository.findById(groupId).orElseThrow {
            logger.warn("Join failed: group {} not found", groupId)
            NotFoundException("Group not found")
        }

        val user = userRepository.findById(userId).orElseThrow {
            logger.warn("Join failed: user {} not found", userId)
            NotFoundException("User not found")
        }

        if (membershipRepository.isMember(userId, groupId)) {
            logger.warn("Join failed: user {} is already a member of group {}", userId, groupId)
            throw BadRequestException("You are already member of this group")
        }

        if (membershipRepository.findCandidate(userId, groupId).isPresent) {
            logger.warn("Join failed: user {} is already a candidate of group {}", userId, groupId)
            throw BadRequestException("You are already candidate to this group")
        }

        val memberCount = membershipRepository.countMembers(groupId)
        if (memberCount >= group.maxMembers) {
            logger.warn("Join failed: group {} is full ({}/{})", groupId, memberCount, group.maxMembers)
            throw BadRequestException("Group is full")
        }

        if (group.isPrivate) {
            val membership = GroupUser(user = user, group = group, role = GroupRole.CANDIDATE)
            membershipRepository.save(membership)
            logger.info("User {} requested access to group '{}' ({}/{} members)", userId, group.name, memberCount + 1, group.maxMembers)
        } else {
            val membership = GroupUser(user = user, group = group, role = GroupRole.MEMBER, joinedAt = LocalDateTime.now())
            membershipRepository.save(membership)
            logger.info("User {} joined group '{}' successfully ({}/{} members)", userId, group.name, memberCount + 1, group.maxMembers)
        }
    }

    @Transactional
    fun leaveGroup(userId: UUID, groupId: UUID) {
        logger.info("User {} attempting to leave group {}", userId, groupId)

        groupRepository.findById(groupId).orElseThrow {
            logger.warn("Leave failed: group {} not found", groupId)
            NotFoundException("Group not found")
        }

        val membership = membershipRepository.findMember(userId, groupId).orElseThrow {
            logger.warn("Leave failed: user {} is not a member of group {}", userId, groupId)
            NotFoundException("You are not member of this group")
        }

        membershipRepository.delete(membership)
        logger.info("User {} left group {} successfully", userId, groupId)
    }

    @Transactional
    fun acceptCandidate(userId: UUID, groupId: UUID, candidateId: UUID) {
        logger.info("User={} attempting to accept candidate={} from group {}", userId, candidateId, groupId)

        val candidate = retrieveJoinRequest(userId, groupId, candidateId)

        val memberCount = membershipRepository.countMembers(groupId)
        if (memberCount >= candidate.group.maxMembers) {
            logger.warn("Accept failed: group {} is full ({}/{})", groupId, memberCount, candidate.group.maxMembers)
            throw BadRequestException("Group is full")
        }

        val newMember = candidate.copy(role = GroupRole.MEMBER, joinedAt = LocalDateTime.now())
        membershipRepository.save(newMember)
        logger.info("Candidate={} accepted into group={} successfully ({}/{} members)", candidateId, groupId, memberCount + 1, newMember.group.maxMembers)
    }

    @Transactional
    fun rejectCandidate(userId: UUID, groupId: UUID, candidateId: UUID) {
        logger.info("User={} attempting to reject candidate={} from group {}", userId, candidateId, groupId)

        val candidate = retrieveJoinRequest(userId, groupId, candidateId)
        membershipRepository.delete(candidate)
        logger.info("Candidate={} rejected from group={} successfully", candidateId, groupId)
    }

    @Transactional(readOnly = true)
    private fun retrieveJoinRequest(userId: UUID, groupId: UUID, candidateId: UUID): GroupUser {
        groupRepository.findById(groupId).orElseThrow {
            logger.warn("Reject failed: group {} not found", groupId)
            NotFoundException("Group not found")
        }

        if (!userRepository.existsById(userId)) {
            logger.warn("Accept failed: user {} not found", candidateId)
            throw NotFoundException("User not found")
        }

        if (!userRepository.existsById(candidateId)) {
            logger.warn("Accept failed: candidate {} not found", candidateId)
            throw NotFoundException("Candidate not found")
        }

        val admin = membershipRepository.findMember(userId, groupId).orElseThrow {
            logger.warn("Reject failed: user {} not found in group {}", userId, groupId)
            BadRequestException("User does not belong to the group")
        }

        if (!admin.role.hasAdminAccess()) {
            logger.warn("Reject failed: user {} is not an admin of group {}", userId, groupId)
            throw BadRequestException("User is not a group admin")
        }

        val candidate = membershipRepository.findCandidate(candidateId, groupId).orElseThrow {
            logger.warn("Reject failed: user {} is not a candidate for group {}", userId, groupId)
            throw BadRequestException("The user is not a candidate for the group")
        }

        return candidate
    }

    @Transactional(readOnly = true)
    fun getMyGroups(userId: UUID): List<MembershipResponse> {
        logger.info("Fetching groups for user {}", userId)
        val memberships = membershipRepository.findUserMemberships(userId)
        logger.info("User {} belongs to {} groups", userId, memberships.size)

        return memberships.map(MembershipResponse::fromMembershipView)
    }

    fun isAdmin(userId: UUID, groupId: UUID): Boolean {
        logger.info("Checking if user={} is admin of group={}", userId, groupId)
        return membershipRepository.findMember(userId, groupId)
            .map { it.role.hasAdminAccess() }.getOrElse { false }
    }

    fun isMember(userId: UUID, groupId: UUID): Boolean {
        logger.info("Checking if user={} is admin of group={}", userId, groupId)
        return membershipRepository.findMember(userId, groupId).isPresent
    }
}
