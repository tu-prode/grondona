package com.grondona.integration.utils

import com.fasterxml.jackson.databind.ObjectMapper
import com.grondona.consistsOf
import com.grondona.createTestingGroupRequest
import com.grondona.createTestingUserRequest
import com.grondona.model.MatchGroup
import com.grondona.model.MatchStage
import com.grondona.model.PlayerPosition
import com.grondona.model.Tournament
import com.grondona.model.UserPermissions
import com.grondona.model.dto.request.CreateMatchRequest
import com.grondona.model.dto.request.CreateMatchesRequest
import com.grondona.model.dto.request.CreatePlayerRequest
import com.grondona.model.dto.request.CreateTeamRequest
import com.grondona.model.dto.request.SubmitAwardPredictionRequest
import com.grondona.model.dto.request.SubmitBulkMatchPredictionsRequest
import com.grondona.model.dto.request.SubmitMatchPredictionRequest
import com.grondona.model.dto.request.UpdateMatchRequest
import com.grondona.model.dto.request.UpdateMatchesRequest
import com.grondona.model.dto.request.UpdateTournamentRequest
import com.grondona.model.dto.response.AuthenticatedUserResponse
import com.grondona.model.dto.response.AwardPredictionsResponse
import com.grondona.model.dto.response.SimpleMatchesResponse
import com.grondona.model.dto.response.GroupMatchPredictionsResponse
import com.grondona.model.dto.response.GroupResponse
import com.grondona.model.dto.response.MatchPredictionResponse
import com.grondona.model.dto.response.PlayerResponse
import com.grondona.model.dto.response.TeamResponse
import com.grondona.model.dto.response.TournamentMatchesResponse
import com.grondona.model.dto.response.TournamentResponse
import com.grondona.model.dto.response.UserResponse
import com.grondona.otherRandom
import com.grondona.randomString
import com.grondona.repository.TournamentRepository
import com.grondona.repository.UserRepository
import com.grondona.service.engine.WorldCupEngine
import org.junit.jupiter.api.Assertions.*
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import java.time.LocalDate

import java.time.ZonedDateTime
import java.util.UUID

class GrondonaClient(
    val mockMvc: MockMvc,
    val objectMapper: ObjectMapper,
) {

    companion object {
        // Matches internal.jobs.token in application-test.properties and the controller header name.
        private const val INTERNAL_JOBS_TOKEN_HEADER = "X-Cron-Token"
        private const val INTERNAL_JOBS_TOKEN = "internal-token"
    }

    data class MinimalMatch(
        val id: UUID, val code: String, val stage: MatchStage, val group: MatchGroup?,
        val homeId: UUID, val homeCode: String, val awayId: UUID, val awayCode: String
    )

    data class MinimalTeam(
        val id: UUID, val code: String
    )

    var teamsToCreate = 48
    var matchesToCreate = 72

    lateinit var adminId: UUID
    lateinit var adminToken: String

    var tournamentId: String? = null
    var userRepository: UserRepository? = null
    var tournamentRepository: TournamentRepository? = null

    val teams: MutableList<MinimalTeam> = mutableListOf()
    val matches: MutableList<MinimalMatch> = mutableListOf()

    val playerIds: MutableList<UUID> = mutableListOf()
    val youngsterIds: MutableList<UUID> = mutableListOf()
    val goalkeeperIds: MutableList<UUID> = mutableListOf()

    // Initialization

    fun withAdminToken(adminToken: String): GrondonaClient {
        this.adminToken = adminToken
        val rawResponse = mockMvc.perform(
            get("/api/users/me")
                .header("Authorization", "Bearer $adminToken")
        ).andExpect(status().isOk).andReturn()
        val response = objectMapper.readValue(rawResponse.response.contentAsString, UserResponse::class.java)
        this.adminId = response.id
        return this
    }

    fun withTournament(tournamentId: String): GrondonaClient {
        this.tournamentId = tournamentId
        return this
    }

    fun withConfiguration(totalTeams: Int = teamsToCreate, totalMatches: Int = matchesToCreate): GrondonaClient {
        this.teamsToCreate = totalTeams
        this.matchesToCreate = totalMatches
        return this
    }

    fun withRepositories(userRepository: UserRepository, tournamentRepository: TournamentRepository): GrondonaClient {
        this.userRepository = userRepository
        this.tournamentRepository = tournamentRepository
        return this
    }

    fun init() {
        val adminResult = mockMvc.perform(
            post("/api/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createTestingUserRequest()))
        ).andReturn()
        val adminResponse = objectMapper.readValue(adminResult.response.contentAsString, AuthenticatedUserResponse::class.java)
        val adminUser = userRepository!!.findById(adminResponse.userId).get()
        userRepository!!.save(adminUser.copy(permissions = UserPermissions.SUPERUSER))
        adminToken = adminResponse.token
        adminId = adminResponse.userId

        tournamentRepository!!.save(Tournament(id = WorldCupEngine.SYSTEM_TOURNAMENT_ID, name = "Testing Tournament for UserIntegrationTest"))
        assertTrue(tournamentRepository!!.existsById(WorldCupEngine.SYSTEM_TOURNAMENT_ID))
        tournamentId = WorldCupEngine.SYSTEM_TOURNAMENT_ID.toString()

        teams.addAll((1..teamsToCreate).map { index ->
            // Unique codes per team: team codes double as englishKey, and matches/odds are keyed by code,
            // so collisions (from random short codes) would mismatch matches and corrupt points.
            createTeam(adminToken, CreateTeamRequest(name = randomString(), code = "T%03d".format(index), icon = "---"))
        })

        val playerIds = teams.flatMap {
            listOf(
                createPlayer(
                    adminToken,
                    CreatePlayerRequest(
                        team = it.id,
                        name = randomString(),
                        position = PlayerPosition.MIDFIELDER,
                        birthdate = LocalDate.now().minusYears(27)
                    )
                ),
            )
        }

        val goalkeeperIds = teams.flatMap {
            listOf(
                createPlayer(
                    adminToken,
                    CreatePlayerRequest(
                        team = it.id,
                        name = randomString(),
                        position = PlayerPosition.GOALKEEPER,
                        birthdate = LocalDate.now().minusYears(32)
                    )
                ),
            )
        }

        val youngsterIds = teams.flatMap {
            listOf(
                createPlayer(
                    adminToken,
                    CreatePlayerRequest(
                        team = it.id,
                        name = randomString(),
                        position = PlayerPosition.FORWARD,
                        birthdate = LocalDate.now().minusYears(18)
                    )
                ),
            )
        }

        this.playerIds.addAll(playerIds + goalkeeperIds + youngsterIds)
        this.goalkeeperIds.addAll(goalkeeperIds)
        this.youngsterIds.addAll(youngsterIds)

        createMatches(matchesToCreate = teams.chunked(4).flatMapIndexed { groupIndex, groupTeams ->
            listOf(
                groupTeams[0] to groupTeams[1], groupTeams[2] to groupTeams[3], groupTeams[0] to groupTeams[3],
                groupTeams[2] to groupTeams[1], groupTeams[2] to groupTeams[0], groupTeams[1] to groupTeams[3],
            ).mapIndexed { matchIndex, (team1, team2) ->
                CreateMatchRequest(
                    code = (groupIndex * 6 + matchIndex).toString(), homeTeam = team1.id, awayTeam = team2.id,
                    stage = MatchStage.GROUP_STAGE, group = MatchGroup.entries[groupIndex], startedAt = ZonedDateTime.now().plusDays(1)
                )
            }
        })
    }

    // API methods

    fun createUser(): Pair<UUID, String> {
        val rawResponse = mockMvc.perform(
            post("/api/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createTestingUserRequest()))
        ).andExpect(status().isCreated).andReturn()
        val response = objectMapper.readValue(rawResponse.response.contentAsString, AuthenticatedUserResponse::class.java)
        return Pair(response.userId, response.token)
    }

    fun createGroup(token: String? = null): UUID {
        val rawResponse = mockMvc.perform(
            post("/api/tournaments/{tournamentId}/groups", tournamentId)
                .header("Authorization", "Bearer ${token ?: adminToken}")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createTestingGroupRequest()))
        ).andExpect(status().isCreated).andReturn()
        return objectMapper.readValue(rawResponse.response.contentAsString, GroupResponse::class.java).id
    }

    private fun createPlayer(token: String? = null, request: CreatePlayerRequest): UUID {
        val rawResponse = mockMvc.perform(
            post("/api/tournaments/{tournamentId}/players", tournamentId)
                .header("Authorization", "Bearer ${token ?: adminToken}")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        ).andExpect(status().isCreated).andReturn()
        return objectMapper.readValue(rawResponse.response.contentAsString, PlayerResponse::class.java).id
    }

    private fun createTeam(token: String? = null, request: CreateTeamRequest): MinimalTeam {
        val rawResponse = mockMvc.perform(
            post("/api/tournaments/{tournamentId}/teams", tournamentId)
                .header("Authorization", "Bearer ${token ?: adminToken}")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        ).andExpect(status().isCreated).andReturn()
        val teamResponse = objectMapper.readValue(rawResponse.response.contentAsString, TeamResponse::class.java)
        return MinimalTeam(id = teamResponse.id, code = teamResponse.code)
    }

    fun runStatusUpdateJob() {
        mockMvc.perform(
            post("/internal/jobs/matches/status")
                .header(INTERNAL_JOBS_TOKEN_HEADER, INTERNAL_JOBS_TOKEN)
        ).andExpect(status().isOk)
    }

    fun runQuotasUpdateJob() {
        mockMvc.perform(
            post("/internal/jobs/matches/quotas")
                .header(INTERNAL_JOBS_TOKEN_HEADER, INTERNAL_JOBS_TOKEN)
        ).andExpect(status().isOk)
    }

    fun syncMatches(token: String? = null) {
        val rawResponse = mockMvc.perform(
            get("/api/tournaments/{tournamentId}/matches", tournamentId)
                .header("Authorization", "Bearer ${token ?: adminToken}")
        ).andExpect(status().isOk).andReturn()
        val response = objectMapper.readValue(rawResponse.response.contentAsString, TournamentMatchesResponse::class.java)
        matches.clear()
        matches.addAll(
            (response.pastMatches + response.liveMatches + response.nextMatches).map {
                MinimalMatch(
                    id = it.id, code = it.code, stage = it.stage, group = it.group,
                    homeId = it.homeTeam.id, homeCode = it.homeTeam.code,
                    awayId = it.awayTeam.id, awayCode = it.awayTeam.code,
                )
            }
        )
    }

    fun createMatches(token: String? = null, matchesToCreate: List<CreateMatchRequest>): List<MinimalMatch> {
        val rawResponse = mockMvc.perform(
            post("/api/tournaments/{tournamentId}/matches", tournamentId)
                .header("Authorization", "Bearer ${token ?: adminToken}")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(CreateMatchesRequest(matches = matchesToCreate)))
        ).andExpect(status().isCreated).andReturn()
        val newMatches = objectMapper.readValue(rawResponse.response.contentAsString, SimpleMatchesResponse::class.java)
            .matches.map {
                MinimalMatch(
                    id = it.id, code = it.code, stage = it.stage, group = it.group,
                    homeId = it.homeTeam.id, homeCode = it.homeTeam.code,
                    awayId = it.awayTeam.id, awayCode = it.awayTeam.code,
                )
            }
        matches.addAll(newMatches)
        return newMatches
    }

    fun updateMatches(token: String? = null, matchesToUpdate: List<UpdateMatchRequest>): List<MinimalMatch> {
        val rawResponse = mockMvc.perform(
            put("/api/tournaments/{tournamentId}/matches", tournamentId)
                .header("Authorization", "Bearer ${token ?: adminToken}")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(UpdateMatchesRequest(matchesToUpdate)))
        ).andExpect(status().isOk).andReturn()
        val newMatches = objectMapper.readValue(rawResponse.response.contentAsString, SimpleMatchesResponse::class.java)
            .matches.map {
                MinimalMatch(
                    id = it.id, code = it.code, stage = it.stage, group = it.group,
                    homeId = it.homeTeam.id, homeCode = it.homeTeam.code,
                    awayId = it.awayTeam.id, awayCode = it.awayTeam.code,
                )
            }
        syncMatches()
        return newMatches
    }

    fun joinGroup(token: String?, groupId: UUID?) {
        mockMvc.perform(
            post("/api/tournaments/{tournamentId}/groups/{groupId}/join", tournamentId, groupId.toString())
                .header("Authorization", "Bearer $token")
        ).andExpect(status().isCreated)
    }

    fun fetchGroup(token: String?, groupId: UUID?): GroupResponse {
        val rawResponse = mockMvc.perform(
            get("/api/tournaments/{tournamentId}/groups/{groupId}", tournamentId, groupId)
                .header("Authorization", "Bearer $token")
        ).andExpect(status().isOk).andReturn()
        return objectMapper.readValue(rawResponse.response.contentAsString, GroupResponse::class.java)
    }

    fun updateTournament(token: String? = null, request: UpdateTournamentRequest): TournamentResponse {
        val rawResponse = mockMvc.perform(
            patch("/api/tournaments/{tournamentId}", tournamentId)
                .header("Authorization", "Bearer ${token ?: adminToken}")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        ).andExpect(status().isOk).andReturn()
        return objectMapper.readValue(rawResponse.response.contentAsString, TournamentResponse::class.java)
    }

    fun fetchMatchPredictionsInGroup(token: String?, groupId: UUID?): List<MatchPredictionResponse> {
        val rawResponse = mockMvc.perform(
            get("/api/tournaments/{tournamentId}/groups/{groupId}/predictions/matches/me", tournamentId, groupId.toString())
                .header("Authorization", "Bearer $token")
        ).andExpect(status().isOk).andReturn()

        return objectMapper.readValue(rawResponse.response.contentAsString, GroupMatchPredictionsResponse::class.java).predictions
    }

    fun fetchAwardPredictionsInGroup(token: String?, groupId: UUID?): AwardPredictionsResponse {
        val rawResponse = mockMvc.perform(
            get("/api/tournaments/{tournamentId}/groups/{groupId}/predictions/awards/me", tournamentId, groupId.toString())
                .header("Authorization", "Bearer $token")
        ).andExpect(status().isOk).andReturn()

        return objectMapper.readValue(rawResponse.response.contentAsString, AwardPredictionsResponse::class.java)
    }

    fun submitMatchPredictionsToGroup(
        token: String?,
        groupId: UUID?,
        matchPredictions: List<SubmitMatchPredictionRequest>,
        withAssertions: Boolean = false
    ) {
        val request = SubmitBulkMatchPredictionsRequest(predictions = matchPredictions)
        val rawResponse = mockMvc.perform(
            post("/api/tournaments/{tournamentId}/groups/{groupId}/predictions/matches", tournamentId, groupId.toString())
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        ).andExpect(status().isCreated).andReturn()

        if (withAssertions) {
            val response = objectMapper.readValue(rawResponse.response.contentAsString, GroupMatchPredictionsResponse::class.java)
            val predictionsPerMatch = response.predictions.groupBy { it.match.id }.mapValues { (_, value) -> value.first() }
            matchPredictions.forEach {
                val submittedPrediction = predictionsPerMatch[it.matchId]!!
                assertEquals(it.homeGoals, submittedPrediction.prediction!!.homeGoals)
                assertEquals(it.awayGoals, submittedPrediction.prediction!!.awayGoals)
            }
        }
    }

    fun submitAwardPredictionsToGroup(
        token: String?,
        groupId: UUID?,
        awardPredictions: SubmitAwardPredictionRequest,
        withAssertions: Boolean = false
    ) {
        val rawResponse = mockMvc.perform(
            post("/api/tournaments/{tournamentId}/groups/{groupId}/predictions/awards", tournamentId, groupId.toString())
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(awardPredictions))
        ).andExpect(status().isCreated).andReturn()

        if (withAssertions) {
            val response = objectMapper.readValue(rawResponse.response.contentAsString, AwardPredictionsResponse::class.java)
            assertTrue(response.champions.map { it.id }.consistsOf(awardPredictions.champions))
            assertTrue(response.topScorers.map { it.id }.consistsOf(awardPredictions.topScorers))
            assertTrue(response.bestPlayers.map { it.id }.consistsOf(awardPredictions.bestPlayers))
            assertTrue(response.bestGoalkeepers.map { it.id }.consistsOf(awardPredictions.bestGoalkeepers))
            assertTrue(response.bestYoungPlayers.map { it.id }.consistsOf(awardPredictions.bestYoungPlayers))
        }
    }

}
