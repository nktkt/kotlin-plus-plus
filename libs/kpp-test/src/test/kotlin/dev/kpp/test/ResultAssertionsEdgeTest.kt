package dev.kpp.test

import dev.kpp.core.Result
import dev.kpp.core.err
import dev.kpp.core.ok
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ResultAssertionsEdgeTest {

    @Test
    fun assertErr_throws_when_ok() {
        val r: Result<Int, String> = ok(7)
        val e = assertFailsWith<AssertionError> { r.assertErr() }
        val msg = e.message ?: ""
        assertTrue(msg.contains("expected: Err"), "should mention expected: $msg")
        assertTrue(msg.contains("Ok(7)"), "should include Ok value: $msg")
    }

    @Test
    fun assertOkValue_fails_when_actual_is_err() {
        val r: Result<String, String> = err("boom")
        val e = assertFailsWith<AssertionError> { r.assertOkValue("hi") }
        val msg = e.message ?: ""
        assertTrue(msg.contains("expected: Ok(hi)"), "should mention expected: $msg")
        assertTrue(msg.contains("Err(boom)"), "should mention actual Err: $msg")
    }

    @Test
    fun assertErrValue_passes_when_equal() {
        val r: Result<Int, String> = err("nope")
        r.assertErrValue("nope")
    }

    @Test
    fun assertErrValue_fails_when_err_value_differs() {
        val r: Result<Int, String> = err("a")
        val e = assertFailsWith<AssertionError> { r.assertErrValue("b") }
        val msg = e.message ?: ""
        assertTrue(msg.contains("Err(b)"), "should mention expected: $msg")
        assertTrue(msg.contains("Err(a)"), "should mention actual: $msg")
    }

    @Test
    fun assertErrValue_fails_when_actual_is_ok() {
        val r: Result<Int, String> = ok(1)
        val e = assertFailsWith<AssertionError> { r.assertErrValue("nope") }
        val msg = e.message ?: ""
        assertTrue(msg.contains("expected: Err(nope)"), "should mention expected: $msg")
        assertTrue(msg.contains("Ok(1)"), "should mention actual Ok: $msg")
    }

    @Test
    fun assertErrType_fails_when_actual_is_ok() {
        val r: Result<Int, RuntimeException> = ok(42)
        val e = assertFailsWith<AssertionError> {
            r.assertErrType<Int, RuntimeException, IllegalStateException>()
        }
        val msg = e.message ?: ""
        assertTrue(msg.contains("expected: Err of type"), "should mention expected: $msg")
        assertTrue(msg.contains("Ok(42)"), "should mention actual Ok: $msg")
    }

    @Test
    fun assertOk_message_formats_null_error_type_label() {
        // Hits the `error?.let { ... } ?: "null"` branch when error is null.
        val r: Result<Int, String?> = err(null)
        val e = assertFailsWith<AssertionError> { r.assertOk() }
        val msg = e.message ?: ""
        // The label used in the message when error is null is "null".
        assertTrue(msg.contains("error type=null"), "should report error type=null: $msg")
    }

    @Test
    fun assertErrType_message_formats_null_error_type_label() {
        val r: Result<Int, String?> = err(null)
        val e = assertFailsWith<AssertionError> {
            r.assertErrType<Int, String?, String>()
        }
        val msg = e.message ?: ""
        assertTrue(msg.contains("error type=null"), "should report error type=null: $msg")
    }

    @Test
    fun assertAllOk_returns_empty_for_empty_iterable() {
        val rs: List<Result<Int, String>> = emptyList()
        assertEquals(emptyList(), rs.assertAllOk())
    }
}
