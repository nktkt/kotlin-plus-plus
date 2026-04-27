package dev.kpp.capability.builtins

import dev.kpp.capability.Capability

interface Logger : Capability {
    fun info(message: String)
    fun error(message: String, throwable: Throwable? = null)
}

class ConsoleLogger : Logger {
    override fun info(message: String) {
        println("INFO: $message")
    }

    override fun error(message: String, throwable: Throwable?) {
        System.err.println("ERROR: $message")
        throwable?.printStackTrace(System.err)
    }
}

class RecordingLogger : Logger {
    private val buffer: MutableList<String> = mutableListOf()

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
