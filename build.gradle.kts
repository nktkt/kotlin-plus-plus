plugins {
    kotlin("jvm") version "2.2.0" apply false
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
    }
}
