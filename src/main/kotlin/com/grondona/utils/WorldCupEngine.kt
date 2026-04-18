package com.grondona.utils

import com.grondona.model.Match
import com.grondona.model.Tournament
import com.grondona.model.TournamentStatus
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.Optional
import java.util.UUID

object WorldCupEngine {
    val BEST_YOUNG_PLAYER_DATE_LIMIT = LocalDate.parse("2005-01-01")
    val SYSTEM_TOURNAMENT_ID: UUID = UUID.fromString("28652183-a2d6-4f33-a624-0d24645ce3cd")
    const val API_TOURNAMENT_ID: String = "2173492"

    val GROUP_CHECKPOINT_MATCH_CODE: List<String> = listOf("69", "70")
    const val RO32_CHECKPOINT_MATCH_CODE: String = "87"
    const val RO16_CHECKPOINT_MATCH_CODE: String = "96"
    const val QF_CHECKPOINT_MATCH_CODE: String = "100"
    const val SF_CHECKPOINT_MATCH_CODE: String = "102"
    const val FINAL_MATCH_CODE: String = "104"

    var now: LocalDateTime = LocalDateTime.now()

    fun isMatchUnlocked(match: Match) =
        match.startedAt?.isAfter(now.plus(15, ChronoUnit.MINUTES)) ?: true
}
