package com.grondona.utils

import com.grondona.model.Match
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID

object WorldCupEngine {
    val BEST_YOUNG_PLAYER_DATE_LIMIT = LocalDate.parse("2005-01-01")
    val SYSTEM_TOURNAMENT_ID: UUID = UUID.fromString("28652183-a2d6-4f33-a624-0d24645ce3cd")
    const val API_TOURNAMENT_ID: String = "107"

    var now: LocalDateTime = LocalDateTime.now()

    fun isMatchUnlocked(match: Match) =
        match.startedAt?.isAfter(now.plus(15, ChronoUnit.MINUTES)) ?: true
}
