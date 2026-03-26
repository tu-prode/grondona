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

    fun existsByUserIdAndGroupId(userId: UUID, groupId: UUID): Boolean

    fun findByUserIdAndGroupId(userId: UUID, groupId: UUID): Optional<GroupUser>

    fun countByGroupId(groupId: UUID): Long

    @Query(
        """
        SELECT new com.grondona.model.dto.UserGroupResponse(
            gu.group.id,
            gu.group.name,
            (SELECT COUNT(m) FROM GroupUser m WHERE m.group.id = gu.group.id AND m.deletedAt IS NULL),
            gu.points,
            gu.role
        )
        FROM GroupUser gu
        WHERE gu.user.id = :userId
        ORDER BY gu.joinedAt DESC
    """
    )
    fun findUserGroups(@Param("userId") userId: UUID): List<UserGroupResponse>
}
