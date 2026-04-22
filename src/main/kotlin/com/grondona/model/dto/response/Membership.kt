package com.grondona.model.dto.response

import com.grondona.model.GroupRole
import com.grondona.model.MembershipView
import com.grondona.utils.round
import java.util.UUID

data class MembershipResponse(
    val group: GroupResponse,
    val memberCount: Int,
    val points: Float = 0f,
    val rank: Int? = null,
    val role: GroupRole = GroupRole.MEMBER,
) {
    companion object {
        fun fromMembershipView(membership: MembershipView) = MembershipResponse(
            group = GroupResponse.from(membership.group),
            memberCount = membership.membersCount.toInt(),
            points = membership.points.round(),
            rank = membership.rank,
            role = membership.role,
        )
    }
}
