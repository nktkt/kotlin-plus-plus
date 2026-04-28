package dev.kpp.validation

import dev.kpp.core.Result
import dev.kpp.core.err
import dev.kpp.core.ok
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CombinatorsTest {

    private val notEmpty: Validator<String, String, String> =
        Validator { s -> if (s.isNotEmpty()) ok(s) else err(nonEmptyListOf("empty")) }

    private val noSpaces: Validator<String, String, String> =
        Validator { s -> if (!s.contains(' ')) ok(s) else err(nonEmptyListOf("has-spaces")) }

    @Test fun and_returns_ok_when_both_succeed() {
        val v = notEmpty and noSpaces
        val r = v.validate("hi")
        assertTrue(r is Result.Ok)
        assertEquals("hi", r.value)
    }

    @Test fun and_accumulates_errors_when_both_fail() {
        val mustHaveSpace: Validator<String, String, String> =
            Validator { s -> if (s.contains(' ')) ok(s) else err(nonEmptyListOf("no-space")) }
        // empty string fails notEmpty AND mustHaveSpace
        val v = notEmpty and mustHaveSpace
        val r = v.validate("")
        assertTrue(r is Result.Err)
        assertEquals(listOf("empty", "no-space"), r.error.toList())
    }

    @Test fun and_returns_left_err_when_only_left_fails() {
        val v = notEmpty and noSpaces
        // empty string fails notEmpty; passes noSpaces
        val r = v.validate("")
        assertTrue(r is Result.Err)
        assertEquals(listOf("empty"), r.error.toList())
    }

    @Test fun and_returns_right_err_when_only_right_fails() {
        val v = notEmpty and noSpaces
        val r = v.validate("hi there")
        assertTrue(r is Result.Err)
        assertEquals(listOf("has-spaces"), r.error.toList())
    }

    @Test fun andThen_short_circuits_on_first_err() {
        val parseInt: Validator<String, Int, String> =
            Validator { s ->
                val n = s.toIntOrNull()
                if (n != null) ok(n) else err(nonEmptyListOf("not-int"))
            }
        val positive: Validator<Int, Int, String> =
            Validator { v -> if (v > 0) ok(v) else err(nonEmptyListOf("not-positive")) }
        val v = parseInt andThen positive
        val r = v.validate("abc")
        assertTrue(r is Result.Err)
        assertEquals(listOf("not-int"), r.error.toList())
    }

    @Test fun andThen_runs_second_when_first_succeeds() {
        val parseInt: Validator<String, Int, String> =
            Validator { s ->
                val n = s.toIntOrNull()
                if (n != null) ok(n) else err(nonEmptyListOf("not-int"))
            }
        val positive: Validator<Int, Int, String> =
            Validator { v -> if (v > 0) ok(v) else err(nonEmptyListOf("not-positive")) }
        val v = parseInt andThen positive
        val rOk = v.validate("5")
        assertTrue(rOk is Result.Ok)
        assertEquals(5, rOk.value)
        val rErr = v.validate("-3")
        assertTrue(rErr is Result.Err)
        assertEquals(listOf("not-positive"), rErr.error.toList())
    }

    @Test fun optional_returns_ok_null_for_null_input() {
        val v = optional(notEmpty)
        val r = v.validate(null)
        assertTrue(r is Result.Ok)
        assertEquals(null, r.value)
    }

    @Test fun optional_runs_inner_for_non_null() {
        val v = optional(notEmpty)
        val rOk = v.validate("hi")
        assertTrue(rOk is Result.Ok)
        assertEquals("hi", rOk.value)
        val rErr = v.validate("")
        assertTrue(rErr is Result.Err)
        assertEquals(listOf("empty"), rErr.error.toList())
    }

    @Test fun required_rejects_null_with_missing_error() {
        val v = required(notEmpty) { "missing" }
        val r = v.validate(null)
        assertTrue(r is Result.Err)
        assertEquals(listOf("missing"), r.error.toList())
    }

    @Test fun required_runs_inner_for_non_null() {
        val v = required(notEmpty) { "missing" }
        val rOk = v.validate("hi")
        assertTrue(rOk is Result.Ok)
        assertEquals("hi", rOk.value)
        val rErr = v.validate("")
        assertTrue(rErr is Result.Err)
        assertEquals(listOf("empty"), rErr.error.toList())
    }

    @Test fun mapError_transforms_each_error_in_nel() {
        val mustHaveSpace: Validator<String, String, String> =
            Validator { s -> if (s.contains(' ')) ok(s) else err(nonEmptyListOf("no-space")) }
        val both = notEmpty and mustHaveSpace
        val mapped = both.mapError { code -> "code=$code" }
        val r = mapped.validate("")
        assertTrue(r is Result.Err)
        assertEquals(listOf("code=empty", "code=no-space"), r.error.toList())
    }
}
