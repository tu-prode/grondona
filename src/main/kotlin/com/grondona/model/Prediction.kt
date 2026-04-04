package com.grondona.model

import jakarta.persistence.*
import org.hibernate.annotations.OnDelete
import org.hibernate.annotations.OnDeleteAction
import org.hibernate.annotations.SQLDelete
import org.hibernate.annotations.SQLRestriction
import java.time.LocalDateTime
import java.util.UUID

enum class PredictionStatus {
    BONUS, CORRECT, PARTIAL, INCORRECT, PENDING, MISSING
}

@Entity
@Table(name = "predictions")
@SQLDelete(sql = "UPDATE predictions SET deleted_at = CURRENT_TIMESTAMP WHERE id=?")
@SQLRestriction("deleted_at is null")
data class Prediction(
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

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "match_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    val match: Match,

    @Column(name = "home_goals", nullable = false)
    var homeGoals: Int,

    @Column(name = "away_goals", nullable = false)
    var awayGoals: Int,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: PredictionStatus = PredictionStatus.PENDING,

    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "deleted_at")
    var deletedAt: LocalDateTime? = null
) {
    fun score() = Score(homeGoals, awayGoals)
}

data class PredictionView(
    val id: UUID?,
    val user: User,
    val rank: Int?,
    val match: Match,
    val prediction: Prediction?,
)
