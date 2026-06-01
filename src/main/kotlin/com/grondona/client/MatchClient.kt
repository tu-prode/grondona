package com.grondona.client

import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming
import com.grondona.exception.ExternalServiceException
import com.grondona.model.ExternalMatch
import com.grondona.model.LOCAL
import com.grondona.model.MatchStatus
import com.grondona.model.MatchSubstatus
import com.grondona.model.PROD
import com.grondona.model.TEST
import com.grondona.now
import com.grondona.service.engine.WorldCupEngine
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Mono
import java.time.LocalDateTime
import java.time.ZonedDateTime
import java.util.UUID

interface MatchClient {
    val matchWebClient: WebClient

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
            throw ExternalServiceException("HTTP error calling matches service: ${ex.statusCode}", ex)
        } catch (ex: Exception) {
            throw ExternalServiceException("Unexpected error calling matches service: ${ex.message}", ex)
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(MatchClient::class.java)
    }
}

@Component
@Profile(LOCAL, TEST)
class MocknaldoMatchClient(
    override val matchWebClient: WebClient,
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
            val homePenalties: Int? = null,
            val awayPenalties: Int? = null,
            val minutes: Int,
            val half: Int,
            val status: String,
            val homeOdds: Float,
            val drawOdds: Float,
            val awayOdds: Float,
            val startedAt: ZonedDateTime,
            val endedAt: ZonedDateTime? = null,
        ) {
            internal enum class Status { TO_START, IN_PLAY, HALF_TIME, PENALTIES, COMPLETED }

            fun toExternalMatch(): ExternalMatch {
                var newHomeGoals = 0
                var newAwayGoals = 0
                var newStatus = MatchStatus.NOT_STARTED
                var newSubstatus: String? = null
                var newHomeOdds: Float? = null
                var newDrawOdds: Float? = null
                var newAwayOdds: Float? = null
                var newHomePenalties: Int? = null
                var newAwayPenalties: Int? = null
                var newFinishedAt: ZonedDateTime? = null

                when (status) {
                    Status.TO_START.name -> {
                        newStatus = MatchStatus.NOT_STARTED
                        newHomeOdds = homeOdds
                        newDrawOdds = drawOdds
                        newAwayOdds = awayOdds
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

                return ExternalMatch(
                    code = code,
                    home = home,
                    away = away,
                    homeGoals = newHomeGoals,
                    awayGoals = newAwayGoals,
                    status = newStatus,
                    substatus = newSubstatus,
                    homePenalties = newHomePenalties,
                    awayPenalties = newAwayPenalties,
                    homeOdds = newHomeOdds,
                    drawOdds = newDrawOdds,
                    awayOdds = newAwayOdds,
                    startedAt = startedAt,
                    finishedAt = newFinishedAt,
                )
            }
        }

        fun parseMatches() = matches.map { it.toExternalMatch() }
    }

    override fun buildRequest() = matchWebClient.get()
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
    override val matchWebClient: WebClient,
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
            val score: Score,
            val minute: Int? = null,
            val injuryTime: Int? = null,
            val odds: Odds,
        ) {
            internal enum class Status { SCHEDULED, TIMED, IN_PLAY, PAUSED, FINISHED, POSTPONED, SUSPENDED, CANCELLED }
            internal enum class ScoreDuration { REGULAR, EXTRA_TIME, PENALTY_SHOOTOUT }
            internal enum class Stage { GROUP_STAGE, LAST_32, LAST_16, QUARTER_FINALS, SEMI_FINALS, THIRD_PLACE, FINAL }

            @JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy::class)
            internal class Team(val tla: String? = null)

            @JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy::class)
            internal class Score(
                val duration: String? = null, val fullTime: InnerScore? = null,
                val regularTime: InnerScore? = null, val extraTime: InnerScore? = null, val penalties: InnerScore? = null
            )

            @JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy::class)
            internal class InnerScore(val home: Int? = null, val away: Int? = null)

            @JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy::class)
            internal class Odds(val homeWin: Float, val draw: Float, val awayWin: Float)

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
                var newHomeOdds: Float? = null
                var newDrawOdds: Float? = null
                var newAwayOdds: Float? = null
                var newHomePenalties: Int? = null
                var newAwayPenalties: Int? = null
                var newFinishedAt: ZonedDateTime? = null

                when (status) {
                    Status.SCHEDULED.name, Status.TIMED.name -> {
                        newStatus = MatchStatus.NOT_STARTED
                        newHomeOdds = odds.homeWin
                        newDrawOdds = odds.draw
                        newAwayOdds = odds.awayWin
                    }

                    Status.IN_PLAY.name -> {
                        newStatus = MatchStatus.IN_PROGRESS
                        val parsedScore = score()
                        newHomeGoals = parsedScore.homeGoals
                        newAwayGoals = parsedScore.awayGoals
                        newHomePenalties = parsedScore.homePenalties
                        newAwayPenalties = parsedScore.awayPenalties
                        newSubstatus = when {
                            score.duration == ScoreDuration.PENALTY_SHOOTOUT.name -> MatchSubstatus.PENALTIES.label
                            minute == null -> "0' PT"
                            minute <= 45 && (injuryTime ?: 0) == 0 -> "$minute' PT"
                            minute == 45 -> "$minute+${injuryTime ?: 0}' PT"
                            minute <= 90 && (injuryTime ?: 0) == 0 -> "${minute - 45}' ST"
                            minute == 90 -> "${minute - 45}+${injuryTime ?: 0}' ST"
                            minute <= 105 && (injuryTime ?: 0) == 0 -> "${minute - 90}' PTE"
                            minute == 105 -> "${minute - 90}+${injuryTime ?: 0}' PTE"
                            minute <= 120 && (injuryTime ?: 0) == 0 -> "${minute - 105}' STE"
                            minute == 120 -> "${minute - 105}+${injuryTime ?: 0}' STE"
                            else -> null
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

                return ExternalMatch(
                    code = WorldCupEngine.calculateKnockoutCode(utcDate),
                    home = homeTeam.tla,
                    away = awayTeam.tla,
                    homeGoals = newHomeGoals,
                    awayGoals = newAwayGoals,
                    status = newStatus,
                    substatus = newSubstatus,
                    homePenalties = newHomePenalties,
                    awayPenalties = newAwayPenalties,
                    homeOdds = newHomeOdds,
                    drawOdds = newDrawOdds,
                    awayOdds = newAwayOdds,
                    startedAt = utcDate,
                    finishedAt = newFinishedAt,
                )
            }
        }

        fun parseMatches() = matches.mapNotNull { it.toExternalMatch() }
    }

    override fun buildRequest() = matchWebClient.get()
        .uri { uriBuilder -> uriBuilder.path(MATCHES_PATH).build() }
        .header(AUTH_TOKEN_HEADER, apiKey)

    override fun matchesResponseClass(): Class<*> = Response::class.java

    override fun parseMatchesResponse(body: Any?): List<ExternalMatch> =
        (body as? Response)?.parseMatches() ?: emptyList()
}