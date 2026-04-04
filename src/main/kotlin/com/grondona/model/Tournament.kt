package com.grondona.model

import jakarta.persistence.*
import org.hibernate.annotations.SQLDelete
import org.hibernate.annotations.SQLRestriction
import java.time.LocalDateTime
import java.util.UUID

enum class TournamentStatus {
    NOT_STARTED, IN_PROGRESS, FINISHED,
}

@Entity
@Table(name = "tournaments")
@SQLDelete(sql = "UPDATE tournaments SET deleted_at = CURRENT_TIMESTAMP WHERE id=?")
@SQLRestriction("deleted_at is null")
data class Tournament(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,

    @Column(nullable = false, unique = true)
    var name: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: TournamentStatus = TournamentStatus.NOT_STARTED,

    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "deleted_at")
    var deletedAt: LocalDateTime? = null
)
