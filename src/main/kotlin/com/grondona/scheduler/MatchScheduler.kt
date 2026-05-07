package com.grondona.scheduler

import com.grondona.model.Environments
import com.grondona.model.Match
import com.grondona.model.MatchStatus
import com.grondona.model.SchedulerData
import com.grondona.service.MatchService
import com.grondona.service.engine.WorldCupEngine
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.TaskScheduler
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Date
import java.util.concurrent.ScheduledFuture

@Service
class MatchScheduler(
    private val matchService: MatchService,
    private val taskScheduler: TaskScheduler,
    @Value("\${external.matches.poll-interval-ms}")
    private val statusPollIntervalMs: Long,
    @Value("\${app.env}")
    private val rawEnv: String
) {

    private var future: ScheduledFuture<*>? = null
    private val env = Environments.valueOf(rawEnv.uppercase())

    companion object {
        private val logger = LoggerFactory.getLogger(MatchService::class.java)
    }

    @Scheduled(cron = "0 0 7 * * *", zone = "America/Argentina/Buenos_Aires")
    fun updateQuotas() {
        logger.debug("Starting matches quotas polling job")
        try {
            matchService.updateMatchesQuotas(WorldCupEngine.SYSTEM_TOURNAMENT_ID)
        } catch (ex: Exception) {
            logger.error("Error while executing MatchScheduler (quotas)", ex)
        }
    }

    @PostConstruct
    fun start() {
        val wait = 120000L
        logger.debug("Started MatchScheduler and waiting {}ms for system to be ready", wait)
        scheduleAfterDelay(wait)
    }

    private fun updateMatches() {
        logger.debug("Executing scheduled job for recalculating matches status")

        val schedulerData: SchedulerData
        try {
            val tournamentMatches = matchService.updateMatchesStatuses(WorldCupEngine.SYSTEM_TOURNAMENT_ID)
            schedulerData = checkSchedule(tournamentMatches)
        } catch (ex: Exception) {
            logger.error("Error while executing MatchScheduler (status). Retrying in 10 minutes", ex)
            scheduleAfterDelay(30 * 60 * 1000L)
            return
        }

        when {
            env == Environments.LOCAL -> {
                scheduleAfterDelay(statusPollIntervalMs)
            }

            schedulerData.shouldStop() -> {
                logger.debug("Stopping MatchScheduler")
                future?.cancel(false)
            }

            schedulerData.shouldSleep() -> {
                val nextRun = schedulerData.nextRunAt!!
                logger.debug("Sleeping MatchScheduler until {}", nextRun)
                scheduleAt(nextRun)
            }

            schedulerData.shouldWait() -> {
                logger.debug("Sleeping MatchScheduler for {}ms", statusPollIntervalMs)
                scheduleAfterDelay(statusPollIntervalMs)
            }
        }
    }

    private fun checkSchedule(tournamentMatches: List<Match>): SchedulerData {
        if (tournamentMatches.any { it.status == MatchStatus.IN_PROGRESS }) {
            return SchedulerData.wait()
        }

        val nextMatches = tournamentMatches.filter { it.status == MatchStatus.NOT_STARTED }
        if (nextMatches.isNotEmpty()) {
            val nextRunAt = nextMatches.sortedBy { it.startedAt }.first().startedAt
            return SchedulerData.sleep(nextRunAt).takeIf { nextRunAt.isAfter(LocalDateTime.now()) } ?: SchedulerData.wait()
        }

        return SchedulerData.stop()
    }

    private fun scheduleAfterDelay(delayMs: Long) {
        future?.cancel(false)
        val instant = Date(System.currentTimeMillis() + delayMs).toInstant()
        future = taskScheduler.schedule({ updateMatches() }, instant)
    }

    private fun scheduleAt(dateTime: LocalDateTime) {
        future?.cancel(false)
        val instant = dateTime.atZone(ZoneId.systemDefault()).toInstant()
        future = taskScheduler.schedule({ updateMatches() }, instant)
    }

}