package com.grondona

import com.grondona.model.Group
import com.grondona.model.Team
import com.grondona.model.Tournament
import com.grondona.model.User
import com.grondona.model.dto.request.CreateGroupRequest
import com.grondona.model.dto.request.CreateTournamentRequest
import com.grondona.model.dto.request.CreateUserRequest
import java.time.LocalDateTime
import java.util.UUID

// Randomizer

fun randomString(str: String = "", maxSize: Int = 50): String {
    val uuid = UUID.randomUUID().toString().replace("-", "")
    val take = if (str.isEmpty()) uuid.length.coerceAtMost(maxSize) else str.length.minus(1).coerceAtMost(maxSize)
    return str + uuid.substring(0, take)
}

// Request builders

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

fun createTestingGroupRequest(
    name: String = "Group",
    isPrivate: Boolean = false,
    maxMembers: Int = 32,
): CreateGroupRequest {
    return CreateGroupRequest(
        name = randomString(name),
        isPrivate = isPrivate,
        maxMembers = maxMembers,
    )
}

fun createTestingTournamentRequest(
    name: String = "Tournament",
): CreateTournamentRequest {
    return CreateTournamentRequest(
        name = randomString("$name "),
    )
}

// Test entities

val testTournament = Tournament(
    id = UUID.randomUUID(),
    name = "Test Tournament",
)

val testTeam = Team(
    id = UUID.randomUUID(),
    code = "TEST",
    name = "Test Team",
    tournament = testTournament,
)

val testGroup = Group(
    id = UUID.randomUUID(),
    tournament = testTournament,
    name = "Test Group",
    isPrivate = false,
    maxMembers = 20,
    createdAt = LocalDateTime.now(),
    updatedAt = LocalDateTime.now()
)

val testUser: User = User(
    id = UUID.randomUUID(),
    fullname = "tester",
    username = "tester",
    email = "test@gmail.com",
    passwordHash = "password",
)

// Utility functions

fun <T> List<T>.otherRandom(vararg others: T): T {
    var choice = this.random()
    while (others.contains(choice)) {
        choice = this.random()
    }
    return choice
}

fun <T> List<T>.consistsOf(other: List<T>) = this.containsAll(other) && other.containsAll(this)
