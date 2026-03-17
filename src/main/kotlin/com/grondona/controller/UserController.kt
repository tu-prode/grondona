package com.grondona.controller

import com.grondona.exception.UnauthorizedException
import com.grondona.model.dto.*
import com.grondona.security.JwtUserPrincipal
import com.grondona.service.GroupMembershipService
import com.grondona.service.UserService
import jakarta.validation.Valid
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/users")
class UserController(
    private val userService: UserService,
    private val groupMembershipService: GroupMembershipService
) {

    companion object {
        private val logger = LoggerFactory.getLogger(UserController::class.java)
    }

    @PostMapping
    fun createUser(@Valid @RequestBody request: CreateUserRequest): ResponseEntity<AuthResponse> {
        logger.info("POST /api/users - Creating user: username='{}', email='{}'", request.username, request.email)
        val response = userService.createUser(request)
        logger.info("POST /api/users - User created: id={}, username='{}'", response.userId, response.username)
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }

    @PostMapping("/login")
    fun login(@Valid @RequestBody request: LoginRequest): ResponseEntity<AuthResponse> {
        logger.info("POST /api/users/login - Login attempt: user='{}'", request.user)
        val response = userService.login(request)
        logger.info("POST /api/users/login - Login successful: userId={}", response.userId)
        return ResponseEntity.ok(response)
    }

    @PatchMapping
    fun updateUser(
        @AuthenticationPrincipal principal: JwtUserPrincipal?,
        @Valid @RequestBody request: UpdateUserRequest
    ): ResponseEntity<UserResponse> {
        val userId = principal?.userId ?: throw UnauthorizedException("Authentication required")

        logger.info("PATCH /api/users - Updating user: userId={}", userId)
        val response = userService.updateUser(userId, request)
        logger.info("PATCH /api/users - User updated: userId={}", userId)
        return ResponseEntity.ok(response)
    }

    @DeleteMapping("/{userId}")
    fun deleteUser(
        @AuthenticationPrincipal principal: JwtUserPrincipal?,
        @PathVariable userId: UUID
    ): ResponseEntity<Void> {
        val authenticatedUserId = principal?.userId ?: throw UnauthorizedException("Authentication required")

        logger.info("DELETE /api/users/{} - Delete request by userId={}", userId, authenticatedUserId)
        userService.deleteUser(authenticatedUserId, userId)
        logger.info("DELETE /api/users/{} - User deleted", userId)
        return ResponseEntity.noContent().build()
    }

    @GetMapping("/me")
    fun getCurrentUser(
        @AuthenticationPrincipal principal: JwtUserPrincipal?
    ): ResponseEntity<UserResponse> {
        val userId = principal?.userId ?: throw UnauthorizedException("Authentication required")

        logger.info("GET /api/users/me - Fetching profile: userId={}", userId)
        val response = userService.getUserById(userId)
        return ResponseEntity.ok(response)
    }

    @GetMapping("/me/groups")
    fun getMyGroups(
        @AuthenticationPrincipal principal: JwtUserPrincipal?
    ): ResponseEntity<List<UserGroupResponse>> {
        val userId = principal?.userId ?: throw UnauthorizedException("Authentication required")

        logger.info("GET /api/users/me/groups - Fetching groups for userId={}", userId)
        val response = groupMembershipService.getMyGroups(userId)
        logger.info("GET /api/users/me/groups - Returning {} groups for userId={}", response.size, userId)
        return ResponseEntity.ok(response)
    }
}
