package dev.kpp.secret

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// These tests verify CORRECTNESS only. They do NOT measure timing.
// A real timing-leak test would need JMH / a microbenchmark harness
// and is out of scope for this module.
class InternalTest {
    @Test fun constant_time_equals_is_true_for_equal_strings() {
        assertTrue(constantTimeEquals("hello", "hello"))
        assertTrue(constantTimeEquals("p@ssw0rd!", "p@ssw0rd!"))
    }

    @Test fun constant_time_equals_is_false_for_different_strings() {
        assertFalse(constantTimeEquals("hello", "world"))
        assertFalse(constantTimeEquals("hellp", "hello"))
    }

    @Test fun constant_time_equals_is_false_for_different_lengths() {
        assertFalse(constantTimeEquals("abc", "abcd"))
        assertFalse(constantTimeEquals("abcd", "abc"))
    }

    @Test fun constant_time_equals_handles_empty_strings() {
        assertTrue(constantTimeEquals("", ""))
        assertFalse(constantTimeEquals("", "x"))
        assertFalse(constantTimeEquals("x", ""))
    }

    @Test fun constant_time_equals_is_true_for_equal_bytes() {
        assertTrue(constantTimeEquals(byteArrayOf(1, 2, 3), byteArrayOf(1, 2, 3)))
        assertTrue(constantTimeEquals(byteArrayOf(0, -1, 127), byteArrayOf(0, -1, 127)))
    }

    @Test fun constant_time_equals_is_false_for_different_bytes() {
        assertFalse(constantTimeEquals(byteArrayOf(1, 2, 3), byteArrayOf(1, 2, 4)))
    }

    @Test fun constant_time_equals_is_false_for_different_byte_lengths() {
        assertFalse(constantTimeEquals(byteArrayOf(1, 2, 3), byteArrayOf(1, 2, 3, 4)))
        assertFalse(constantTimeEquals(byteArrayOf(1, 2, 3, 4), byteArrayOf(1, 2, 3)))
    }

    @Test fun constant_time_equals_handles_empty_bytes() {
        assertTrue(constantTimeEquals(ByteArray(0), ByteArray(0)))
        assertFalse(constantTimeEquals(ByteArray(0), byteArrayOf(1)))
        assertFalse(constantTimeEquals(byteArrayOf(1), ByteArray(0)))
    }
}
