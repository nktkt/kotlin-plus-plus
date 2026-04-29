package dev.kpp.samples.ksp

import dev.kpp.derive.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DeriveJsonGeneratedTest {

    @Test
    fun generated_matches_runtime_for_default_class() {
        val g = Greeting("a", "b", 1)
        assertEquals(Json.encode(g), g.toJsonGenerated())
    }

    @Test
    fun generated_matches_runtime_for_snake_case_class() {
        val r = Request(42, "abc", true)
        assertEquals(Json.encode(r), r.toJsonGenerated())
    }

    @Test
    fun generated_escapes_quotes_and_backslashes() {
        val g = Greeting("hello \"world\"", "x\\y", 0)
        val out = g.toJsonGenerated()
        assertTrue(out.contains("\\\""), "expected escaped quote in $out")
        assertTrue(out.contains("\\\\"), "expected escaped backslash in $out")
        assertEquals(Json.encode(g), out)
    }

    @Test
    fun generated_handles_control_chars() {
        val g = Greeting("a\nb", "c\td\re", 0)
        val out = g.toJsonGenerated()
        assertTrue(out.contains("\\n"))
        assertTrue(out.contains("\\t"))
        assertTrue(out.contains("\\r"))
        assertEquals(Json.encode(g), out)
    }

    @Test
    fun runtime_and_generated_outputs_have_matching_string_form() {
        val cases = listOf(
            Greeting("", "", 0),
            Greeting("simple", "case", 7),
            Greeting("with\nnewline", "and\ttab", -1),
            Greeting("quote\"inside", "back\\slash", Int.MAX_VALUE),
            Greeting("formfeed", "carriage\rreturn", Int.MIN_VALUE),
        )
        for (c in cases) {
            assertEquals(Json.encode(c), c.toJsonGenerated(), "mismatch for $c")
        }

        val snakeCases = listOf(
            Request(0, "", false),
            Request(1, "tok", true),
            Request(-7, "with\"quote", false),
            Request(Int.MAX_VALUE, "back\\slash\nnl", true),
        )
        for (c in snakeCases) {
            assertEquals(Json.encode(c), c.toJsonGenerated(), "mismatch for $c")
        }
    }
}
