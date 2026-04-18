package com.grondona.model

import java.time.LocalDateTime

data class SchedulerData(
    val nextRunAt: LocalDateTime? = null,
    val shouldStop: Boolean = false,
) {
    fun shouldStop(): Boolean = shouldStop
    fun shouldWait(): Boolean = nextRunAt == null
    fun shouldSleep(): Boolean = nextRunAt != null

    companion object {
        fun wait() = SchedulerData()
        fun stop() = SchedulerData(shouldStop = true)
        fun sleep(until: LocalDateTime) = SchedulerData(nextRunAt = until)
    }
}
