package com.grondona.model

import jakarta.persistence.*
import org.hibernate.annotations.OnDelete
import org.hibernate.annotations.OnDeleteAction
import org.hibernate.annotations.SQLDelete
import org.hibernate.annotations.SQLRestriction
import java.time.LocalDateTime
import java.util.UUID

data class Standing(
    val rank: Int,
    val user: User,
    val points: Float,
    val lastPredictions: List<PredictionStatus>
)

@Entity
@Table(name = "groups")
@SQLDelete(sql = "UPDATE groups SET deleted_at = CURRENT_TIMESTAMP WHERE id=?")
@SQLRestriction("deleted_at is null")
data class Group(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "tournament_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    val tournament: Tournament,

    @Column(nullable = false, unique = true, length = 100)
    var name: String,

    @Column(name = "is_private", nullable = false)
    var isPrivate: Boolean = false,

    @Column(name = "max_members", nullable = false)
    var maxMembers: Int = 128,

    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "deleted_at")
    var deletedAt: LocalDateTime? = null,

    @OneToMany(mappedBy = "group", fetch = FetchType.LAZY)
    val members: MutableList<GroupUser> = emptyList<GroupUser>().toMutableList()
)
