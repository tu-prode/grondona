package com.grondona.client

import com.grondona.exception.ExternalServiceException
import com.grondona.exception.NotFoundException
import com.grondona.model.ExternalMatch
import com.grondona.utils.WorldCupEngine
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Mono
import java.util.UUID

@Component
class MatchClient(
    private val matchWebClient: WebClient,
    @Value("\${external.api.key}")
    private val apiKey: String,
    @Value("\${external.api.secret}")
    private val apiSecret: String
) {

    companion object {
        private val logger = LoggerFactory.getLogger(MatchClient::class.java)
        private const val MATCHES_PATH = "/api-client/matches/live.json"

        private val tournamentIdsMapper: Map<UUID, String> =
            mapOf(WorldCupEngine.SYSTEM_TOURNAMENT_ID to WorldCupEngine.API_TOURNAMENT_ID)
    }

    fun getMatches(tournamentId: UUID): List<ExternalMatch> {
        val competitionId = tournamentIdsMapper[tournamentId] ?: throw NotFoundException("Tournament $tournamentId not found")
        return try {
            matchWebClient
                .get()
                .uri { uriBuilder ->
                    uriBuilder.path(MATCHES_PATH)
                        .queryParam("competition_id", competitionId)
                        .queryParam("secret", apiSecret)
                        .queryParam("key", apiKey)
                        .build()
                }.retrieve()
                .onStatus({ it.is4xxClientError }) { response ->
                    response.bodyToMono(String::class.java)
                        .flatMap { body ->
                            logger.error("Error 4xx calling Matches API: $body")
                            Mono.error(ExternalServiceException("Error 4xx calling Matches API: $body"))
                        }
                }
                .onStatus({ it.is5xxServerError }) { response ->
                    response.bodyToMono(String::class.java)
                        .flatMap { body ->
                            logger.error("Error 5xx calling Matches API: $body")
                            Mono.error(ExternalServiceException("Error 5xx calling Matches API: $body"))
                        }
                }
                .bodyToFlux(ExternalMatch::class.java)
                .collectList()
                .block() ?: emptyList()
        } catch (ex: WebClientResponseException) {
            throw ExternalServiceException("HTTP error calling matches service: ${ex.statusCode}", ex)
        } catch (ex: Exception) {
            throw ExternalServiceException("Unexpected error calling matches service", ex)
        }
    }
}