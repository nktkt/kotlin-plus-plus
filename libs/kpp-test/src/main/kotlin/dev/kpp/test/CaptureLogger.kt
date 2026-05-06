package dev.kpp.test

import dev.kpp.capability.builtins.Logger

/**
 * A Logger capability that captures structured records: every call is stored
 * with its level (INFO/ERROR), message, and optional throwable. Convenience
 * over RecordingLogger when tests want to assert on log structure.
 */
class CaptureLogger : Logger {
    /** Severity of a captured log entry. */
    enum class Level { INFO, ERROR }

    /** A captured log entry: level, message, and optional throwable. */
    data class Entry(val level: Level, val message: String, val throwable: Throwable? = null)

    private val lock = Any()
    private val buffer: MutableList<Entry> = mutableListOf()

    /** Snapshot of captured entries in insertion order. */
    val entries: List<Entry>
        get() = synchronized(lock) { buffer.toList() }

    override fun info(message: String) {
        synchronized(lock) { buffer += Entry(Level.INFO, message, null) }
    }

    override fun error(message: String, throwable: Throwable?) {
        synchronized(lock) { buffer += Entry(Level.ERROR, message, throwable) }
    }

    /** Clears all captured entries. */
    fun reset() {
        synchronized(lock) { buffer.clear() }
    }
}
