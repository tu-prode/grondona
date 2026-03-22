package com.grondona.model.dto

import com.fasterxml.jackson.annotation.JsonProperty
import javax.validation.constraints.Min
import javax.validation.constraints.Size

data class UpdateGroupRequest(
    @field:Size(max = 100, message = "Name must be at most 100 characters")
    val name: String? = null,

    @JsonProperty("private")
    val isPrivate: Boolean? = null,

    @field:Min(value = 1, message = "Max members must be at least 1")
    val maxMembers: Int? = null
)
