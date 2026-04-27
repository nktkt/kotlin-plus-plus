package dev.kpp.derive

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ParserTest {
    @Test fun parsesAllStringEscapes() {
        val text = "\"a\\\"b\\\\c\\nd\\re\\tf\\bg\\fh\""
        val v = JsonParser(text).parseRoot() as String
        assertEquals("a\"b\\c\nd\re\tf\bgh", v)
    }

    @Test fun parsesUnicodeEscape() {
        val text = "\"\\u0041\\u0042\""
        val v = JsonParser(text).parseRoot() as String
        assertEquals("AB", v)
    }

    @Test fun parsesScientificNotation() {
        assertEquals(1500.0, JsonParser("1.5e3").parseRoot())
        assertEquals(1500.0, JsonParser("1.5E3").parseRoot())
        assertEquals(0.015, JsonParser("1.5e-2").parseRoot())
        assertEquals(2.0e10, JsonParser("2e10").parseRoot())
    }

    @Test fun parsesNegativeAndPositiveInts() {
        assertEquals(-42L, JsonParser("-42").parseRoot())
        assertEquals(0L, JsonParser("0").parseRoot())
        assertEquals(123L, JsonParser("123").parseRoot())
    }

    @Test fun parsesDeeplyNestedArrays() {
        val text = "[[[[[1,2],[3]],[]],[[[4]]]]]"
        val v = JsonParser(text).parseRoot()
        assertTrue(v is List<*>)
        // verify by re-encoding via Json (lists pass through)
        // walk down to confirm leaf
        var cur: Any? = v
        var depth = 0
        while (cur is List<*> && cur.isNotEmpty() && cur[0] is List<*>) {
            cur = cur[0]
            depth++
        }
        assertTrue(depth >= 3)
    }

    @Test fun parsesEmptyContainers() {
        assertEquals(emptyList<Any?>(), JsonParser("[]").parseRoot())
        assertEquals(emptyMap<String, Any?>(), JsonParser("{}").parseRoot())
    }

    @Test fun parsesNullBoolean() {
        assertEquals(null, JsonParser("null").parseRoot())
        assertEquals(true, JsonParser("true").parseRoot())
        assertEquals(false, JsonParser("false").parseRoot())
    }

    @Test fun parsesMixedObject() {
        val text = """{"n": null, "b": true, "i": 1, "f": 1.5, "s": "x", "a": [1,2], "o": {"k":"v"}}"""
        @Suppress("UNCHECKED_CAST")
        val m = JsonParser(text).parseRoot() as Map<String, Any?>
        assertEquals(null, m["n"])
        assertEquals(true, m["b"])
        assertEquals(1L, m["i"])
        assertEquals(1.5, m["f"])
        assertEquals("x", m["s"])
        assertEquals(listOf(1L, 2L), m["a"])
        assertEquals(mapOf("k" to "v"), m["o"])
    }

    @Test fun rejectsUnterminatedString() {
        assertFailsWith<JsonLexException> {
            JsonParser("\"abc").parseRoot()
        }
    }

    @Test fun rejectsTrailingData() {
        assertFailsWith<JsonParseException> {
            JsonParser("1 2").parseRoot()
        }
    }

    @Test fun handlesLeadingWhitespace() {
        assertEquals(1L, JsonParser("   \n\t  1   ").parseRoot())
    }
}
