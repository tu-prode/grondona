package com.grondona.model.dto.request

import com.fasterxml.jackson.annotation.JsonProperty
import javax.validation.constraints.Min
import javax.validation.constraints.NotBlank
import javax.validation.constraints.Size

data class CreateGroupRequest(
    @field:NotBlank(message = "Name is required")
    @field:Size(max = 100, message = "Name must be at most 100 characters")
    val name: String,

    @JsonProperty("private")
    val isPrivate: Boolean = false,

    @field:Min(value = 1, message = "Max members must be at least 1")
    val maxMembers: Int
)
