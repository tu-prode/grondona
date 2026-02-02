package com.grondona.controller

import com.grondona.exception.UnauthorizedException
import com.grondona.model.dto.*
import com.grondona.security.JwtUserPrincipal
import com.grondona.service.UserService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/users")
class UserController(
    private val userService: UserService
) {

    /**
     * Create a new user
     * POST /api/users
     */
    @PostMapping
    fun createUser(@Valid @RequestBody request: CreateUserRequest): ResponseEntity<AuthResponse> {
        val response = userService.createUser(request)
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }

    /**
     * User login
     * POST /api/users/login
     */
    @PostMapping("/login")
    fun login(@Valid @RequestBody request: LoginRequest): ResponseEntity<AuthResponse> {
        val response = userService.login(request)
        return ResponseEntity.ok(response)
    }

    /**
     * Update current user (partial update)
     * PATCH /api/users
     * Requires JWT Authentication
     */
    @PatchMapping
    fun updateUser(
        @AuthenticationPrincipal principal: JwtUserPrincipal?,
        @Valid @RequestBody request: UpdateUserRequest
    ): ResponseEntity<UserResponse> {
        val userId = principal?.userId
            ?: throw UnauthorizedException("Authentication required")

        val response = userService.updateUser(userId, request)
        return ResponseEntity.ok(response)
    }

    /**
     * Delete a user by ID
     * DELETE /api/users/{userId}
     * Requires JWT Authentication
     */
    @DeleteMapping("/{userId}")
    fun deleteUser(
        @AuthenticationPrincipal principal: JwtUserPrincipal?,
        @PathVariable userId: UUID
    ): ResponseEntity<Void> {
        val authenticatedUserId = principal?.userId
            ?: throw UnauthorizedException("Authentication required")

        userService.deleteUser(authenticatedUserId, userId)
        return ResponseEntity.noContent().build()
    }

    /**
     * Get current user profile
     * GET /api/users/me
     * Requires JWT Authentication
     */
    @GetMapping("/me")
    fun getCurrentUser(
        @AuthenticationPrincipal principal: JwtUserPrincipal?
    ): ResponseEntity<UserResponse> {
        val userId = principal?.userId
            ?: throw UnauthorizedException("Authentication required")

        val response = userService.getUserById(userId)
        return ResponseEntity.ok(response)
    }
}
