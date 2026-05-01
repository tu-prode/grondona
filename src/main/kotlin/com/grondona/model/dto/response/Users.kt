package com.grondona.model.dto.response

import com.grondona.model.GroupUser
import com.grondona.model.User
import com.grondona.model.UserPermissions
import java.util.UUID

data class AuthenticatedUserResponse(
    val token: String,
    val userId: UUID,
    val username: String,
    val email: String,
    val fullname: String,
    val permissions: UserPermissions,
)

data class UserResponse(
    val id: UUID,
    val fullname: String,
    val username: String,
    val email: String,
    val permissions: UserPermissions,
    val uniquePredictions: Boolean,
    val joinRequests: List<JoinRequestResponse> = emptyList(),
) {
    companion object {
        fun from(user: User): UserResponse = UserResponse(
            id = user.id!!,
            fullname = user.fullname,
            username = user.username,
            email = user.email,
            permissions = user.permissions,
            uniquePredictions = user.uniquePredictions,
        )

        fun withJoinRequests(user: User, joinRequests: List<GroupUser>): UserResponse = UserResponse(
            id = user.id!!,
            fullname = user.fullname,
            username = user.username,
            email = user.email,
            permissions = user.permissions,
            uniquePredictions = user.uniquePredictions,
            joinRequests = joinRequests.groupBy { it.group }.map { (group, users) ->
                JoinRequestResponse.from(group, users.map { it.user })
            }
        )
    }
}
