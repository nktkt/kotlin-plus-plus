package dev.kpp.samples.http.handlers

import dev.kpp.capability.Capabilities
import dev.kpp.concurrent.withTimeoutOrErr
import dev.kpp.core.Result
import dev.kpp.samples.http.ApiError
import dev.kpp.samples.http.User
import dev.kpp.samples.http.UserId
import dev.kpp.samples.http.UserRepository

suspend fun Capabilities.getUser(id: UserId): Result<User, ApiError> {
    val repo = get<UserRepository>()
    // Demonstrates the typed timeout helper from kpp-concurrent. 500ms is
    // plenty for an in-memory lookup; the point is to show the shape.
    return withTimeoutOrErr(500, { ApiError.Timeout }) { repo.findById(id) }
}
