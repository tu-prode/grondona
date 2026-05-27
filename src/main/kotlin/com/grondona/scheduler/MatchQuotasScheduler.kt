package com.grondona.scheduler

import com.grondona.service.MatchService
import com.grondona.service.engine.WorldCupEngine
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(
    name = ["external.api.matches.with-updated-quotas"],
    havingValue = "true",
)
class MatchQuotasScheduler(
    private val matchService: MatchService,
) {

    companion object {
        private val logger = LoggerFactory.getLogger(MatchQuotasScheduler::class.java)
    }

    @Scheduled(cron = "0 0 7 * * *", zone = "America/Argentina/Buenos_Aires")
    fun updateQuotas() {
        logger.debug("Starting matches quotas polling job")
        try {
            matchService.updateMatchesQuotas(WorldCupEngine.SYSTEM_TOURNAMENT_ID)
        } catch (ex: Exception) {
            logger.error("Error while executing MatchQuotasScheduler", ex)
        }
    }
}
