package dev.kpp.samples.http.handlers

import dev.kpp.capability.Capabilities
import dev.kpp.capability.builtins.Logger
import dev.kpp.concurrent.parallelMap
import dev.kpp.core.Result
import dev.kpp.core.map
import dev.kpp.immutable.ImmutableList
import dev.kpp.immutable.toImmutableList
import dev.kpp.samples.http.ApiError
import dev.kpp.samples.http.User
import dev.kpp.samples.http.UserId
import dev.kpp.samples.http.UserRepository

suspend fun Capabilities.getUsersBatch(ids: List<UserId>): Result<ImmutableList<User>, ApiError> {
    val repo = get<UserRepository>()
    getOrNull<Logger>()?.info("getUsersBatch: fetching ${ids.size} users")
    return ids.parallelMap(concurrency = 8) { repo.findById(it) }
        .map { it.toImmutableList() }
}
