package com.grondona.config

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.http.codec.json.Jackson2JsonDecoder
import org.springframework.web.reactive.function.client.ExchangeStrategies
import org.springframework.web.reactive.function.client.WebClient
import reactor.netty.http.client.HttpClient
import java.time.Duration

@Configuration
class ClientConfig {

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

        return WebClient.builder()
            .baseUrl(baseUrl)
            .exchangeStrategies(strategies)
            .clientConnector(ReactorClientHttpConnector(httpClient))
            .build()
    }
}