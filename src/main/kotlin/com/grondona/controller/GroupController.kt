package com.grondona.controller

import com.grondona.exception.ForbiddenException
import com.grondona.exception.UnauthorizedException
import com.grondona.model.GroupRole
import com.grondona.model.dto.request.CreateGroupRequest
import com.grondona.model.dto.request.UpdateGroupRequest
import com.grondona.model.dto.response.GroupResponse
import com.grondona.security.JwtUserPrincipal
import com.grondona.service.GroupMembershipService
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
    private val groupMembershipService: GroupMembershipService,
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

        val response = groupService.createGroup(tournamentId, request)
        logger.info("POST /api/tournaments/{}/groups - Group created: id={}", tournamentId, response.id)

        groupMembershipService.joinGroup(userId, groupId = response.id, role = GroupRole.OWNER)
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }

    @PatchMapping("/{groupId}")
    fun updateGroup(
        @AuthenticationPrincipal principal: JwtUserPrincipal?,
        @PathVariable groupId: UUID,
        @PathVariable tournamentId: UUID,
        @Valid @RequestBody request: UpdateGroupRequest
    ): ResponseEntity<GroupResponse> {
        logger.info("PATCH /api/tournaments/{}/groups/{} - Updating group", tournamentId, groupId)

        val userId = principal?.userId ?: throw UnauthorizedException("Authentication required")
        if (!groupMembershipService.isAdmin(userId, groupId)) {
            throw ForbiddenException("User not allowed")
        }

        val response = groupService.updateGroup(groupId, request)
        logger.info("PATCH /api/tournaments/{}/groups/{} - Group updated", tournamentId, groupId)
        return ResponseEntity.ok(response)
    }

    @DeleteMapping("/{groupId}")
    fun deleteGroup(
        @AuthenticationPrincipal principal: JwtUserPrincipal?,
        @PathVariable groupId: UUID,
        @PathVariable tournamentId: UUID,
    ): ResponseEntity<Void> {
        logger.info("DELETE /api/tournaments/{}/groups/{} - Deleting group", tournamentId, groupId)

        val userId = principal?.userId ?: throw UnauthorizedException("Authentication required")
        groupService.getGroupById(groupId)

        if (!groupMembershipService.isAdmin(userId, groupId)) {
            throw ForbiddenException("User not allowed")
        }

        groupService.deleteGroup(groupId)
        logger.info("DELETE /api/tournaments/{}/groups/{} - Group deleted", tournamentId, groupId)
        return ResponseEntity.noContent().build()
    }

    @GetMapping("/{groupId}")
    fun getGroup(
        @AuthenticationPrincipal principal: JwtUserPrincipal?,
        @PathVariable groupId: UUID,
        @PathVariable tournamentId: UUID,
    ): ResponseEntity<GroupResponse> {
        logger.info("GET /api/tournaments/{}/groups/{} - Fetching group", tournamentId, groupId)

        val userId = principal?.userId ?: throw UnauthorizedException("Authentication required")
        val response = groupService.getGroupById(groupId)

        if (!groupMembershipService.isMember(userId, groupId)) {
            throw ForbiddenException("User not allowed")
        }

        return ResponseEntity.ok(response)
    }

    @GetMapping
    fun getAllGroups(
        @AuthenticationPrincipal principal: JwtUserPrincipal?,
        @PathVariable tournamentId: UUID,
        @RequestParam(required = false) search: String?,
        @RequestParam(required = false) joined: Boolean?
    ): ResponseEntity<List<GroupResponse>> {
        logger.info("GET /api/tournaments/{}/groups - Fetching groups, search='{}', joined='{}'", tournamentId, search, joined)
        principal?.userId ?: throw UnauthorizedException("Authentication required")

        val response = groupService.findOtherGroups(principal.userId, tournamentId, search, joined)
        logger.info("GET /api/tournaments/{}/groups - Returning {} groups", tournamentId, response.size)
        return ResponseEntity.ok(response)
    }

    @PostMapping("/{groupId}/join")
    fun joinGroup(
        @AuthenticationPrincipal principal: JwtUserPrincipal?,
        @PathVariable tournamentId: UUID,
        @PathVariable groupId: UUID
    ): ResponseEntity<Void> {
        val userId = principal?.userId ?: throw UnauthorizedException("Authentication required")
        logger.info("POST /api/tournaments/{}/groups/{}/join - userId={}", tournamentId, groupId, userId)
        groupMembershipService.joinGroup(userId, groupId)
        logger.info("POST /api/tournaments/{}/groups/{}/join - Joined successfully, userId={}", tournamentId, groupId, userId)
        return ResponseEntity.status(HttpStatus.CREATED).build()
    }

    @DeleteMapping("/{groupId}/leave")
    fun leaveGroup(
        @AuthenticationPrincipal principal: JwtUserPrincipal?,
        @PathVariable tournamentId: UUID,
        @PathVariable groupId: UUID
    ): ResponseEntity<Void> {
        val userId = principal?.userId ?: throw UnauthorizedException("Authentication required")
        logger.info("DELETE /api/tournaments/{}/groups/{}/leave - userId={}", tournamentId, groupId, userId)
        groupMembershipService.leaveGroup(userId, groupId)
        logger.info("DELETE /api/tournaments/{}/groups/{}/leave - Left successfully, userId={}", tournamentId, groupId, userId)
        return ResponseEntity.noContent().build()
    }
}
