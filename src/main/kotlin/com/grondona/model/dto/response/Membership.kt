package com.grondona.model.dto.response

import com.grondona.model.GroupRole
import java.util.UUID

data class MembershipResponse(
    val groupId: UUID,
    val name: String,
    val memberCount: Long,
    val points: Float = 0f,
    val rank: Int? = null,
    val role: GroupRole = GroupRole.MEMBER,
)
