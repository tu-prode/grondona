package com.grondona.model

import java.time.ZonedDateTime

data class SchedulerData(
    val nextRunAt: ZonedDateTime? = null,
    val shouldStop: Boolean = false,
) {
    fun shouldStop(): Boolean = shouldStop
    fun shouldWait(): Boolean = nextRunAt == null
    fun shouldSleep(): Boolean = nextRunAt != null

    companion object {
        fun wait() = SchedulerData()
        fun stop() = SchedulerData(shouldStop = true)
        fun sleep(until: ZonedDateTime) = SchedulerData(nextRunAt = until)
    }
}
