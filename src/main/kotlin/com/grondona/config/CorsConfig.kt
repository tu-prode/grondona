package com.grondona.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

@Configuration
class CorsConfig {

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val configuration = CorsConfiguration().apply {
            // Allow prod host
            allowedOrigins = listOf(
                "https://elprodedelmundial-7d579.web.app",
                "https://elprodedelmundial-7d579.firebaseapp.com",
            )

            // Allow any localhost origin (any port)
            allowedOriginPatterns = listOf(
                "http://localhost:*",
                "http://127.0.0.1:*",
            )
            
            // Allow all common HTTP methods
            allowedMethods = listOf("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
            
            // Allow all headers
            allowedHeaders = listOf("*")
            
            // Expose Authorization header to the client
            exposedHeaders = listOf("Authorization")
            
            // Allow credentials (cookies, authorization headers)
            allowCredentials = true
            
            // Cache preflight response for 1 hour
            maxAge = 3600L
        }

        return UrlBasedCorsConfigurationSource().apply {
            registerCorsConfiguration("/**", configuration)
        }
    }

}
