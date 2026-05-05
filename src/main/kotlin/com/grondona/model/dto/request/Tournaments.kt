package com.grondona.model.dto.request

import com.grondona.model.Awards
import com.grondona.model.PlayerPosition
import com.grondona.model.TournamentStatus
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import org.springframework.format.annotation.DateTimeFormat
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

data class CreateTournamentRequest(
    @field:NotBlank(message = "Name is required")
    val name: String,

    val status: TournamentStatus? = TournamentStatus.NOT_STARTED
)

data class UpdateTournamentRequest(
    val name: String? = null,

    val status: TournamentStatus? = null,

    val awards: Awards? = null,
)

data class CreateTeamRequest(
    @field:NotBlank(message = "Name is required")
    val name: String,

    @field:NotBlank(message = "Code is required")
    val code: String,

    @field:NotBlank(message = "Icon is required")
    val icon: String,
)

data class CreatePlayerRequest(
    @field:NotBlank(message = "Name is required")
    val name: String,

    @field:NotNull(message = "Position is required")
    val position: PlayerPosition,

    @field:NotNull(message = "Team is required")
    val team: UUID,

    @field:NotNull(message = "Birthdate is required")
    @field:DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    val birthdate: LocalDate,
)

data class CreateMatchRequest(
    @field:NotBlank(message = "Code is required")
    val code: String,

    @field:NotNull(message = "Home team is required")
    val homeTeam: UUID,

    @field:NotNull(message = "Away team is required")
    val awayTeam: UUID,

    @field:NotNull(message = "Start date is required")
    @field:DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    val startedAt: LocalDateTime,

    val hasMultiplier: Boolean? = null,
)
