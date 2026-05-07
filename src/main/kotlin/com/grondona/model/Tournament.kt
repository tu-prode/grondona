package com.grondona.model

import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.annotations.SQLDelete
import org.hibernate.annotations.SQLRestriction
import org.hibernate.type.SqlTypes
import java.time.LocalDateTime
import java.util.UUID

enum class TournamentStatus {
    NOT_STARTED, IN_PROGRESS, FINISHED,
}

data class ExtendedAwards(
    val champion: Team,
    val topScorer: Player,
    val bestPlayer: Player,
    val bestGoalkeeper: Player,
    val bestYoungPlayer: Player,
)

data class Awards(
    val champion: UUID,
    val topScorer: UUID,
    val bestPlayer: UUID,
    val bestGoalkeeper: UUID,
    val bestYoungPlayer: UUID,
)

@Entity
@Table(name = "tournaments")
@SQLDelete(sql = "UPDATE tournaments SET deleted_at = CURRENT_TIMESTAMP WHERE id=?")
@SQLRestriction("deleted_at is null")
data class Tournament(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,

    @Column(nullable = false, unique = true)
    val name: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val status: TournamentStatus = TournamentStatus.NOT_STARTED,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = true)
    val awards: Awards? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    val updatedAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "deleted_at")
    val deletedAt: LocalDateTime? = null
)
