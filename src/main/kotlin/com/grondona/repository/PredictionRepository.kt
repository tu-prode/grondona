package com.grondona.repository

import com.grondona.model.Group
import com.grondona.model.Match
import com.grondona.model.Prediction
import com.grondona.model.PredictionStatus
import com.grondona.model.PredictionView
import com.grondona.model.User
import jakarta.persistence.EntityManager
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Repository
interface PredictionRepository : JpaRepository<Prediction, UUID>, JpaSpecificationExecutor<Prediction>, PredictionRepositoryCustom {

    @Query(
        """
    INSERT INTO predictions (id, user_id, group_id, match_id, home_goals, away_goals)
    VALUES (gen_random_uuid(), :#{#prediction.user.id}, :#{#prediction.group.id}, :#{#prediction.match.id}, :#{#prediction.homeGoals}, :#{#prediction.awayGoals})
    ON CONFLICT (user_id, group_id, match_id) WHERE deleted_at IS NULL
    DO UPDATE SET
        home_goals = EXCLUDED.home_goals,
        away_goals = EXCLUDED.away_goals,
        updated_at = CURRENT_TIMESTAMP
    RETURNING *
    """,
        nativeQuery = true
    )
    fun upsert(@Param("prediction") prediction: Prediction): Prediction

    @Query(
        """
        SELECT new com.grondona.model.PredictionView(p.id, gu.user, gu.rank, m, p)
        FROM GroupUser gu
        JOIN Match m ON m.tournament.id = gu.group.tournament.id
        LEFT JOIN Prediction p
            ON p.user.id = gu.user.id
            AND p.group.id = gu.group.id
            AND p.match.id = m.id
        WHERE gu.group.id = :groupId AND gu.user.id = :userId
        ORDER BY CASE WHEN m.startedAt IS NULL THEN 1 ELSE 0 END, m.startedAt ASC
    """
    )
    fun findGroupPredictionsForUser(groupId: UUID, userId: UUID): List<PredictionView>

    @Query(
        """
        SELECT new com.grondona.model.PredictionView(p.id, gu.user, gu.rank, m, p)
        FROM GroupUser gu
        JOIN Match m ON m.id = :matchId
        LEFT JOIN Prediction p
            ON p.user.id = gu.user.id
            AND p.group.id = gu.group.id
            AND p.match.id = :matchId
        WHERE gu.group.id = :groupId
        ORDER BY CASE WHEN gu.rank IS NULL THEN 1 ELSE 0 END, gu.rank ASC
    """
    )
    fun findGroupPredictionsForMatch(groupId: UUID, matchId: UUID): List<PredictionView>

    fun findByGroupIdAndMatchIdIn(groupId: UUID, matchIds: List<UUID>): List<Prediction>

    fun findByStatusAndMatchIdIn(status: PredictionStatus, matchIds: List<UUID>): List<Prediction>
}

interface PredictionRepositoryCustom {
    fun upsertAll(predictions: List<Prediction>): List<Prediction>
}

@Repository
class PredictionRepositoryImpl(
    private val entityManager: EntityManager
) : PredictionRepositoryCustom {

    @Transactional
    override fun upsertAll(predictions: List<Prediction>): List<Prediction> {
        if (predictions.isEmpty()) return emptyList()

        val valuesClause = predictions.joinToString(",") { "(gen_random_uuid(), ?, ?, ?, ?, ?, ?)" }
        val sql = """
            INSERT INTO predictions (id, user_id, group_id, match_id, home_goals, away_goals, status)
            VALUES $valuesClause
            ON CONFLICT (user_id, group_id, match_id) WHERE deleted_at IS NULL
            DO UPDATE SET
                home_goals = EXCLUDED.home_goals,
                away_goals = EXCLUDED.away_goals,
                updated_at = CURRENT_TIMESTAMP
            RETURNING *
        """

        val session = entityManager.unwrap(org.hibernate.Session::class.java)

        return session.doReturningWork { connection ->
            connection.prepareStatement(sql).use { stmt ->
                var i = 1
                for (p in predictions) {
                    stmt.setObject(i++, p.user.id)
                    stmt.setObject(i++, p.group.id)
                    stmt.setObject(i++, p.match.id)
                    stmt.setInt(i++, p.homeGoals)
                    stmt.setInt(i++, p.awayGoals)
                    stmt.setString(i++, p.status.name)
                }

                val rs = stmt.executeQuery()
                val results = mutableListOf<Prediction>()

                while (rs.next()) {
                    results.add(
                        Prediction(
                            id = rs.getObject("id", UUID::class.java),
                            user = entityManager.getReference(
                                User::class.java,
                                rs.getObject("user_id", UUID::class.java)
                            ),
                            group = entityManager.getReference(
                                Group::class.java,
                                rs.getObject("group_id", UUID::class.java)
                            ),
                            match = entityManager.getReference(
                                Match::class.java,
                                rs.getObject("match_id", UUID::class.java)
                            ),
                            homeGoals = rs.getInt("home_goals"),
                            awayGoals = rs.getInt("away_goals"),
                            status = PredictionStatus.valueOf(rs.getString("status")),
                            createdAt = rs.getTimestamp("created_at").toLocalDateTime(),
                            updatedAt = rs.getTimestamp("updated_at").toLocalDateTime(),
                            deletedAt = rs.getTimestamp("deleted_at")?.toLocalDateTime()
                        )
                    )
                }
                results
            }
        }
    }
}