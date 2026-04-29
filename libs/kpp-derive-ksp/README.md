# kpp-derive-ksp

Phase-4 prototype KSP processor for `@DeriveJson`. Generates a
`fun X.toJsonGenerated(): String` extension for each annotated class,
producing the same JSON output as `dev.kpp.derive.Json.encode(x)` from the
runtime path, but without using `kotlin.reflect`.

## Status

Minimum viable subset. The runtime reflection-based path
(`dev.kpp.derive.Json`) is unchanged; users opt in to codegen by applying
the KSP plugin and this processor to a consumer module. See
`samples/derive-ksp-demo/build.gradle.kts` for a working example.

## Supported property types

Only the following primitive-ish types are supported in this slice:

- `kotlin.String`
- `kotlin.Int`, `kotlin.Long`, `kotlin.Short`, `kotlin.Byte`
- `kotlin.Double`, `kotlin.Float`
- `kotlin.Boolean`

`@DeriveJson(snakeCase = true)` is honoured; the property name → JSON key
mapping uses the same `camelToSnake` rule as the runtime encoder. JSON
string escaping matches `kpp-derive/Internal.kt` (`"`, `\`, `\n`, `\r`,
`\t`, `\b`, `\f`).

## Limitations (deferred to later Phase-4 slices)

- Nested `@DeriveJson` types are rejected (KSP error).
- `List<*>` / `Map<*, *>` properties are rejected.
- `Secret<*>` properties are rejected; `allowSecrets` is not honoured here.
- Nullable properties are rejected.
- `@JsonName` and `@JsonIgnore` are not yet read by the processor.
- The decoder (`Json.decode`) has no codegen counterpart in this slice.

If a class contains an unsupported type, the processor emits a
`KSPLogger.error(...)` that names the class, the property, and the type
that tripped it.

## How to apply

In a consumer module's `build.gradle.kts`:

```kotlin
plugins {
    kotlin("jvm")
    id("com.google.devtools.ksp") version "2.2.20-2.0.4"
}

dependencies {
    implementation(project(":libs:kpp-derive"))
    ksp(project(":libs:kpp-derive-ksp"))
}
```

Annotate a class:

```kotlin
@DeriveJson
data class Greeting(val message: String, val priority: Int)
```

Build, then call `Greeting("hi", 1).toJsonGenerated()`. The generated source
lands at `build/generated/ksp/main/kotlin/<package>/<Class>_DeriveJson.kt`.

KSP version chosen: `2.2.20-2.0.4` (matches the Kotlin 2.2.20 toolchain
that Gradle 9.2 ships and that the root project uses).
