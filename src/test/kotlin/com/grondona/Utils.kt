package com.grondona

import com.grondona.model.dto.request.CreateTournamentRequest
import com.grondona.model.dto.request.CreateUserRequest
import java.util.UUID
import kotlin.math.min

private fun randomString(str: String, maxSize: Int = 50) = str + UUID.randomUUID().toString()
    .replace("-", "").substring(0, min(maxSize, str.length-1))

fun createTestingUserRequest(
    fullname: String = "User",
    username: String = "user",
    email: String = "user@test.com",
    password: String = "password123",
): CreateUserRequest {
    return CreateUserRequest(
        fullname = fullname,
        username = randomString(username),
        email = email.split("@")
            .mapIndexed { idx, str -> if (idx == 0) randomString("$str-") else str }
            .joinToString("@"),
        password = password,
    )
}

fun createTestingTournamentRequest(
    name: String = "Tournament",
): CreateTournamentRequest {
    return CreateTournamentRequest(
        name = randomString("$name "),
    )
}