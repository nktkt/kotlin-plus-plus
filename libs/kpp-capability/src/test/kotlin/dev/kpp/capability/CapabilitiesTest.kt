package dev.kpp.capability

import dev.kpp.capability.builtins.ConsoleLogger
import dev.kpp.capability.builtins.FixedClock
import dev.kpp.capability.builtins.Logger
import dev.kpp.capability.builtins.RecordingLogger
import dev.kpp.capability.builtins.Clock
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class CapabilitiesTest {

    @Test
    fun emptyHasNothing() {
        assertNull(Capabilities.EMPTY.getOrNull<Logger>())
    }

    @Test
    fun ofIndexesByCapabilitySupertype() {
        val log = RecordingLogger()
        val caps = Capabilities.of(log)
        assertSame(log, caps.get<Logger>())
    }

    @Test
    fun getThrowsWhenMissing() {
        val ex = assertFailsWith<IllegalStateException> {
            Capabilities.EMPTY.get<Logger>()
        }
        assertTrue(ex.message!!.contains("Logger"))
    }

    @Test
    fun lastWinsForSameInterface() {
        val first = RecordingLogger()
        val second = ConsoleLogger()
        val caps = Capabilities.of(first, second)
        assertSame(second, caps.get<Logger>())
    }

    @Test
    fun plusAddsCapability() {
        val log = RecordingLogger()
        val clock = FixedClock(Instant.EPOCH)
        val caps = Capabilities.of(log) + clock
        assertSame(log, caps.get<Logger>())
        assertSame(clock, caps.get<Clock>())
    }

    @Test
    fun plusOverwritesLastWins() {
        val a = RecordingLogger()
        val b = RecordingLogger()
        val caps = Capabilities.of(a) + b
        assertSame(b, caps.get<Logger>())
    }

    @Test
    fun multipleDistinctCapabilities() {
        val log = RecordingLogger()
        val clock = FixedClock(Instant.parse("2026-04-26T00:00:00Z"))
        val caps = Capabilities.of(log, clock)
        assertNotNull(caps.getOrNull<Logger>())
        assertNotNull(caps.getOrNull<Clock>())
        assertEquals(Instant.parse("2026-04-26T00:00:00Z"), caps.get<Clock>().now())
    }
}
