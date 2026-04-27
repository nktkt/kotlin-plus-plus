package dev.kpp.gradle

/**
 * Registers:
 *   - the `kpp { }` extension
 *   - a `kppCheck` task with conventions wiring sourceRoots from the
 *     project's Kotlin/JVM source sets if available, else falling back
 *     to src/main/kotlin and src/test/kotlin
 *
 * Apply with: plugins { id("dev.kotlinplusplus.kpp") }
 */
class KppPlugin : org.gradle.api.Plugin<org.gradle.api.Project> {
    override fun apply(target: org.gradle.api.Project) {
        val extension = target.extensions.create("kpp", KppExtension::class.java)

        extension.failOnViolation.convention(true)
        // Default roots are the conventional Kotlin source directories. We do
        // not filter to "exists" here because the dirs might be created by a
        // generator task; the actual existence check happens in KppCheckTask.run().
        extension.sourceRoots.convention(
            listOf(
                target.file("src/main/kotlin"),
                target.file("src/test/kotlin"),
            ),
        )

        target.tasks.register("kppCheck", KppCheckTask::class.java) { task ->
            task.sourceRoots.set(extension.sourceRoots)
            task.suppressedRules.set(extension.suppressedRules)
            task.failOnViolation.set(extension.failOnViolation)
            task.group = "verification"
            task.description = "Runs the Kotlin++ regex analyzer over the configured source roots."
        }
    }
}
