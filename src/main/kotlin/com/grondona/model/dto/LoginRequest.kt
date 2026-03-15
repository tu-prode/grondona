package com.grondona.model.dto

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank

data class LoginRequest(
    @field:NotBlank(message = "User is required")
    val user: String,

    @field:NotBlank(message = "Password is required")
    val password: String
)
