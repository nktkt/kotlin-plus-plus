package dev.kpp.capability

import dev.kpp.capability.builtins.ConsoleLogger
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import kotlin.test.Test
import kotlin.test.assertTrue

class ConsoleLoggerTest {

    private fun captureStdout(block: () -> Unit): String {
        val orig = System.out
        val buf = ByteArrayOutputStream()
        System.setOut(PrintStream(buf, true, "UTF-8"))
        try {
            block()
        } finally {
            System.setOut(orig)
        }
        return buf.toString("UTF-8")
    }

    private fun captureStderr(block: () -> Unit): String {
        val orig = System.err
        val buf = ByteArrayOutputStream()
        System.setErr(PrintStream(buf, true, "UTF-8"))
        try {
            block()
        } finally {
            System.setErr(orig)
        }
        return buf.toString("UTF-8")
    }

    @Test
    fun info_writes_to_stdout_with_INFO_prefix() {
        val out = captureStdout {
            ConsoleLogger().info("hello")
        }
        assertTrue(out.contains("INFO: hello"), "expected INFO prefix in stdout, got: $out")
    }

    @Test
    fun error_writes_to_stderr_with_ERROR_prefix() {
        val err = captureStderr {
            ConsoleLogger().error("boom", null)
        }
        assertTrue(err.contains("ERROR: boom"), "expected ERROR prefix in stderr, got: $err")
    }

    @Test
    fun error_with_throwable_prints_stack_trace() {
        val err = captureStderr {
            ConsoleLogger().error("boom", IllegalStateException("fail-detail"))
        }
        assertTrue(err.contains("ERROR: boom"), "expected ERROR prefix: $err")
        // The throwable's class name and message should appear in the printed stack trace.
        assertTrue(err.contains("IllegalStateException"), "expected exception class in output: $err")
        assertTrue(err.contains("fail-detail"), "expected exception message in output: $err")
    }
}
