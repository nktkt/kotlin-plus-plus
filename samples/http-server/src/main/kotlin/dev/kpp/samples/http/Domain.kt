package dev.kpp.samples.http

import dev.kpp.derive.DeriveJson

@JvmInline
value class UserId(val raw: String)

@DeriveJson
data class User(
    val id: String,
    val email: String,
    val displayName: String,
)

@DeriveJson(snakeCase = true)
data class CreateUserRequest(
    val email: String,
    val displayName: String,
)

// Wire boundary uses plain List<User> because Json.encode does not yet
// recognize ImmutableList. In-memory code paths still pass ImmutableList.
@DeriveJson
data class UsersResponse(
    val users: List<User>,
)
