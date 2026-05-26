package com.grondona.integration

import com.fasterxml.jackson.databind.ObjectMapper
import com.grondona.createTestingTournamentRequest
import com.grondona.createTestingUserRequest
import com.grondona.model.TournamentStatus
import com.grondona.model.UserPermissions
import com.grondona.model.dto.request.UpdateTournamentRequest
import com.grondona.model.dto.response.AuthenticatedUserResponse
import com.grondona.model.dto.response.TournamentResponse
import com.grondona.repository.GroupRepository
import com.grondona.repository.TournamentRepository
import com.grondona.repository.UserRepository
import org.junit.jupiter.api.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.test.context.support.WithAnonymousUser
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class TournamentIntegrationTest {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var objectMapper: ObjectMapper
    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var tournamentRepository: TournamentRepository
    @Autowired private lateinit var groupRepository: GroupRepository

    @Autowired private lateinit var testDatabaseCleaner: TestDatabaseCleaner

    private var superuserToken: String? = null
    private var regularUserToken: String? = null
    private var createdTournamentId: String? = null

    @BeforeAll
    fun setUp() {
        testDatabaseCleaner.cleanAll()

        // Create superuser
        val adminResult = mockMvc.perform(
            post("/api/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createTestingUserRequest(username = "admin")))
        ).andReturn()
        val adminResponse = objectMapper.readValue(adminResult.response.contentAsString, AuthenticatedUserResponse::class.java)
        val adminUser = userRepository.findById(adminResponse.userId).get()
        userRepository.save(adminUser.copy(permissions = UserPermissions.SUPERUSER))
        superuserToken = adminResponse.token

        // Create regular user
        val userResult = mockMvc.perform(
            post("/api/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createTestingUserRequest(username = "regular")))
        ).andReturn()
        regularUserToken = objectMapper.readValue(userResult.response.contentAsString, AuthenticatedUserResponse::class.java).token
    }

    @BeforeEach
    fun clearSecurityContext() {
        SecurityContextHolder.clearContext()
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    @TestMethodOrder(MethodOrderer.OrderAnnotation::class)
    inner class TournamentLifecycleTests {

        @Test
        @Order(1)
        fun `should create tournament successfully as superuser`() {
            val request = createTestingTournamentRequest(name = "World Cup")

            val result = mockMvc.perform(
                post("/api/tournaments")
                    .header("Authorization", "Bearer $superuserToken")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isCreated)
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.name").value(request.name))
                .andExpect(jsonPath("$.status").value("NOT_STARTED"))
                .andReturn()

            createdTournamentId = objectMapper.readValue(result.response.contentAsString, TournamentResponse::class.java).id.toString()
        }

        @Test
        @Order(2)
        fun `should fail to create tournament with duplicate name`() {
            val name = tournamentRepository.findAll().first().name

            mockMvc.perform(
                post("/api/tournaments")
                    .header("Authorization", "Bearer $superuserToken")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(createTestingTournamentRequest(name = name).copy(name = name)))
            )
                .andExpect(status().isConflict)
                .andExpect(jsonPath("$.data.field").value("name"))
        }

        @Test
        @Order(3)
        fun `should get tournament by id without authentication`() {
            mockMvc.perform(get("/api/tournaments/{id}", createdTournamentId))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.id").value(createdTournamentId))
                .andExpect(jsonPath("$.name").exists())
                .andExpect(jsonPath("$.status").value("NOT_STARTED"))
        }

        @Test
        @Order(4)
        fun `should update tournament name as superuser`() {
            val request = UpdateTournamentRequest(name = "Copa America Updated")

            mockMvc.perform(
                patch("/api/tournaments/{id}", createdTournamentId)
                    .header("Authorization", "Bearer $superuserToken")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.name").value("Copa America Updated"))
        }

        @Test
        @Order(5)
        fun `should update tournament status as superuser`() {
            val request = UpdateTournamentRequest(name = null, status = TournamentStatus.IN_PROGRESS)

            mockMvc.perform(
                patch("/api/tournaments/{id}", createdTournamentId)
                    .header("Authorization", "Bearer $superuserToken")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
        }

        @Test
        @Order(6)
        fun `should get tournament matches with no matches found`() {
            mockMvc.perform(get("/api/tournaments/{id}/matches", createdTournamentId))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.tournament_id").value(createdTournamentId))
                .andExpect(jsonPath("$.tournament_name").exists())
                // Empty collections are omitted from the response due to non_empty Jackson config
                .andExpect(jsonPath("$.past_matches").doesNotExist())
                .andExpect(jsonPath("$.live_matches").doesNotExist())
                .andExpect(jsonPath("$.next_matches").doesNotExist())
        }

        @Test
        @Order(7)
        fun `should delete tournament as superuser`() {
            mockMvc.perform(
                delete("/api/tournaments/{id}", createdTournamentId)
                    .header("Authorization", "Bearer $superuserToken")
            )
                .andExpect(status().isNoContent)

            // Verify deletion
            mockMvc.perform(get("/api/tournaments/{id}", createdTournamentId))
                .andExpect(status().isNotFound)
        }
    }

    @Nested
    inner class TournamentAccessControlTests {

        @Test
        @WithAnonymousUser
        fun `should return 401 when creating tournament without authentication`() {
            mockMvc.perform(
                post("/api/tournaments")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(createTestingTournamentRequest()))
            )
                .andExpect(status().isUnauthorized)
        }

        @Test
        fun `should return 403 when creating tournament as regular user`() {
            mockMvc.perform(
                post("/api/tournaments")
                    .header("Authorization", "Bearer $regularUserToken")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(createTestingTournamentRequest()))
            )
                .andExpect(status().isForbidden)
        }

        @Test
        fun `should return 401 when updating tournament without authentication`() {
            mockMvc.perform(
                patch("/api/tournaments/{id}", UUID.randomUUID())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(UpdateTournamentRequest(name = "New Name")))
            )
                .andExpect(status().isUnauthorized)
        }

        @Test
        fun `should return 403 when updating tournament as regular user`() {
            // ForbiddenException is thrown before any tournament lookup
            mockMvc.perform(
                patch("/api/tournaments/{id}", UUID.randomUUID())
                    .header("Authorization", "Bearer $regularUserToken")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(UpdateTournamentRequest(name = "Hijacked")))
            )
                .andExpect(status().isForbidden)
        }

        @Test
        fun `should return 401 when deleting tournament without authentication`() {
            mockMvc.perform(delete("/api/tournaments/{id}", UUID.randomUUID()))
                .andExpect(status().isUnauthorized)
        }

        @Test
        fun `should return 403 when deleting tournament as regular user`() {
            mockMvc.perform(
                delete("/api/tournaments/{id}", UUID.randomUUID())
                    .header("Authorization", "Bearer $regularUserToken")
            )
                .andExpect(status().isForbidden)
        }
    }

    @Nested
    inner class TournamentNotFoundTests {

        private val nonExistentId = UUID.randomUUID()

        @Test
        fun `should return 404 when getting non-existent tournament`() {
            mockMvc.perform(get("/api/tournaments/{id}", nonExistentId))
                .andExpect(status().isNotFound)
                .andExpect(jsonPath("$.message").value("Tournament not found"))
        }

        @Test
        fun `should return 404 when updating non-existent tournament`() {
            mockMvc.perform(
                patch("/api/tournaments/{id}", nonExistentId)
                    .header("Authorization", "Bearer $superuserToken")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(UpdateTournamentRequest(name = "Any")))
            )
                .andExpect(status().isNotFound)
                .andExpect(jsonPath("$.message").value("Tournament not found"))
        }

        @Test
        fun `should return 404 when deleting non-existent tournament`() {
            mockMvc.perform(
                delete("/api/tournaments/{id}", nonExistentId)
                    .header("Authorization", "Bearer $superuserToken")
            )
                .andExpect(status().isNotFound)
                .andExpect(jsonPath("$.message").value("Tournament not found"))
        }

        @Test
        fun `should return 404 when getting matches for non-existent tournament`() {
            mockMvc.perform(get("/api/tournaments/{id}/matches", nonExistentId))
                .andExpect(status().isNotFound)
                .andExpect(jsonPath("$.message").value("Tournament not found"))
        }
    }

    @Nested
    inner class TournamentValidationTests {

        @Test
        fun `should return 400 when creating tournament with blank name`() {
            val body = mapOf("name" to "")

            mockMvc.perform(
                post("/api/tournaments")
                    .header("Authorization", "Bearer $superuserToken")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(body))
            )
                .andExpect(status().isBadRequest)
        }
    }
}
