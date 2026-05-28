package com.grondona.model

import jakarta.persistence.*
import org.hibernate.annotations.OnDelete
import org.hibernate.annotations.OnDeleteAction
import org.hibernate.annotations.SQLDelete
import org.hibernate.annotations.SQLRestriction
import java.time.LocalDateTime
import java.time.ZonedDateTime
import java.util.UUID

enum class MatchOutcome {
    HOME, TIE, AWAY
}

data class Score(
    val homeGoals: Int,
    val awayGoals: Int,
) {
    fun goals() = homeGoals + awayGoals
    fun outcome() = when {
        homeGoals > awayGoals -> MatchOutcome.HOME
        homeGoals < awayGoals -> MatchOutcome.AWAY
        else -> MatchOutcome.TIE
    }
}

enum class MatchStatus {
    NOT_STARTED, IN_PROGRESS, FINISHED, SUSPENDED
}

enum class MatchSubstatus(val label: String) {
    HALFTIME("ET"), PENALTIES("PEN"), FINISHED("FIN"), SUSPENDED("SUSP")
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
    val code: String,

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
    val homeQuota: Float = 0F,

    @Column(name = "away_quota", nullable = false)
    val awayQuota: Float = 0F,

    @Column(name = "draw_quota", nullable = false)
    val drawQuota: Float = 0F,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val status: MatchStatus = MatchStatus.NOT_STARTED,

    @Column(name = "substatus")
    val substatus: String? = null,

    @Column(name = "started_at")
    val startedAt: ZonedDateTime,

    @Column(name = "finished_at")
    val finishedAt: ZonedDateTime? = null,

    @Column(name = "home_goals")
    val homeGoals: Int? = null,

    @Column(name = "away_goals")
    val awayGoals: Int? = null,

    @Column(name = "home_penalties")
    val homePenalties: Int? = null,

    @Column(name = "away_penalties")
    val awayPenalties: Int? = null,

    @Column(name = "has_multiplier")
    val hasMultiplier: Boolean = false,

    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    val updatedAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "deleted_at")
    val deletedAt: LocalDateTime? = null
) {
    fun score(): Score? = homeGoals?.let { home -> awayGoals?.let { away -> Score(home, away) } }
}
