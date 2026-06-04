package com.grondona.repository

import com.grondona.model.MatchPrediction
import com.grondona.model.PredictionStatus
import com.grondona.model.MatchPredictionView
import jakarta.persistence.EntityManager
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Repository
interface MatchPredictionRepository : JpaRepository<MatchPrediction, UUID>, JpaSpecificationExecutor<MatchPrediction>, MatchPredictionRepositoryCustom {

    fun findByUserIdAndGroupId(userId: UUID, groupId: UUID): List<MatchPrediction>

    @Query(
        """
        SELECT new com.grondona.model.MatchPredictionView(p.id, gu.user, gu.rank, m, p)
        FROM GroupUser gu
        JOIN Match m ON m.tournament.id = gu.group.tournament.id
        LEFT JOIN MatchPrediction p
            ON p.user.id = gu.user.id
            AND p.group.id = gu.group.id
            AND p.match.id = m.id
        WHERE gu.group.id = :groupId
        ORDER BY CASE WHEN m.startedAt IS NULL THEN 1 ELSE 0 END, m.startedAt ASC
    """
    )
    fun findGroupPredictions(@Param("groupId") groupId: UUID): List<MatchPredictionView>

    @Query(
        """
        SELECT new com.grondona.model.MatchPredictionView(p.id, gu.user, gu.rank, m, p)
        FROM GroupUser gu
        JOIN Match m ON m.tournament.id = gu.group.tournament.id
        LEFT JOIN MatchPrediction p
            ON p.user.id = gu.user.id
            AND p.group.id = gu.group.id
            AND p.match.id = m.id
        WHERE gu.group.id = :groupId AND gu.user.id = :userId
        ORDER BY CASE WHEN m.startedAt IS NULL THEN 1 ELSE 0 END, m.startedAt ASC
    """)
    fun findGroupPredictionsForUser(@Param("groupId") groupId: UUID, @Param("userId") userId: UUID): List<MatchPredictionView>

    @Query(
        """
        SELECT new com.grondona.model.MatchPredictionView(p.id, gu.user, gu.rank, m, p)
        FROM GroupUser gu
        JOIN Match m ON m.id = :matchId
        LEFT JOIN MatchPrediction p
            ON p.user.id = gu.user.id
            AND p.group.id = gu.group.id
            AND p.match.id = :matchId
        WHERE gu.group.id = :groupId
        ORDER BY CASE WHEN gu.rank IS NULL THEN 1 ELSE 0 END, gu.rank ASC
    """)
    fun findGroupPredictionsForMatch(@Param("groupId") groupId: UUID, @Param("matchId")matchId: UUID): List<MatchPredictionView>

    fun findByStatusAndMatchIdIn(status: PredictionStatus, matchIds: List<UUID>): List<MatchPrediction>

    @Query(
        """
        SELECT mp
        FROM MatchPrediction mp
        WHERE mp.group.tournament.id = :tournamentId
    """)
    fun findByTournamentId(@Param("tournamentId") tournamentId: UUID): List<MatchPrediction>

    @Modifying
    @Query("DELETE FROM MatchPrediction mp WHERE mp.group.id = :groupId")
    fun deleteByGroupId(@Param("groupId") groupId: UUID): Int

    @Modifying
    @Query("DELETE FROM MatchPrediction mp WHERE mp.user.id = :userId")
    fun deleteByUserId(@Param("userId") userId: UUID): Int
}

interface MatchPredictionRepositoryCustom {
    fun upsert(prediction: MatchPrediction): MatchPrediction
    fun upsertAll(predictions: List<MatchPrediction>): List<MatchPrediction>
}

@Repository
class MatchPredictionRepositoryImpl(
    private val entityManager: EntityManager
) : MatchPredictionRepositoryCustom {

    @Transactional
    override fun upsert(prediction: MatchPrediction): MatchPrediction =
        upsertAll(listOf(prediction)).first()

    @Transactional
    override fun upsertAll(predictions: List<MatchPrediction>): List<MatchPrediction> {
        if (predictions.isEmpty()) return emptyList()

        val results = predictions.map { prediction ->
            val existing = entityManager.createQuery(
                """
                SELECT mp FROM MatchPrediction mp
                WHERE mp.user.id = :userId
                AND mp.group.id = :groupId
                AND mp.match.id = :matchId
                """.trimIndent(),
                MatchPrediction::class.java
            )
                .setParameter("userId", prediction.user.id)
                .setParameter("groupId", prediction.group.id)
                .setParameter("matchId", prediction.match.id)
                .resultList
                .firstOrNull()

            existing?.also {
                it.homeGoals = prediction.homeGoals
                it.awayGoals = prediction.awayGoals
                it.status = prediction.status
            } ?: prediction.also(entityManager::persist)
        }

        entityManager.flush()
        return results
    }
}