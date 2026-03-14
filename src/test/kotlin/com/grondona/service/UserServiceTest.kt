package com.grondona.service

import com.grondona.exception.BadRequestException
import com.grondona.exception.ConflictException
import com.grondona.exception.ForbiddenException
import com.grondona.exception.NotFoundException
import com.grondona.model.User
import com.grondona.model.dto.CreateUserRequest
import com.grondona.model.dto.LoginRequest
import com.grondona.model.dto.UpdateUserRequest
import com.grondona.repository.UserRepository
import com.grondona.security.JwtService
import io.mockk.*
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDateTime
import java.util.*

class UserServiceTest {

    @MockK
    private lateinit var userRepository: UserRepository

    @MockK
    private lateinit var jwtService: JwtService

    @InjectMockKs
    private lateinit var userService: UserService

    private val testUserId = UUID.randomUUID()
    private val testToken = "test.jwt.token"
    private val testUser = User(
        id = testUserId,
        fullname = "Test User",
        username = "testuser",
        email = "test@example.com",
        passwordHash = "098f6bcd4621d373cade4e832627b4f6", // MD5 of "test"
        createdAt = LocalDateTime.now(),
        updatedAt = LocalDateTime.now()
    )

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
    }

    @Nested
    inner class CreateUserTests {

        @Test
        fun `createUser should return AuthResponse when successful`() {
            // Given
            val request = CreateUserRequest(
                fullname = "New User",
                username = "newuser",
                email = "new@example.com",
                password = "password123"
            )
            val savedUser = User(
                id = testUserId,
                fullname = request.fullname,
                username = request.username,
                email = request.email,
                passwordHash = "any-hash"
            )

            every { userRepository.existsByUsername(request.username) } returns false
            every { userRepository.existsByEmail(request.email) } returns false
            every { userRepository.save(any()) } returns savedUser
            every { jwtService.generateToken(testUserId, request.username) } returns testToken

            // When
            val result = userService.createUser(request)

            // Then
            assertEquals(testToken, result.token)
            assertEquals(testUserId, result.userId)
            assertEquals(request.username, result.username)
            assertEquals(request.email, result.email)
            assertEquals(request.fullname, result.fullname)

            verify { userRepository.existsByUsername(request.username) }
            verify { userRepository.existsByEmail(request.email) }
            verify { userRepository.save(any()) }
        }

        @Test
        fun `createUser should throw ConflictException when username exists`() {
            // Given
            val request = CreateUserRequest(
                fullname = "New User",
                username = "existinguser",
                email = "new@example.com",
                password = "password123"
            )
            every { userRepository.existsByUsername(request.username) } returns true

            // When/Then
            val exception = assertThrows<ConflictException> {
                userService.createUser(request)
            }
            assertEquals("Nombre de usuario ya registrado", exception.message)
            assertEquals("username", exception.field)
            assertEquals("existinguser", exception.rejectedValue)
        }

        @Test
        fun `createUser should throw ConflictException when email exists`() {
            // Given
            val request = CreateUserRequest(
                fullname = "New User",
                username = "newuser",
                email = "existing@example.com",
                password = "password123"
            )
            every { userRepository.existsByUsername(request.username) } returns false
            every { userRepository.existsByEmail(request.email) } returns true

            // When/Then
            val exception = assertThrows<ConflictException> {
                userService.createUser(request)
            }
            assertEquals("Email ya registrado", exception.message)
            assertEquals("email", exception.field)
            assertEquals("existing@example.com", exception.rejectedValue)
        }
    }

    @Nested
    inner class LoginTests {

        @Test
        fun `login should return AuthResponse when credentials are valid`() {
            // Given
            val request = LoginRequest(user = "testuser", password = "test")
            every { userRepository.findByUsername(request.user) } returns Optional.of(testUser)
            every { jwtService.generateToken(testUserId, testUser.username) } returns testToken

            // When
            val result = userService.login(request)

            // Then
            assertEquals(testToken, result.token)
            assertEquals(testUserId, result.userId)
            assertEquals(testUser.username, result.username)
        }

        @Test
        fun `login should throw BadRequestException when user not found`() {
            // Given
            val request = LoginRequest(user = "nonexistent", password = "password")
            every { userRepository.findByUsername(request.user) } returns Optional.empty()
            every { userRepository.findByEmail(request.user) } returns Optional.empty()

            // When/Then
            val exception = assertThrows<BadRequestException> {
                userService.login(request)
            }
            assertEquals("No hay usuario con ese username o email", exception.message)
        }

        @Test
        fun `login should throw BadRequestException when password is incorrect`() {
            // Given
            val request = LoginRequest(user = "testuser", password = "wrongpassword")
            every { userRepository.findByUsername(request.user) } returns Optional.of(testUser)

            // When/Then
            val exception = assertThrows<BadRequestException> {
                userService.login(request)
            }
            assertEquals("Nombre de usuario o contraseña incorrectos", exception.message)
        }
    }

    @Nested
    inner class UpdateUserTests {

        @Test
        fun `updateUser should update fullname when provided`() {
            // Given
            val request = UpdateUserRequest(fullname = "Updated Name")
            every { userRepository.findById(testUserId) } returns Optional.of(testUser.copy())
            every { userRepository.save(any()) } answers { firstArg() }

            // When
            val result = userService.updateUser(testUserId, request)

            // Then
            assertEquals("Updated Name", result.fullname)
        }

        @Test
        fun `updateUser should update username when provided and not taken`() {
            // Given
            val request = UpdateUserRequest(username = "newusername")
            val userCopy = testUser.copy()
            every { userRepository.findById(testUserId) } returns Optional.of(userCopy)
            every { userRepository.existsByUsername("newusername") } returns false
            every { userRepository.save(any()) } answers { firstArg() }

            // When
            val result = userService.updateUser(testUserId, request)

            // Then
            assertEquals("newusername", result.username)
        }

        @Test
        fun `updateUser should throw ConflictException when new username already exists`() {
            // Given
            val request = UpdateUserRequest(username = "takenusername")
            every { userRepository.findById(testUserId) } returns Optional.of(testUser.copy())
            every { userRepository.existsByUsername("takenusername") } returns true

            // When/Then
            val exception = assertThrows<ConflictException> {
                userService.updateUser(testUserId, request)
            }
            assertEquals("username", exception.field)
            assertEquals("takenusername", exception.rejectedValue)
        }

        @Test
        fun `updateUser should throw NotFoundException when user not found`() {
            // Given
            val request = UpdateUserRequest(fullname = "Updated Name")
            every { userRepository.findById(testUserId) } returns Optional.empty()

            // When/Then
            val exception = assertThrows<NotFoundException> {
                userService.updateUser(testUserId, request)
            }
            assertEquals("Usuario no encontrado", exception.message)
        }

        @Test
        fun `updateUser should update email when provided and not taken`() {
            // Given
            val request = UpdateUserRequest(email = "newemail@example.com")
            val userCopy = testUser.copy()
            every { userRepository.findById(testUserId) } returns Optional.of(userCopy)
            every { userRepository.existsByEmail("newemail@example.com") } returns false
            every { userRepository.save(any()) } answers { firstArg() }

            // When
            val result = userService.updateUser(testUserId, request)

            // Then
            assertEquals("newemail@example.com", result.email)
        }

        @Test
        fun `updateUser should throw ConflictException when new email already exists`() {
            // Given
            val request = UpdateUserRequest(email = "taken@example.com")
            every { userRepository.findById(testUserId) } returns Optional.of(testUser.copy())
            every { userRepository.existsByEmail("taken@example.com") } returns true

            // When/Then
            val exception = assertThrows<ConflictException> {
                userService.updateUser(testUserId, request)
            }
            assertEquals("email", exception.field)
            assertEquals("taken@example.com", exception.rejectedValue)
        }
    }

    @Nested
    inner class DeleteUserTests {

        @Test
        fun `deleteUser should delete when authenticated user deletes own account`() {
            // Given
            every { userRepository.findById(testUserId) } returns Optional.of(testUser)
            every { userRepository.delete(testUser) } just Runs

            // When
            userService.deleteUser(testUserId, testUserId)

            // Then
            verify { userRepository.delete(testUser) }
        }

        @Test
        fun `deleteUser should throw ForbiddenException when trying to delete another user`() {
            // Given
            val otherUserId = UUID.randomUUID()

            // When/Then
            val exception = assertThrows<ForbiddenException> {
                userService.deleteUser(testUserId, otherUserId)
            }
            assertEquals("Sólo puedes eliminar tu propia cuenta", exception.message)
        }

        @Test
        fun `deleteUser should throw NotFoundException when user not found`() {
            // Given
            every { userRepository.findById(testUserId) } returns Optional.empty()

            // When/Then
            val exception = assertThrows<NotFoundException> {
                userService.deleteUser(testUserId, testUserId)
            }
            assertEquals("Usuario no encontrado", exception.message)
        }
    }

    @Nested
    inner class GetUserByIdTests {

        @Test
        fun `getUserById should return UserResponse when user exists`() {
            // Given
            every { userRepository.findById(testUserId) } returns Optional.of(testUser)

            // When
            val result = userService.getUserById(testUserId)

            // Then
            assertEquals(testUserId, result.id)
            assertEquals(testUser.username, result.username)
            assertEquals(testUser.email, result.email)
        }

        @Test
        fun `getUserById should throw NotFoundException when user not found`() {
            // Given
            every { userRepository.findById(testUserId) } returns Optional.empty()

            // When/Then
            val exception = assertThrows<NotFoundException> {
                userService.getUserById(testUserId)
            }
            assertEquals("Usuario no encontrado", exception.message)
        }
    }
}
