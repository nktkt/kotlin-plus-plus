package dev.kpp.analyzer

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ScannerTest {

    private fun withTempTree(block: (Path) -> Unit) {
        val dir = Files.createTempDirectory("kpp-analyzer-test")
        try {
            block(dir)
        } finally {
            dir.toFile().walkBottomUp().forEach { it.delete() }
        }
    }

    @Test
    fun kpp001_ignored_must_handle_return() = withTempTree { dir ->
        val f = dir.resolve("A.kt")
        f.writeText(
            """
            package sample

            @MustHandle
            fun save(): Int = 1

            fun caller() {
                save()
            }
            """.trimIndent(),
        )
        val violations = KppScanner(listOf(dir.toFile())).scan()
        assertTrue(violations.any { it.ruleId == "KPP001" }, "expected KPP001, got=$violations")
    }

    @Test
    fun kpp004_mutable_public_api() = withTempTree { dir ->
        val f = dir.resolve("B.kt")
        f.writeText(
            """
            package sample

            public fun bad(): MutableList<String> = mutableListOf()
            """.trimIndent(),
        )
        val violations = KppScanner(listOf(dir.toFile())).scan()
        assertTrue(violations.any { it.ruleId == "KPP004" }, "expected KPP004, got=$violations")
    }

    @Test
    fun kpp011_blocking_in_suspend() = withTempTree { dir ->
        val f = dir.resolve("C.kt")
        f.writeText(
            """
            package sample

            suspend fun work() {
                Thread.sleep(100)
            }
            """.trimIndent(),
        )
        val violations = KppScanner(listOf(dir.toFile())).scan()
        assertTrue(violations.any { it.ruleId == "KPP011" }, "expected KPP011, got=$violations")
    }

    @Test
    fun kpp018_exception_escapes_public_api() = withTempTree { dir ->
        val f = dir.resolve("D.kt")
        f.writeText(
            """
            package sample

            public fun risky() { throw RuntimeException("x") }
            """.trimIndent(),
        )
        val violations = KppScanner(listOf(dir.toFile())).scan()
        assertTrue(violations.any { it.ruleId == "KPP018" }, "expected KPP018, got=$violations")
    }

    @Test
    fun all_four_rules_fire_in_one_tree() = withTempTree { dir ->
        dir.resolve("A.kt").writeText(
            """
            @MustHandle
            fun save(): Int = 1

            fun caller() {
                save()
            }
            """.trimIndent(),
        )
        dir.resolve("B.kt").writeText(
            """public fun bad(): MutableList<String> = mutableListOf()""",
        )
        dir.resolve("C.kt").writeText(
            """
            suspend fun work() {
                Thread.sleep(100)
            }
            """.trimIndent(),
        )
        dir.resolve("D.kt").writeText(
            """public fun risky() { throw RuntimeException("x") }""",
        )
        val ids = KppScanner(listOf(dir.toFile())).scan().map { it.ruleId }.toSet()
        assertTrue("KPP001" in ids, "missing KPP001 in $ids")
        assertTrue("KPP004" in ids, "missing KPP004 in $ids")
        assertTrue("KPP011" in ids, "missing KPP011 in $ids")
        assertTrue("KPP018" in ids, "missing KPP018 in $ids")
    }

    @Test
    fun kpp002_catches_raw_throwable_and_exception() = withTempTree { dir ->
        val f = dir.resolve("E.kt")
        f.writeText(
            """
            package sample

            fun a() {
                try { } catch (e: Throwable) { }
            }
            fun b() {
                try { } catch (_: Exception) { }
            }
            fun c() {
                try { } catch (ex: java.lang.RuntimeException) { }
            }
            """.trimIndent(),
        )
        val violations = KppScanner(listOf(dir.toFile())).scan().filter { it.ruleId == "KPP002" }
        assertEquals(3, violations.size, "expected 3 KPP002, got=$violations")
    }

    @Test
    fun kpp002_does_not_fire_on_specific_exceptions() = withTempTree { dir ->
        val f = dir.resolve("F.kt")
        f.writeText(
            """
            package sample

            class MyError : Exception()
            fun a() {
                try { } catch (e: IOException) { }
            }
            fun b() {
                try { } catch (e: MyError) { }
            }
            """.trimIndent(),
        )
        val violations = KppScanner(listOf(dir.toFile())).scan().filter { it.ruleId == "KPP002" }
        assertTrue(violations.isEmpty(), "expected no KPP002, got=$violations")
    }

    @Test
    fun kpp005_mutable_field_in_immutable_data_class() = withTempTree { dir ->
        val f = dir.resolve("G.kt")
        f.writeText(
            """
            package sample

            @Immutable
            data class X(val items: MutableList<String>, var name: String)
            """.trimIndent(),
        )
        val violations = KppScanner(listOf(dir.toFile())).scan().filter { it.ruleId == "KPP005" }
        assertEquals(2, violations.size, "expected 2 KPP005, got=$violations")
    }

    @Test
    fun kpp005_does_not_fire_on_clean_immutable_class() = withTempTree { dir ->
        val f = dir.resolve("H.kt")
        f.writeText(
            """
            package sample

            @Immutable
            data class X(val items: List<String>, val name: String)
            """.trimIndent(),
        )
        val violations = KppScanner(listOf(dir.toFile())).scan().filter { it.ruleId == "KPP005" }
        assertTrue(violations.isEmpty(), "expected no KPP005, got=$violations")
    }

    @Test
    fun suppression_line_comment_skips_rule() = withTempTree { dir ->
        val f = dir.resolve("I.kt")
        f.writeText(
            """
            package sample

            fun a() {
                // noinspection KPP002
                try { } catch (e: Throwable) { }
            }
            """.trimIndent(),
        )
        val violations = KppScanner(listOf(dir.toFile())).scan().filter { it.ruleId == "KPP002" }
        assertTrue(violations.isEmpty(), "expected no KPP002, got=$violations")
    }

    @Test
    fun suppression_file_directive_skips_rule() = withTempTree { dir ->
        val f = dir.resolve("J.kt")
        f.writeText(
            """
            @file:Suppress("KPP002")
            package sample

            fun a() {
                try { } catch (e: Throwable) { }
            }
            fun b() {
                try { } catch (_: Exception) { }
            }
            """.trimIndent(),
        )
        val violations = KppScanner(listOf(dir.toFile())).scan().filter { it.ruleId == "KPP002" }
        assertTrue(violations.isEmpty(), "expected no KPP002, got=$violations")
    }

    @Test
    fun suppression_only_silences_listed_rules() = withTempTree { dir ->
        val f = dir.resolve("K.kt")
        f.writeText(
            """
            package sample

            // noinspection KPP002
            public fun bad(): MutableList<String> = mutableListOf()
            """.trimIndent(),
        )
        val violations = KppScanner(listOf(dir.toFile())).scan()
        assertTrue(violations.any { it.ruleId == "KPP004" }, "expected KPP004, got=$violations")
        assertFalse(violations.any { it.ruleId == "KPP002" }, "did not expect KPP002, got=$violations")
    }

    @Test
    fun triple_quoted_string_does_not_trigger_kpp011() = withTempTree { dir ->
        val f = dir.resolve("L.kt")
        f.writeText(
            "package sample\n\n" +
                "suspend fun foo() { val s = \"\"\" Thread.sleep(100) \"\"\" }\n",
        )
        val violations = KppScanner(listOf(dir.toFile())).scan()
        assertTrue(violations.isEmpty(), "expected no violations, got=$violations")
    }

    @Test
    fun triple_quoted_string_does_not_trigger_kpp004() = withTempTree { dir ->
        val f = dir.resolve("M.kt")
        f.writeText(
            "package sample\n\n" +
                "private fun host() {\n" +
                "    val template = \"\"\" public fun bad(): MutableList<String> = mutableListOf() \"\"\"\n" +
                "}\n",
        )
        val violations = KppScanner(listOf(dir.toFile())).scan()
        assertTrue(violations.isEmpty(), "expected no violations, got=$violations")
    }

    @Test
    fun triple_quoted_string_does_not_trigger_kpp018() = withTempTree { dir ->
        val f = dir.resolve("N.kt")
        f.writeText(
            "package sample\n\n" +
                "public fun host() {\n" +
                "    try {\n" +
                "        val t = \"\"\" throw RuntimeException(\"x\") \"\"\"\n" +
                "    } catch (e: IllegalStateException) { }\n" +
                "}\n",
        )
        val violations = KppScanner(listOf(dir.toFile())).scan()
        assertTrue(violations.isEmpty(), "expected no violations, got=$violations")
    }

    @Test
    fun regular_kotlin_violation_still_fires_when_string_is_present() = withTempTree { dir ->
        val f = dir.resolve("O.kt")
        f.writeText(
            "package sample\n\n" +
                "fun real() {\n" +
                "    try { } catch (e: Throwable) { }\n" +
                "}\n" +
                "fun fixture() {\n" +
                "    val s = \"\"\" catch (e: Exception) \"\"\"\n" +
                "}\n",
        )
        val violations = KppScanner(listOf(dir.toFile())).scan().filter { it.ruleId == "KPP002" }
        assertEquals(1, violations.size, "expected exactly one KPP002, got=$violations")
    }

    @Test
    fun multi_line_triple_quote_string_is_masked_correctly() = withTempTree { dir ->
        val f = dir.resolve("P.kt")
        f.writeText(
            "package sample\n\n" +
                "private fun host() {\n" +
                "    val s = \"\"\"\n" +
                "        public fun bad(): MutableList<String> = mutableListOf()\n" +
                "        Thread.sleep(100)\n" +
                "        catch (e: Throwable) { }\n" +
                "        throw RuntimeException(\"x\")\n" +
                "    \"\"\"\n" +
                "}\n",
        )
        val violations = KppScanner(listOf(dir.toFile())).scan()
        assertTrue(violations.isEmpty(), "expected no violations, got=$violations")
    }

    @Test
    fun kpp013_fires_on_top_level_public_var() = withTempTree { dir ->
        val f = dir.resolve("Q.kt")
        f.writeText(
            """
            package sample

            var counter = 0
            """.trimIndent(),
        )
        val violations = KppScanner(listOf(dir.toFile())).scan().filter { it.ruleId == "KPP013" }
        assertEquals(1, violations.size, "expected 1 KPP013, got=$violations")
    }

    @Test
    fun kpp013_fires_on_class_body_public_var() = withTempTree { dir ->
        val f = dir.resolve("R.kt")
        f.writeText(
            """
            package sample

            class Foo {
                var name = "x"
            }
            """.trimIndent(),
        )
        val violations = KppScanner(listOf(dir.toFile())).scan().filter { it.ruleId == "KPP013" }
        assertEquals(1, violations.size, "expected 1 KPP013, got=$violations")
    }

    @Test
    fun kpp013_does_not_fire_on_private_var() = withTempTree { dir ->
        val f = dir.resolve("S.kt")
        f.writeText(
            """
            package sample

            private var x = 0
            """.trimIndent(),
        )
        val violations = KppScanner(listOf(dir.toFile())).scan().filter { it.ruleId == "KPP013" }
        assertTrue(violations.isEmpty(), "expected no KPP013, got=$violations")
    }

    @Test
    fun kpp013_does_not_fire_on_internal_var() = withTempTree { dir ->
        val f = dir.resolve("T.kt")
        f.writeText(
            """
            package sample

            internal var x = 0
            """.trimIndent(),
        )
        val violations = KppScanner(listOf(dir.toFile())).scan().filter { it.ruleId == "KPP013" }
        assertTrue(violations.isEmpty(), "expected no KPP013, got=$violations")
    }

    @Test
    fun kpp013_does_not_fire_on_local_var() = withTempTree { dir ->
        val f = dir.resolve("U.kt")
        f.writeText(
            """
            package sample

            fun work() {
                var local = 1
                local += 1
            }
            """.trimIndent(),
        )
        val violations = KppScanner(listOf(dir.toFile())).scan().filter { it.ruleId == "KPP013" }
        assertTrue(violations.isEmpty(), "expected no KPP013, got=$violations")
    }

    @Test
    fun kpp013_does_not_fire_on_constructor_var_param() = withTempTree { dir ->
        val f = dir.resolve("V.kt")
        f.writeText(
            """
            package sample

            class Foo(var x: Int)
            """.trimIndent(),
        )
        val violations = KppScanner(listOf(dir.toFile())).scan().filter { it.ruleId == "KPP013" }
        assertTrue(violations.isEmpty(), "expected no KPP013, got=$violations")
    }

    @Test
    fun kpp013_severity_is_warning() {
        val rule = KPP_RULES.firstOrNull { it.id == "KPP013" }
        assertTrue(rule != null, "KPP013 not registered")
        assertEquals(Severity.WARN, rule!!.severity)
    }
}
