package com.grondona.model

import jakarta.persistence.*
import org.hibernate.annotations.OnDelete
import org.hibernate.annotations.OnDeleteAction
import org.hibernate.annotations.SQLDelete
import org.hibernate.annotations.SQLRestriction
import java.time.LocalDateTime
import java.util.UUID

enum class MatchStatus {
    NOT_STARTED, IN_PROGRESS, FINISHED,
}

@Entity
@Table(name = "matches")
@SQLDelete(sql = "UPDATE matches SET deleted_at = CURRENT_TIMESTAMP WHERE id=?")
@SQLRestriction("deleted_at is null")
data class Match(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,

    @Column(nullable = false)
    var matchKey: String,

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "tournament_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    val tournament: Tournament,

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "home_team_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    val homeTeam: Team,

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "away_team_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    val awayTeam: Team,

    @Column(name = "home_quota", nullable = false)
    var homeQuota: Float = 1F,

    @Column(name = "away_quota", nullable = false)
    var awayQuota: Float = 1F,

    @Column(name = "tie_quota", nullable = false)
    var tieQuota: Float = 1F,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: MatchStatus = MatchStatus.NOT_STARTED,

    @Column(name = "started_at")
    var startedAt: LocalDateTime? = null,

    @Column(name = "finished_at")
    var finishedAt: LocalDateTime? = null,

    @Column(name = "home_goals")
    var homeGoals: Int? = null,

    @Column(name = "away_goals")
    var awayGoals: Int? = null,

    @Column(name = "home_penalties")
    var homePenalties: Int? = null,

    @Column(name = "away_penalties")
    var awayPenalties: Int? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "deleted_at")
    var deletedAt: LocalDateTime? = null
)
