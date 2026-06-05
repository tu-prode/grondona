package com.grondona.service

import com.grondona.exception.BadRequestException
import com.grondona.exception.ConflictException
import com.grondona.exception.ForbiddenException
import com.grondona.exception.NotFoundException
import com.grondona.model.User
import com.grondona.model.UserPermissions
import com.grondona.model.dto.request.CreateUserRequest
import com.grondona.model.dto.request.ForgottenPasswordRequest
import com.grondona.model.dto.request.LoginUserRequest
import com.grondona.model.dto.request.UpdateUserRequest
import com.grondona.model.dto.response.AuthenticatedUserResponse
import com.grondona.model.dto.response.PredictionProfileResponse
import com.grondona.model.dto.response.UserResponse
import com.grondona.repository.AwardPredictionRepository
import com.grondona.repository.GroupRepository
import com.grondona.repository.MatchPredictionRepository
import com.grondona.repository.MembershipRepository
import com.grondona.repository.UserRepository
import com.grondona.security.JwtService
import com.grondona.service.mailing.EmailService
import com.grondona.utils.hashMD5
import java.time.LocalDateTime
import java.util.UUID
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class UserService(
    private val jwtService: JwtService,
    private val userRepository: UserRepository,
    private val groupRepository: GroupRepository,
    private val predictionService: PredictionService,
    private val membershipRepository: MembershipRepository,
    private val matchPredictionRepository: MatchPredictionRepository,
    private val awardPredictionRepository: AwardPredictionRepository,
    private val emailService: EmailService,
) {

    companion object {
        private val logger = LoggerFactory.getLogger(UserService::class.java)

        private fun generateResetToken(): String {
            val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
            val base = "AGUANTE-EL-ROJO::"
            return base + (1..10).map { chars.random() }.joinToString("")
        }
    }

    @Transactional
    fun createUser(request: CreateUserRequest): AuthenticatedUserResponse {
        logger.info("Creating user: username='{}', email='{}'", request.username, request.email)

        if (userRepository.existsByUsername(request.username)) {
            logger.warn("User creation failed: username '{}' already exists", request.username)
            throw ConflictException(message = "Username already exists", field = "username", rejectedValue = request.username)
        }

        if (userRepository.existsByEmail(request.email)) {
            logger.warn("User creation failed: email '{}' already exists", request.email)
            throw ConflictException(message = "Email already exists", field = "email", rejectedValue = request.email)
        }

        val user = User(
            fullname = request.fullname,
            username = request.username,
            email = request.email,
            passwordHash = hashMD5(request.password),
            createdAt = LocalDateTime.now(),
        )

        val savedUser = userRepository.save(user)
        val token = jwtService.generateToken(savedUser.id!!, savedUser.username)

        logger.info("User created successfully: id={}, username='{}'", savedUser.id, savedUser.username)
        return AuthenticatedUserResponse(
            token = token,
            userId = savedUser.id,
            username = savedUser.username,
            email = savedUser.email,
            fullname = savedUser.fullname,
            permissions = savedUser.permissions,
        )
    }

    @Transactional
    fun login(request: LoginUserRequest): AuthenticatedUserResponse {
        logger.info("Login attempt: user='{}'", request.user)

        var user = userRepository.findByUsername(request.user).orElseGet {
            userRepository.findByEmail(request.user).orElseThrow {
                logger.warn("Login failed: user '{}' not found", request.user)
                BadRequestException("User not found")
            }
        }

        val hashedPassword = hashMD5(request.password)
        val shouldResetPassword = when {
            user.resetToken == hashedPassword -> {
                logger.warn("Login successful with reset token: userId={}, user={}", user.id, user.username)
                true
            }

            user.passwordHash != hashedPassword -> {
                logger.warn("Login failed: invalid password for username {}", request.user)
                throw BadRequestException("User or password incorrect")
            }

            else -> {
                logger.info("Login successful: userId={}, user={}", user.id, user.username)
                false
            }
        }

        if (user.resetToken != null) {
            user = userRepository.save(user.copy(resetToken = null))
        }

        val userId = user.id!!
        val token = jwtService.generateToken(userId, user.username)
        return AuthenticatedUserResponse(
            token = token,
            userId = userId,
            username = user.username,
            email = user.email,
            fullname = user.fullname,
            permissions = user.permissions,
            shouldResetPassword = shouldResetPassword,
        )
    }

    @Transactional
    fun forgottenPasswordToken(request: ForgottenPasswordRequest) {
        logger.info("Requesting reset token for user={}", request.user)

        var user = userRepository.findByUsername(request.user).orElseGet {
            userRepository.findByEmail(request.user).orElseThrow {
                logger.warn("Login failed: user '{}' not found", request.user)
                BadRequestException("User not found")
            }
        }

        val resetToken = generateResetToken()
        user = userRepository.save(user.copy(resetToken = hashMD5(resetToken)))
        emailService.sendPasswordResetEmail(to = user.email, token = resetToken)
        logger.info("Reset token generated and emailed for user={}", request.user)
    }

    @Transactional
    fun updateUser(authenticatedUserId: UUID, request: UpdateUserRequest): UserResponse {
        logger.info("Updating user: userId={}", authenticatedUserId)

        val user = userRepository.findById(authenticatedUserId).orElseThrow {
            logger.warn("User not found for update: userId={}", authenticatedUserId)
            NotFoundException("User not found")
        }

        if (request.username != null) {
            if (request.username != user.username && userRepository.existsByUsername(request.username)) {
                logger.warn("User update failed: username '{}' already exists", request.username)
                throw ConflictException(message = "Username already exists", field = "username", rejectedValue = request.username)
            }
        }

        if (request.email != null) {
            if (request.email != user.email && userRepository.existsByEmail(request.email)) {
                logger.warn("User update failed: email '{}' already exists", request.email)
                throw ConflictException(message = "Email already exists", field = "email", rejectedValue = request.email)
            }
        }

        if (request.uniquePredictions == true && membershipRepository.findUserGroups(authenticatedUserId).size > 1) {
            request.uniquePredictionsMaster?.let { masterGroupId ->
                predictionService.clonePredictions(user.id!!, masterGroupId)
            } ?: run {
                logger.warn("User update failed: unique predictions master")
                throw BadRequestException(message = "Cannot set the predictions-uniqueness flag to true without indicating a master")
            }
        }

        val userToSave = user.copy(
            username = request.username ?: user.username,
            fullname = request.fullname ?: user.fullname,
            email = request.email ?: user.email,
            passwordHash = request.password?.let { hashMD5(it) } ?: user.passwordHash,
            hasUniquePredictions = request.uniquePredictions ?: user.hasUniquePredictions,
            updatedAt = LocalDateTime.now(),
        )


        val savedUser = userRepository.save(userToSave)
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

        val user = userRepository.findById(targetUserId).orElseThrow {
            logger.warn("User not found for deletion: userId={}", targetUserId)
            NotFoundException("User not found")
        }

        val deletedMatchPredictions = matchPredictionRepository.deleteByUserId(targetUserId)
        val deletedAwardPredictions = awardPredictionRepository.deleteByUserId(targetUserId)
        membershipRepository.clearUser(targetUserId)
        logger.info(
            "Removed user data for user={}: matchPredictions={}, awardPredictions={}, memberships cleared",
            targetUserId, deletedMatchPredictions, deletedAwardPredictions
        )

        userRepository.delete(user)
        logger.info("User deleted successfully: userId={}", targetUserId)
    }

    fun getCurrentUser(userId: UUID): UserResponse {
        logger.info("Fetching user: userId={}", userId)

        val user = userRepository.findById(userId).orElseThrow {
            logger.warn("User not found: userId={}", userId)
            NotFoundException("User not found")
        }

        val joinRequests = membershipRepository.findJoinRequests(userId)
        val userGroups = membershipRepository.findUserGroups(userId)

        logger.info("User fetched successfully: userId={}, username='{}'", user.id, user.username)
        return UserResponse.from(user).withJoinRequests(joinRequests).withProfiles(userGroups.map {
            predictionService.calculatePredictionsProfile(userId, it.group.id!!)
        })
    }

    fun getUserProfile(currentUserId: UUID, memberId: UUID, groupId: UUID): PredictionProfileResponse {
        groupRepository.findById(groupId).orElseThrow { NotFoundException("Group not found") }
        userRepository.findById(currentUserId).orElseThrow { NotFoundException("User not found") }
        userRepository.findById(memberId).orElseThrow { NotFoundException("User not found") }

        if (!membershipRepository.isMember(currentUserId, groupId)) {
            logger.warn("User={} trying to check user profile in group={}, but doesn't belong to", currentUserId, groupId)
            throw ForbiddenException("User doesn't belong to the group")
        }

        return predictionService.calculatePredictionsProfile(memberId, groupId)
    }

    fun hasAdminAccess(userId: UUID): Boolean =
        userRepository.findById(userId).map { it.permissions == UserPermissions.SUPERUSER }.orElseThrow {
            logger.warn("User not found: userId={}", userId)
            NotFoundException("User not found")
        }
}
