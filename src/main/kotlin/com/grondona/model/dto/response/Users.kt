package com.grondona.model.dto.response

import com.grondona.model.User
import java.util.UUID

data class AuthenticatedUserResponse(
    val token: String,
    val userId: UUID,
    val username: String,
    val email: String,
    val fullname: String
)

data class UserResponse(
    val id: UUID,
    val fullname: String,
    val username: String,
    val email: String,
    val uniquePredictions: Boolean,
) {
    companion object {
        fun from(user: User): UserResponse = UserResponse(
            id = user.id!!,
            fullname = user.fullname,
            username = user.username,
            email = user.email,
            uniquePredictions = user.uniquePredictions,
        )
    }
}
