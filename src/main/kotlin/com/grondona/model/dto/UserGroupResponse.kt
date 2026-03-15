package com.grondona.model.dto

import java.time.LocalDateTime
import java.util.UUID

data class UserGroupResponse(
    val groupId: UUID,
    val name: String,
    val memberCount: Long,
    val joinedAt: LocalDateTime
)
