package com.grondona.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.grondona.client.MatchClient
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.http.codec.json.Jackson2JsonDecoder
import org.springframework.web.reactive.function.client.ExchangeFilterFunction
import org.springframework.web.reactive.function.client.ExchangeStrategies
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import reactor.netty.http.client.HttpClient
import java.time.Duration

@Configuration
class ClientConfig {

    companion object {
        private val logger = LoggerFactory.getLogger(MatchClient::class.java)
    }

    @Bean
    fun matchWebClient(
        objectMapper: ObjectMapper,
        @Value("\${external.api.matches.base-url}") baseUrl: String,
        @Value("\${external.api.matches.timeout-ms}") timeoutMs: Long
    ): WebClient {

        val httpClient = HttpClient.create()
            .responseTimeout(Duration.ofMillis(timeoutMs))

        val strategies = ExchangeStrategies.builder()
            .codecs { it.defaultCodecs().jackson2JsonDecoder(Jackson2JsonDecoder(objectMapper)) }
            .build()

        val withLogs = ExchangeFilterFunction.ofRequestProcessor { request ->
            logger.info("Request: {} {}", request.method(), request.url())
            Mono.just(request)
        }

        return WebClient.builder()
            .filter(withLogs)
            .baseUrl(baseUrl)
            .exchangeStrategies(strategies)
            .clientConnector(ReactorClientHttpConnector(httpClient))
            .build()
    }
}