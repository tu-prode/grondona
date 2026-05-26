package com.grondona.integration

import com.fasterxml.jackson.databind.ObjectMapper
import com.grondona.client.MatchClient
import com.grondona.createTestingUserRequest
import com.grondona.integration.utils.GrondonaClient
import com.grondona.model.ExternalMatch
import com.grondona.model.Tournament
import com.grondona.model.UserPermissions
import com.grondona.model.dto.request.CreateMatchRequest
import com.grondona.model.dto.request.CreateMatchesRequest
import com.grondona.model.dto.request.CreateTeamRequest
import com.grondona.model.dto.response.AuthenticatedUserResponse
import com.grondona.model.dto.request.CreateUserRequest
import com.grondona.model.dto.request.LoginUserRequest
import com.grondona.model.dto.request.SubmitMatchPredictionRequest
import com.grondona.model.dto.request.UpdateUserRequest
import com.grondona.model.dto.response.CreatedMatchesResponse
import com.grondona.repository.TournamentRepository
import com.grondona.repository.UserRepository
import com.grondona.scheduler.MatchScheduler
import com.grondona.service.engine.WorldCupEngine
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import org.junit.jupiter.api.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.transaction.annotation.Transactional
import java.time.ZonedDateTime
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class UserIntegrationTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var tournamentRepository: TournamentRepository

    @Autowired
    lateinit var matchScheduler: MatchScheduler

    @MockkBean
    lateinit var matchClient: MatchClient

    @Autowired
    private lateinit var testDatabaseCleaner: TestDatabaseCleaner

    private var adminId: String? = null
    private var adminToken: String? = null

    private var testTournamentId: String? = null
    private var testMatch1Id: UUID? = null
    private var testMatch2Id: UUID? = null
    private var testMatch3Id: UUID? = null
    private var testTeam1Id: String? = null
    private var testTeam2Id: String? = null
    private var testTeam3Id: String? = null
    private var testTeam4Id: String? = null
    private var testTeam5Id: String? = null
    private var testTeam6Id: String? = null

    @BeforeAll
    fun setUp() {
        testDatabaseCleaner.cleanAll()

        // Create an admin user to create the first tournament
        val adminResult = mockMvc.perform(
            post("/api/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createTestingUserRequest()))
        ).andReturn()
        val adminResponse = objectMapper.readValue(adminResult.response.contentAsString, AuthenticatedUserResponse::class.java)
        val adminUser = userRepository.findById(adminResponse.userId).get()
        userRepository.save(adminUser.copy(permissions = UserPermissions.SUPERUSER))
        adminToken = adminResponse.token
        adminId = adminResponse.userId.toString()

        // Create tournament
        tournamentRepository.save(Tournament(id = WorldCupEngine.SYSTEM_TOURNAMENT_ID, name = "Testing Tournament for UserIntegrationTest"))
        Assertions.assertTrue(tournamentRepository.existsById(WorldCupEngine.SYSTEM_TOURNAMENT_ID))
        testTournamentId = WorldCupEngine.SYSTEM_TOURNAMENT_ID.toString()

        val teamIds = (1..6).map {
            val request = CreateTeamRequest(name = "Team $it", code = "T$it", icon = "unknown")
            val result = mockMvc.perform(
                post("/api/tournaments/{tournamentId}/teams", testTournamentId)
                    .header("Authorization", "Bearer $adminToken")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            ).andExpect(status().isCreated).andReturn()
            UUID.fromString(objectMapper.readTree(result.response.contentAsString).get("id").asText())
        }
        testTeam1Id = teamIds[0].toString()
        testTeam2Id = teamIds[1].toString()
        testTeam3Id = teamIds[2].toString()
        testTeam4Id = teamIds[3].toString()
        testTeam5Id = teamIds[4].toString()
        testTeam6Id = teamIds[5].toString()

        val createMatchesRequest = CreateMatchesRequest(matches = listOf(
            CreateMatchRequest(code = "MT1", homeTeam = teamIds[0], awayTeam = teamIds[1], startedAt = ZonedDateTime.now().plusDays(10)),
            CreateMatchRequest(code = "MT2", homeTeam = teamIds[2], awayTeam = teamIds[3], startedAt = ZonedDateTime.now().plusDays(11)),
            CreateMatchRequest(code = "MT3", homeTeam = teamIds[4], awayTeam = teamIds[5], startedAt = ZonedDateTime.now().plusDays(11))
        ))
        val result = mockMvc.perform(
            post("/api/tournaments/{tournamentId}/matches", testTournamentId)
                .header("Authorization", "Bearer $adminToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createMatchesRequest))
        ).andExpect(status().isCreated).andReturn()

        objectMapper.readValue(result.response.contentAsString, CreatedMatchesResponse::class.java).matches.forEach {
            when (it.code) {
                "MT1" -> testMatch1Id = it.id
                "MT2" -> testMatch2Id = it.id
                "MT3" -> testMatch3Id = it.id
            }
        }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    @TestMethodOrder(MethodOrderer.OrderAnnotation::class)
    inner class UserLifecycleTests {

        private var userId: String? = null
        private var authToken: String? = null

        @Test
        @Order(1)
        fun `should create a new user successfully`() {
            // Given
            val request = CreateUserRequest(
                fullname = "Integration Test User",
                username = "integrationuser",
                email = "integration@test.com",
                password = "password123"
            )

            // When/Then
            val result = mockMvc.perform(
                post("/api/users")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isCreated)
                .andExpect(jsonPath("$.token").exists())
                .andExpect(jsonPath("$.user_id").exists())
                .andExpect(jsonPath("$.username").value("integrationuser"))
                .andExpect(jsonPath("$.email").value("integration@test.com"))
                .andExpect(jsonPath("$.fullname").value("Integration Test User"))
                .andReturn()

            // Store token and userId for subsequent tests
            val response = objectMapper.readValue(
                result.response.contentAsString,
                AuthenticatedUserResponse::class.java
            )
            authToken = response.token
            userId = response.userId.toString()
        }

        @Test
        @Order(2)
        fun `should fail to create user with duplicate username`() {
            // Given
            val request = CreateUserRequest(
                fullname = "Another User",
                username = "integrationuser", // Same username
                email = "another@test.com",
                password = "password123"
            )

            // When/Then
            mockMvc.perform(
                post("/api/users")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isConflict)
        }

        @Test
        @Order(3)
        fun `should fail to create user with duplicate email`() {
            // Given
            val request = CreateUserRequest(
                fullname = "Another User",
                username = "anotheruser",
                email = "integration@test.com", // Same email
                password = "password123"
            )

            // When/Then
            mockMvc.perform(
                post("/api/users")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isConflict)
        }

        @Test
        @Order(4)
        fun `should login successfully with valid credentials`() {
            // Given
            val request = LoginUserRequest(
                user = "integrationuser",
                password = "password123"
            )

            // When/Then
            val result = mockMvc.perform(
                post("/api/users/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.token").exists())
                .andExpect(jsonPath("$.username").value("integrationuser"))
                .andReturn()

            // Update token for subsequent tests
            val response = objectMapper.readValue(
                result.response.contentAsString,
                AuthenticatedUserResponse::class.java
            )
            authToken = response.token
        }

        @Test
        @Order(5)
        fun `should fail login with invalid password`() {
            // Given
            val request = LoginUserRequest(
                user = "integrationuser",
                password = "wrongpassword"
            )

            // When/Then
            mockMvc.perform(
                post("/api/users/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isBadRequest)
        }

        @Test
        @Order(6)
        fun `should get current user profile with valid token`() {
            // When/Then
            mockMvc.perform(
                get("/api/users/me")
                    .header("Authorization", "Bearer $authToken")
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.username").value("integrationuser"))
                .andExpect(jsonPath("$.email").value("integration@test.com"))
        }

        @Test
        @Order(7)
        fun `should fail to get profile without authentication`() {
            // When/Then - Spring Security returns 403 Forbidden for missing authentication
            mockMvc.perform(
                get("/api/users/me")
            )
                .andExpect(status().isForbidden)
        }

        @Test
        @Order(8)
        fun `should update user profile successfully`() {
            // Given
            val request = UpdateUserRequest(
                fullname = "Updated Integration User"
            )

            // When/Then
            mockMvc.perform(
                patch("/api/users")
                    .header("Authorization", "Bearer $authToken")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.fullname").value("Updated Integration User"))
        }

        @Test
        @Order(9)
        fun `should fail to update user without authentication`() {
            // Given
            val request = UpdateUserRequest(fullname = "Should Fail")

            // When/Then - Spring Security returns 403 Forbidden for missing authentication
            mockMvc.perform(
                patch("/api/users")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isForbidden)
        }

        @Test
        @Order(10)
        fun `should fail to delete another user account`() {
            // Given - Create another user first
            val anotherUserRequest = CreateUserRequest(
                fullname = "Another User",
                username = "anotheruser2",
                email = "another2@test.com",
                password = "password123"
            )
            val result = mockMvc.perform(
                post("/api/users")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(anotherUserRequest))
            ).andReturn()

            val anotherUserResponse = objectMapper.readValue(
                result.response.contentAsString,
                AuthenticatedUserResponse::class.java
            )

            // When/Then - Try to delete another user with our token
            mockMvc.perform(
                delete("/api/users/{userId}", anotherUserResponse.userId)
                    .header("Authorization", "Bearer $authToken")
            )
                .andExpect(status().isForbidden)
        }

        @Test
        @Order(11)
        fun `should delete own account successfully`() {
            // When/Then
            mockMvc.perform(
                delete("/api/users/{userId}", userId)
                    .header("Authorization", "Bearer $authToken")
            )
                .andExpect(status().isNoContent)

            // Verify user is deleted
            mockMvc.perform(
                get("/api/users/me")
                    .header("Authorization", "Bearer $authToken")
            )
                .andExpect(status().isNotFound)
        }
    }

    @Nested
    inner class ValidationTests {

        @Test
        fun `should fail to create user with invalid email format`() {
            val request = CreateUserRequest(
                fullname = "Test User",
                username = "validuser",
                email = "invalid-email",
                password = "password123"
            )

            mockMvc.perform(
                post("/api/users")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isBadRequest)
        }

        @Test
        fun `should fail to create user with short password`() {
            val request = CreateUserRequest(
                fullname = "Test User",
                username = "validuser",
                email = "valid@email.com",
                password = "short"
            )

            mockMvc.perform(
                post("/api/users")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isBadRequest)
        }

        @Test
        fun `should fail to create user with short username`() {
            val request = CreateUserRequest(
                fullname = "Test User",
                username = "ab", // Too short
                email = "valid@email.com",
                password = "password123"
            )

            mockMvc.perform(
                post("/api/users")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isBadRequest)
        }

        @Test
        fun `should fail to create user with blank fullname`() {
            val request = mapOf(
                "fullname" to "",
                "username" to "validuser",
                "email" to "valid@email.com",
                "password" to "password123"
            )

            mockMvc.perform(
                post("/api/users")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isBadRequest)
        }

    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    @TestMethodOrder(MethodOrderer.OrderAnnotation::class)
    inner class PredictionUniquenessFlowTests {
        
        private val client = GrondonaClient(mockMvc, objectMapper)
            .withAdminToken(adminToken!!)
            .withTournament(testTournamentId!!)

        private var userId: UUID? = null
        private var userToken: String? = null

        private var group1Id: UUID? = null
        private var group2Id: UUID? = null
        private var group3Id: UUID? = null

        private fun setPredictionUniqueness(uniqueness: Boolean, masterGroup: UUID? = null) {
            mockMvc.perform(
                patch("/api/users", userId)
                    .header("Authorization", "Bearer $userToken")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(UpdateUserRequest(uniquePredictions = uniqueness, uniquePredictionsMaster = masterGroup)))
            ).andExpect(status().isOk).andReturn()
        }

        @BeforeAll
        fun setUp() {
            val userResult = mockMvc.perform(
                post("/api/users")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(createTestingUserRequest()))
            ).andReturn()
            val userResponse = objectMapper.readValue(userResult.response.contentAsString, AuthenticatedUserResponse::class.java)
            userId = userResponse.userId
            userToken = userResponse.token

            group1Id = client.createGroup()
            group2Id = client.createGroup()
            group3Id = client.createGroup()
            client.joinGroup(userToken, group1Id)
            client.joinGroup(userToken, group2Id)
            client.joinGroup(userToken, group3Id)
        }

        @Test
        @Order(1)
        fun `user submits predictions in group 1`() {
            val matchPredictions = listOf(
                SubmitMatchPredictionRequest(matchId = testMatch1Id!!, homeGoals = 0, awayGoals = 0),
                SubmitMatchPredictionRequest(matchId = testMatch2Id!!, homeGoals = 1, awayGoals = 0),
                SubmitMatchPredictionRequest(matchId = testMatch3Id!!, homeGoals = 2, awayGoals = 0),
            )
            client.submitMatchPredictionsToGroup(userToken, group1Id, matchPredictions)
        }

        @Test
        @Order(2)
        fun `user submits predictions in group 2`() {
            val matchPredictions = listOf(
                SubmitMatchPredictionRequest(matchId = testMatch1Id!!, homeGoals = 1, awayGoals = 1),
                SubmitMatchPredictionRequest(matchId = testMatch2Id!!, homeGoals = 2, awayGoals = 1),
                SubmitMatchPredictionRequest(matchId = testMatch3Id!!, homeGoals = 3, awayGoals = 1),
            )
            client.submitMatchPredictionsToGroup(userToken, group2Id, matchPredictions)
        }

        @Test
        @Order(3)
        fun `user submits predictions in group 3`() {
            val matchPredictions = listOf(
                SubmitMatchPredictionRequest(matchId = testMatch1Id!!, homeGoals = 2, awayGoals = 2),
                SubmitMatchPredictionRequest(matchId = testMatch2Id!!, homeGoals = 3, awayGoals = 2),
                SubmitMatchPredictionRequest(matchId = testMatch3Id!!, homeGoals = 4, awayGoals = 2),
            )
            client.submitMatchPredictionsToGroup(userToken, group3Id, matchPredictions)
        }

        @Test
        @Order(4)
        fun `user checks predictions in group 1 and are the same as submitted`() {
            val matchPredictions = client.fetchMatchPredictionsInGroup(userToken, group1Id)
            matchPredictions.forEach {
                when (it.match.id) {
                    testMatch1Id -> {
                        Assertions.assertEquals(0, it.prediction!!.homeGoals)
                        Assertions.assertEquals(0, it.prediction!!.awayGoals)
                    }
                    testMatch2Id -> {
                        Assertions.assertEquals(1, it.prediction!!.homeGoals)
                        Assertions.assertEquals(0, it.prediction!!.awayGoals)
                    }
                    testMatch3Id -> {
                        Assertions.assertEquals(2, it.prediction!!.homeGoals)
                        Assertions.assertEquals(0, it.prediction!!.awayGoals)
                    }
                }
            }
        }

        @Test
        @Order(5)
        fun `user checks predictions in group 2 and are the same as submitted`() {
            val matchPredictions = client.fetchMatchPredictionsInGroup(userToken, group2Id)
            matchPredictions.forEach {
                it.prediction!!
                when (it.match.id) {
                    testMatch1Id -> {
                        Assertions.assertEquals(1, it.prediction!!.homeGoals)
                        Assertions.assertEquals(1, it.prediction!!.awayGoals)
                    }
                    testMatch2Id -> {
                        Assertions.assertEquals(2, it.prediction!!.homeGoals)
                        Assertions.assertEquals(1, it.prediction!!.awayGoals)
                    }
                    testMatch3Id -> {
                        Assertions.assertEquals(3, it.prediction!!.homeGoals)
                        Assertions.assertEquals(1, it.prediction!!.awayGoals)
                    }
                }
            }
        }

        @Test
        @Order(6)
        fun `user checks predictions in group 3 and are the same as submitted`() {
            val matchPredictions = client.fetchMatchPredictionsInGroup(userToken, group3Id)
            matchPredictions.forEach {
                it.prediction!!
                when (it.match.id) {
                    testMatch1Id -> {
                        Assertions.assertEquals(2, it.prediction!!.homeGoals)
                        Assertions.assertEquals(2, it.prediction!!.awayGoals)
                    }
                    testMatch2Id -> {
                        Assertions.assertEquals(3, it.prediction!!.homeGoals)
                        Assertions.assertEquals(2, it.prediction!!.awayGoals)
                    }
                    testMatch3Id -> {
                        Assertions.assertEquals(4, it.prediction!!.homeGoals)
                        Assertions.assertEquals(2, it.prediction!!.awayGoals)
                    }
                }
            }
        }

        @Test
        @Order(7)
        fun `it starts match 1 and lock its predictions`() {
            every { matchClient.getMatches(any()) } returns listOf(
                ExternalMatch(
                    code = "MT1", home = testTeam1Id!!, away = testTeam2Id!!, status = "IN_PLAY",
                    homeGoals = 1, awayGoals = 0, half = 1, minutes = 25, startedAt = ZonedDateTime.now().minusMinutes(25)
                ),
                ExternalMatch(
                    code = "MT2", home = testTeam3Id!!, away = testTeam4Id!!, status = "TO_START",
                    homeGoals = 0, awayGoals = 0, half = 0, minutes = 0, startedAt = ZonedDateTime.now().plusDays(1)
                ),
                ExternalMatch(
                    code = "MT3", home = testTeam4Id!!, away = testTeam5Id!!, status = "TO_START",
                    homeGoals = 0, awayGoals = 0, half = 0, minutes = 0, startedAt = ZonedDateTime.now().plusDays(2)
                ),
            )

            matchScheduler.updateMatches()
        }

        @Test
        @Order(8)
        fun `user sets uniqueness for predictions`() {
            setPredictionUniqueness(true, group1Id)
        }

        @Test
        @Order(9)
        fun `user checks predictions in group 1 and they are still the same`() {
            val matchPredictions = client.fetchMatchPredictionsInGroup(userToken, group1Id)
            matchPredictions.forEach {
                it.prediction!!
                when (it.match.id) {
                    testMatch1Id -> {
                        Assertions.assertEquals(0, it.prediction!!.homeGoals)
                        Assertions.assertEquals(0, it.prediction!!.awayGoals)
                    }
                    testMatch2Id -> {
                        Assertions.assertEquals(1, it.prediction!!.homeGoals)
                        Assertions.assertEquals(0, it.prediction!!.awayGoals)
                    }
                    testMatch3Id -> {
                        Assertions.assertEquals(2, it.prediction!!.homeGoals)
                        Assertions.assertEquals(0, it.prediction!!.awayGoals)
                    }
                }
            }
        }

        @Test
        @Order(10)
        fun `user checks predictions in group 2 and only the active ones were updated`() {
            val matchPredictions = client.fetchMatchPredictionsInGroup(userToken, group2Id)
            matchPredictions.forEach {
                it.prediction!!
                when (it.match.id) {
                    testMatch1Id -> {
                        Assertions.assertEquals(0, it.prediction!!.homeGoals)
                        Assertions.assertEquals(0, it.prediction!!.awayGoals)
                    }
                    testMatch2Id -> {
                        Assertions.assertEquals(1, it.prediction!!.homeGoals)
                        Assertions.assertEquals(0, it.prediction!!.awayGoals)
                    }
                    testMatch3Id -> {
                        Assertions.assertEquals(2, it.prediction!!.homeGoals)
                        Assertions.assertEquals(0, it.prediction!!.awayGoals)
                    }
                }
            }
        }

        @Test
        @Order(11)
        fun `user checks predictions in group 3 and only the active ones were updated`() {
            val matchPredictions = client.fetchMatchPredictionsInGroup(userToken, group3Id)
            matchPredictions.forEach {
                it.prediction!!
                when (it.match.id) {
                    testMatch1Id -> {
                        Assertions.assertEquals(0, it.prediction!!.homeGoals)
                        Assertions.assertEquals(0, it.prediction!!.awayGoals)
                    }
                    testMatch2Id -> {
                        Assertions.assertEquals(1, it.prediction!!.homeGoals)
                        Assertions.assertEquals(0, it.prediction!!.awayGoals)
                    }
                    testMatch3Id -> {
                        Assertions.assertEquals(2, it.prediction!!.homeGoals)
                        Assertions.assertEquals(0, it.prediction!!.awayGoals)
                    }
                }
            }
        }

        @Test
        @Order(12)
        fun `user submits predictions for match 2 in group 1`() {
            val matchPredictions = listOf(
                SubmitMatchPredictionRequest(matchId = testMatch2Id!!, homeGoals = 1, awayGoals = 1),
            )
            client.submitMatchPredictionsToGroup(userToken, group1Id, matchPredictions)
        }

        @Test
        @Order(13)
        fun `user checks predictions in group 1 and were updated`() {
            val matchPredictions = client.fetchMatchPredictionsInGroup(userToken, group1Id)
            matchPredictions.forEach {
                when (it.match.id) {
                    testMatch2Id -> {
                        Assertions.assertEquals(1, it.prediction!!.homeGoals)
                        Assertions.assertEquals(1, it.prediction!!.awayGoals)
                    }
                }
            }
        }

        @Test
        @Order(14)
        fun `user checks predictions in group 2 and also were updated`() {
            val matchPredictions = client.fetchMatchPredictionsInGroup(userToken, group2Id)
            matchPredictions.forEach {
                when (it.match.id) {
                    testMatch2Id -> {
                        Assertions.assertEquals(1, it.prediction!!.homeGoals)
                        Assertions.assertEquals(1, it.prediction!!.awayGoals)
                    }
                }
            }
        }

        @Test
        @Order(15)
        fun `user checks predictions in group 3 and also were updated`() {
            val matchPredictions = client.fetchMatchPredictionsInGroup(userToken, group3Id)
            matchPredictions.forEach {
                when (it.match.id) {
                    testMatch2Id -> {
                        Assertions.assertEquals(1, it.prediction!!.homeGoals)
                        Assertions.assertEquals(1, it.prediction!!.awayGoals)
                    }
                }
            }
        }

        @Test
        @Order(16)
        fun `it starts match 2 and lock its predictions`() {
            every { matchClient.getMatches(any()) } returns listOf(
                ExternalMatch(
                    code = "MT1", home = testTeam1Id!!, away = testTeam2Id!!, status = "COMPLETED",
                    homeGoals = 5, awayGoals = 0, half = 2, minutes = 93, startedAt = ZonedDateTime.now().minusDays(2)
                ),
                ExternalMatch(
                    code = "MT2", home = testTeam3Id!!, away = testTeam4Id!!, status = "IN_PLAY",
                    homeGoals = 0, awayGoals = 2, half = 1, minutes = 14, startedAt = ZonedDateTime.now().minusMinutes(14)
                ),
                ExternalMatch(
                    code = "MT3", home = testTeam4Id!!, away = testTeam5Id!!, status = "TO_START",
                    homeGoals = 0, awayGoals = 0, half = 0, minutes = 0, startedAt = ZonedDateTime.now().plusDays(2)
                ),
            )

            matchScheduler.updateMatches()
        }

        @Test
        @Order(17)
        fun `user checks predictions in group 1 and are still the same`() {
            val matchPredictions = client.fetchMatchPredictionsInGroup(userToken, group1Id)
            matchPredictions.forEach {
                when (it.match.id) {
                    testMatch2Id -> {
                        Assertions.assertEquals(1, it.prediction!!.homeGoals)
                        Assertions.assertEquals(1, it.prediction!!.awayGoals)
                    }
                    testMatch3Id -> {
                        Assertions.assertEquals(2, it.prediction!!.homeGoals)
                        Assertions.assertEquals(0, it.prediction!!.awayGoals)
                    }
                }
            }
        }

        @Test
        @Order(18)
        fun `user checks predictions in group 2 and are still the same`() {
            val matchPredictions = client.fetchMatchPredictionsInGroup(userToken, group2Id)
            matchPredictions.forEach {
                when (it.match.id) {
                    testMatch2Id -> {
                        Assertions.assertEquals(1, it.prediction!!.homeGoals)
                        Assertions.assertEquals(1, it.prediction!!.awayGoals)
                    }
                    testMatch3Id -> {
                        Assertions.assertEquals(2, it.prediction!!.homeGoals)
                        Assertions.assertEquals(0, it.prediction!!.awayGoals)
                    }
                }
            }
        }

        @Test
        @Order(19)
        fun `user checks predictions in group 3 and are still the same`() {
            val matchPredictions = client.fetchMatchPredictionsInGroup(userToken, group3Id)
            matchPredictions.forEach {
                when (it.match.id) {
                    testMatch2Id -> {
                        Assertions.assertEquals(1, it.prediction!!.homeGoals)
                        Assertions.assertEquals(1, it.prediction!!.awayGoals)
                    }
                    testMatch3Id -> {
                        Assertions.assertEquals(2, it.prediction!!.homeGoals)
                        Assertions.assertEquals(0, it.prediction!!.awayGoals)
                    }
                }
            }
        }

        @Test
        @Order(20)
        fun `user unsets uniqueness for predictions`() {
            setPredictionUniqueness(false)
        }

        @Test
        @Order(21)
        fun `user submits predictions for match 3 in group 1`() {
            val matchPredictions = listOf(
                SubmitMatchPredictionRequest(matchId = testMatch3Id!!, homeGoals = 5, awayGoals = 0),
            )
            client.submitMatchPredictionsToGroup(userToken, group1Id, matchPredictions)
        }

        @Test
        @Order(22)
        fun `user checks predictions in group 1 and they were updated one more time`() {
            val matchPredictions = client.fetchMatchPredictionsInGroup(userToken, group1Id)
            matchPredictions.forEach {
                when (it.match.id) {
                    testMatch3Id -> {
                        Assertions.assertEquals(5, it.prediction!!.homeGoals)
                        Assertions.assertEquals(0, it.prediction!!.awayGoals)
                    }
                }
            }
        }

        @Test
        @Order(23)
        fun `user checks predictions in group 2 and they were not updated this time`() {
            val matchPredictions = client.fetchMatchPredictionsInGroup(userToken, group2Id)
            matchPredictions.forEach {
                when (it.match.id) {
                    testMatch3Id -> {
                        Assertions.assertEquals(2, it.prediction!!.homeGoals)
                        Assertions.assertEquals(0, it.prediction!!.awayGoals)
                    }
                }
            }
        }

        @Test
        @Order(24)
        fun `user checks predictions in group 3 and they were not updated this time`() {
            val matchPredictions = client.fetchMatchPredictionsInGroup(userToken, group3Id)
            matchPredictions.forEach {
                when (it.match.id) {
                    testMatch3Id -> {
                        Assertions.assertEquals(2, it.prediction!!.homeGoals)
                        Assertions.assertEquals(0, it.prediction!!.awayGoals)
                    }
                }
            }
        }
    }
}
