package dev.kpp.gradle

/**
 * DSL for the kpp { } block. Configure source roots, suppressions, and
 * exit-on-violation behavior.
 *
 *   kpp {
 *       sourceRoots.set(listOf(file("src/main/kotlin")))
 *       failOnViolation.set(true)
 *       suppressedRules.add("KPP002")
 *   }
 */
abstract class KppExtension {
    abstract val sourceRoots: org.gradle.api.provider.ListProperty<java.io.File>
    abstract val suppressedRules: org.gradle.api.provider.SetProperty<String>
    abstract val failOnViolation: org.gradle.api.provider.Property<Boolean>
}
