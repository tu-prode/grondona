package com.grondona.model.dto.response

import com.grondona.model.Group
import com.grondona.model.GroupRole
import com.grondona.model.MembershipView
import com.grondona.model.User
import com.grondona.utils.round

data class JoinRequestResponse(
    val group: GroupResponse,
    val users: List<UserResponse>,
) {
    companion object {
        fun from(group: Group, users: List<User>) =
            JoinRequestResponse(
                group = GroupResponse.from(group),
                users = users.map(UserResponse::from)
            )
    }
}

data class MembershipResponse(
    val group: GroupResponse,
    val points: Float = 0f,
    val rank: Int? = null,
    val role: GroupRole = GroupRole.MEMBER,
    val memberCount: Int,
    val candidatesCount: Int? = null,
) {
    companion object {
        fun fromMembershipView(membership: MembershipView) =
            MembershipResponse(
                group = GroupResponse.from(membership.group),
                points = membership.points.round(),
                rank = membership.rank,
                role = membership.role,
                memberCount = membership.membersCount.toInt(),
                candidatesCount = if (membership.role.hasAdminAccess()) membership.candidatesCount.toInt() else null
            )
    }
}
