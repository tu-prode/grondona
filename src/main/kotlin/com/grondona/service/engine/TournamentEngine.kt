package com.grondona.service.engine

import com.grondona.model.Match
import com.grondona.model.TournamentStatus
import java.util.UUID

interface TournamentEngine {
    val tournamentId: UUID

    fun calculateTournamentStatus(matches: List<Match>): TournamentStatus?

    fun generateMatchesCodes(newMatches: List<Match>): List<Match>
}