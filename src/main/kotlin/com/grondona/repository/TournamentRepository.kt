package com.grondona.repository

import com.grondona.model.Tournament
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface TournamentRepository : JpaRepository<Tournament, UUID>, JpaSpecificationExecutor<Tournament> {

    fun existsByName(name: String): Boolean

}
