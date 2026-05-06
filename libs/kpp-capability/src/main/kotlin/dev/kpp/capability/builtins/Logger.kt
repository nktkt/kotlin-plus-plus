package dev.kpp.capability.builtins

import dev.kpp.capability.Capability

/** A `Capability` for structured info/error logging. */
interface Logger : Capability {
    /** Logs an informational message. */
    fun info(message: String)
    /** Logs an error message, optionally with a throwable. */
    fun error(message: String, throwable: Throwable? = null)
}

/** A `Logger` that writes INFO to stdout and ERROR (with stack trace) to stderr. */
class ConsoleLogger : Logger {
    override fun info(message: String) {
        println("INFO: $message")
    }

    override fun error(message: String, throwable: Throwable?) {
        System.err.println("ERROR: $message")
        throwable?.printStackTrace(System.err)
    }
}

/** A `Logger` that buffers messages in memory; intended for tests. */
class RecordingLogger : Logger {
    private val buffer: MutableList<String> = mutableListOf()

    /** Snapshot of buffered records in insertion order. */
    val records: List<String>
        get() = buffer.toList()

    override fun info(message: String) {
        buffer += "INFO: $message"
    }

    override fun error(message: String, throwable: Throwable?) {
        val suffix = throwable?.let { " (${it::class.simpleName}: ${it.message})" }.orEmpty()
        buffer += "ERROR: $message$suffix"
    }
}
