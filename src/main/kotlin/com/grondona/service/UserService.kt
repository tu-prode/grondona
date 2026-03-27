package com.grondona.service

import com.grondona.exception.BadRequestException
import com.grondona.exception.ConflictException
import com.grondona.exception.ForbiddenException
import com.grondona.exception.NotFoundException
import com.grondona.model.User
import com.grondona.model.UserPermissions
import com.grondona.model.dto.*
import com.grondona.repository.UserRepository
import com.grondona.security.JwtService
import java.security.MessageDigest
import java.time.LocalDateTime
import java.util.UUID
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class UserService(
    private val userRepository: UserRepository,
    private val jwtService: JwtService,
) {

    companion object {
        private val logger = LoggerFactory.getLogger(UserService::class.java)
    }

    @Transactional
    fun createUser(request: CreateUserRequest): AuthResponse {
        logger.info("Creating user: username='{}', email='{}'", request.username, request.email)

        if (userRepository.existsByUsername(request.username)) {
            logger.warn("User creation failed: username '{}' already exists", request.username)
            throw ConflictException(
                message = "Username already exists",
                field = "username",
                rejectedValue = request.username
            )
        }

        if (userRepository.existsByEmail(request.email)) {
            logger.warn("User creation failed: email '{}' already exists", request.email)
            throw ConflictException(message = "Email already exists", field = "email", rejectedValue = request.email)
        }

        val user = User(
            fullname = request.fullname,
            username = request.username,
            email = request.email,
            passwordHash = hashMd5(request.password)
        )

        val savedUser = userRepository.save(user)
        val token = jwtService.generateToken(savedUser.id!!, savedUser.username)

        logger.info("User created successfully: id={}, username='{}'", savedUser.id, savedUser.username)
        return AuthResponse(
            token = token,
            userId = savedUser.id,
            username = savedUser.username,
            email = savedUser.email,
            fullname = savedUser.fullname
        )
    }

    fun login(request: LoginRequest): AuthResponse {
        logger.info("Login attempt: user='{}'", request.user)

        val user = userRepository.findByUsername(request.user).orElseGet {
            userRepository.findByEmail(request.user).orElseThrow {
                logger.warn("Login failed: user '{}' not found", request.user)
                BadRequestException("User not found")
            }
        }

        val hashedPassword = hashMd5(request.password)
        if (user.passwordHash != hashedPassword) {
            logger.warn("Login failed: invalid password for username '{}'", request.user)
            throw BadRequestException("User or password incorrect")
        }

        val token = jwtService.generateToken(user.id!!, user.username)

        logger.info("Login successful: userId={}, user='{}'", user.id, user.username)
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
        logger.info("Updating user: userId={}", authenticatedUserId)

        val user =
            userRepository.findById(authenticatedUserId).orElseThrow {
                logger.warn("User not found for update: userId={}", authenticatedUserId)
                NotFoundException("User not found")
            }

        request.fullname?.let { user.fullname = it }

        request.username?.let { newUsername ->
            if (newUsername != user.username && userRepository.existsByUsername(newUsername)) {
                logger.warn("User update failed: username '{}' already exists", newUsername)
                throw ConflictException(
                    message = "Username already exists",
                    field = "username",
                    rejectedValue = newUsername
                )
            }
            user.username = newUsername
        }

        request.email?.let { newEmail ->
            if (newEmail != user.email && userRepository.existsByEmail(newEmail)) {
                logger.warn("User update failed: email '{}' already exists", newEmail)
                throw ConflictException(message = "Email already exists", field = "email", rejectedValue = newEmail)
            }
            user.email = newEmail
        }

        request.password?.let { user.passwordHash = hashMd5(it) }

        user.updatedAt = LocalDateTime.now()

        val savedUser = userRepository.save(user)
        logger.info("User updated successfully: userId={}", savedUser.id)
        return UserResponse.from(savedUser)
    }

    @Transactional
    fun deleteUser(authenticatedUserId: UUID, targetUserId: UUID) {
        logger.info("Deleting user: targetUserId={}, requestedBy={}", targetUserId, authenticatedUserId)

        if (authenticatedUserId != targetUserId) {
            logger.warn("Delete forbidden: user {} tried to delete user {}", authenticatedUserId, targetUserId)
            throw ForbiddenException("You can only delete your own account")
        }

        val user =
            userRepository.findById(targetUserId).orElseThrow {
                logger.warn("User not found for deletion: userId={}", targetUserId)
                NotFoundException("User not found")
            }

        userRepository.delete(user)
        logger.info("User deleted successfully: userId={}", targetUserId)
    }

    fun getUserById(userId: UUID): UserResponse {
        logger.info("Fetching user: userId={}", userId)

        val user =
            userRepository.findById(userId).orElseThrow {
                logger.warn("User not found: userId={}", userId)
                NotFoundException("User not found")
            }

        logger.info("User fetched successfully: userId={}, username='{}'", user.id, user.username)
        return UserResponse.from(user)
    }

    private fun hashMd5(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(input.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun hasAdminAccess(userId: UUID): Boolean =
        userRepository.findById(userId).map { it.permissions == UserPermissions.SUPERUSER }.orElseThrow {
            logger.warn("User not found: userId={}", userId)
            NotFoundException("User not found")
        }
}
