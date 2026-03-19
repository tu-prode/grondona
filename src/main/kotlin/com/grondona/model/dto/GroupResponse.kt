package com.grondona.model.dto

import com.fasterxml.jackson.annotation.JsonProperty
import com.grondona.model.Group
import java.time.LocalDateTime
import java.util.UUID

data class GroupResponse(
    val id: UUID,
    val name: String,
    @get:JsonProperty("private")
    val isPrivate: Boolean,
    val maxMembers: Int,
) {
    companion object {
        fun from(group: Group): GroupResponse = GroupResponse(
            id = group.id!!,
            name = group.name,
            isPrivate = group.isPrivate,
            maxMembers = group.maxMembers,
        )
    }
}
