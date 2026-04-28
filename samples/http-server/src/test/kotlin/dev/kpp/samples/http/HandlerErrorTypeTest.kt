package dev.kpp.samples.http

import dev.kpp.capability.withCapabilities
import dev.kpp.core.Result
import dev.kpp.core.err
import dev.kpp.derive.Json
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

private class UpstreamRepo : UserRepository {
    override suspend fun findById(id: UserId): Result<User, ApiError> =
        err(ApiError.Upstream("downstream service offline"))

    override suspend fun create(req: CreateUserRequest): Result<User, ApiError> =
        err(ApiError.Upstream("downstream service offline"))
}

private class TimeoutRepo : UserRepository {
    override suspend fun findById(id: UserId): Result<User, ApiError> =
        err(ApiError.Timeout)

    override suspend fun create(req: CreateUserRequest): Result<User, ApiError> =
        err(ApiError.Timeout)
}

class HandlerErrorTypeTest {

    @Test
    fun upstream_error_returns_502_with_cause() = runTest {
        withCapabilities(UpstreamRepo()) {
            val resp = handle(Request("GET", "/users/abc", emptyMap(), null))
            assertEquals(502, resp.status)
            val payload = Json.decode<Map<String, Any?>>(resp.body)
            assertEquals("Upstream", payload["error"])
            @Suppress("UNCHECKED_CAST")
            val details = payload["details"] as Map<String, Any?>
            assertEquals("downstream service offline", details["cause"])
        }
    }

    @Test
    fun timeout_error_returns_504_with_empty_details() = runTest {
        withCapabilities(TimeoutRepo()) {
            val resp = handle(Request("GET", "/users/abc", emptyMap(), null))
            assertEquals(504, resp.status)
            val payload = Json.decode<Map<String, Any?>>(resp.body)
            assertEquals("Timeout", payload["error"])
            @Suppress("UNCHECKED_CAST")
            val details = payload["details"] as Map<String, Any?>
            assertEquals(emptyMap(), details)
        }
    }
}
