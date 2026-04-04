package com.grondona.integration

import com.fasterxml.jackson.databind.ObjectMapper
import com.grondona.createTestingTournamentRequest
import com.grondona.createTestingUserRequest
import com.grondona.model.UserPermissions
import com.grondona.model.dto.request.SubmitBulkPredictionsRequest
import com.grondona.model.dto.request.SubmitPredictionRequest
import com.grondona.model.dto.request.CreateGroupRequest
import com.grondona.model.dto.response.AuthenticatedUserResponse
import com.grondona.model.dto.response.GroupResponse
import com.grondona.model.dto.response.TournamentResponse
import com.grondona.repository.GroupRepository
import com.grondona.repository.MembershipRepository
import com.grondona.repository.TournamentRepository
import com.grondona.repository.UserRepository
import org.junit.jupiter.api.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PredictionIntegrationTest {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var objectMapper: ObjectMapper
    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var tournamentRepository: TournamentRepository
    @Autowired private lateinit var groupRepository: GroupRepository
    @Autowired private lateinit var membershipRepository: MembershipRepository

    private var memberToken: String? = null
    private var nonMemberToken: String? = null
    private var tournamentId: String? = null
    private var groupId: String? = null

    @BeforeAll
    fun setUp() {
        membershipRepository.deleteAll()
        groupRepository.deleteAll()
        tournamentRepository.deleteAll()
        userRepository.deleteAll()

        // Create superuser for setup
        val adminResult = mockMvc.perform(
            post("/api/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createTestingUserRequest(username = "admin")))
        ).andReturn()
        val adminResponse = objectMapper.readValue(adminResult.response.contentAsString, AuthenticatedUserResponse::class.java)
        val adminUser = userRepository.findById(adminResponse.userId).get()
        adminUser.permissions = UserPermissions.SUPERUSER
        userRepository.save(adminUser)
        val adminToken = adminResponse.token

        // Create tournament
        val tournamentResult = mockMvc.perform(
            post("/api/tournaments")
                .header("Authorization", "Bearer $adminToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createTestingTournamentRequest(name = "Prediction Test")))
        ).andReturn()
        tournamentId = objectMapper.readValue(tournamentResult.response.contentAsString, TournamentResponse::class.java).id.toString()

        // Create group
        val groupResult = mockMvc.perform(
            post("/api/tournaments/{tournamentId}/groups", tournamentId)
                .header("Authorization", "Bearer $adminToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(CreateGroupRequest(name = "Prediction Group", isPrivate = false, maxMembers = 10)))
        ).andReturn()
        groupId = objectMapper.readValue(groupResult.response.contentAsString, GroupResponse::class.java).id.toString()

        // Create member user and join group
        val memberResult = mockMvc.perform(
            post("/api/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createTestingUserRequest(username = "member")))
        ).andReturn()
        memberToken = objectMapper.readValue(memberResult.response.contentAsString, AuthenticatedUserResponse::class.java).token

        mockMvc.perform(
            post("/api/tournaments/{tournamentId}/groups/{groupId}/join", tournamentId, groupId)
                .header("Authorization", "Bearer $memberToken")
        ).andExpect(status().isCreated)

        // Create non-member user
        val nonMemberResult = mockMvc.perform(
            post("/api/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createTestingUserRequest(username = "nonmember")))
        ).andReturn()
        nonMemberToken = objectMapper.readValue(nonMemberResult.response.contentAsString, AuthenticatedUserResponse::class.java).token
    }

    @AfterAll
    fun tearDown() {
        membershipRepository.deleteAll()
        groupRepository.deleteAll()
        tournamentRepository.deleteAll()
        userRepository.deleteAll()
    }

    @Nested
    inner class SubmitPredictionAuthTests {

        private val matchId = UUID.randomUUID()
        private val validRequest = SubmitPredictionRequest(matchId = matchId, homeGoals = 1, awayGoals = 0)

        @Test
        fun `should return 401 when submitting prediction without authentication`() {
            mockMvc.perform(
                post("/api/tournaments/{tournamentId}/groups/{groupId}/predictions/matches/{matchId}",
                    tournamentId, groupId, matchId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(validRequest))
            )
                .andExpect(status().isUnauthorized)
        }

        @Test
        fun `should return 403 when submitting prediction as non-member`() {
            mockMvc.perform(
                post("/api/tournaments/{tournamentId}/groups/{groupId}/predictions/matches/{matchId}",
                    tournamentId, groupId, matchId)
                    .header("Authorization", "Bearer $nonMemberToken")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(validRequest))
            )
                .andExpect(status().isForbidden)
                .andExpect(jsonPath("$.message").value("User doesn't belong to the group"))
        }

        @Test
        fun `should return 404 when group does not exist`() {
            val nonExistentGroupId = UUID.randomUUID()
            mockMvc.perform(
                post("/api/tournaments/{tournamentId}/groups/{groupId}/predictions/matches/{matchId}",
                    tournamentId, nonExistentGroupId, matchId)
                    .header("Authorization", "Bearer $memberToken")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(validRequest))
            )
                .andExpect(status().isNotFound)
                .andExpect(jsonPath("$.message").value("Group not found"))
        }

        @Test
        fun `should return 404 when match does not exist`() {
            val nonExistentMatchId = UUID.randomUUID()
            val request = SubmitPredictionRequest(matchId = nonExistentMatchId, homeGoals = 1, awayGoals = 0)
            mockMvc.perform(
                post("/api/tournaments/{tournamentId}/groups/{groupId}/predictions/matches/{matchId}",
                    tournamentId, groupId, nonExistentMatchId)
                    .header("Authorization", "Bearer $memberToken")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isNotFound)
                .andExpect(jsonPath("$.message").value("Match not found"))
        }

        @Test
        fun `should return 400 when homeGoals is negative`() {
            val invalidRequest = SubmitPredictionRequest(matchId = matchId, homeGoals = -1, awayGoals = 0)
            mockMvc.perform(
                post("/api/tournaments/{tournamentId}/groups/{groupId}/predictions/matches/{matchId}",
                    tournamentId, groupId, matchId)
                    .header("Authorization", "Bearer $memberToken")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(invalidRequest))
            )
                .andExpect(status().isBadRequest)
        }
    }

    @Nested
    inner class SubmitBulkPredictionsAuthTests {

        private val validRequest = SubmitBulkPredictionsRequest(
            predictions = listOf(SubmitPredictionRequest(matchId = UUID.randomUUID(), homeGoals = 1, awayGoals = 0))
        )

        @Test
        fun `should return 401 when submitting bulk predictions without authentication`() {
            mockMvc.perform(
                post("/api/tournaments/{tournamentId}/groups/{groupId}/predictions", tournamentId, groupId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(validRequest))
            )
                .andExpect(status().isUnauthorized)
        }

        @Test
        fun `should return 403 when submitting bulk predictions as non-member`() {
            mockMvc.perform(
                post("/api/tournaments/{tournamentId}/groups/{groupId}/predictions", tournamentId, groupId)
                    .header("Authorization", "Bearer $nonMemberToken")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(validRequest))
            )
                .andExpect(status().isForbidden)
                .andExpect(jsonPath("$.message").value("User doesn't belong to the group"))
        }

        @Test
        fun `should return 404 when group does not exist for bulk submission`() {
            val nonExistentGroupId = UUID.randomUUID()
            mockMvc.perform(
                post("/api/tournaments/{tournamentId}/groups/{groupId}/predictions", tournamentId, nonExistentGroupId)
                    .header("Authorization", "Bearer $memberToken")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(validRequest))
            )
                .andExpect(status().isNotFound)
                .andExpect(jsonPath("$.message").value("Group not found"))
        }

        @Test
        fun `should return 400 when bulk predictions list is empty`() {
            val emptyRequest = SubmitBulkPredictionsRequest(predictions = emptyList())
            mockMvc.perform(
                post("/api/tournaments/{tournamentId}/groups/{groupId}/predictions", tournamentId, groupId)
                    .header("Authorization", "Bearer $memberToken")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(emptyRequest))
            )
                .andExpect(status().isBadRequest)
        }
    }

    @Nested
    inner class GetGroupUserPredictionsAuthTests {

        @Test
        fun `should return 401 when getting predictions without authentication`() {
            mockMvc.perform(
                get("/api/tournaments/{tournamentId}/groups/{groupId}/predictions", tournamentId, groupId)
            )
                .andExpect(status().isUnauthorized)
        }

        @Test
        fun `should return 403 when getting predictions as non-member`() {
            mockMvc.perform(
                get("/api/tournaments/{tournamentId}/groups/{groupId}/predictions", tournamentId, groupId)
                    .header("Authorization", "Bearer $nonMemberToken")
            )
                .andExpect(status().isForbidden)
                .andExpect(jsonPath("$.message").value("User doesn't belong to the group"))
        }

        @Test
        fun `should return 404 when group does not exist`() {
            val nonExistentGroupId = UUID.randomUUID()
            mockMvc.perform(
                get("/api/tournaments/{tournamentId}/groups/{groupId}/predictions", tournamentId, nonExistentGroupId)
                    .header("Authorization", "Bearer $memberToken")
            )
                .andExpect(status().isNotFound)
                .andExpect(jsonPath("$.message").value("Group not found"))
        }
    }

    @Nested
    inner class GetGroupMatchPredictionsAuthTests {

        private val matchId = UUID.randomUUID()

        @Test
        fun `should return 401 when getting match predictions without authentication`() {
            mockMvc.perform(
                get("/api/tournaments/{tournamentId}/groups/{groupId}/predictions/matches/{matchId}",
                    tournamentId, groupId, matchId)
            )
                .andExpect(status().isUnauthorized)
        }

        @Test
        fun `should return 403 when getting match predictions as non-member`() {
            mockMvc.perform(
                get("/api/tournaments/{tournamentId}/groups/{groupId}/predictions/matches/{matchId}",
                    tournamentId, groupId, matchId)
                    .header("Authorization", "Bearer $nonMemberToken")
            )
                .andExpect(status().isForbidden)
                .andExpect(jsonPath("$.message").value("User doesn't belong to the group"))
        }

        @Test
        fun `should return 404 when group does not exist`() {
            val nonExistentGroupId = UUID.randomUUID()
            mockMvc.perform(
                get("/api/tournaments/{tournamentId}/groups/{groupId}/predictions/matches/{matchId}",
                    tournamentId, nonExistentGroupId, matchId)
                    .header("Authorization", "Bearer $memberToken")
            )
                .andExpect(status().isNotFound)
                .andExpect(jsonPath("$.message").value("Group not found"))
        }

        @Test
        fun `should return 404 when match does not exist`() {
            val nonExistentMatchId = UUID.randomUUID()
            mockMvc.perform(
                get("/api/tournaments/{tournamentId}/groups/{groupId}/predictions/matches/{matchId}",
                    tournamentId, groupId, nonExistentMatchId)
                    .header("Authorization", "Bearer $memberToken")
            )
                .andExpect(status().isNotFound)
                .andExpect(jsonPath("$.message").value("Match not found"))
        }
    }
}
