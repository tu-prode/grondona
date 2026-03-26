package com.grondona.repository

import com.grondona.model.Prediction
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface PredictionRepository : JpaRepository<Prediction, UUID>, JpaSpecificationExecutor<Prediction>
