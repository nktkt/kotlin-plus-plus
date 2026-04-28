# kpp-secret

Wrap secrets so they cannot leak through `toString`, `println`, default JSON,
or accidental exception messages.

## Usage

```kotlin
import dev.kpp.secret.secretOf
import dev.kpp.secret.toSecret

val s = secretOf("hunter2")
println(s)            // Secret(***)
val raw = s.expose()  // "hunter2"

val s2: RedactedString = "hunter2".toSecret()
```

The wrapped value is reachable only via `expose()`. Treat that boundary
like unwrapping a sharp object: get in, do the thing, get out.

## Equality

`equals` is value-equal, but timing-safe for `String` and `ByteArray`:
the comparison runs the full length of the longer input and accumulates
mismatches with bitwise OR, so it does not short-circuit on the first
differing byte. For other `T`, `equals` falls back to plain `==`. That
is acceptable for non-credential secrets like UUIDs or session ids.

`hashCode` is value-based to keep `Secret<T>` usable as a map key.

## JSON

A `Secret<T>` field encodes as the literal JSON string `"[REDACTED]"` when
the enclosing class is `@DeriveJson`, regardless of `T`. To disable redaction
for a specific class (for diagnostics-only contexts where the secret is
actually needed), opt in with `@DeriveJson(allowSecrets = true)`:

```kotlin
@DeriveJson
data class Login(val email: String, val password: Secret<String>)
// {"email":"a@b","password":"[REDACTED]"}

@DeriveJson(allowSecrets = true)
data class DiagLogin(val email: String, val password: Secret<String>)
// {"email":"a@b","password":"hunter2"}
```

`Secret<ByteArray>` also encodes as `"[REDACTED]"` and crucially does NOT
reveal the underlying length.

## Decoding

`Json.decode` does NOT support reading a JSON value back into a `Secret<T>`
field. Reconstructing the inner type from a `KType` argument needs the
KSP-backed `@DeriveJson` planned for Phase 4. For now, encode-only.
