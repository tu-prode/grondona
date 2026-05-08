package com.grondona.model.dto.request

import com.fasterxml.jackson.annotation.JsonProperty
import com.grondona.model.GroupRole
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class CreateGroupRequest(
    @field:NotBlank(message = "Name is required")
    @field:Size(max = 100, message = "Name must be at most 100 characters")
    val name: String,

    @JsonProperty("private")
    val isPrivate: Boolean = false,

    @field:Min(value = 1, message = "Max members must be at least 1")
    val maxMembers: Int
)

data class UpdateGroupRequest(
    @field:Size(max = 100, message = "Name must be at most 100 characters")
    val name: String? = null,

    @JsonProperty("private")
    val isPrivate: Boolean? = null,

    @field:Min(value = 1, message = "Max members must be at least 1")
    val maxMembers: Int? = null
)

data class UpdateMemberRequest(
    val role: GroupRole? = null,
)
