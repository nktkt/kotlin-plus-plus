package dev.kpp.test

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class CaptureLoggerTest {
    @Test
    fun info_records_entry() {
        val log = CaptureLogger()
        log.info("hello")
        val entries = log.entries
        assertEquals(1, entries.size)
        assertEquals(CaptureLogger.Level.INFO, entries[0].level)
        assertEquals("hello", entries[0].message)
        assertNull(entries[0].throwable)
    }

    @Test
    fun error_records_entry_with_throwable() {
        val log = CaptureLogger()
        val boom = IllegalStateException("kaboom")
        log.error("failed", boom)
        val entries = log.entries
        assertEquals(1, entries.size)
        assertEquals(CaptureLogger.Level.ERROR, entries[0].level)
        assertEquals("failed", entries[0].message)
        assertSame(boom, entries[0].throwable)
    }

    @Test
    fun entries_are_immutable_snapshot() {
        val log = CaptureLogger()
        log.info("a")
        val snapshot = log.entries
        // The returned list is a snapshot; subsequent log calls must not mutate it.
        log.info("b")
        assertEquals(1, snapshot.size)
        assertEquals(2, log.entries.size)
        // And the snapshot is not the same instance as a future snapshot.
        log.info("c")
        assertEquals(1, snapshot.size)
        assertEquals(3, log.entries.size)
    }
}
