package com.grondona.repository

import com.grondona.model.Prediction
import com.grondona.model.PredictionView
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface PredictionRepository : JpaRepository<Prediction, UUID>, JpaSpecificationExecutor<Prediction> {

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
        SELECT new com.grondona.model.PredictionView(p.id, gu.user, m, p)
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
        SELECT new com.grondona.model.PredictionView(p.id, gu.user, m, p)
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

}
