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
    @Query("DELETE FROM AwardPrediction ap WHERE ap.user.id = :userId")
    fun deleteByUserId(@Param("userId") userId: UUID): Int

    @Query("""
        SELECT ap
        FROM AwardPrediction ap
        WHERE ap.group.tournament.id = :tournamentId
    """)
    fun findByTournamentId(@Param("tournamentId") tournamentId: UUID): List<AwardPrediction>

}
