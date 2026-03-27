package com.grondona.model.dto.request

import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.Size

data class UpdateGroupRequest(
    @field:Size(max = 100, message = "Name must be at most 100 characters")
    val name: String? = null,

    @JsonProperty("private")
    val isPrivate: Boolean? = null,

    @field:Min(value = 1, message = "Max members must be at least 1")
    val maxMembers: Int? = null
)
