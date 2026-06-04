package com.grondona.repository

import com.grondona.model.Match
import com.grondona.model.MatchStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface MatchRepository : JpaRepository<Match, UUID>, JpaSpecificationExecutor<Match> {

    fun findByTournamentIdOrderByStartedAt(tournamentId: UUID): List<Match>

    fun findByTournamentId(tournamentId: UUID): List<Match>

    fun findByTournamentIdAndStatus(tournamentId: UUID, status: MatchStatus): List<Match>

    @Query(
        """
        SELECT m
        FROM Match m
        WHERE m.tournament.id = :tournamentId AND m.code IN :codes
    """
    )
    fun findByCodes(tournamentId: UUID, codes: List<String>): List<Match>

}
