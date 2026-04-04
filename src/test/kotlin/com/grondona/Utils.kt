package com.grondona

import com.grondona.model.Tournament
import com.grondona.model.User
import com.grondona.model.UserPermissions
import com.grondona.model.dto.request.CreateTournamentRequest
import com.grondona.model.dto.request.CreateUserRequest
import com.grondona.utils.WorldCupEngine
import com.grondona.utils.hashSHA256
import java.util.UUID
import kotlin.math.min

fun randomString(str: String = "", maxSize: Int = 50): String {
    val uuid = UUID.randomUUID().toString().replace("-", "")
    val take = if (str.isEmpty()) uuid.length.coerceAtMost(maxSize) else str.length.minus(1).coerceAtMost(maxSize)
    return str + uuid.substring(0, take)
}

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
fun createCronTestingUser(apiKey: String): User {
    return User(
        id = UUID.randomUUID(),
        username = randomString("CronUser-"),
        fullname = randomString("CronUser-"),
        email = "cron@${randomString()}",
        passwordHash = hashSHA256(apiKey),
        permissions = UserPermissions.CRON,
    )
}
