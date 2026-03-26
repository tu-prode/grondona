package com.grondona.model.dto.response

import java.util.UUID

data class AuthResponse(
    val token: String,
    val userId: UUID,
    val username: String,
    val email: String,
    val fullname: String
)
