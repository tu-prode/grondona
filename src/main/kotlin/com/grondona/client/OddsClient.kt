package com.grondona.client

import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming
import com.grondona.exception.ExternalServiceException
import com.grondona.model.ExternalOdds
import com.grondona.model.LOCAL
import com.grondona.model.PROD
import com.grondona.model.TEST
import com.grondona.utils.Clock
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

interface OddsClient {
    val oddsWebClient: WebClient

    fun buildRequest(): WebClient.RequestHeadersSpec<*>

    fun oddsResponseClass(): Class<*>

    fun parseOddsResponse(body: Any?): List<ExternalOdds>

    fun onMatchesResponseReceived(body: Any?) {}

    fun getOdds(tournamentId: UUID): List<ExternalOdds> {
        return try {
            val body = buildRequest()
                .retrieve()
                .onStatus({ it.is4xxClientError }) { response ->
                    response.bodyToMono(String::class.java)
                        .flatMap { responseBody ->
                            logger.error("Error 4xx calling Odds API: $responseBody")
                            Mono.error(ExternalServiceException("Error 4xx calling Odds API: $responseBody"))
                        }
                }
                .onStatus({ it.is5xxServerError }) { response ->
                    response.bodyToMono(String::class.java)
                        .flatMap { responseBody ->
                            logger.error("Error 5xx calling Odds API: $responseBody")
                            Mono.error(ExternalServiceException("Error 5xx calling Odds API: $responseBody"))
                        }
                }
                .bodyToMono(oddsResponseClass())
                .block()
            onMatchesResponseReceived(body)
            parseOddsResponse(body)
        } catch (ex: WebClientResponseException) {
            throw ExternalServiceException("HTTP error calling Odds API: ${ex.statusCode}", ex)
        } catch (ex: Exception) {
            throw ExternalServiceException("Unexpected error calling Odds API: ${ex.message}", ex)
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(MatchClient::class.java)
    }
}

@Component
@Profile(LOCAL, TEST)
class MocknaldoOddsClient(
    override val oddsWebClient: WebClient,
) : OddsClient {

    companion object {
        private const val ODDS_PATH = "/odds"
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
    internal class Response(
        val current: LocalDateTime,
        val odds: List<Odds>,
    ) {
        @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
        internal class Odds(
            val homeKey: String,
            val awayKey: String,
            val homeOdds: Float,
            val drawOdds: Float,
            val awayOdds: Float,
            val startedAt: ZonedDateTime? = null,
        ) {
            fun toExternalOdds(): ExternalOdds? = startedAt?.let {
                ExternalOdds(
                    homeKey = homeKey, awayKey = awayKey, homeOdds = homeOdds, drawOdds = drawOdds, awayOdds = awayOdds, startedAt = it
                )
            }
        }

        fun parseOdds() = odds.mapNotNull { it.toExternalOdds() }
    }

    override fun buildRequest() = oddsWebClient.get()
        .uri { uriBuilder -> uriBuilder.path(ODDS_PATH).build() }

    override fun oddsResponseClass(): Class<*> = Response::class.java

    override fun parseOddsResponse(body: Any?): List<ExternalOdds> =
        (body as? Response)?.parseOdds() ?: emptyList()

    override fun onMatchesResponseReceived(body: Any?) {
        val response = body as? Response ?: return
        Clock.sync(response.current)
    }
}

@Component
@Profile(PROD)
class TheApiOddsClient(
    override val oddsWebClient: WebClient,
    @Value("\${external.api.odds.key}")
    private val apiKey: String,
) : OddsClient {

    // For more information, check: https://the-odds-api.com/

    companion object {
        private const val ODDS_PATH = "/v4/sports/soccer_fifa_world_cup/odds"
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
    internal class Response(
        val homeTeam: String,
        val awayTeam: String,
        val commenceTime: ZonedDateTime? = null,
        val bookmakers: List<Bookmaker> = emptyList(),
    ) {
        companion object {
            private const val DRAW = "Draw"
        }

        @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
        internal class Bookmaker(
            val markets: List<Market> = emptyList(),
        ) {
            @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
            internal class Market(
                val outcomes: List<Outcome> = emptyList(),
            ) {
                @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
                internal class Outcome(
                    val name: String,
                    val price: Float,
                )
            }
        }

        fun parseOdds(): ExternalOdds? = commenceTime?.let {
            val homeOdds = mutableListOf<Float>()
            val drawOdds = mutableListOf<Float>()
            val awayOdds = mutableListOf<Float>()

            bookmakers.forEach { bookmaker ->
                bookmaker.markets.forEach { market ->
                    market.outcomes.forEach { outcome ->
                        when (outcome.name) {
                            homeTeam -> homeOdds.add(outcome.price)
                            awayTeam -> awayOdds.add(outcome.price)
                            DRAW -> drawOdds.add(outcome.price)
                        }
                    }
                }
            }

            if (homeOdds.isEmpty() || drawOdds.isEmpty() || awayOdds.isEmpty()) {
                return null
            }

            return ExternalOdds(
                homeKey = homeTeam, awayKey = awayTeam, startedAt = commenceTime,
                homeOdds = homeOdds.average().toFloat(), drawOdds = drawOdds.average().toFloat(), awayOdds = awayOdds.average().toFloat()
            )
        }
    }

    private fun List<Response>.parseOdds(): List<ExternalOdds> = mapNotNull { it.parseOdds() }

    override fun buildRequest() = oddsWebClient.get()
        .uri { uriBuilder ->
            uriBuilder.path(ODDS_PATH)
                .queryParam("regions", "us")
                .queryParam("apiKey", apiKey)
                .build()
        }

    override fun oddsResponseClass(): Class<*> = Array<Response>::class.java

    override fun parseOddsResponse(body: Any?): List<ExternalOdds> =
        (body as? Array<*>)?.filterIsInstance<Response>()?.parseOdds() ?: emptyList()
}