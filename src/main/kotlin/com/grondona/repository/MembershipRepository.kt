package com.grondona.repository

import com.grondona.model.GroupUser
import com.grondona.model.dto.response.MembershipResponse
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.Optional
import java.util.UUID

@Repository
interface MembershipRepository : JpaRepository<GroupUser, UUID> {

    fun existsByUserIdAndGroupId(userId: UUID, groupId: UUID): Boolean

    fun findByUserIdAndGroupId(userId: UUID, groupId: UUID): Optional<GroupUser>

    fun findByGroupId(groupId: UUID): List<GroupUser>

    fun findByUserId(userId: UUID): List<GroupUser>

    fun countByGroupId(groupId: UUID): Long

}
