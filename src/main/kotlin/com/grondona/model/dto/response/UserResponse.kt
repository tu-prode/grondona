package com.grondona.model.dto.response

import com.grondona.model.User
import java.util.UUID

data class UserResponse(
    val id: UUID,
    val fullname: String,
    val username: String,
    val email: String,
) {
    companion object {
        fun from(user: User): UserResponse = UserResponse(
            id = user.id!!,
            fullname = user.fullname,
            username = user.username,
            email = user.email,
        )
    }
}
