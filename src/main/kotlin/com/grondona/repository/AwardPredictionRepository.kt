package com.grondona.repository

import com.grondona.model.AwardPrediction
import com.grondona.model.AwardPredictionView
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Repository
interface AwardPredictionRepository : JpaRepository<AwardPrediction, UUID>, JpaSpecificationExecutor<AwardPrediction> {

    fun findByUserIdAndGroupId(userId: UUID, groupId: UUID): List<AwardPrediction>

    @Query(
        """
        SELECT new com.grondona.model.AwardPredictionView(p.id, gu.user, p)
        FROM GroupUser gu
        LEFT JOIN AwardPrediction p
            ON p.user.id = gu.user.id
            AND p.group.id = gu.group.id
        WHERE gu.group.id = :groupId
        ORDER BY CASE WHEN gu.rank IS NULL THEN 1 ELSE 0 END, gu.rank ASC
    """
    )
    fun findGroupAwardPredictions(groupId: UUID): List<AwardPredictionView>

    @Modifying
    @Transactional
    @Query("DELETE FROM AwardPrediction ap WHERE ap.user.id = :userId AND ap.grou.id = :groupId")
    fun deleteAwardPredictionsForGroup(@Param("userId") userId: UUID, @Param("groupId") groupId: UUID): Int

    @Query("""
        SELECT ap
        FROM AwardPrediction ap
        WHERE ap.group.tournament.id = :tournamentId
    """)
    fun findByTournamentId(@Param("tournamentId") tournamentId: UUID): List<AwardPrediction>

    @Modifying
    @Query("""
        DELETE FROM award_predictions
        WHERE user_id = :userId
        AND group_id = ANY(:groupIds)
    """, nativeQuery = true)
    fun deleteAwardPredictionsForMultipleGroups(
        @Param("userId") userId: UUID,
        @Param("groupIds") groupIds: List<UUID>
    ): Int

    @Modifying
    @Query("""
        INSERT INTO award_predictions (user_id, group_id, award_type, awarded_team_id, awarded_player_id, status, created_at, updated_at)
        SELECT ap.user_id, g.group_id, ap.award_type, ap.awarded_team_id, ap.awarded_player_id, ap.status, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
        FROM award_predictions ap
        CROSS JOIN UNNEST(:groupIds) AS g(group_id)
        WHERE ap.user_id = :userId AND ap.group_id = :masterGroupId AND ap.deleted_at IS NULL
    """, nativeQuery = true)
    fun cloneAwardPredictionsIntoGroups(
        @Param("userId") userId: UUID,
        @Param("masterGroupId") masterGroupId: UUID,
        @Param("groupIds") groupIds: List<UUID>
    ): Int
}
