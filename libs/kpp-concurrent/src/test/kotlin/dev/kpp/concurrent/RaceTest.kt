package dev.kpp.concurrent

import dev.kpp.core.Result
import dev.kpp.core.err
import dev.kpp.core.ok
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class RaceTest {

    @Test
    fun returnsFirstOkAndCancelsOthers() = runTest {
        val slowFinished = AtomicBoolean(false)

        val r = raceFirstSuccess<Int, String>(
            { delay(10); ok(42) },
            {
                try {
                    delay(10_000)
                    slowFinished.set(true)
                    ok(99)
                } catch (ce: CancellationException) {
                    throw ce
                }
            },
        )

        assertIs<Result.Ok<Int>>(r)
        assertEquals(42, r.value)
        assertTrue(!slowFinished.get(), "loser task should have been cancelled")
    }

    @Test
    fun allFailReturnsErrorsInCompletionOrder() = runTest {
        val r = raceFirstSuccess<Int, String>(
            { delay(30); err("c") },
            { delay(10); err("a") },
            { delay(20); err("b") },
        )

        assertIs<Result.Err<List<String>>>(r)
        assertEquals(listOf("a", "b", "c"), r.error)
    }
}
