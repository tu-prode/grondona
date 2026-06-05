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
@Table(name = "match_predictions")
@SQLDelete(sql = "UPDATE match_predictions SET deleted_at = CURRENT_TIMESTAMP WHERE id=?")
@SQLRestriction("deleted_at is null")
data class MatchPrediction(
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
    val updatedAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "deleted_at")
    val deletedAt: LocalDateTime? = null
) {
    fun score() = Score(homeGoals, awayGoals)
}

data class MatchPredictionView(
    val id: UUID? = null,
    val user: User,
    val rank: Int? = null,
    val match: Match,
    val prediction: MatchPrediction? = null,
)

enum class AwardType {
    CHAMPION, TOP_SCORER, BEST_PLAYER, BEST_GOALKEEPER, BEST_YOUNG_PLAYER
}

@Entity
@Table(name = "award_predictions")
@SQLDelete(sql = "UPDATE award_predictions SET deleted_at = CURRENT_TIMESTAMP WHERE id=?")
@SQLRestriction("deleted_at is null")
data class AwardPrediction(
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
    val awardType: AwardType,

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "awarded_team_id")
    @OnDelete(action = OnDeleteAction.CASCADE)
    val team: Team? = null,

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "awarded_player_id")
    @OnDelete(action = OnDeleteAction.CASCADE)
    val player: Player? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: PredictionStatus = PredictionStatus.PENDING,

    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "deleted_at")
    var deletedAt: LocalDateTime? = null
)

data class AwardPredictionView(
    val id: UUID?,
    val user: User,
    val awardPrediction: AwardPrediction?,
)