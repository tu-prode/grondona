package com.grondona.service.engine

import com.grondona.model.ExternalMatch
import com.grondona.model.Match
import com.grondona.model.TournamentStatus
import java.util.UUID

interface TournamentEngine {
    val tournamentId: UUID

    fun calculateTournamentStatus(matches: List<Match>): TournamentStatus?

    fun calculateNewMatches(systemMatches: List<Match>, externalMatches: List<ExternalMatch>): List<Match>
}