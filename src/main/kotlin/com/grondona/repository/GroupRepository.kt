package com.grondona.repository

import com.grondona.model.Group
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface GroupRepository : JpaRepository<Group, UUID>, JpaSpecificationExecutor<Group> {
    fun findByNameContainingIgnoreCase(name: String): List<Group>

    fun existsByName(name: String): Boolean
}
