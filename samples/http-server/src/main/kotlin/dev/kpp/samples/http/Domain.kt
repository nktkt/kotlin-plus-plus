package dev.kpp.samples.http

import dev.kpp.derive.DeriveJson
import dev.kpp.secret.Secret

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
    val apiKey: Secret<String>,
)

// Wire boundary uses plain List<User> because Json.encode does not yet
// recognize ImmutableList. In-memory code paths still pass ImmutableList.
@DeriveJson
data class UsersResponse(
    val users: List<User>,
)

// Default redaction: callerApiKey serializes to "[REDACTED]".
@DeriveJson(snakeCase = true)
data class AuditEntry(
    val timestamp: String,
    val event: String,
    val userId: String,
    val callerApiKey: Secret<String>,
)

// Diagnostic-only mirror of AuditEntry. allowSecrets = true causes
// callerApiKey to round-trip its underlying value verbatim — never write
// this output to durable storage or logs that ship off-host.
@DeriveJson(snakeCase = true, allowSecrets = true)
data class AuditEntryDiagnostic(
    val timestamp: String,
    val event: String,
    val userId: String,
    val callerApiKey: Secret<String>,
)
