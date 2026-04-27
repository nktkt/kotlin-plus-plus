# kpp-gradle-plugin

The `dev.kotlinplusplus.kpp` Gradle plugin packages the Kotlin++ regex analyzer
(`:libs:kpp-analyzer`) as a reusable plugin. Applying it adds a `kppCheck` task
that scans the project's Kotlin sources, reports violations to the console, and
fails the build on any non-suppressed ERROR-severity finding.

## Installation

```kotlin
plugins {
    id("dev.kotlinplusplus.kpp")
}
```

## Usage

```bash
./gradlew kppCheck
```

## DSL

The plugin registers a `kpp { }` extension:

```kotlin
kpp {
    sourceRoots.set(listOf(file("src/main/kotlin"), file("src/test/kotlin")))
    suppressedRules.add("KPP002")
    failOnViolation.set(false)
}
```

Defaults:

- `sourceRoots` falls back to `src/main/kotlin` and `src/test/kotlin`.
  Non-existent directories are filtered at task-run time, so adding
  generated-source dirs that may or may not exist is safe.
- `suppressedRules` is empty.
- `failOnViolation` is `true`.

## Distribution

Not yet published to a public plugin portal. For local consumption, depend on
this build via `includeBuild` in your composite build's `settings.gradle.kts`,
or publish to `mavenLocal()` and resolve from there.
