# kpp-derive

## Status: Phase-4 STUB

This module is a runtime, reflection-based stand-in for what real Kotlin++ will
ship in Phase 4: a compile-time `@derive(Json, ...)` meta facility that emits
typed serializers via a KSP/FIR pipeline. The annotations and the `Json` object
shipped here are the user-facing API the future codegen backend will fill in.
User code written against `@DeriveJson` today will not change when the Phase-4
backend lands.

## Why a runtime stub

Wiring a real KSP processor into the MVP would mean:

- adding the `com.google.devtools.ksp` Gradle plugin
- a separate processor classpath
- `META-INF/services` provider registration
- generated-source folders feeding back into `kotlin-jvm` compilation

That is too heavyweight for the MVP slot this module occupies. The runtime
implementation here exposes the same API surface so callers do not need to
refactor when codegen replaces it.

## Performance disclaimer

Encoding and decoding walk the constructor parameters and read properties via
`kotlin-reflect`. This is fine for tests, fixtures, and config. It is not
appropriate for hot paths or large payloads. The Phase-4 KSP backend will
remove the reflection cost.

## Example

```kotlin
import dev.kpp.derive.DeriveJson
import dev.kpp.derive.Json

@DeriveJson(snakeCase = true)
data class CreateUser(val firstName: String, val lastName: String)

val text = Json.encode(CreateUser("Ada", "Lovelace"))
// {"first_name":"Ada","last_name":"Lovelace"}

val back = Json.decode<CreateUser>(text)
```

`@JsonName("...")` overrides the emitted key for a property; `@JsonIgnore`
excludes a property from output.
