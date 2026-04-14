package com.grondona.repository

import com.grondona.model.AwardPrediction
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

    fun findByUserId(userId: UUID): List<AwardPrediction>

    fun findByGroupId(groupId: UUID): List<AwardPrediction>

    @Modifying
    @Transactional
    @Query("DELETE FROM AwardPrediction ap WHERE ap.user.id = :userId")
    fun deleteByUserId(@Param("userId") userId: UUID): Int

}
