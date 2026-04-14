package com.grondona.repository

import com.grondona.model.Player
import com.grondona.model.PlayerPosition
import com.grondona.model.Team
import com.grondona.model.Tournament
import jakarta.persistence.EntityManager
import jakarta.persistence.criteria.Predicate
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.stereotype.Repository
import java.time.LocalDate
import java.util.UUID

@Repository
interface PlayerRepository : JpaRepository<Player, UUID>, JpaSpecificationExecutor<Player>, PlayerRepositoryCustom

interface PlayerRepositoryCustom {
    fun findTournamentPlayers(
        tournamentId: UUID,
        country: String?,
        isGoalkeeper: Boolean?,
        bornAfter: LocalDate?
    ): List<Player>
}

@Repository
class PlayerRepositoryImpl(private val entityManager: EntityManager) : PlayerRepositoryCustom {

    override fun findTournamentPlayers(
        tournamentId: UUID,
        country: String?,
        isGoalkeeper: Boolean?,
        bornAfter: LocalDate?
    ): List<Player> {

        val cb = entityManager.criteriaBuilder
        val query = cb.createQuery(Player::class.java)
        val root = query.from(Player::class.java)

        val team = root.join<Player, Team>("team")

        val predicates = mutableListOf<Predicate>()

        predicates += cb.equal(
            team.get<Tournament>("tournament").get<UUID>("id"),
            tournamentId
        )

        country?.let {
            predicates += cb.equal(team.get<String>("name"), it)
        }

        isGoalkeeper?.let {
            if (it) {
                predicates += cb.equal(root.get<PlayerPosition>("position"), PlayerPosition.GOALKEEPER)
            }
        }

        bornAfter?.let {
            predicates += cb.greaterThan(root.get("birthdate"), it)
        }

        query.where(cb.and(*predicates.toTypedArray()))

        return entityManager.createQuery(query).resultList
    }

}