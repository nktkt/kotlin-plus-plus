package dev.kpp.secret

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class SecretTest {
    @Test fun toString_redacts_value() {
        assertEquals("Secret(***)", secretOf("p@ss").toString())
        assertEquals("Secret(***)", "p@ss".toSecret().toString())
    }

    @Test fun expose_returns_value() {
        assertEquals("p@ss", secretOf("p@ss").expose())
        val bytes = byteArrayOf(1, 2, 3)
        assertTrue(bytes.toSecret().expose().contentEquals(bytes))
    }

    @Test fun equals_is_value_equal_for_string_secrets() {
        assertEquals(secretOf("hello"), secretOf("hello"))
        assertNotEquals(secretOf("hello"), secretOf("world"))
    }

    @Test fun equals_is_value_equal_for_bytearray_secrets() {
        val a = byteArrayOf(1, 2, 3).toSecret()
        val b = byteArrayOf(1, 2, 3).toSecret()
        val c = byteArrayOf(1, 2, 4).toSecret()
        assertEquals(a, b)
        assertNotEquals(a, c)
    }

    @Test fun equals_returns_false_against_non_secret() {
        @Suppress("KotlinConstantConditions")
        assertFalse(secretOf("x").equals("x"))
    }

    @Test fun equals_returns_false_for_different_t_types() {
        // String secret vs ByteArray secret: hits the `else -> a == b`
        // branch which is false because String != ByteArray.
        val s: Secret<*> = secretOf("hello")
        val b: Secret<*> = secretOf("hello".encodeToByteArray())
        assertNotEquals(s, b)
    }

    @Test fun hashCode_is_value_hash() {
        assertEquals(secretOf("hello").hashCode(), secretOf("hello").hashCode())
        assertEquals("hello".hashCode(), secretOf("hello").hashCode())
    }

    @Test fun redacted_string_typealias_round_trips() {
        val s: RedactedString = "x".toSecret()
        assertEquals("x", s.expose())
        assertEquals("Secret(***)", s.toString())
        val b: RedactedBytes = byteArrayOf(7).toSecret()
        assertTrue(b.expose().contentEquals(byteArrayOf(7)))
    }
}
