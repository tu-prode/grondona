package com.grondona.model

import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.annotations.OnDelete
import org.hibernate.annotations.OnDeleteAction
import org.hibernate.annotations.SQLDelete
import org.hibernate.annotations.SQLRestriction
import org.hibernate.type.SqlTypes
import java.time.LocalDateTime
import java.util.UUID

enum class GroupRole {
    OWNER, ADMIN, MEMBER, CANDIDATE
}

fun GroupRole.hasAdminAccess() = this == GroupRole.ADMIN || this == GroupRole.OWNER

@Entity
@Table(name = "group_users")
@SQLDelete(sql = "UPDATE group_users SET deleted_at = CURRENT_TIMESTAMP WHERE id=?")
@SQLRestriction("deleted_at is null")
data class GroupUser(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    val user: User,

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "group_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    val group: Group,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var role: GroupRole = GroupRole.MEMBER,

    @Column(nullable = false)
    var points: Float = 0F,

    @Column(nullable = true)
    var rank: Int? = null,

    @Column(name = "joined_at", nullable = true)
    val joinedAt: LocalDateTime? = null,

    @Column(name = "amount_bonus", nullable = false)
    var amountBonus: Int = 0,

    @Column(name = "amount_correct", nullable = false)
    var amountCorrect: Int = 0,

    @Column(name = "amount_partial", nullable = false)
    var amountPartial: Int = 0,

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Enumerated(EnumType.STRING)
    @Column(name = "last_predictions", nullable = false)
    var lastPredictions: List<PredictionStatus> = emptyList(),

    @Column(name = "created_at")
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "updated_at")
    val updatedAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "deleted_at")
    val deletedAt: LocalDateTime? = null
)

data class MembershipView(
    val group: Group,
    val points: Float,
    val rank: Int? = null,
    val role: GroupRole,
    val membersCount: Long,
    val candidatesCount: Long,
)
