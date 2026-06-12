package com.grondona.utils

import com.grondona.exception.GeneralException
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

object Clock {

    private enum class Mode { SYSTEM, SYNCED }

    @Volatile
    private var mode = Mode.SYNCED

    @Volatile
    private var syncedTime: LocalDateTime? = null

    fun now(): ZonedDateTime = when (mode) {
        Mode.SYSTEM -> LocalDateTime.now()
        Mode.SYNCED -> syncedTime ?: LocalDateTime.now()
    }.atZone(ZoneId.systemDefault())

    fun sync(time: LocalDateTime) {
        if (mode == Mode.SYNCED) syncedTime = time
    }

    fun reset() {
        when (mode) {
            Mode.SYNCED -> syncedTime = null
            Mode.SYSTEM -> throw GeneralException("Cannot reset SYSTEM clock")
        }
    }

    internal fun configureForProduction() {
        mode = Mode.SYSTEM
        syncedTime = null
    }

    internal fun configureForSimulation() {
        mode = Mode.SYNCED
        syncedTime = null
    }

}