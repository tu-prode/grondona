package com.grondona.scheduler

import com.grondona.service.MatchService
import com.grondona.utils.WorldCupEngine
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service

@Service
class MatchScheduler(
    private val matchService: MatchService,
) {

    companion object {
        private val logger = LoggerFactory.getLogger(MatchService::class.java)
    }

    @Scheduled(fixedDelayString = "\${app.matches.poll-interval-ms}")
    fun updateMatches() {
        logger.debug("Starting matches statuses polling job")
        matchService.updateMatchesStatuses(WorldCupEngine.SYSTEM_TOURNAMENT_ID)
    }

    @Scheduled(fixedDelayString = "\${app.matches.poll-interval-ms}")
    fun updateQuotas() {
        logger.debug("Starting matches quotas polling job")
        matchService.updateMatchesQuotas(WorldCupEngine.SYSTEM_TOURNAMENT_ID)
    }

}