package com.grondona.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.web.reactive.function.client.WebClient
import reactor.netty.http.client.HttpClient
import java.time.Duration

@Configuration
class ClientConfig {

    @Bean
    fun matchWebClient(
        @Value("\${external.api.base-url}") baseUrl: String,
        @Value("\${external.api.timeout-ms}") timeoutMs: Long
    ): WebClient {

        val httpClient = HttpClient.create()
            .responseTimeout(Duration.ofMillis(timeoutMs))

        return WebClient.builder()
            .baseUrl(baseUrl)
            .clientConnector(ReactorClientHttpConnector(httpClient))
            .build()
    }
}