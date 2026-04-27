package dev.kpp.test

import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class VirtualClockTest {
    private val t0: Instant = Instant.parse("2025-01-01T00:00:00Z")

    @Test
    fun now_returns_initial_until_advanced() {
        val clock = VirtualClock(t0)
        assertEquals(t0, clock.now())
        assertEquals(t0, clock.now())
        assertEquals(t0, clock.now())
    }

    @Test
    fun advanceBy_moves_clock_forward() {
        val clock = VirtualClock(t0)
        clock.advanceBy(Duration.ofSeconds(30))
        assertEquals(t0.plusSeconds(30), clock.now())
        clock.advanceBy(Duration.ofMinutes(2))
        assertEquals(t0.plusSeconds(30 + 120), clock.now())
    }

    @Test
    fun setNow_replaces_instant() {
        val clock = VirtualClock(t0)
        clock.advanceBy(Duration.ofSeconds(99))
        val target = Instant.parse("2030-06-15T12:00:00Z")
        clock.setNow(target)
        assertEquals(target, clock.now())
    }
}
