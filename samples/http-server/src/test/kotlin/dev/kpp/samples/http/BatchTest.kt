package dev.kpp.samples.http

import dev.kpp.capability.withCapabilities
import dev.kpp.core.Result
import dev.kpp.core.ok
import dev.kpp.derive.Json
import dev.kpp.samples.http.handlers.getUsersBatch
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BatchTest {

    @Test
    fun batch_get_runs_in_parallel_and_preserves_order() = runTest {
        val repo = InMemoryUserRepository().apply {
            seed(User("id-a", "a@x.com", "A"))
            seed(User("id-b", "b@x.com", "B"))
            seed(User("id-c", "c@x.com", "C"))
        }
        withCapabilities(repo) {
            val resp = handle(
                Request(
                    method = "GET",
                    path = "/users",
                    query = mapOf("ids" to "id-a,id-b,id-c"),
                    body = null,
                ),
            )
            assertEquals(200, resp.status)
            val payload = Json.decode<UsersResponse>(resp.body)
            assertEquals(listOf("id-a", "id-b", "id-c"), payload.users.map { it.id })
        }
    }

    @Test
    fun batch_get_first_failure_short_circuits() = runTest {
        val repo = InMemoryUserRepository().apply {
            seed(User("id-a", "a@x.com", "A"))
            seed(User("id-c", "c@x.com", "C"))
        }
        withCapabilities(repo) {
            val resp = handle(
                Request(
                    method = "GET",
                    path = "/users",
                    query = mapOf("ids" to "id-a,id-missing,id-c"),
                    body = null,
                ),
            )
            assertEquals(404, resp.status)
            val payload = Json.decode<Map<String, Any?>>(resp.body)
            assertEquals("NotFound", payload["error"])
        }
    }

    @Test
    fun batch_get_executes_concurrently() = runTest {
        // Wraps the repo with a concurrency probe. If parallelMap is doing real
        // fan-out, the high-water mark must be > 1 across the seeded ids.
        val inner = InMemoryUserRepository().apply {
            seed(User("u1", "1@x.com", "1"))
            seed(User("u2", "2@x.com", "2"))
            seed(User("u3", "3@x.com", "3"))
            seed(User("u4", "4@x.com", "4"))
        }
        val active = AtomicInteger(0)
        val peak = AtomicInteger(0)
        val probe = object : UserRepository {
            override suspend fun findById(id: UserId): Result<User, ApiError> {
                val now = active.incrementAndGet()
                peak.updateAndGet { maxOf(it, now) }
                try {
                    delay(20)
                    return inner.findById(id)
                } finally {
                    active.decrementAndGet()
                }
            }
            override suspend fun create(req: CreateUserRequest) = inner.create(req)
        }
        withCapabilities(probe) {
            val r = getUsersBatch(listOf("u1", "u2", "u3", "u4").map { UserId(it) })
            assertTrue(r is Result.Ok)
        }
        assertTrue(peak.get() > 1, "expected parallel fan-out, peak=${peak.get()}")
    }
}
