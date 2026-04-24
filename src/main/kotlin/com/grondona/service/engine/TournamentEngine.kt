package com.grondona.service.engine

import com.grondona.model.ExternalMatch
import com.grondona.model.Match
import com.grondona.model.Tournament
import com.grondona.model.TournamentStatus
import java.util.UUID

interface TournamentEngine {
    val tournamentId: UUID

    fun calculateNewStatus(tournament: Tournament, matches: List<Match>): TournamentStatus?

    fun calculateNewMatches(tournament: Tournament, systemMatches: List<Match>, externalMatches: List<ExternalMatch>): List<Match>
}