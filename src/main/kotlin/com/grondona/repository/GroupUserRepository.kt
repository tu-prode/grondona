package com.grondona.repository

import com.grondona.model.GroupUser
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.Optional
import java.util.UUID

@Repository
interface GroupUserRepository : JpaRepository<GroupUser, UUID> {

    fun existsByUserIdAndGroupId(userId: UUID, groupId: UUID): Boolean

    fun findByUserIdAndGroupId(userId: UUID, groupId: UUID): Optional<GroupUser>

    fun countByGroupId(groupId: UUID): Long

    fun findByUserIdOrderByJoinedAtDesc(userId: UUID): List<GroupUser>
}
