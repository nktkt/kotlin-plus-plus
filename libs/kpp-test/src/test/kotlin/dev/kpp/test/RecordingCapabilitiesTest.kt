package dev.kpp.test

import dev.kpp.capability.Capability
import dev.kpp.capability.builtins.Clock
import dev.kpp.capability.builtins.Logger
import dev.kpp.capability.builtins.RecordingLogger
import dev.kpp.capability.builtins.SystemClock
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RecordingCapabilitiesTest {
    @Test
    fun records_a_single_call() {
        val recorder = CapabilityRecorder()
        val logger: Logger = recordingCapability(Logger::class, RecordingLogger(), recorder)
        logger.info("hello")
        val records = recorder.records()
        assertEquals(1, records.size)
        val call = records[0]
        assertEquals("info", call.method)
        assertEquals(listOf<Any?>("hello"), call.args)
        assertEquals(Logger::class, call.capability)
    }

    @Test
    fun records_calls_in_order() {
        val recorder = CapabilityRecorder()
        val logger: Logger = recordingCapability(Logger::class, RecordingLogger(), recorder)
        logger.info("a")
        logger.error("b", null)
        logger.info("c")
        val records = recorder.records()
        assertEquals(listOf("info", "error", "info"), records.map { it.method })
        assertEquals("a", records[0].args[0])
        assertEquals("b", records[1].args[0])
        assertEquals("c", records[2].args[0])
    }

    @Test
    fun recordsFor_filters_by_capability_type() {
        val recorder = CapabilityRecorder()
        val logger: Logger = recordingCapability(Logger::class, RecordingLogger(), recorder)
        val clock: Clock = recordingCapability(Clock::class, SystemClock(), recorder)
        logger.info("x")
        clock.now()
        clock.now()
        val loggerCalls = recorder.recordsFor(Logger::class)
        val clockCalls = recorder.recordsFor(Clock::class)
        assertEquals(1, loggerCalls.size)
        assertEquals("info", loggerCalls[0].method)
        assertEquals(2, clockCalls.size)
        assertTrue(clockCalls.all { it.method == "now" })
    }

    @Test
    fun reset_clears_records() {
        val recorder = CapabilityRecorder()
        val logger: Logger = recordingCapability(Logger::class, RecordingLogger(), recorder)
        logger.info("a")
        assertEquals(1, recorder.records().size)
        recorder.reset()
        assertEquals(0, recorder.records().size)
        logger.info("b")
        assertEquals(1, recorder.records().size)
    }

    @Test
    fun records_call_even_when_delegate_throws() {
        // Verifies the implementation records BEFORE delegating, so exceptions don't lose the call.
        val recorder = CapabilityRecorder()
        val throwingLogger = object : Logger {
            override fun info(message: String) { throw IllegalStateException("boom") }
            override fun error(message: String, throwable: Throwable?) {}
        }
        val logger: Logger = recordingCapability(Logger::class, throwingLogger, recorder)
        try {
            logger.info("hi")
        } catch (_: IllegalStateException) {
            // expected
        }
        assertEquals(1, recorder.records().size)
        assertEquals("info", recorder.records()[0].method)
    }

    @Test
    fun rejects_non_interface_capability_type() {
        class ConcreteCap : Capability
        val recorder = CapabilityRecorder()
        try {
            recordingCapability(ConcreteCap::class, ConcreteCap(), recorder)
            error("expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
            // expected
        }
    }
}
