package com.grondona.model.dto.request

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.Size

data class UpdateUserRequest(
    val fullname: String? = null,

    @field:Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters")
    val username: String? = null,

    @field:Email(message = "Email must be valid")
    val email: String? = null,

    @field:Size(min = 6, message = "Password must be at least 6 characters")
    val password: String? = null
)
