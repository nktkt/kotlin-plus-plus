# kpp-validation

Typed, accumulating validation for Kotlin++. A `Validator<I, O, E>` is a pure
function from an input to `Result<O, NonEmptyList<E>>`. Errors compose: `and`
runs both sides and accumulates every failure, while `andThen` chains
validators sequentially and short-circuits on the first failure. The
`validate { ... }` builder lets you validate a multi-field object so that
every field reports its own errors instead of bailing on the first.

## Built-in validators

- `nonEmptyString` — rejects `""` with code `"empty"`.
- `nonBlankString` — rejects strings of only whitespace with code `"blank"`.
- `lengthBetween(min, max)` — string length must lie in `min..max`.
- `rangeInt(min, max)` — integer must lie in `min..max`.
- `matches(regex, label)` — string must match the regex; emits
  `"does-not-match:<label>"`.
- `email` — basic email shape; emits `"not-email"`.
- `oneOf(allowed)` — value must be in the allowed set.

All built-ins use `String` as the error type. Map to your own type with
`.mapError { ... }`.

## Combinators

- `a and b` — run both, accumulate every error; succeed only when both succeed.
- `a andThen b` — run `a`, then on success feed its output to `b`. Short-circuits.
- `optional(inner)` — accept null; otherwise apply `inner`.
- `required(inner) { missing }` — reject null with `missing`; otherwise apply `inner`.
- `Validator.mapError { ... }` — transform the error type.

`and` accumulates errors; `andThen` short-circuits — choose deliberately.

## Builder

```kotlin
data class Profile(val name: String, val age: Int)

val r: Result<Profile, NonEmptyList<FieldError>> = validate {
    val name = field("name", input.name, nonBlankString and lengthBetween(1, 32))
    val age = field("age", input.age, rangeInt(0, 150))
    Profile(name, age)
}
```

`field(name, value, validator)` returns the input value even on failure so
the builder body keeps running and every field still gets validated; the
scope's collected errors dominate the final outcome of `validate { ... }`.
