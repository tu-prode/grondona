package com.grondona.repository

import com.grondona.model.AwardPrediction
import com.grondona.model.GroupUser
import com.grondona.model.MembershipView
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.Optional
import java.util.UUID

@Repository
interface MembershipRepository : JpaRepository<GroupUser, UUID> {

    @Query("""
        SELECT CASE WHEN COUNT(u) > 0 THEN true ELSE false END
        FROM GroupUser u
        WHERE u.user.id = :userId AND u.group.id = :groupId AND u.role <>  'CANDIDATE'
    """)
    fun isMember(@Param("userId") userId: UUID, @Param("groupId") groupId: UUID): Boolean

    @Query("""
        SELECT u
        FROM GroupUser u
        WHERE u.user.id = :userId AND u.group.id = :groupId AND u.role <>  'CANDIDATE'
    """)
    fun findMember(@Param("userId") userId: UUID, @Param("groupId") groupId: UUID): Optional<GroupUser>

    @Query("""
        SELECT u
        FROM GroupUser u
        WHERE u.user.id = :userId AND u.group.id = :groupId AND u.role =  'CANDIDATE'
    """)
    fun findCandidate(@Param("userId") userId: UUID, @Param("groupId") groupId: UUID): Optional<GroupUser>

    @Query("""
        SELECT u
        FROM GroupUser u
        WHERE u.group.id = :groupId AND u.role <>  'CANDIDATE'
    """)
    fun findMembers(@Param("groupId") groupId: UUID): List<GroupUser>

    @Query("""
        SELECT u
        FROM GroupUser u
        WHERE u.group.id = :groupId
    """)
    fun findEveryGroupUser(@Param("groupId") groupId: UUID): List<GroupUser>

    @Query("""
        SELECT new com.grondona.model.MembershipView(
            gu.group,
            gu.points,
            gu.rank,
            gu.role,
            (SELECT COUNT(m) FROM GroupUser m WHERE m.group.id = gu.group.id AND m.role <> 'CANDIDATE' AND m.deletedAt IS NULL),
            (SELECT COUNT(m) FROM GroupUser m WHERE m.group.id = gu.group.id AND m.role = 'CANDIDATE' AND m.deletedAt IS NULL)
        )
        FROM GroupUser gu
        WHERE gu.user.id = :userId AND gu.role <> 'CANDIDATE'
        ORDER BY gu.joinedAt DESC
    """
    )
    fun findUserMemberships(@Param("userId") userId: UUID): List<MembershipView>

    @Query("""
        SELECT u
        FROM GroupUser u
        WHERE u.user.id = :userId AND u.role <>  'CANDIDATE'
    """)
    fun findUserGroups(@Param("userId") userId: UUID): List<GroupUser>

    @Query("""
        SELECT COUNT(u)
        FROM GroupUser u
        WHERE u.group.id = :groupId AND u.role <>  'CANDIDATE'
    """)
    fun countMembers(groupId: UUID): Long

    @Query("""
        SELECT gu
        FROM GroupUser gu
        WHERE gu.role = 'CANDIDATE' AND gu.group.id IN (
            SELECT gu2.group.id
            FROM GroupUser gu2
            WHERE gu2.user.id = :userId AND gu2.role IN ('ADMIN', 'OWNER')
        )
    """)
    fun findJoinRequests(@Param("userId") userId: UUID): List<GroupUser>

    @Query("""
        SELECT gu
        FROM GroupUser gu
        WHERE gu.group.tournament.id = :tournamentId
    """)
    fun findByTournamentId(@Param("tournamentId") tournamentId: UUID): List<GroupUser>

}
