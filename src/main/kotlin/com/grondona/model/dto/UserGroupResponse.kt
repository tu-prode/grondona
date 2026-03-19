package com.grondona.model.dto

import java.time.LocalDateTime
import java.util.UUID

data class UserGroupResponse(
    val groupId: UUID,
    val name: String,
    val memberCount: Long,
    val joinedAt: LocalDateTime,
    val points: Float = 0F,
    val rank: Int? = null
) {
    constructor(
        groupId: UUID,
        name: String,
        memberCount: Long,
        joinedAt: LocalDateTime,
        points: Float,
    ) : this(groupId, name, memberCount, joinedAt, points, null)
}
