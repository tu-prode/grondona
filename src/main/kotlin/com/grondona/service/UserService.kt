package com.grondona.service

import com.grondona.exception.BadRequestException
import com.grondona.exception.ConflictException
import com.grondona.exception.ForbiddenException
import com.grondona.exception.NotFoundException
import com.grondona.model.User
import com.grondona.model.dto.*
import com.grondona.repository.UserRepository
import com.grondona.security.JwtService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.time.LocalDateTime
import java.util.UUID

@Service
class UserService(
    private val userRepository: UserRepository,
    private val jwtService: JwtService
) {

    @Transactional
    fun createUser(request: CreateUserRequest): AuthResponse {
        // Check if username already exists
        if (userRepository.existsByUsername(request.username)) {
            throw ConflictException("Username '${request.username}' already exists")
        }

        // Check if email already exists
        if (userRepository.existsByEmail(request.email)) {
            throw ConflictException("Email '${request.email}' already exists")
        }

        val user = User(
            fullname = request.fullname,
            username = request.username,
            email = request.email,
            passwordHash = hashMd5(request.password)
        )

        val savedUser = userRepository.save(user)
        val token = jwtService.generateToken(savedUser.id!!, savedUser.username)

        return AuthResponse(
            token = token,
            userId = savedUser.id,
            username = savedUser.username,
            email = savedUser.email,
            fullname = savedUser.fullname
        )
    }

    fun login(request: LoginRequest): AuthResponse {
        val user = userRepository.findByUsername(request.username)
            .orElseThrow { BadRequestException("Invalid username or password") }

        val hashedPassword = hashMd5(request.password)
        if (user.passwordHash != hashedPassword) {
            throw BadRequestException("Invalid username or password")
        }

        val token = jwtService.generateToken(user.id!!, user.username)

        return AuthResponse(
            token = token,
            userId = user.id,
            username = user.username,
            email = user.email,
            fullname = user.fullname
        )
    }

    @Transactional
    fun updateUser(authenticatedUserId: UUID, request: UpdateUserRequest): UserResponse {
        val user = userRepository.findById(authenticatedUserId)
            .orElseThrow { NotFoundException("User not found") }

        // Update fields if provided
        request.fullname?.let { user.fullname = it }

        request.username?.let { newUsername ->
            if (newUsername != user.username && userRepository.existsByUsername(newUsername)) {
                throw ConflictException("Username '$newUsername' already exists")
            }
            user.username = newUsername
        }

        request.email?.let { newEmail ->
            if (newEmail != user.email && userRepository.existsByEmail(newEmail)) {
                throw ConflictException("Email '$newEmail' already exists")
            }
            user.email = newEmail
        }

        request.password?.let { user.passwordHash = hashMd5(it) }

        user.updatedAt = LocalDateTime.now()

        val savedUser = userRepository.save(user)
        return UserResponse.from(savedUser)
    }

    @Transactional
    fun deleteUser(authenticatedUserId: UUID, targetUserId: UUID) {
        // Only allow users to delete their own account
        if (authenticatedUserId != targetUserId) {
            throw ForbiddenException("You can only delete your own account")
        }

        val user = userRepository.findById(targetUserId)
            .orElseThrow { NotFoundException("User not found") }

        userRepository.delete(user)
    }

    fun getUserById(userId: UUID): UserResponse {
        val user = userRepository.findById(userId)
            .orElseThrow { NotFoundException("User not found") }
        return UserResponse.from(user)
    }

    private fun hashMd5(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(input.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
}
