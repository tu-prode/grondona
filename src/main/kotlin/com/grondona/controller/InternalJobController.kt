package com.grondona.controller

import com.grondona.exception.UnauthorizedException
import com.grondona.service.MatchService
import com.grondona.service.engine.WorldCupEngine
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

@RestController
@RequestMapping("/internal/jobs")
class InternalJobController(
    private val matchService: MatchService,
    @Value("\${internal.jobs.token:}") private val expectedToken: String,
) {

    companion object {
        private val logger = LoggerFactory.getLogger(InternalJobController::class.java)
        private const val TOKEN_HEADER = "X-Cron-Token"
    }

    @PostMapping("/matches/status")
    fun runMatchStatusUpdate(
        @RequestHeader(value = TOKEN_HEADER, required = false) token: String?
    ): ResponseEntity<Void> {
        authorize(token)
        logger.info("POST /internal/jobs/matches/status - Running match status update job")
        matchService.updateMatchesStatuses(WorldCupEngine.SYSTEM_TOURNAMENT_ID)
        logger.info("POST /internal/jobs/matches/status - Match status update job finished")
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/matches/quotas")
    fun runMatchQuotasUpdate(
        @RequestHeader(value = TOKEN_HEADER, required = false) token: String?
    ): ResponseEntity<Void> {
        authorize(token)
        logger.info("POST /internal/jobs/matches/quotas - Running match quotas update job")
        matchService.updateMatchesQuotas(WorldCupEngine.SYSTEM_TOURNAMENT_ID)
        logger.info("POST /internal/jobs/matches/quotas - Match quotas update job finished")
        return ResponseEntity.noContent().build()
    }

    private fun authorize(token: String?) {
        if (expectedToken.isBlank()) {
            logger.error("Internal jobs token is not configured; rejecting request")
            throw UnauthorizedException("Internal jobs endpoint is not configured")
        }
        // Constant-time comparison to avoid leaking the token through timing.
        val matches = MessageDigest.isEqual(
            (token ?: "").toByteArray(StandardCharsets.UTF_8),
            expectedToken.toByteArray(StandardCharsets.UTF_8),
        )
        if (!matches) {
            logger.warn("Rejected internal job request: invalid or missing token")
            throw UnauthorizedException("Invalid internal token")
        }
    }
}
