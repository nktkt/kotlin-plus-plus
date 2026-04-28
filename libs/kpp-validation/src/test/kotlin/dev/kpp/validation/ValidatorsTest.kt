package dev.kpp.validation

import dev.kpp.core.Result
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ValidatorsTest {

    @Test fun nonEmptyString_accepts_non_empty() {
        val r = nonEmptyString.validate("hi")
        assertTrue(r is Result.Ok)
        assertEquals("hi", r.value)
    }

    @Test fun nonEmptyString_rejects_empty() {
        val r = nonEmptyString.validate("")
        assertTrue(r is Result.Err)
        assertEquals(listOf("empty"), r.error.toList())
    }

    @Test fun nonBlankString_accepts_non_blank() {
        val r = nonBlankString.validate("hi")
        assertTrue(r is Result.Ok)
        assertEquals("hi", r.value)
    }

    @Test fun nonBlankString_rejects_whitespace_only() {
        val r = nonBlankString.validate("   ")
        assertTrue(r is Result.Err)
        assertEquals(listOf("blank"), r.error.toList())
    }

    @Test fun lengthBetween_accepts_in_range() {
        val v = lengthBetween(2, 5)
        val r = v.validate("abcd")
        assertTrue(r is Result.Ok)
        assertEquals("abcd", r.value)
    }

    @Test fun lengthBetween_rejects_too_short() {
        val v = lengthBetween(3, 5)
        val r = v.validate("ab")
        assertTrue(r is Result.Err)
        assertEquals(listOf("length:expected[3..5],got=2"), r.error.toList())
    }

    @Test fun lengthBetween_rejects_too_long() {
        val v = lengthBetween(2, 4)
        val r = v.validate("abcde")
        assertTrue(r is Result.Err)
        assertEquals(listOf("length:expected[2..4],got=5"), r.error.toList())
    }

    @Test fun rangeInt_accepts_in_range() {
        val v = rangeInt(0, 100)
        val r = v.validate(50)
        assertTrue(r is Result.Ok)
        assertEquals(50, r.value)
    }

    @Test fun rangeInt_rejects_out_of_range() {
        val v = rangeInt(0, 100)
        val r = v.validate(200)
        assertTrue(r is Result.Err)
        assertEquals(listOf("out-of-range:[0..100],got=200"), r.error.toList())
    }

    @Test fun matches_accepts_pattern() {
        val v = matches(Regex("[a-z]+"), "lower")
        val r = v.validate("abc")
        assertTrue(r is Result.Ok)
    }

    @Test fun matches_rejects_non_pattern() {
        val v = matches(Regex("[a-z]+"), "lower")
        val r = v.validate("ABC")
        assertTrue(r is Result.Err)
        assertEquals(listOf("does-not-match:lower"), r.error.toList())
    }

    @Test fun email_accepts_valid_email() {
        val r = email.validate("user.name+tag@example.co")
        assertTrue(r is Result.Ok)
    }

    @Test fun email_rejects_invalid_email() {
        val r = email.validate("not-an-email")
        assertTrue(r is Result.Err)
        assertEquals(listOf("not-email"), r.error.toList())
    }

    @Test fun oneOf_accepts_allowed() {
        val v = oneOf(setOf("red", "green", "blue"))
        val r = v.validate("green")
        assertTrue(r is Result.Ok)
        assertEquals("green", r.value)
    }

    @Test fun oneOf_rejects_disallowed() {
        val v = oneOf(setOf("red", "green", "blue"))
        val r = v.validate("yellow")
        assertTrue(r is Result.Err)
        assertEquals(listOf("not-in-allowed-set"), r.error.toList())
    }
}
