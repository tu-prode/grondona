package com.grondona.controller

import com.grondona.exception.UnauthorizedException
import com.grondona.model.dto.*
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
@RequestMapping("/api/groups")
class GroupController(
    private val groupService: GroupService,
    private val groupMembershipService: GroupMembershipService
) {

    companion object {
        private val logger = LoggerFactory.getLogger(GroupController::class.java)
    }

    @PostMapping
    fun createGroup(@Valid @RequestBody request: CreateGroupRequest): ResponseEntity<GroupResponse> {
        logger.info("POST /api/groups - Creating group: name='{}'", request.name)
        val response = groupService.createGroup(request)
        logger.info("POST /api/groups - Group created: id={}", response.id)
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }

    @PatchMapping("/{groupId}")
    fun updateGroup(
        @PathVariable groupId: UUID,
        @Valid @RequestBody request: UpdateGroupRequest
    ): ResponseEntity<GroupResponse> {
        logger.info("PATCH /api/groups/{} - Updating group", groupId)
        val response = groupService.updateGroup(groupId, request)
        logger.info("PATCH /api/groups/{} - Group updated", groupId)
        return ResponseEntity.ok(response)
    }

    @DeleteMapping("/{groupId}")
    fun deleteGroup(@PathVariable groupId: UUID): ResponseEntity<Void> {
        logger.info("DELETE /api/groups/{} - Deleting group", groupId)
        groupService.deleteGroup(groupId)
        logger.info("DELETE /api/groups/{} - Group deleted", groupId)
        return ResponseEntity.noContent().build()
    }

    @GetMapping("/{groupId}")
    fun getGroup(@PathVariable groupId: UUID): ResponseEntity<GroupResponse> {
        logger.info("GET /api/groups/{} - Fetching group", groupId)
        val response = groupService.getGroupById(groupId)
        return ResponseEntity.ok(response)
    }

    @GetMapping
    fun getAllGroups(
        @RequestParam(required = false) search: String?,
        @RequestParam(required = false) joined: Boolean?
    ): ResponseEntity<List<GroupResponse>> {
        logger.info("GET /api/groups - Fetching groups, search='{}', joined='{}'", search, joined)
        val response = groupService.findGroups(search, joined)
        logger.info("GET /api/groups - Returning {} groups", response.size)
        return ResponseEntity.ok(response)
    }

    @PostMapping("/{groupId}/join")
    fun joinGroup(
        @AuthenticationPrincipal principal: JwtUserPrincipal?,
        @PathVariable groupId: UUID
    ): ResponseEntity<Void> {
        val userId = principal?.userId ?: throw UnauthorizedException("Authentication required")
        logger.info("POST /api/groups/{}/join - userId={}", groupId, userId)
        groupMembershipService.joinGroup(userId, groupId)
        logger.info("POST /api/groups/{}/join - Joined successfully, userId={}", groupId, userId)
        return ResponseEntity.status(HttpStatus.CREATED).build()
    }

    @DeleteMapping("/{groupId}/leave")
    fun leaveGroup(
        @AuthenticationPrincipal principal: JwtUserPrincipal?,
        @PathVariable groupId: UUID
    ): ResponseEntity<Void> {
        val userId = principal?.userId ?: throw UnauthorizedException("Authentication required")
        logger.info("DELETE /api/groups/{}/leave - userId={}", groupId, userId)
        groupMembershipService.leaveGroup(userId, groupId)
        logger.info("DELETE /api/groups/{}/leave - Left successfully, userId={}", groupId, userId)
        return ResponseEntity.noContent().build()
    }

}
