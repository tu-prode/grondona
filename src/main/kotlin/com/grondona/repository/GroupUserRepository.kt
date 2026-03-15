package com.grondona.repository

import com.grondona.model.GroupUser
import com.grondona.model.dto.UserGroupResponse
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.Optional
import java.util.UUID

@Repository
interface GroupUserRepository : JpaRepository<GroupUser, UUID> {

    fun existsByUser_IdAndGroup_Id(userId: UUID, groupId: UUID): Boolean

    fun findByUser_IdAndGroup_Id(userId: UUID, groupId: UUID): Optional<GroupUser>

    fun countByGroup_Id(groupId: UUID): Long

    @Query("""
        SELECT new com.grondona.model.dto.UserGroupResponse(
            gu.group.id,
            gu.group.name,
            (SELECT COUNT(m) FROM GroupUser m WHERE m.group.id = gu.group.id),
            gu.joinedAt
        )
        FROM GroupUser gu
        WHERE gu.user.id = :userId
        ORDER BY gu.joinedAt DESC
    """)
    fun findUserGroupsWithMemberCount(@Param("userId") userId: UUID): List<UserGroupResponse>
}
