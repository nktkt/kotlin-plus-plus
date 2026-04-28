package dev.kpp.analyzer

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ScannerEdgeCaseTest {

    private fun withTempTree(block: (Path) -> Unit) {
        val dir = Files.createTempDirectory("kpp-analyzer-edge-test")
        try {
            block(dir)
        } finally {
            dir.toFile().walkBottomUp().forEach { it.delete() }
        }
    }

    @Test
    fun must_handle_annotation_on_separate_line_above_fun() = withTempTree { dir ->
        // Exercises the armed-state path in collectMustHandleNames where the
        // `fun` declaration appears on a line BELOW the @MustHandle annotation
        // (with no other annotation between them).
        val f = dir.resolve("Above.kt")
        f.writeText(
            """
            package sample

            @MustHandle
            fun separated(): Int = 1

            fun caller() {
                separated()
            }
            """.trimIndent(),
        )
        val violations = KppScanner(listOf(dir.toFile())).scan().filter { it.ruleId == "KPP001" }
        assertEquals(1, violations.size, "expected 1 KPP001, got=$violations")
    }

    @Test
    fun must_handle_armed_state_disarms_on_unrelated_decl() = withTempTree { dir ->
        // When @MustHandle is followed by an unrelated declaration (no `fun`),
        // the armed state must disarm so a later `fun` is NOT incorrectly tagged.
        val f = dir.resolve("Disarm.kt")
        f.writeText(
            """
            package sample

            @MustHandle
            class NotAFun

            fun unrelated(): Int = 1

            fun caller() {
                unrelated()
            }
            """.trimIndent(),
        )
        val violations = KppScanner(listOf(dir.toFile())).scan().filter { it.ruleId == "KPP001" }
        assertTrue(violations.isEmpty(), "expected no KPP001, got=$violations")
    }

    @Test
    fun io_annotation_on_separate_line_above_fun() = withTempTree { dir ->
        // Sibling test for collectIoOrDbNames armed-state path.
        val f = dir.resolve("Io.kt")
        f.writeText(
            """
            package sample

            @Io
            fun fetchSeparate(): String = "x"

            fun caller() {
                fetchSeparate()
            }
            """.trimIndent(),
        )
        val violations = KppScanner(listOf(dir.toFile())).scan().filter { it.ruleId == "KPP008" }
        assertEquals(1, violations.size, "expected 1 KPP008, got=$violations")
    }

    @Test
    fun io_annotation_armed_state_disarms_on_unrelated_decl() = withTempTree { dir ->
        val f = dir.resolve("IoDisarm.kt")
        f.writeText(
            """
            package sample

            @Io
            class NotAFun

            fun unrelated(): Int = 1

            fun caller() {
                unrelated()
            }
            """.trimIndent(),
        )
        val violations = KppScanner(listOf(dir.toFile())).scan().filter { it.ruleId == "KPP008" }
        assertTrue(violations.isEmpty(), "expected no KPP008, got=$violations")
    }

    @Test
    fun kpp018_fires_on_throws_annotation() = withTempTree { dir ->
        // The @Throws annotation on the previous line triggers a separate KPP018
        // path from "throw on inline body".
        val f = dir.resolve("Throws.kt")
        f.writeText(
            """
            package sample

            @Throws(IllegalStateException::class)
            public fun risky(): Int { return 1 }
            """.trimIndent(),
        )
        val violations = KppScanner(listOf(dir.toFile())).scan().filter { it.ruleId == "KPP018" }
        assertTrue(violations.any { it.message.contains("@Throws") }, "expected @Throws KPP018, got=$violations")
    }

    @Test
    fun kpp011_fires_on_readText_inside_suspend() = withTempTree { dir ->
        val f = dir.resolve("ReadText.kt")
        f.writeText(
            """
            package sample

            import java.net.URL

            suspend fun fetch(): String {
                return URL("http://x").readText()
            }
            """.trimIndent(),
        )
        val violations = KppScanner(listOf(dir.toFile())).scan().filter { it.ruleId == "KPP011" }
        // Both URL( and .readText( should fire — but at minimum .readText must appear.
        assertTrue(violations.any { it.message.contains(".readText") }, "expected .readText KPP011, got=$violations")
    }

    @Test
    fun missing_root_directory_yields_no_violations() = withTempTree { dir ->
        // Pass a non-existent root to exercise the !root.exists() branch.
        val nonexistent = dir.resolve("does-not-exist").toFile()
        val violations = KppScanner(listOf(nonexistent)).scan()
        assertTrue(violations.isEmpty(), "expected no violations for missing root, got=$violations")
    }

    @Test
    fun stripStringsAndComments_handles_escaped_quote_inside_string() = withTempTree { dir ->
        // A line like `val s = "a\"b"` has an escaped quote inside a string. The
        // sanitizer must keep the brace count consistent so suspendDepth tracking
        // works correctly. Place a Thread.sleep AFTER such a string in a suspend body.
        val f = dir.resolve("Esc.kt")
        f.writeText(
            "package sample\n\n" +
                "suspend fun work() {\n" +
                "    val s = \"a\\\"b\"\n" +
                "    Thread.sleep(100)\n" +
                "}\n",
        )
        val violations = KppScanner(listOf(dir.toFile())).scan().filter { it.ruleId == "KPP011" }
        assertEquals(1, violations.size, "expected KPP011 to fire after string with escape, got=$violations")
    }

    @Test
    fun char_literal_is_masked_in_stripStringsAndComments() = withTempTree { dir ->
        // A char literal `'{'` should not be counted as an opening brace by the
        // sanitizer. Place a suspend fun whose body relies on accurate brace
        // tracking.
        val f = dir.resolve("Char.kt")
        f.writeText(
            "package sample\n\n" +
                "suspend fun work() {\n" +
                "    val open = '{'\n" +
                "    Thread.sleep(100)\n" +
                "}\n",
        )
        val violations = KppScanner(listOf(dir.toFile())).scan().filter { it.ruleId == "KPP011" }
        assertEquals(1, violations.size, "expected KPP011 with char literal masked, got=$violations")
    }

    @Test
    fun deeply_nested_immutable_data_class_with_multiline_constructor() = withTempTree { dir ->
        // Multi-line primary constructor on @Immutable data class — exercises the
        // header-vs-body slice handling in scanImmutableClasses for multi-line params.
        val f = dir.resolve("MultiLine.kt")
        f.writeText(
            """
            package sample

            @Immutable
            data class Big(
                val name: String,
                val items: MutableList<String>,
                var counter: Int,
            )
            """.trimIndent(),
        )
        val violations = KppScanner(listOf(dir.toFile())).scan().filter { it.ruleId == "KPP005" }
        // One for `items: MutableList`, one for `var counter`.
        assertEquals(2, violations.size, "expected 2 KPP005, got=$violations")
    }

    @Test
    fun data_class_dedup_with_kpp005_still_fires_when_no_immutable() = withTempTree { dir ->
        // Verify the KPP007 look-back across blank, comment, and other annotation
        // lines still fires when no @Immutable is found in that look-back.
        val f = dir.resolve("Dedup.kt")
        f.writeText(
            """
            package sample

            // a comment
            @Suppress("unused")

            data class Foo(var name: String)
            """.trimIndent(),
        )
        val violations = KppScanner(listOf(dir.toFile())).scan().filter { it.ruleId == "KPP007" }
        assertEquals(1, violations.size, "expected 1 KPP007, got=$violations")
    }

    @Test
    fun suppression_anchor_silences_kpp005_from_class_header_line() = withTempTree { dir ->
        // KPP005 may be silenced by a suppression on the construct anchor (class header) line.
        // The trailing same-line `// noinspection KPP005` comment marks this anchor.
        val f = dir.resolve("Anchor.kt")
        f.writeText(
            """
            package sample

            @Immutable
            data class Big( // noinspection KPP005
                val items: MutableList<String>,
            )
            """.trimIndent(),
        )
        val violations = KppScanner(listOf(dir.toFile())).scan().filter { it.ruleId == "KPP005" }
        assertTrue(violations.isEmpty(), "expected anchor suppression to silence KPP005, got=$violations")
    }

    @Test
    fun class_with_no_primary_constructor_is_skipped() = withTempTree { dir ->
        // An @Immutable class without `(...)` after the name has no primary
        // constructor — exercises the `if (!seenOpen)` skip branch.
        val f = dir.resolve("NoCtor.kt")
        f.writeText(
            """
            package sample

            @Immutable
            class NoCtor {
                val items: List<String> = emptyList()
            }
            """.trimIndent(),
        )
        val violations = KppScanner(listOf(dir.toFile())).scan().filter { it.ruleId == "KPP005" }
        assertTrue(violations.isEmpty(), "expected no KPP005 for class without primary ctor, got=$violations")
    }

    @Test
    fun immutable_marker_without_class_below_is_ignored() = withTempTree { dir ->
        // @Immutable annotation that is NOT followed by a class header (e.g. an
        // unrelated declaration) — exercises the `headerLine == -1` early continue.
        val f = dir.resolve("OrphanAnno.kt")
        f.writeText(
            """
            package sample

            @Immutable
            val notAClass = 0

            data class Real(val x: Int)
            """.trimIndent(),
        )
        val violations = KppScanner(listOf(dir.toFile())).scan().filter { it.ruleId == "KPP005" }
        assertTrue(violations.isEmpty(), "expected no KPP005 for orphan @Immutable, got=$violations")
    }

    @Test
    fun kpp001_does_not_fire_on_chained_must_handle_call() = withTempTree { dir ->
        // Chaining detection: `save().toString()` flows the result, so KPP001 must NOT fire.
        val f = dir.resolve("Chain.kt")
        f.writeText(
            """
            package sample

            @MustHandle
            fun save(): String = "x"

            fun caller() {
                save().toString()
            }
            """.trimIndent(),
        )
        val violations = KppScanner(listOf(dir.toFile())).scan().filter { it.ruleId == "KPP001" }
        assertTrue(violations.isEmpty(), "expected no KPP001 for chained call, got=$violations")
    }

    @Test
    fun kpp013_does_not_fire_on_var_inside_multiline_signature_body() = withTempTree { dir ->
        // Multi-line `fun` signature where `(` and `{` are on different lines.
        // The computeInsideFunBody walker must still classify the body lines as inside.
        val f = dir.resolve("MultiSig.kt")
        f.writeText(
            """
            package sample

            fun work(
                a: Int,
                b: Int,
            ) {
                var local = a + b
                local += 1
            }
            """.trimIndent(),
        )
        val violations = KppScanner(listOf(dir.toFile())).scan().filter { it.ruleId == "KPP013" }
        assertTrue(violations.isEmpty(), "expected no KPP013 for local var in multi-line fun, got=$violations")
    }

    @Test
    fun kpp013_handles_single_expression_fun_then_var() = withTempTree { dir ->
        // Single-expression `fun foo() = ...` doesn't open a brace; a later top-level
        // `var` must still be flagged because the awaitBrace state was cleared.
        val f = dir.resolve("SingleExpr.kt")
        f.writeText(
            """
            package sample

            fun double(x: Int) = x * 2

            var counter = 0
            """.trimIndent(),
        )
        val violations = KppScanner(listOf(dir.toFile())).scan().filter { it.ruleId == "KPP013" }
        assertEquals(1, violations.size, "expected KPP013 on top-level var after single-expr fun, got=$violations")
    }

    @Test
    fun reporter_renders_violation_summary() = withTempTree { dir ->
        // Sanity check for the Reporter / Violation data structure too.
        val f = dir.resolve("R.kt")
        f.writeText(
            """
            package sample

            public fun bad(): MutableList<String> = mutableListOf()
            """.trimIndent(),
        )
        val violations = KppScanner(listOf(dir.toFile())).scan()
        assertFalse(violations.isEmpty())
        val v = violations.first()
        assertEquals("KPP004", v.ruleId)
        assertTrue(v.line > 0)
        assertTrue(v.column > 0)
    }
}
