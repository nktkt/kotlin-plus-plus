package dev.kpp.derive.ksp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InternalTest {
    @Test
    fun camelToSnake_basic() {
        assertEquals("user_id", camelToSnake("userId"))
        assertEquals("session_token", camelToSnake("sessionToken"))
    }

    @Test
    fun camelToSnake_leadingLowerNoUnderscore() {
        assertEquals("name", camelToSnake("name"))
        assertEquals("a", camelToSnake("a"))
        assertEquals("", camelToSnake(""))
    }

    @Test
    fun escape_quoteAndBackslash() {
        assertEquals("\"\\\"\"", escapeJsonString("\""))
        assertEquals("\"\\\\\"", escapeJsonString("\\"))
    }

    @Test
    fun escape_controlChars() {
        assertEquals("\"\\n\"", escapeJsonString("\n"))
        assertEquals("\"\\r\"", escapeJsonString("\r"))
        assertEquals("\"\\t\"", escapeJsonString("\t"))
        assertEquals("\"\\b\"", escapeJsonString("\b"))
        assertEquals("\"\\f\"", escapeJsonString(""))
    }

    @Test
    fun escape_plainString() {
        assertEquals("\"hello\"", escapeJsonString("hello"))
    }

    @Test
    fun supportedTypes_coverPrototypeSet() {
        assertTrue("kotlin.String" in SUPPORTED_TYPES)
        assertTrue("kotlin.Int" in SUPPORTED_TYPES)
        assertTrue("kotlin.Long" in SUPPORTED_TYPES)
        assertTrue("kotlin.Boolean" in SUPPORTED_TYPES)
        assertTrue("kotlin.Double" in SUPPORTED_TYPES)
        assertTrue("kotlin.Float" in SUPPORTED_TYPES)
        assertTrue("kotlin.Short" in SUPPORTED_TYPES)
        assertTrue("kotlin.Byte" in SUPPORTED_TYPES)
    }
}
