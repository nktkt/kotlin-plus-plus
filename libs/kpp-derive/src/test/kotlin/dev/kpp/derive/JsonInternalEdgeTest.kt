package dev.kpp.derive

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class JsonInternalEdgeTest {
    @Test fun escapeEmitsBackspace() {
        // The encoder escape table includes \b — exercise it directly.
        val out = escapeJsonString("a\bb")
        assertEquals("\"a\\bb\"", out)
    }

    @Test fun escapeEmitsFormFeed() {
        // Form-feed () escape branch.
        val out = escapeJsonString("ab")
        assertEquals("\"a\\fb\"", out)
    }

    @Test fun camelToSnakeEmptyIsEmpty() {
        assertEquals("", camelToSnake(""))
    }

    @Test fun camelToSnakeSingleLowercase() {
        assertEquals("foo", camelToSnake("foo"))
    }

    @Test fun camelToSnakeLeadingUppercase() {
        // Leading uppercase: no underscore prefix on i==0.
        assertEquals("foo_bar", camelToSnake("FooBar"))
    }

    @Test fun lexerRejectsUnexpectedChar() {
        val ex = assertFailsWith<JsonLexException> {
            JsonParser("@").parseRoot()
        }
        assertTrue(ex.message!!.contains("unexpected char"))
    }

    @Test fun lexerEscapeSlashIsHandled() {
        // \/ escape decodes to /.
        val v = JsonParser("\"a\\/b\"").parseRoot() as String
        assertEquals("a/b", v)
    }

    @Test fun lexerRejectsBadEscape() {
        val ex = assertFailsWith<JsonLexException> {
            JsonParser("\"\\x\"").parseRoot()
        }
        assertTrue(ex.message!!.contains("bad escape"))
    }

    @Test fun lexerRejectsDanglingEscape() {
        val ex = assertFailsWith<JsonLexException> {
            JsonParser("\"abc\\").parseRoot()
        }
        assertTrue(ex.message!!.contains("dangling"))
    }

    @Test fun lexerRejectsBadUnicodeEscape() {
        val ex = assertFailsWith<JsonLexException> {
            JsonParser("\"\\u00\"").parseRoot()
        }
        assertTrue(ex.message!!.contains("bad \\u"))
    }

    @Test fun lexerRejectsBadTrueLiteral() {
        // Starts with t but is not "true" — covers the readBool fallthrough.
        val ex = assertFailsWith<JsonLexException> {
            JsonParser("trueX").parseRoot()
        }
        // Either "trailing data" or "bad literal" depending on which fires first.
        // Force the readBool error specifically: a token starting with 't' but not "true".
        val ex2 = assertFailsWith<JsonLexException> {
            JsonParser("tru").parseRoot()
        }
        assertTrue(ex2.message!!.contains("bad literal"))
        // ex1 still proves the bool branch fired.
        assertTrue(ex.message!!.isNotBlank())
    }

    @Test fun lexerRejectsBadNullLiteral() {
        val ex = assertFailsWith<JsonLexException> {
            JsonParser("nul").parseRoot()
        }
        assertTrue(ex.message!!.contains("bad literal"))
    }

    @Test fun parserRejectsBareCommaToken() {
        // Top-level `,` is not a valid value — exercises parseValue's `else -> throw`.
        val ex = assertFailsWith<JsonParseException> {
            JsonParser(",").parseRoot()
        }
        assertTrue(ex.message!!.contains("unexpected token"))
    }

    @Test fun parserRejectsObjectMissingStringKey() {
        // After `{` the next token must be a string. `{1:2}` triggers the "expected string key" branch.
        val ex = assertFailsWith<JsonParseException> {
            JsonParser("{1:2}").parseRoot()
        }
        assertTrue(ex.message!!.contains("expected string key"))
    }

    @Test fun parserRejectsObjectMissingCommaOrBrace() {
        // After a key:value, expect , or } — feeding `]` triggers the else-throw.
        val ex = assertFailsWith<JsonParseException> {
            JsonParser("""{"a":1]""").parseRoot()
        }
        assertTrue(ex.message!!.contains("expected , or }"))
    }

    @Test fun parserRejectsArrayMissingCommaOrBracket() {
        val ex = assertFailsWith<JsonParseException> {
            JsonParser("""[1}""").parseRoot()
        }
        assertTrue(ex.message!!.contains("expected , or ]"))
    }
}
