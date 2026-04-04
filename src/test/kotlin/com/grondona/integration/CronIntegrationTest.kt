package com.grondona.integration

import com.fasterxml.jackson.databind.ObjectMapper
import com.grondona.client.MatchClient
import com.grondona.model.Group
import org.springframework.jdbc.core.JdbcTemplate
import java.time.LocalDateTime
import com.grondona.model.GroupUser
import com.grondona.model.Match
import com.grondona.model.MatchStatus
import com.grondona.model.Prediction
import com.grondona.model.PredictionStatus
import com.grondona.model.Team
import com.grondona.model.Tournament
import com.grondona.model.User
import com.grondona.model.UserPermissions
import com.grondona.randomString
import com.grondona.repository.GroupRepository
import com.grondona.repository.MatchRepository
import com.grondona.repository.MembershipRepository
import com.grondona.repository.PredictionRepository
import com.grondona.repository.TeamRepository
import com.grondona.repository.TournamentRepository
import com.grondona.repository.UserRepository
import com.grondona.utils.WorldCupEngine
import com.grondona.utils.hashMD5
import com.grondona.utils.hashSHA256
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class CronIntegrationTest {

    companion object {
        // Started during class loading so its port is available for @DynamicPropertySource
        private val mockWebServer = MockWebServer().apply { start() }

        @JvmStatic
        @DynamicPropertySource
        fun overrideProperties(registry: DynamicPropertyRegistry) {
            registry.add("external.api.base-url") { "http://localhost:${mockWebServer.port}" }
            // Use a dedicated H2 database to avoid DDL conflicts with other test contexts
            registry.add("spring.datasource.url") {
                "jdbc:h2:mem:crontest;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;NON_KEYWORDS=GROUPS"
            }
        }
    }

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var objectMapper: ObjectMapper
    @Autowired private lateinit var matchClient: MatchClient
    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var groupRepository: GroupRepository
    @Autowired private lateinit var matchRepository: MatchRepository
    @Autowired private lateinit var teamRepository: TeamRepository
    @Autowired private lateinit var tournamentRepository: TournamentRepository
    @Autowired private lateinit var jdbcTemplate: JdbcTemplate
    @Autowired private lateinit var membershipRepository: MembershipRepository
    @Autowired private lateinit var predictionRepository: PredictionRepository

    private val apiKey = "apiKey"

    private var authToken: String? = null
    private var testGroupId: String? = null
    private var testTournamentId: String? = null
    private var testUserToken1: String? = null
    private var testUserToken2: String? = null
    private var testUser1Id: UUID? = null
    private var testUser2Id: UUID? = null
    private var testMatch1Id: UUID? = null
    private var testMatch2Id: UUID? = null

    @BeforeAll
    fun setUp() {
        predictionRepository.deleteAll()
        matchRepository.deleteAll()
        teamRepository.deleteAll()
        membershipRepository.deleteAll()
        groupRepository.deleteAll()
        tournamentRepository.deleteAll()
        userRepository.deleteAll()

        // Insert the World Cup tournament directly via JDBC to guarantee the specific UUID is used.
        // Cannot use tournamentRepository.save() because Hibernate 6 with @GeneratedValue(UUID)
        // overrides any provided ID via reflection, ignoring our WorldCupEngine.SYSTEM_TOURNAMENT_ID.
        val now = LocalDateTime.now()
        jdbcTemplate.update(
            "INSERT INTO tournaments (id, name, status, created_at, updated_at) VALUES (?, ?, ?, ?, ?)",
            WorldCupEngine.SYSTEM_TOURNAMENT_ID.toString(), randomString("World Cup"), "NOT_STARTED", now, now
        )
        val testTournament = tournamentRepository.findById(WorldCupEngine.SYSTEM_TOURNAMENT_ID).get()
        testTournamentId = testTournament.id.toString()

        // Create CRON user (no explicit ID; capture the returned saved entity)
        userRepository.save(User(
            username = randomString("CronUser-"), fullname = randomString("CronUser-"),
            email = "${randomString("cron")}@example.com",
            passwordHash = hashSHA256(apiKey), permissions = UserPermissions.CRON
        ))

        // Create a group
        val testGroup = groupRepository.save(Group(tournament = testTournament, name = randomString("Group")))
        testGroupId = testGroup.id.toString()

        // Create regular users and capture their generated IDs
        val savedUser1 = userRepository.save(User(
            fullname = randomString("User1"), username = randomString("User1"),
            email = "${randomString("user1")}@test.com", passwordHash = hashMD5("pass")
        ))
        val savedUser2 = userRepository.save(User(
            fullname = randomString("User2"), username = randomString("User2"),
            email = "${randomString("user2")}@test.com", passwordHash = hashMD5("pass")
        ))
        testUser1Id = savedUser1.id
        testUser2Id = savedUser2.id

        // Create user-group memberships
        membershipRepository.save(GroupUser(user = savedUser1, group = testGroup))
        membershipRepository.save(GroupUser(user = savedUser2, group = testGroup))

        // Persist teams first, then create matches referencing them
        val teamQAT = teamRepository.save(Team(name = "Qatar", code = "QAT", tournament = testTournament))
        val teamECU = teamRepository.save(Team(name = "Ecuador", code = "ECU", tournament = testTournament))
        val teamNED = teamRepository.save(Team(name = "Países Bajos", code = "NED", tournament = testTournament))
        val teamSEN = teamRepository.save(Team(name = "Senegal", code = "SEN", tournament = testTournament))

        val savedMatch1 = matchRepository.save(Match(
            tournament = testTournament, code = "FG01", homeTeam = teamQAT, awayTeam = teamECU
        ))
        val savedMatch2 = matchRepository.save(Match(
            tournament = testTournament, code = "FG02", homeTeam = teamNED, awayTeam = teamSEN
        ))
        testMatch1Id = savedMatch1.id
        testMatch2Id = savedMatch2.id

        // user1 predicts exact final score (0-2) for both matches → will be CORRECT
        // user2 predicts correct outcome but wrong score (0-1) for both matches → will be PARTIAL
        predictionRepository.save(Prediction(group = testGroup, user = savedUser1, match = savedMatch1, homeGoals = 0, awayGoals = 2))
        predictionRepository.save(Prediction(group = testGroup, user = savedUser1, match = savedMatch2, homeGoals = 0, awayGoals = 2))
        predictionRepository.save(Prediction(group = testGroup, user = savedUser2, match = savedMatch1, homeGoals = 0, awayGoals = 1))
        predictionRepository.save(Prediction(group = testGroup, user = savedUser2, match = savedMatch2, homeGoals = 0, awayGoals = 1))
    }

    @AfterAll
    fun tearDown() {
        predictionRepository.deleteAll()
        matchRepository.deleteAll()
        teamRepository.deleteAll()
        membershipRepository.deleteAll()
        groupRepository.deleteAll()
        tournamentRepository.deleteAll()
        userRepository.deleteAll()
        mockWebServer.shutdown()
    }

    // Builds JSON for a single ExternalMatch using the snake_case field names that Spring's
    // SNAKE_CASE ObjectMapper expects when deserializing in the WebClient codec
    private fun externalMatchJson(
        home: String, away: String,
        homeGoals: Int = 0, awayGoals: Int = 0,
        minutes: Int = 0, half: Int = 0,
        status: String,
        homeOdds: Float = 1f, tieOdds: Float = 1f, awayOdds: Float = 1f,
    ) = """{"home":"$home","away":"$away","homeGoals":$homeGoals,"awayGoals":$awayGoals,"minutes":$minutes,"half":$half,"status":"$status","homeOdds":$homeOdds,"tieOdds":$tieOdds,"awayOdds":$awayOdds}"""

    private fun enqueueMatchesResponse(vararg matchJsons: String) {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("[${matchJsons.joinToString(",")}]")
        )
    }

    private fun cronRequestBody() = """{"api_key":"$apiKey","tournament_id":"$testTournamentId"}"""

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    @TestMethodOrder(MethodOrderer.OrderAnnotation::class)
    inner class CronQuotasUpdatesTests {

        @Test
        @Order(1)
        fun `first quota update sets initial betting odds for both matches`() {
            enqueueMatchesResponse(
                externalMatchJson("Qatar", "Ecuador", status = "TO_START",
                    homeOdds = 2.5f, tieOdds = 3.0f, awayOdds = 2.8f),
                externalMatchJson("Países Bajos", "Senegal", status = "TO_START",
                    homeOdds = 1.5f, tieOdds = 3.5f, awayOdds = 2.0f),
            )

            mockMvc.perform(
                post("/cron/matches/quotas")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(cronRequestBody())
            ).andExpect(status().isNoContent)

            val match1 = matchRepository.findById(testMatch1Id!!).get()
            val match2 = matchRepository.findById(testMatch2Id!!).get()

            assertEquals(MatchStatus.NOT_STARTED, match1.status)
            assertEquals(2.5f, match1.homeQuota)
            assertEquals(3.0f, match1.tieQuota)
            assertEquals(2.8f, match1.awayQuota)

            assertEquals(MatchStatus.NOT_STARTED, match2.status)
            assertEquals(1.5f, match2.homeQuota)
            assertEquals(3.5f, match2.tieQuota)
            assertEquals(2.0f, match2.awayQuota)
        }

        @Test
        @Order(2)
        fun `second quota update reflects odds movement for both matches`() {
            enqueueMatchesResponse(
                externalMatchJson("Qatar", "Ecuador", status = "TO_START",
                    homeOdds = 2.2f, tieOdds = 3.2f, awayOdds = 3.0f),
                externalMatchJson("Países Bajos", "Senegal", status = "TO_START",
                    homeOdds = 1.4f, tieOdds = 3.6f, awayOdds = 2.2f),
            )

            mockMvc.perform(
                post("/cron/matches/quotas")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(cronRequestBody())
            ).andExpect(status().isNoContent)

            val match1 = matchRepository.findById(testMatch1Id!!).get()
            val match2 = matchRepository.findById(testMatch2Id!!).get()

            assertEquals(MatchStatus.NOT_STARTED, match1.status)
            assertEquals(2.2f, match1.homeQuota)
            assertEquals(3.2f, match1.tieQuota)
            assertEquals(3.0f, match1.awayQuota)

            assertEquals(MatchStatus.NOT_STARTED, match2.status)
            assertEquals(1.4f, match2.homeQuota)
            assertEquals(3.6f, match2.tieQuota)
            assertEquals(2.2f, match2.awayQuota)
        }

        @Test
        @Order(3)
        fun `third quota update sets final pre-match odds before matches are locked`() {
            enqueueMatchesResponse(
                externalMatchJson("Qatar", "Ecuador", status = "TO_START",
                    homeOdds = 2.0f, tieOdds = 3.5f, awayOdds = 3.2f),
                externalMatchJson("Países Bajos", "Senegal", status = "TO_START",
                    homeOdds = 1.3f, tieOdds = 3.8f, awayOdds = 2.5f),
            )

            mockMvc.perform(
                post("/cron/matches/quotas")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(cronRequestBody())
            ).andExpect(status().isNoContent)

            val match1 = matchRepository.findById(testMatch1Id!!).get()
            val match2 = matchRepository.findById(testMatch2Id!!).get()

            assertEquals(MatchStatus.NOT_STARTED, match1.status)
            assertEquals(2.0f, match1.homeQuota)
            assertEquals(3.5f, match1.tieQuota)
            assertEquals(3.2f, match1.awayQuota)

            assertEquals(MatchStatus.NOT_STARTED, match2.status)
            assertEquals(1.3f, match2.homeQuota)
            assertEquals(3.8f, match2.tieQuota)
            assertEquals(2.5f, match2.awayQuota)
        }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    @TestMethodOrder(MethodOrderer.OrderAnnotation::class)
    inner class CronStatusUpdatesTests {

        @Test
        @Order(1)
        fun `15 minutes before kick-off matches remain not started and predictions stay pending`() {
            enqueueMatchesResponse(
                externalMatchJson("Qatar", "Ecuador", status = "TO_START"),
                externalMatchJson("Países Bajos", "Senegal", status = "TO_START"),
            )

            mockMvc.perform(
                post("/cron/matches/status")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(cronRequestBody())
            ).andExpect(status().isNoContent)

            val match1 = matchRepository.findById(testMatch1Id!!).get()
            val match2 = matchRepository.findById(testMatch2Id!!).get()
            assertEquals(MatchStatus.NOT_STARTED, match1.status)
            assertEquals(MatchStatus.NOT_STARTED, match2.status)

            val predictions = predictionRepository.findAll()
            assertTrue(predictions.all { it.status == PredictionStatus.PENDING })
        }

        @Test
        @Order(2)
        fun `first half 15 minutes both matches go in progress at 0-0 and predictions stay pending`() {
            enqueueMatchesResponse(
                externalMatchJson("Qatar", "Ecuador", minutes = 15, half = 1, status = "IN_PLAY"),
                externalMatchJson("Países Bajos", "Senegal", minutes = 15, half = 1, status = "IN_PLAY"),
            )

            mockMvc.perform(
                post("/cron/matches/status")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(cronRequestBody())
            ).andExpect(status().isNoContent)

            val match1 = matchRepository.findById(testMatch1Id!!).get()
            val match2 = matchRepository.findById(testMatch2Id!!).get()

            assertEquals(MatchStatus.IN_PROGRESS, match1.status)
            assertEquals("15' PT", match1.substatus)
            assertEquals(0, match1.homeGoals)
            assertEquals(0, match1.awayGoals)

            assertEquals(MatchStatus.IN_PROGRESS, match2.status)
            assertEquals("15' PT", match2.substatus)
            assertEquals(0, match2.homeGoals)
            assertEquals(0, match2.awayGoals)

            val predictions = predictionRepository.findAll()
            assertTrue(predictions.all { it.status == PredictionStatus.PENDING })
        }

        @Test
        @Order(3)
        fun `half time both matches still 0-0 and predictions remain pending`() {
            enqueueMatchesResponse(
                externalMatchJson("Qatar", "Ecuador", minutes = 45, half = 1, status = "IN_PLAY"),
                externalMatchJson("Países Bajos", "Senegal", minutes = 45, half = 1, status = "IN_PLAY"),
            )

            mockMvc.perform(
                post("/cron/matches/status")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(cronRequestBody())
            ).andExpect(status().isNoContent)

            val match1 = matchRepository.findById(testMatch1Id!!).get()
            val match2 = matchRepository.findById(testMatch2Id!!).get()

            assertEquals(MatchStatus.IN_PROGRESS, match1.status)
            assertEquals("45' PT", match1.substatus)
            assertEquals(0, match1.homeGoals)
            assertEquals(0, match1.awayGoals)

            assertEquals(MatchStatus.IN_PROGRESS, match2.status)
            assertEquals("45' PT", match2.substatus)
            assertEquals(0, match2.homeGoals)
            assertEquals(0, match2.awayGoals)

            val predictions = predictionRepository.findAll()
            assertTrue(predictions.all { it.status == PredictionStatus.PENDING })
        }

        @Test
        @Order(4)
        fun `second half 30 minutes both matches in progress with away team leading 0-2`() {
            enqueueMatchesResponse(
                externalMatchJson("Qatar", "Ecuador",
                    homeGoals = 0, awayGoals = 2, minutes = 75, half = 2, status = "IN_PLAY"),
                externalMatchJson("Países Bajos", "Senegal",
                    homeGoals = 0, awayGoals = 2, minutes = 75, half = 2, status = "IN_PLAY"),
            )

            mockMvc.perform(
                post("/cron/matches/status")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(cronRequestBody())
            ).andExpect(status().isNoContent)

            val match1 = matchRepository.findById(testMatch1Id!!).get()
            val match2 = matchRepository.findById(testMatch2Id!!).get()

            assertEquals(MatchStatus.IN_PROGRESS, match1.status)
            assertEquals("30' ST", match1.substatus)
            assertEquals(0, match1.homeGoals)
            assertEquals(2, match1.awayGoals)

            assertEquals(MatchStatus.IN_PROGRESS, match2.status)
            assertEquals("30' ST", match2.substatus)
            assertEquals(0, match2.homeGoals)
            assertEquals(2, match2.awayGoals)

            val predictions = predictionRepository.findAll()
            assertTrue(predictions.all { it.status == PredictionStatus.PENDING })
        }

        @Test
        @Order(5)
        fun `full time both matches end 0-2 predictions are resolved and standings are updated`() {
            enqueueMatchesResponse(
                externalMatchJson("Qatar", "Ecuador",
                    homeGoals = 0, awayGoals = 2, minutes = 90, half = 2, status = "COMPLETED"),
                externalMatchJson("Países Bajos", "Senegal",
                    homeGoals = 0, awayGoals = 2, minutes = 90, half = 2, status = "COMPLETED"),
            )

            mockMvc.perform(
                post("/cron/matches/status")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(cronRequestBody())
            ).andExpect(status().isNoContent)

            // Both matches should now be FINISHED with final score 0-2
            val match1 = matchRepository.findById(testMatch1Id!!).get()
            val match2 = matchRepository.findById(testMatch2Id!!).get()

            assertEquals(MatchStatus.FINISHED, match1.status)
            assertEquals("FINALIZADO", match1.substatus)
            assertEquals(0, match1.homeGoals)
            assertEquals(2, match1.awayGoals)

            assertEquals(MatchStatus.FINISHED, match2.status)
            assertEquals("FINALIZADO", match2.substatus)
            assertEquals(0, match2.homeGoals)
            assertEquals(2, match2.awayGoals)

            // user1 predicted exact score 0-2 for both → CORRECT on both
            // user2 predicted 0-1 (away wins, wrong score) → PARTIAL on both
            val allPredictions = predictionRepository.findAll()
            val user1Predictions = allPredictions.filter { it.user.id == testUser1Id }
            val user2Predictions = allPredictions.filter { it.user.id == testUser2Id }

            assertEquals(2, user1Predictions.size)
            assertTrue(user1Predictions.all { it.status == PredictionStatus.CORRECT })

            assertEquals(2, user2Predictions.size)
            assertTrue(user2Predictions.all { it.status == PredictionStatus.PARTIAL })

            // Standings after both matches finish:
            // Final awayQuota from quota test 3: match1 = 3.2, match2 = 2.5
            // user1: CORRECT × 2 → (3 + 3.2) + (3 + 2.5) ≈ 11.7 pts  → rank 1
            // user2: PARTIAL × 2 → (1 + 3.2) + (1 + 2.5) ≈ 7.7 pts   → rank 2
            val members = membershipRepository.findByGroupId(UUID.fromString(testGroupId!!))
            val member1 = members.first { it.user.id == testUser1Id }
            val member2 = members.first { it.user.id == testUser2Id }

            assertEquals(1, member1.rank)
            assertEquals(2, member2.rank)

            assertEquals(2, member1.amountCorrect)
            assertEquals(0, member1.amountPartial)
            assertEquals(0, member2.amountCorrect)
            assertEquals(2, member2.amountPartial)

            assertTrue(member1.points > member2.points)
            assertEquals(11.7, member1.points.toDouble(), 0.1)
            assertEquals(7.7, member2.points.toDouble(), 0.1)

            assertEquals(listOf(PredictionStatus.CORRECT, PredictionStatus.CORRECT), member1.lastPredictions)
            assertEquals(listOf(PredictionStatus.PARTIAL, PredictionStatus.PARTIAL), member2.lastPredictions)
        }
    }
}
