package dev.kpp.gradle

import org.gradle.testkit.runner.GradleRunner
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertTrue

class KppPluginFunctionalTest {

    private fun newProjectDir(): File {
        val dir = Files.createTempDirectory("kpp-fn-").toFile()
        dir.resolve("settings.gradle.kts").writeText(
            """
            rootProject.name = "test-project"
            """.trimIndent(),
        )
        return dir
    }

    private fun writeBuildScript(dir: File, body: String = "") {
        dir.resolve("build.gradle.kts").writeText(
            """
            plugins { id("dev.kotlinplusplus.kpp") }
            $body
            """.trimIndent(),
        )
    }

    private fun writeKt(dir: File, relPath: String, content: String) {
        val target = dir.resolve(relPath)
        target.parentFile.mkdirs()
        target.writeText(content)
    }

    @Test
    fun fails_on_violation() {
        val dir = newProjectDir()
        writeBuildScript(dir, "kpp { failOnViolation.set(true) }")
        writeKt(
            dir,
            "src/main/kotlin/Foo.kt",
            """
            fun foo() { try { bar() } catch (e: Throwable) {} }
            fun bar() {}
            """.trimIndent(),
        )

        val result = GradleRunner.create()
            .withProjectDir(dir)
            .withArguments("kppCheck", "--stacktrace")
            .withPluginClasspath()
            .buildAndFail()

        assertTrue("KPP002" in result.output, result.output)
    }

    @Test
    fun succeeds_on_clean_code() {
        val dir = newProjectDir()
        writeBuildScript(dir)
        writeKt(
            dir,
            "src/main/kotlin/Foo.kt",
            """
            fun foo(): Int = 42
            """.trimIndent(),
        )

        val result = GradleRunner.create()
            .withProjectDir(dir)
            .withArguments("kppCheck")
            .withPluginClasspath()
            .build()

        assertTrue("BUILD SUCCESSFUL" in result.output, result.output)
    }

    @Test
    fun suppressed_rule_passes() {
        val dir = newProjectDir()
        writeBuildScript(dir, "kpp { suppressedRules.add(\"KPP002\") }")
        writeKt(
            dir,
            "src/main/kotlin/Foo.kt",
            """
            fun foo() { try { bar() } catch (e: Throwable) {} }
            fun bar() {}
            """.trimIndent(),
        )

        val result = GradleRunner.create()
            .withProjectDir(dir)
            .withArguments("kppCheck")
            .withPluginClasspath()
            .build()

        assertTrue("BUILD SUCCESSFUL" in result.output, result.output)
    }
}
