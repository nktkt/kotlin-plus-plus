package dev.kpp.concurrent

import dev.kpp.core.Result
import dev.kpp.core.err
import dev.kpp.core.ok
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ParallelTest {

    @Test
    fun allSuccessPreservesOrder() = runTest {
        val r = listOf(1, 2, 3).parallelMap { ok(it * 2) }
        assertIs<Result.Ok<List<Int>>>(r)
        assertEquals(listOf(2, 4, 6), r.value)
    }

    @Test
    fun firstFailureShortCircuitsAndCancelsSiblings() = runTest {
        // Items 0, 1 finish fast with Ok; item 2 errors immediately;
        // items 3, 4 sleep long and should be cancelled before completing normally.
        val slowFinishedFlags = List(5) { AtomicBoolean(false) }

        val r = (0..4).toList().parallelMap { i ->
            when (i) {
                0, 1 -> {
                    delay(10)
                    ok(i)
                }
                2 -> {
                    delay(20)
                    err("boom-$i")
                }
                else -> {
                    try {
                        delay(10_000)
                        slowFinishedFlags[i].set(true)
                        ok(i)
                    } catch (ce: CancellationException) {
                        throw ce
                    }
                }
            }
        }

        assertIs<Result.Err<String>>(r)
        assertEquals("boom-2", r.error)
        assertTrue(!slowFinishedFlags[3].get(), "slow item 3 should have been cancelled")
        assertTrue(!slowFinishedFlags[4].get(), "slow item 4 should have been cancelled")
    }

    @Test
    fun concurrencyLimitHonored() = runTest {
        val inFlight = AtomicInteger(0)
        val peak = AtomicInteger(0)

        val r = (1..10).toList().parallelMap(concurrency = 2) { i ->
            val now = inFlight.incrementAndGet()
            peak.updateAndGet { kotlin.math.max(it, now) }
            try {
                delay(50)
                ok(i)
            } finally {
                inFlight.decrementAndGet()
            }
        }

        assertIs<Result.Ok<List<Int>>>(r)
        assertEquals((1..10).toList(), r.value)
        assertTrue(peak.get() <= 2, "peak in-flight was ${peak.get()}, expected <= 2")
    }
}
