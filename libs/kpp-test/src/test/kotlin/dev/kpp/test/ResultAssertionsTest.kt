package dev.kpp.test

import dev.kpp.core.Result
import dev.kpp.core.err
import dev.kpp.core.ok
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

private sealed interface DomainErr {
    data class NotFound(val id: String) : DomainErr
    data class Invalid(val reason: String) : DomainErr
}

class ResultAssertionsTest {
    @Test
    fun assertOk_returns_value_when_ok() {
        val r: Result<Int, String> = ok(42)
        assertEquals(42, r.assertOk())
    }

    @Test
    fun assertOk_throws_with_typed_message_when_err() {
        val errValue = DomainErr.NotFound("abc")
        val r: Result<Int, DomainErr> = err(errValue)
        val e = assertFailsWith<AssertionError> { r.assertOk() }
        val msg = e.message ?: ""
        assertTrue(msg.contains(errValue.toString()), "message should include Err's toString; was: $msg")
        assertTrue(msg.contains("expected: Ok"), "message should mention expected Ok; was: $msg")
    }

    @Test
    fun assertErr_returns_error_when_err() {
        val errValue = DomainErr.Invalid("nope")
        val r: Result<Int, DomainErr> = err(errValue)
        assertSame(errValue, r.assertErr())
    }

    @Test
    fun assertOkValue_passes_when_equal() {
        val r: Result<String, Nothing> = ok("hello")
        r.assertOkValue("hello")
    }

    @Test
    fun assertOkValue_fails_when_unequal() {
        val r: Result<String, Nothing> = ok("hello")
        val e = assertFailsWith<AssertionError> { r.assertOkValue("world") }
        val msg = e.message ?: ""
        assertTrue(msg.contains("hello"), "should contain actual: $msg")
        assertTrue(msg.contains("world"), "should contain expected: $msg")
    }

    @Test
    fun assertErrType_returns_typed_instance() {
        val r: Result<Int, DomainErr> = err(DomainErr.NotFound("x"))
        val typed: DomainErr.NotFound = r.assertErrType<Int, DomainErr, DomainErr.NotFound>()
        assertEquals("x", typed.id)
    }

    @Test
    fun assertErrType_fails_when_wrong_subtype() {
        val r: Result<Int, DomainErr> = err(DomainErr.Invalid("bad"))
        val e = assertFailsWith<AssertionError> {
            r.assertErrType<Int, DomainErr, DomainErr.NotFound>()
        }
        val msg = e.message ?: ""
        assertTrue(msg.contains("NotFound"), "should mention expected type: $msg")
        assertTrue(msg.contains("Invalid"), "should mention actual: $msg")
    }

    @Test
    fun assertAllOk_returns_list_in_order_when_all_ok() {
        val rs: List<Result<Int, String>> = listOf(ok(1), ok(2), ok(3))
        assertEquals(listOf(1, 2, 3), rs.assertAllOk())
    }

    @Test
    fun assertAllOk_throws_when_any_err() {
        val rs: List<Result<Int, String>> = listOf(ok(1), err("boom"), ok(3))
        val e = assertFailsWith<AssertionError> { rs.assertAllOk() }
        val msg = e.message ?: ""
        assertTrue(msg.contains("index=1"), "should mention failing index: $msg")
        assertTrue(msg.contains("boom"), "should include Err value: $msg")
    }
}
