package com.grondona.security

import java.util.UUID

data class JwtUserPrincipal(
    val userId: UUID,
    val username: String
)
