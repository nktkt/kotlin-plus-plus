package dev.kpp.concurrent

import dev.kpp.core.Result
import dev.kpp.core.ok
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class TimeoutTest {

    @Test
    fun blockCompletesWithinTimeout() = runTest {
        val r = withTimeoutOrErr<Int, String>(timeoutMillis = 1_000, onTimeout = { "timeout" }) {
            delay(10)
            ok(7)
        }
        assertIs<Result.Ok<Int>>(r)
        assertEquals(7, r.value)
    }

    @Test
    fun blockExceedsTimeoutReturnsErr() = runTest {
        val r = withTimeoutOrErr<Int, String>(timeoutMillis = 50, onTimeout = { "timeout" }) {
            delay(10_000)
            ok(7)
        }
        assertIs<Result.Err<String>>(r)
        assertEquals("timeout", r.error)
    }
}
