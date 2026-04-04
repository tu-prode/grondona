package com.grondona.model

import com.grondona.utils.WorldCupEngine
import jakarta.persistence.*
import org.hibernate.annotations.OnDelete
import org.hibernate.annotations.OnDeleteAction
import org.hibernate.annotations.SQLDelete
import org.hibernate.annotations.SQLRestriction
import java.time.LocalDateTime
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
    var code: String,

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

    @Column(name = "substatus")
    var substatus: String? = null,

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
) {
    fun score(): Score? = homeGoals?.let { home -> awayGoals?.let { away -> Score(home, away) } }
}

// Data class for matches retrieved from LiveScoreAPI: https://live-score-api.com/documentation
data class ExternalMatch(
    val home: String,
    val away: String,
    val homeGoals: Int,
    val awayGoals: Int,
    val minutes: Int,
    val half: Int,
    val status: String,
    val homeOdds: Float = 1f,
    val tieOdds: Float = 1f,
    val awayOdds: Float = 1f,
) {
    private enum class Status { TO_START, IN_PLAY, COMPLETED }

    fun toMatchUpdated(matches: List<Match>): Pair<Match, Boolean>? {
        var changedToFinished = false
        val match = matches.filter { it.status != MatchStatus.FINISHED }
            .firstOrNull { it.homeTeam.name == home && it.awayTeam.name == away }?.also {
                when (status) {
                    Status.IN_PLAY.name -> {
                        it.homeGoals = homeGoals
                        it.awayGoals = awayGoals
                        it.status = MatchStatus.IN_PROGRESS
                        it.substatus = when {
                            half == 1 && minutes <= 45 -> "$minutes' PT"
                            half == 1 && minutes > 45 -> "45+${minutes - 45}' PT"
                            half == 2 && minutes <= 90 -> "${minutes - 45}' ST"
                            half == 2 && minutes > 90 -> "45+${minutes - 90}' ST"
                            else -> null
                        }
                    }

                    Status.COMPLETED.name -> {
                        if (it.status == MatchStatus.IN_PROGRESS) {
                            changedToFinished = true
                        }
                        it.homeGoals = homeGoals
                        it.awayGoals = awayGoals
                        it.status = MatchStatus.FINISHED
                        it.substatus = "FINALIZADO"
                        it.finishedAt = it.finishedAt ?: LocalDateTime.now()
                    }
                }
            }

        return match?.let { Pair(it, changedToFinished) }
    }

    fun toQuotasUpdated(matches: List<Match>): Match? =
        matches.filter { it.status == MatchStatus.NOT_STARTED && WorldCupEngine.isMatchUnlocked(it) }
            .firstOrNull { it.homeTeam.name == home && it.awayTeam.name == away }?.also {
                when (status) {
                    Status.TO_START.name -> {
                        it.homeQuota = homeOdds
                        it.tieQuota = tieOdds
                        it.awayQuota = awayOdds
                    }
                }
            }
}