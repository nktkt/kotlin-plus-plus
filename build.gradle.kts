plugins {
    kotlin("jvm") version "2.2.0" apply false
    id("org.jetbrains.kotlinx.kover") version "0.9.8"
}

allprojects {
    group = "dev.kotlinplusplus"
    version = "0.4.0"
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

    // Apply maven-publish to library modules only — not samples, and not
    // kpp-gradle-plugin (which publishes via java-gradle-plugin's own convention).
    val isLibrary = project.path.startsWith(":libs:") &&
        project.path != ":libs:kpp-gradle-plugin"
    if (isLibrary) {
        apply(plugin = "maven-publish")
        // afterEvaluate: the Kotlin plugin must register its `java` software
        // component before `from(components["java"])` resolves.
        afterEvaluate {
            extensions.configure<org.gradle.api.publish.PublishingExtension> {
                publications {
                    create<org.gradle.api.publish.maven.MavenPublication>("maven") {
                        groupId = project.group.toString()
                        artifactId = project.name
                        version = project.version.toString()
                        from(components["java"])
                        pom {
                            name.set("Kotlin++: ${project.name}")
                            description.set(
                                "Kotlin++ MVP — ${project.name}. " +
                                    "Library-level emulation of typed errors, " +
                                    "capability DI, deep immutability, and more."
                            )
                            url.set("https://github.com/nktkt/kotlin-plus-plus")
                            scm {
                                url.set("https://github.com/nktkt/kotlin-plus-plus")
                                connection.set("scm:git:https://github.com/nktkt/kotlin-plus-plus.git")
                                developerConnection.set("scm:git:git@github.com:nktkt/kotlin-plus-plus.git")
                            }
                            developers {
                                developer {
                                    id.set("nktkt")
                                    name.set("nktkt")
                                    url.set("https://github.com/nktkt")
                                }
                            }
                            // No <licenses> entry yet — license is undecided.
                            // Add an Apache-2.0 (or other) license block here
                            // before publishing to Maven Central.
                        }
                    }
                }
            }
        }
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
