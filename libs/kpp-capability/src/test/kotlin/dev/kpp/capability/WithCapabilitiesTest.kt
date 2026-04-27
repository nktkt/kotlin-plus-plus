package dev.kpp.capability

import dev.kpp.capability.builtins.Clock
import dev.kpp.capability.builtins.FixedClock
import dev.kpp.capability.builtins.Logger
import dev.kpp.capability.builtins.RecordingLogger
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class WithCapabilitiesTest {

    @Test
    fun receiverExposesCapabilities() {
        val log = RecordingLogger()
        val result = withCapabilities(log) {
            get<Logger>().info("hello")
            42
        }
        assertEquals(42, result)
        assertEquals(listOf("INFO: hello"), log.records)
    }

    @Test
    fun useAliasRunsBlockOnExistingCapabilities() {
        val log = RecordingLogger()
        val clock = FixedClock(Instant.EPOCH)
        val caps = Capabilities.of(log, clock)
        val tick = caps.use {
            get<Logger>().info("tick")
            get<Clock>().now()
        }
        assertEquals(Instant.EPOCH, tick)
        assertEquals(listOf("INFO: tick"), log.records)
    }

    @Test
    fun audit_kotlinPpStyleSimulation() {
        // Simulates a Kotlin++ `context(log: Logger, clock: Clock) fun audit(msg: String)`.
        fun Capabilities.audit(msg: String) {
            val log = get<Logger>()
            val clock = get<Clock>()
            log.info("[${clock.now()}] $msg")
        }

        val log = RecordingLogger()
        val clock = FixedClock(Instant.parse("2026-04-26T12:00:00Z"))
        withCapabilities(log, clock) {
            audit("login")
            audit("logout")
        }
        assertEquals(
            listOf(
                "INFO: [2026-04-26T12:00:00Z] login",
                "INFO: [2026-04-26T12:00:00Z] logout",
            ),
            log.records,
        )
    }

    @Test
    fun recordingLoggerErrorIncludesThrowable() {
        val log = RecordingLogger()
        val boom = IllegalArgumentException("bad")
        log.error("oops", boom)
        assertEquals(1, log.records.size)
        val rec = log.records[0]
        assertSame(true, rec.startsWith("ERROR: oops"))
        assertSame(true, rec.contains("IllegalArgumentException"))
        assertSame(true, rec.contains("bad"))
    }
}
