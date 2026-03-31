package com.grondona.repository

import com.grondona.model.Match
import com.grondona.model.MatchStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface MatchRepository : JpaRepository<Match, UUID>, JpaSpecificationExecutor<Match> {

    @Query("""
        select m
        from Match m
        where m.tournament.id = :tournamentId
        order by m.finishedAt asc nulls first
    """)
    fun findLastMatch(@Param("tournamentId") tournamentId: UUID): Match?

    fun findByTournamentIdOrderByStartedAt(tournamentId: UUID): List<Match>

    fun findByTournamentIdAndStatusOrderByStartedAt(tournamentId: UUID, status: MatchStatus): List<Match>

}
