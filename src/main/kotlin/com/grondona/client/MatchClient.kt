package com.grondona.client

import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming
import com.grondona.exception.ExternalServiceException
import com.grondona.model.ExternalMatch
import com.grondona.model.LOCAL
import com.grondona.model.MatchGroup
import com.grondona.model.MatchStage
import com.grondona.model.MatchStatus
import com.grondona.model.MatchSubstatus
import com.grondona.model.PROD
import com.grondona.model.TEST
import com.grondona.now
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Mono
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID

interface MatchClient {
    val matchesWebClient: WebClient

    fun buildRequest(): WebClient.RequestHeadersSpec<*>

    fun matchesResponseClass(): Class<*>

    fun parseMatchesResponse(body: Any?): List<ExternalMatch>

    fun onMatchesResponseReceived(body: Any?) {}

    fun getMatches(tournamentId: UUID): List<ExternalMatch> {
        return try {
            val body = buildRequest()
                .retrieve()
                .onStatus({ it.is4xxClientError }) { response ->
                    response.bodyToMono(String::class.java)
                        .flatMap { responseBody ->
                            logger.error("Error 4xx calling Matches API: $responseBody")
                            Mono.error(ExternalServiceException("Error 4xx calling Matches API: $responseBody"))
                        }
                }
                .onStatus({ it.is5xxServerError }) { response ->
                    response.bodyToMono(String::class.java)
                        .flatMap { responseBody ->
                            logger.error("Error 5xx calling Matches API: $responseBody")
                            Mono.error(ExternalServiceException("Error 5xx calling Matches API: $responseBody"))
                        }
                }
                .bodyToMono(matchesResponseClass())
                .block()
            onMatchesResponseReceived(body)
            parseMatchesResponse(body)
        } catch (ex: WebClientResponseException) {
            throw ExternalServiceException("HTTP error calling Matches API: ${ex.statusCode}", ex)
        } catch (ex: Exception) {
            throw ExternalServiceException("Unexpected error calling Matches API: ${ex.message}", ex)
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(MatchClient::class.java)
    }
}

@Component
@Profile(LOCAL, TEST)
class MocknaldoMatchClient(
    override val matchesWebClient: WebClient,
) : MatchClient {

    companion object {
        private const val MATCHES_PATH = "/matches"
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
    internal class Response(
        val current: LocalDateTime,
        val matches: List<Match>,
    ) {
        @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
        internal class Match(
            val code: String,
            val home: String,
            val away: String,
            val homeGoals: Int,
            val awayGoals: Int,
            val stage: String,
            val group: String? = null,
            val homePenalties: Int? = null,
            val awayPenalties: Int? = null,
            val minutes: Int,
            val half: Int,
            val status: String,
            val startedAt: ZonedDateTime,
            val endedAt: ZonedDateTime? = null,
        ) {
            internal enum class Status { TO_START, IN_PLAY, HALF_TIME, PENALTIES, COMPLETED }
            internal enum class Stage { GS, R32, R16, QF, SF, TP, F }
            internal enum class Group { A, B, C, D, E, F, G, H, I, J, K, L }

            fun toExternalMatch(): ExternalMatch? {
                var newHomeGoals = 0
                var newAwayGoals = 0
                var newStatus = MatchStatus.NOT_STARTED
                var newSubstatus: String? = null
                var newHomePenalties: Int? = null
                var newAwayPenalties: Int? = null
                var newFinishedAt: ZonedDateTime? = null

                when (status) {
                    Status.TO_START.name -> {
                        newStatus = MatchStatus.NOT_STARTED
                    }

                    Status.IN_PLAY.name -> {
                        newStatus = MatchStatus.IN_PROGRESS
                        newHomeGoals = homeGoals
                        newAwayGoals = awayGoals
                        newSubstatus = when {
                            half == 1 && minutes <= 45 -> "$minutes' PT"
                            half == 1 && minutes > 45 -> "45+${minutes - 45}' PT"
                            half == 2 && minutes <= 90 -> "${minutes - 45}' ST"
                            half == 2 && minutes > 90 -> "45+${minutes - 90}' ST"
                            half == 3 && minutes <= 105 -> "${minutes - 90}' PTE"
                            half == 3 && minutes > 105 -> "15+${minutes - 105}' PTE"
                            half == 4 && minutes <= 120 -> "${minutes - 105}' STE"
                            half == 4 && minutes > 120 -> "15+${minutes - 120}' STE"
                            else -> null
                        }
                    }

                    Status.HALF_TIME.name -> {
                        newStatus = MatchStatus.IN_PROGRESS
                        newSubstatus = MatchSubstatus.HALFTIME.label
                        newHomeGoals = homeGoals
                        newAwayGoals = awayGoals
                    }

                    Status.PENALTIES.name -> {
                        newStatus = MatchStatus.IN_PROGRESS
                        newSubstatus = MatchSubstatus.PENALTIES.label
                        newHomeGoals = homeGoals
                        newAwayGoals = awayGoals
                        newHomePenalties = homePenalties
                        newAwayPenalties = awayPenalties
                    }

                    Status.COMPLETED.name -> {
                        newStatus = MatchStatus.FINISHED
                        newSubstatus = MatchSubstatus.FINISHED.label
                        newHomeGoals = homeGoals
                        newAwayGoals = awayGoals
                        newHomePenalties = homePenalties
                        newAwayPenalties = awayPenalties
                        newFinishedAt = endedAt ?: ZonedDateTime.now()
                    }
                }

                val newStage = when (stage) {
                    Stage.GS.name -> MatchStage.GROUP_STAGE
                    Stage.R32.name -> MatchStage.ROUND_OF_32
                    Stage.R16.name -> MatchStage.ROUND_OF_16
                    Stage.QF.name -> MatchStage.QUARTERFINALS
                    Stage.SF.name -> MatchStage.SEMIFINALS
                    Stage.TP.name -> MatchStage.THIRD_PLACE
                    Stage.F.name -> MatchStage.FINAL
                    else -> null
                }

                if (newStage == null) {
                    return null
                }

                val newGroup = when (group) {
                    Group.A.name -> MatchGroup.GROUP_A
                    Group.B.name -> MatchGroup.GROUP_B
                    Group.C.name -> MatchGroup.GROUP_C
                    Group.D.name -> MatchGroup.GROUP_D
                    Group.E.name -> MatchGroup.GROUP_E
                    Group.F.name -> MatchGroup.GROUP_F
                    Group.G.name -> MatchGroup.GROUP_G
                    Group.H.name -> MatchGroup.GROUP_H
                    Group.I.name -> MatchGroup.GROUP_I
                    Group.J.name -> MatchGroup.GROUP_J
                    Group.K.name -> MatchGroup.GROUP_K
                    Group.L.name -> MatchGroup.GROUP_L
                    else -> null
                }

                return ExternalMatch(
                    home = home,
                    away = away,
                    stage = newStage,
                    group = newGroup,
                    homeGoals = newHomeGoals,
                    awayGoals = newAwayGoals,
                    status = newStatus,
                    substatus = newSubstatus,
                    homePenalties = newHomePenalties,
                    awayPenalties = newAwayPenalties,
                    startedAt = startedAt,
                    finishedAt = newFinishedAt,
                )
            }
        }

        fun parseMatches() = matches.mapNotNull { it.toExternalMatch() }
    }

    override fun buildRequest() = matchesWebClient.get()
        .uri { uriBuilder -> uriBuilder.path(MATCHES_PATH).build() }

    override fun matchesResponseClass(): Class<*> = Response::class.java

    override fun parseMatchesResponse(body: Any?): List<ExternalMatch> =
        (body as? Response)?.parseMatches() ?: emptyList()

    override fun onMatchesResponseReceived(body: Any?) {
        val response = body as? Response ?: return
        now = response.current
    }
}

@Component
@Profile(PROD)
class FootballDataMatchClient(
    override val matchesWebClient: WebClient,
    @Value("\${external.api.matches.key}")
    private val apiKey: String,
) : MatchClient {

    // For more information, check: https://www.football-data.org/

    companion object {
        private const val MATCHES_PATH = "/v4/competitions/WC/matches"
        private const val AUTH_TOKEN_HEADER = "x-auth-token"
    }

    @JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy::class)
    internal class Response(
        val matches: List<Match> = emptyList(),
    ) {
        @JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy::class)
        internal class Match(
            val utcDate: ZonedDateTime,
            val lastUpdated: ZonedDateTime? = null,
            val status: String,
            val homeTeam: Team,
            val awayTeam: Team,
            val stage: String,
            val group: String? = null,
            val score: Score,
        ) {
            internal enum class Status { SCHEDULED, TIMED, IN_PLAY, PAUSED, FINISHED, POSTPONED, SUSPENDED, CANCELLED }
            internal enum class ScoreDuration { REGULAR, EXTRA_TIME, PENALTY_SHOOTOUT }
            internal enum class Stage { GROUP_STAGE, LAST_32, LAST_16, QUARTER_FINALS, SEMI_FINALS, THIRD_PLACE, FINAL }
            internal enum class Group { GROUP_A, GROUP_B, GROUP_C, GROUP_D, GROUP_E, GROUP_F, GROUP_G, GROUP_H, GROUP_I, GROUP_J, GROUP_K, GROUP_L }

            @JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy::class)
            internal class Team(val tla: String? = null)

            @JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy::class)
            internal class Score(
                val duration: String? = null, val fullTime: InnerScore? = null,
                val regularTime: InnerScore? = null, val extraTime: InnerScore? = null, val penalties: InnerScore? = null
            )

            @JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy::class)
            internal class InnerScore(val home: Int? = null, val away: Int? = null)

            private class ParsedScore(val homeGoals: Int, val awayGoals: Int, val homePenalties: Int? = null, val awayPenalties: Int? = null)

            private fun score(): ParsedScore {
                return when (score.duration) {
                    ScoreDuration.REGULAR.name, ScoreDuration.EXTRA_TIME.name -> ParsedScore(
                        homeGoals = score.fullTime?.home ?: 0,
                        awayGoals = score.fullTime?.away ?: 0,
                    )

                    ScoreDuration.PENALTY_SHOOTOUT.name -> ParsedScore(
                        homeGoals = (score.regularTime?.home ?: 0) + (score.extraTime?.home ?: 0),
                        awayGoals = (score.regularTime?.away ?: 0) + (score.extraTime?.away ?: 0),
                        homePenalties = score.penalties?.home ?: 0, awayPenalties = score.penalties?.away ?: 0,
                    )

                    else -> ParsedScore(homeGoals = 0, awayGoals = 0)
                }
            }

            fun toExternalMatch(): ExternalMatch? {
                if (homeTeam.tla == null || awayTeam.tla == null) {
                    return null
                }

                var newHomeGoals = 0
                var newAwayGoals = 0
                var newStatus = MatchStatus.NOT_STARTED
                var newSubstatus: String? = null
                var newHomePenalties: Int? = null
                var newAwayPenalties: Int? = null
                var newFinishedAt: ZonedDateTime? = null

                when (status) {
                    Status.SCHEDULED.name, Status.TIMED.name -> {
                        newStatus = MatchStatus.NOT_STARTED
                        val current = now
                        if (current != null && utcDate.isBefore(current.atZone(ZoneId.systemDefault()).plus(15, ChronoUnit.MINUTES))) {
                            newSubstatus = MatchSubstatus.NEXT.label
                        }
                    }

                    Status.IN_PLAY.name -> {
                        newStatus = MatchStatus.IN_PROGRESS
                        val parsedScore = score()
                        newHomeGoals = parsedScore.homeGoals
                        newAwayGoals = parsedScore.awayGoals
                        newHomePenalties = parsedScore.homePenalties
                        newAwayPenalties = parsedScore.awayPenalties
                        newSubstatus = MatchSubstatus.LIVE.label
                        if (newHomePenalties != null && newAwayPenalties != null) {
                            newSubstatus = MatchSubstatus.PENALTIES.label
                        }
                    }

                    Status.PAUSED.name -> {
                        newStatus = MatchStatus.IN_PROGRESS
                        val parsedScore = score()
                        newHomeGoals = parsedScore.homeGoals
                        newAwayGoals = parsedScore.awayGoals
                        newSubstatus = MatchSubstatus.HALFTIME.label
                    }

                    Status.FINISHED.name -> {
                        newStatus = MatchStatus.FINISHED
                        newSubstatus = MatchSubstatus.FINISHED.label
                        val parsedScore = score()
                        newHomeGoals = parsedScore.homeGoals
                        newAwayGoals = parsedScore.awayGoals
                        newHomePenalties = parsedScore.homePenalties
                        newAwayPenalties = parsedScore.awayPenalties
                        newFinishedAt = lastUpdated ?: ZonedDateTime.now()
                    }

                    Status.POSTPONED.name, Status.SUSPENDED.name, Status.CANCELLED.name -> {
                        newStatus = MatchStatus.SUSPENDED
                        newSubstatus = MatchSubstatus.SUSPENDED.label
                        val parsedScore = score()
                        newHomeGoals = parsedScore.homeGoals
                        newAwayGoals = parsedScore.awayGoals
                        newHomePenalties = parsedScore.homePenalties
                        newAwayPenalties = parsedScore.awayPenalties
                        newFinishedAt = lastUpdated ?: ZonedDateTime.now()
                    }
                }

                val newStage = when (stage) {
                    Stage.GROUP_STAGE.name -> MatchStage.GROUP_STAGE
                    Stage.LAST_32.name -> MatchStage.ROUND_OF_32
                    Stage.LAST_16.name -> MatchStage.ROUND_OF_16
                    Stage.QUARTER_FINALS.name -> MatchStage.QUARTERFINALS
                    Stage.SEMI_FINALS.name -> MatchStage.SEMIFINALS
                    Stage.THIRD_PLACE.name -> MatchStage.THIRD_PLACE
                    Stage.FINAL.name -> MatchStage.FINAL
                    else -> null
                }

                if (newStage == null) {
                    return null
                }

                val newGroup = when (group) {
                    Group.GROUP_A.name -> MatchGroup.GROUP_A
                    Group.GROUP_B.name -> MatchGroup.GROUP_B
                    Group.GROUP_C.name -> MatchGroup.GROUP_C
                    Group.GROUP_D.name -> MatchGroup.GROUP_D
                    Group.GROUP_E.name -> MatchGroup.GROUP_E
                    Group.GROUP_F.name -> MatchGroup.GROUP_F
                    Group.GROUP_G.name -> MatchGroup.GROUP_G
                    Group.GROUP_H.name -> MatchGroup.GROUP_H
                    Group.GROUP_I.name -> MatchGroup.GROUP_I
                    Group.GROUP_J.name -> MatchGroup.GROUP_J
                    Group.GROUP_K.name -> MatchGroup.GROUP_K
                    Group.GROUP_L.name -> MatchGroup.GROUP_L
                    else -> null
                }

                return ExternalMatch(
                    home = homeTeam.tla,
                    away = awayTeam.tla,
                    stage = newStage,
                    group = newGroup,
                    homeGoals = newHomeGoals,
                    awayGoals = newAwayGoals,
                    status = newStatus,
                    substatus = newSubstatus,
                    homePenalties = newHomePenalties,
                    awayPenalties = newAwayPenalties,
                    startedAt = utcDate,
                    finishedAt = newFinishedAt,
                )
            }
        }

        fun parseMatches() = matches.mapNotNull { it.toExternalMatch() }
    }

    override fun buildRequest() = matchesWebClient.get()
        .uri { uriBuilder -> uriBuilder.path(MATCHES_PATH).build() }
        .header(AUTH_TOKEN_HEADER, apiKey)

    override fun matchesResponseClass(): Class<*> = Response::class.java

    override fun parseMatchesResponse(body: Any?): List<ExternalMatch> =
        (body as? Response)?.parseMatches() ?: emptyList()
}