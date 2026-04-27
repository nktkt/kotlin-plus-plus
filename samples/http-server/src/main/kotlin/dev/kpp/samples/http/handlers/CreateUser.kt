package dev.kpp.samples.http.handlers

import dev.kpp.capability.Capabilities
import dev.kpp.core.Result
import dev.kpp.samples.http.ApiError
import dev.kpp.samples.http.CreateUserRequest
import dev.kpp.samples.http.User
import dev.kpp.samples.http.UserRepository

suspend fun Capabilities.createUser(req: CreateUserRequest): Result<User, ApiError> {
    val repo = get<UserRepository>()
    return repo.create(req)
}
