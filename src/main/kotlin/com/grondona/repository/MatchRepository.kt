package com.grondona.repository

import com.grondona.model.Match
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface MatchRepository : JpaRepository<Match, UUID>, JpaSpecificationExecutor<Match> {

    fun findByTournamentIdOrderByStartedAt(tournamentId: UUID): List<Match>

}
