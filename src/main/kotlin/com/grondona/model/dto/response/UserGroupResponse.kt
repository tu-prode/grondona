package com.grondona.model.dto.response

import com.grondona.model.GroupRole
import java.util.UUID

data class UserGroupResponse(
    val groupId: UUID,
    val name: String,
    val memberCount: Long,
    val points: Float = 0f,
    val rank: Int? = null,
    val role: GroupRole = GroupRole.MEMBER,
) {
    constructor(
        groupId: UUID,
        name: String,
        memberCount: Long,
        points: Float,
        role: GroupRole = GroupRole.MEMBER,
    ) : this(groupId, name, memberCount, points, null, role)
}
