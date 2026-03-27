package com.grondona.repository

import com.grondona.model.Prediction
import com.grondona.model.dto.response.GroupPredictionsResponse
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface PredictionRepository : JpaRepository<Prediction, UUID>, JpaSpecificationExecutor<Prediction> {

    fun findAllByUserIdAndGroupIdOrderByMatchStartedAt(userId: UUID, groupId: UUID): List<Prediction>

    @Query("""
        SELECT new com.grondona.model.Prediction(
            p.id,
            gu.user,
            gu.group,
            m,
            p.homeGoals,
            p.awayGoals,
            p.createdAt,
            p.updatedAt,
            p.deletedAt
        )
        FROM GroupUser gu
        JOIN Match m ON m.id = :matchId
        LEFT JOIN Prediction p
            ON p.user.id = gu.user.id
            AND p.group.id = gu.group.id
            AND p.match.id = :matchId
        WHERE gu.group.id = :groupId
        ORDER BY CASE WHEN gu.rank IS NULL THEN 1 ELSE 0 END, gu.rank ASC
    """)
    fun findGroupPredictionsForMatch(groupId: UUID, matchId: UUID): List<Prediction>

}
