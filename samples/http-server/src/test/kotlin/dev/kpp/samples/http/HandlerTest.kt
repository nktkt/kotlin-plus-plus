package dev.kpp.samples.http

import dev.kpp.capability.withCapabilities
import dev.kpp.derive.Json
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HandlerTest {

    @Test
    fun creates_user_returns_201_with_json_body() = runTest {
        withCapabilities(InMemoryUserRepository()) {
            val resp = handle(
                Request(
                    method = "POST",
                    path = "/users",
                    query = emptyMap(),
                    body = """{"email":"a@b.com","display_name":"Alice","api_key":"test-key-001"}""",
                ),
            )
            assertEquals(201, resp.status)
            val user = Json.decode<User>(resp.body)
            assertTrue(user.id.isNotBlank())
            assertEquals("a@b.com", user.email)
            assertEquals("Alice", user.displayName)
        }
    }

    @Test
    fun get_unknown_user_returns_404_typed_error() = runTest {
        withCapabilities(InMemoryUserRepository()) {
            val resp = handle(Request("GET", "/users/missing", emptyMap(), null))
            assertEquals(404, resp.status)
            val payload = Json.decode<Map<String, Any?>>(resp.body)
            assertEquals("NotFound", payload["error"])
        }
    }

    @Test
    fun validation_error_returns_400() = runTest {
        withCapabilities(InMemoryUserRepository()) {
            val resp = handle(
                Request(
                    method = "POST",
                    path = "/users",
                    query = emptyMap(),
                    body = """{"email":"bad","display_name":"","api_key":"test-key-001"}""",
                ),
            )
            assertEquals(400, resp.status)
            val payload = Json.decode<Map<String, Any?>>(resp.body)
            assertEquals("Validation", payload["error"])
        }
    }

    @Test
    fun request_with_missing_api_key_returns_400_validation() = runTest {
        withCapabilities(InMemoryUserRepository()) {
            val resp = handle(
                Request(
                    method = "POST",
                    path = "/users",
                    query = emptyMap(),
                    body = """{"email":"a@b.com","display_name":"Alice"}""",
                ),
            )
            assertEquals(400, resp.status)
            val payload = Json.decode<Map<String, Any?>>(resp.body)
            assertEquals("Validation", payload["error"])
            @Suppress("UNCHECKED_CAST")
            val details = payload["details"] as Map<String, Any?>
            assertEquals("api_key", details["field"])
        }
    }

    @Test
    fun bad_json_returns_400() = runTest {
        withCapabilities(InMemoryUserRepository()) {
            val resp = handle(
                Request(
                    method = "POST",
                    path = "/users",
                    query = emptyMap(),
                    body = "{not-json",
                ),
            )
            assertEquals(400, resp.status)
            val payload = Json.decode<Map<String, Any?>>(resp.body)
            assertEquals("BadJson", payload["error"])
        }
    }

    @Test
    fun unknown_route_returns_404() = runTest {
        withCapabilities(InMemoryUserRepository()) {
            val resp = handle(Request("GET", "/things/1", emptyMap(), null))
            assertEquals(404, resp.status)
            val payload = Json.decode<Map<String, Any?>>(resp.body)
            assertEquals("NotFound", payload["error"])
        }
    }
}
