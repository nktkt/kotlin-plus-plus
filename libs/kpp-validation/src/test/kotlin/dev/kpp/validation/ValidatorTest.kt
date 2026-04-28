package dev.kpp.validation

import dev.kpp.core.Result
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ValidatorTest {

    @Test fun validId_returns_input_as_ok() {
        val v: Validator<Int, Int, String> = validId()
        val r = v.validate(7)
        assertTrue(r is Result.Ok)
        assertEquals(7, r.value)
    }

    @Test fun validFail_returns_err_with_single_error() {
        val v: Validator<Int, Int, String> = validFail("nope")
        val r = v.validate(7)
        assertTrue(r is Result.Err)
        assertEquals(listOf("nope"), r.error.toList())
    }

    @Test fun require_returns_ok_when_predicate_true() {
        val v = require<Int, String>({ it > 0 }, { "not-positive:$it" })
        val r = v.validate(3)
        assertTrue(r is Result.Ok)
        assertEquals(3, r.value)
    }

    @Test fun require_returns_err_when_predicate_false() {
        val v = require<Int, String>({ it > 0 }, { "not-positive:$it" })
        val r = v.validate(-1)
        assertTrue(r is Result.Err)
        assertEquals(listOf("not-positive:-1"), r.error.toList())
    }
}
