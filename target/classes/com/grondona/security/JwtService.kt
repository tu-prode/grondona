package com.grondona.security

import com.grondona.config.JwtConfig
import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.stereotype.Service
import java.util.*
import javax.crypto.SecretKey

@Service
class JwtService(private val jwtConfig: JwtConfig) {

    private val secretKey: SecretKey by lazy {
        Keys.hmacShaKeyFor(jwtConfig.secret.toByteArray())
    }

    fun generateToken(userId: UUID, username: String): String {
        val now = Date()
        val expiry = Date(now.time + jwtConfig.expiration)

        return Jwts.builder()
            .subject(userId.toString())
            .claim("username", username)
            .issuedAt(now)
            .expiration(expiry)
            .signWith(secretKey)
            .compact()
    }

    fun validateToken(token: String): Boolean {
        return try {
            val claims = extractAllClaims(token)
            !claims.expiration.before(Date())
        } catch (e: Exception) {
            false
        }
    }

    fun extractUserId(token: String): UUID? {
        return try {
            val subject = extractAllClaims(token).subject
            UUID.fromString(subject)
        } catch (e: Exception) {
            null
        }
    }

    fun extractUsername(token: String): String? {
        return try {
            extractAllClaims(token)["username"] as? String
        } catch (e: Exception) {
            null
        }
    }

    private fun extractAllClaims(token: String): Claims {
        return Jwts.parser()
            .verifyWith(secretKey)
            .build()
            .parseSignedClaims(token)
            .payload
    }
}
