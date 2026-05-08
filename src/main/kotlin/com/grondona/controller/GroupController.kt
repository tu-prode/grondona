package com.grondona.controller

import com.grondona.exception.ForbiddenException
import com.grondona.exception.UnauthorizedException
import com.grondona.model.dto.request.CreateGroupRequest
import com.grondona.model.dto.request.UpdateGroupRequest
import com.grondona.model.dto.request.UpdateMemberRequest
import com.grondona.model.dto.response.GroupResponse
import com.grondona.security.JwtUserPrincipal
import com.grondona.service.MembershipService
import com.grondona.service.GroupService
import jakarta.validation.Valid
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/tournaments/{tournamentId}/groups")
class GroupController(
    private val groupService: GroupService,
    private val membershipService: MembershipService,
) {

    companion object {
        private val logger = LoggerFactory.getLogger(GroupController::class.java)
    }

    @PostMapping
    fun createGroup(
        @AuthenticationPrincipal principal: JwtUserPrincipal?,
        @PathVariable tournamentId: UUID,
        @Valid @RequestBody request: CreateGroupRequest,
    ): ResponseEntity<GroupResponse> {
        logger.info("POST /api/tournaments/{}/groups - Creating group: name='{}'", tournamentId, request.name)
        val userId = principal?.userId ?: throw UnauthorizedException("Authentication required")

        val response = groupService.createGroup(userId, tournamentId, request)
        logger.info("POST /api/tournaments/{}/groups - Group created: id={}", tournamentId, response.id)

        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }

    @PatchMapping("/{groupId}")
    fun updateGroup(
        @AuthenticationPrincipal principal: JwtUserPrincipal?,
        @PathVariable groupId: UUID, @PathVariable tournamentId: UUID,
        @Valid @RequestBody request: UpdateGroupRequest
    ): ResponseEntity<GroupResponse> {
        logger.info("PATCH /api/tournaments/{}/groups/{} - Updating group", tournamentId, groupId)

        val userId = principal?.userId ?: throw UnauthorizedException("Authentication required")
        if (!membershipService.isAdmin(userId, groupId)) {
            throw ForbiddenException("User not allowed")
        }

        val response = groupService.updateGroup(groupId, request)
        logger.info("PATCH /api/tournaments/{}/groups/{} - Group updated", tournamentId, groupId)
        return ResponseEntity.ok(response)
    }

    @DeleteMapping("/{groupId}")
    fun deleteGroup(
        @AuthenticationPrincipal principal: JwtUserPrincipal?,
        @PathVariable groupId: UUID, @PathVariable tournamentId: UUID,
    ): ResponseEntity<Void> {
        logger.info("DELETE /api/tournaments/{}/groups/{} - Deleting group", tournamentId, groupId)

        val userId = principal?.userId ?: throw UnauthorizedException("Authentication required")
        groupService.getGroupById(groupId, omitStandings = true)

        if (!membershipService.isAdmin(userId, groupId)) {
            throw ForbiddenException("User not allowed")
        }

        groupService.deleteGroup(groupId)
        logger.info("DELETE /api/tournaments/{}/groups/{} - Group deleted", tournamentId, groupId)
        return ResponseEntity.noContent().build()
    }

    @GetMapping("/{groupId}")
    fun getGroup(
        @AuthenticationPrincipal principal: JwtUserPrincipal?,
        @PathVariable groupId: UUID, @PathVariable tournamentId: UUID,
        @RequestParam(required = false) live: Boolean?,
    ): ResponseEntity<GroupResponse> {
        logger.info("GET /api/tournaments/{}/groups/{} - Fetching group, live={}", tournamentId, groupId, live)

        val liveStandings = live ?: false
        val userId = principal?.userId ?: throw UnauthorizedException("Authentication required")
        val response = groupService.getGroupById(groupId, liveStandings)

        if (!membershipService.isMember(userId, groupId)) {
            throw ForbiddenException("User not allowed")
        }

        return ResponseEntity.ok(response)
    }

    @GetMapping
    fun searchGroups(
        @AuthenticationPrincipal principal: JwtUserPrincipal?,
        @PathVariable tournamentId: UUID,
        @RequestParam(required = false) search: String?, @RequestParam(required = false) joined: Boolean?
    ): ResponseEntity<List<GroupResponse>> {
        logger.info("GET /api/tournaments/{}/groups - Fetching groups, search='{}', joined='{}'", tournamentId, search, joined)
        principal?.userId ?: throw UnauthorizedException("Authentication required")

        val response = groupService.searchGroups(principal.userId, tournamentId, search, joined)
        logger.info("GET /api/tournaments/{}/groups - Returning {} groups", tournamentId, response.size)
        return ResponseEntity.ok(response)
    }

    @PostMapping("/{groupId}/join")
    fun joinGroup(
        @AuthenticationPrincipal principal: JwtUserPrincipal?,
        @PathVariable tournamentId: UUID, @PathVariable groupId: UUID
    ): ResponseEntity<Void> {
        val userId = principal?.userId ?: throw UnauthorizedException("Authentication required")
        logger.info("POST /api/tournaments/{}/groups/{}/join - userId={}", tournamentId, groupId, userId)
        membershipService.joinGroup(userId, groupId)
        logger.info("POST /api/tournaments/{}/groups/{}/join - Joined successfully, userId={}", tournamentId, groupId, userId)
        return ResponseEntity.status(HttpStatus.CREATED).build()
    }

    @DeleteMapping("/{groupId}/leave")
    fun leaveGroup(
        @AuthenticationPrincipal principal: JwtUserPrincipal?,
        @PathVariable tournamentId: UUID, @PathVariable groupId: UUID
    ): ResponseEntity<Void> {
        val userId = principal?.userId ?: throw UnauthorizedException("Authentication required")
        logger.info("DELETE /api/tournaments/{}/groups/{}/leave - userId={}", tournamentId, groupId, userId)
        membershipService.leaveGroup(userId, groupId)
        logger.info("DELETE /api/tournaments/{}/groups/{}/leave - Left successfully, userId={}", tournamentId, groupId, userId)
        return ResponseEntity.noContent().build()
    }

    @PutMapping("/{groupId}/members/{candidateId}/accept")
    fun acceptCandidate(
        @AuthenticationPrincipal principal: JwtUserPrincipal?,
        @PathVariable tournamentId: UUID, @PathVariable groupId: UUID, @PathVariable candidateId: UUID
    ): ResponseEntity<Void> {
        val userId = principal?.userId ?: throw UnauthorizedException("Authentication required")
        logger.info("PUT /api/tournaments/{}/groups/{}/members/{}/accept - userId={}", tournamentId, groupId, candidateId, userId)
        membershipService.acceptCandidate(userId, groupId, candidateId)
        logger.info("PUT /api/tournaments/{}/groups/{}/members/{}/accept - Accepted successfully, userId={}", tournamentId, groupId, candidateId, userId)
        return ResponseEntity.status(HttpStatus.OK).build()
    }

    @DeleteMapping("/{groupId}/members/{candidateId}/reject")
    fun rejectCandidate(
        @AuthenticationPrincipal principal: JwtUserPrincipal?,
        @PathVariable tournamentId: UUID, @PathVariable groupId: UUID, @PathVariable candidateId: UUID
    ): ResponseEntity<Void> {
        val userId = principal?.userId ?: throw UnauthorizedException("Authentication required")
        logger.info("DELETE /api/tournaments/{}/groups/{}/members/{}/reject - userId={}", tournamentId, groupId, candidateId, userId)
        membershipService.rejectCandidate(userId, groupId, candidateId)
        logger.info("DELETE /api/tournaments/{}/groups/{}/members/{}/reject - Accepted successfully, userId={}", tournamentId, groupId, candidateId, userId)
        return ResponseEntity.noContent().build()
    }

    @PatchMapping("/{groupId}/members/{memberId}")
    fun manageMember(
        @AuthenticationPrincipal principal: JwtUserPrincipal?,
        @PathVariable tournamentId: UUID, @PathVariable groupId: UUID, @PathVariable memberId: UUID,
        @Valid @RequestBody request: UpdateMemberRequest,
    ): ResponseEntity<Void> {
        val userId = principal?.userId ?: throw UnauthorizedException("Authentication required")
        logger.info("PATCH /api/tournaments/{}/groups/{}/members/{} - userId={}", tournamentId, groupId, memberId, userId)
        membershipService.updateMember(userId, groupId, memberId, request)
        logger.info("PATCH /api/tournaments/{}/groups/{}/members/{} - Updated successfully, userId={}", tournamentId, groupId, memberId, userId)
        return ResponseEntity.status(HttpStatus.OK).build()
    }

    @DeleteMapping("/{groupId}/members/{memberId}")
    fun kickMember(
        @AuthenticationPrincipal principal: JwtUserPrincipal?,
        @PathVariable tournamentId: UUID, @PathVariable groupId: UUID, @PathVariable memberId: UUID,
    ): ResponseEntity<Void> {
        val userId = principal?.userId ?: throw UnauthorizedException("Authentication required")
        logger.info("DELETE /api/tournaments/{}/groups/{}/members/{} - userId={}", tournamentId, groupId, memberId, userId)
        membershipService.kickMember(userId, groupId, memberId)
        logger.info("DELETE /api/tournaments/{}/groups/{}/members/{} - Removed successfully, userId={}", tournamentId, groupId, memberId, userId)
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build()
    }
}
