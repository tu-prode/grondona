package com.grondona.controller

import com.grondona.model.dto.*
import com.grondona.service.GroupService
import jakarta.validation.Valid
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/groups")
class GroupController(
    private val groupService: GroupService
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
    fun getAllGroups(@RequestParam(required = false) search: String?): ResponseEntity<List<GroupResponse>> {
        logger.info("GET /api/groups - Fetching groups, search='{}'", search)
        val response = if (search != null) {
            groupService.searchGroups(search)
        } else {
            groupService.getAllGroups()
        }
        logger.info("GET /api/groups - Returning {} groups", response.size)
        return ResponseEntity.ok(response)
    }
}
