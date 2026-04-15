package com.grondona.model.dto.request

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import kotlin.text.trim

data class CreateUserRequest(
    @field:NotBlank(message = "Full name is required")
    val fullname: String,

    @field:NotBlank(message = "Username is required")
    @field:Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters")
    val username: String,

    @field:NotBlank(message = "Email is required")
    @field:Email(message = "Email must be valid")
    val email: String,

    @field:NotBlank(message = "Password is required")
    @field:Size(min = 6, message = "Password must be at least 6 characters")
    val password: String
) {
    fun sanitized() = CreateUserRequest(
        fullname = fullname.trim(),
        username = username.trim().lowercase(),
        email = email.trim().lowercase(),
        password = password.trim()
    )
}

data class UpdateUserRequest(
    val fullname: String? = null,

    @field:Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters")
    val username: String? = null,

    @field:Email(message = "Email must be valid")
    val email: String? = null,

    @field:Size(min = 6, message = "Password must be at least 6 characters")
    val password: String? = null
) {
    fun sanitized() = UpdateUserRequest(
        fullname = fullname?.trim(),
        username = username?.trim()?.lowercase(),
        email = email?.trim()?.lowercase(),
        password = password?.trim()
    )
}

data class LoginUserRequest(
    @field:NotBlank(message = "User is required")
    val user: String,

    @field:NotBlank(message = "Password is required")
    val password: String
) {
    fun sanitized() = LoginUserRequest(
        user = user.trim().lowercase(),
        password = password.trim()
    )
}
