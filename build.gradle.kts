plugins {
    kotlin("jvm") version "2.2.0" apply false
    id("org.jetbrains.kotlinx.kover") version "0.9.1"
}

allprojects {
    group = "dev.kotlinplusplus"
    version = "0.1.0-SNAPSHOT"
}

subprojects {
    plugins.withId("org.jetbrains.kotlin.jvm") {
        extensions.configure<org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension> {
            jvmToolchain(21)
            compilerOptions {
                freeCompilerArgs.addAll(
                    "-Xcontext-parameters",
                    "-Xnon-local-break-continue",
                )
            }
        }
        apply(plugin = "org.jetbrains.kotlinx.kover")
    }
}

// Aggregate Kover coverage from every Kotlin-JVM subproject into the root report.
dependencies {
    subprojects.forEach { sp ->
        sp.plugins.withId("org.jetbrains.kotlin.jvm") {
            kover(sp)
        }
    }
}
