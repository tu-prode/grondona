package com.grondona.controller

import com.grondona.model.dto.request.CronRequest
import com.grondona.service.CronService
import com.grondona.service.UserService
import jakarta.validation.Valid
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/cron/matches")
class CronController(
    private val userService: UserService,
    private val cronService: CronService,
) {

    companion object {
        private val logger = LoggerFactory.getLogger(CronController::class.java)
    }

    @PostMapping("/status")
    fun updateMatches(@Valid @RequestBody request: CronRequest): ResponseEntity<Void> {
        logger.debug("POST /cron/matches/status - Executing MatchesStatus CRON job for tournament='{}'", request.tournamentId)
        userService.validateCronUser(request.apiKey)

        cronService.updateMatchesStatuses(request.tournamentId)
        logger.debug("POST /cron/matches/status - Executed MatchesStatus CRON job: tournament='{}'", request.tournamentId)
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/quotas")
    fun updateQuotas(@Valid @RequestBody request: CronRequest): ResponseEntity<Void> {
        logger.debug("POST /cron/matches/quotas - Executing MatchesQuotas CRON job for tournament='{}'", request.tournamentId)
        userService.validateCronUser(request.apiKey)

        cronService.updateMatchesQuotas(request.tournamentId)
        logger.debug("POST /cron/matches/quotas - Executed MatchesQuotas CRON job for tournament='{}'", request.tournamentId)
        return ResponseEntity.noContent().build()
    }

}
