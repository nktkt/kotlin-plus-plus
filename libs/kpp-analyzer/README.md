# kpp-analyzer

A best-effort, regex/line-based static checker for the Kotlin++ MVP.
The production version of this analyzer will be a K2 (FIR) compiler plugin
with full type information; this MVP module only enforces the cheapest-to-detect
subset of the KPP rule set so that the project can experiment with the
annotation surface and rule ergonomics before that plugin is built.

## Annotations

The module ships marker annotations under `dev.kpp.analyzer.*`. All have
`SOURCE` retention because they are read by the scanner only and have no
runtime effect:

- `@MustHandle` — function/property whose return value callers must not
  discard (drives KPP001).
- `@Pure` — declares the function has no side effects.
- `@Io` — declares the function performs IO.
- `@Db` — declares the function touches a database.
- `@Blocking` — declares the function blocks the current thread.
- `@PublicApi` — opts a declaration into stricter public-surface checks.

## Rules implemented in the MVP

| ID     | Severity | Spec section | What it checks |
|--------|----------|--------------|----------------|
| KPP001 | error    | "Ignored Result returns" | Calls to `@MustHandle` functions whose return value is discarded. |
| KPP002 | error    | "Raw exception catch"     | `catch (_: Throwable \| Exception \| RuntimeException)` clauses; advises a specific exception type or `Result<T, E>`. |
| KPP004 | error    | "Mutable public APIs"     | `public fun` returning `MutableList`/`MutableMap`/`MutableSet`. |
| KPP005 | error    | "Mutable field on @Immutable" | `@Immutable` data class whose primary constructor uses `var`, or whose properties are typed `MutableList`/`MutableMap`/`MutableSet`/`ArrayList`/`HashMap`/`HashSet`. |
| KPP011 | error    | "Blocking calls inside suspend" | Calls to `Thread.sleep`, `runBlocking`, `URL(...).readText`, `File.readText` inside a `suspend fun` body. |
| KPP013 | warning  | "public var property"      | Top-level or class-body `var` without an explicit `private`/`internal`/`protected` modifier. Local `var`s inside `fun` bodies and constructor `var` parameters are excluded (the latter are caught by KPP005/KPP007). Promoted to error in Phase 3. |
| KPP018 | error    | "Exceptions escaping public APIs" | `public fun` annotated with `@Throws(...)`, or whose body contains a `throw` not wrapped in `try`. |

The remaining rules from the KPP001..KPP018 spec (effect-system tagging
with `@Pure`/`@Io`/`@Db`, capability scoping, exhaustive-when invariants,
context-parameter purity, ...) require real type information and are
deferred to the FIR-plugin implementation.

## Heuristic limits

This scanner does **not** parse Kotlin. It walks `*.kt` files line-by-line
and applies regex heuristics. Consequences:

- False positives are possible: e.g., a same-named function in a different
  package can trigger KPP001; a `throw` inside a public fun body can match
  KPP018 even if a `try` exists across multiple lines.
- False negatives are likely: anything spread across multiple lines, or
  hidden behind aliases / extension imports, will be missed.
- String-literal contents are masked before pattern matching to avoid the
  most obvious false positives, but full lexical correctness is not
  attempted.
- KPP005 matches `@Immutable` by short name only — it cannot disambiguate
  packages, so any annotation called `Immutable` (regardless of which
  package it comes from) will arm the rule on the following class.

Treat the output as advisory until the FIR plugin lands.

## Suppression

Two suppression mechanisms are recognised:

- Line-level: a `// noinspection KPPxxx` comment placed on the line
  immediately before the offending statement, or trailing the offending
  statement on the same line, silences the listed rule on that line.
  Multiple ids are supported: `// noinspection KPP002, KPP005`. The
  suppression only silences the listed rule ids — other rules still fire.
- File-level: `@file:Suppress("KPPxxx", "KPPyyy")` at the top of a `.kt`
  file silences the listed rule(s) for the whole file.
- For multi-line constructs (e.g. a KPP005 violation reported on a field
  several lines below the `class` header), a line-level suppression
  attached to the FIRST line of the construct (the `class` line) also
  silences the rule for any violation reported within that construct's
  primary-constructor slice.

## Running

Via Gradle:

```
./gradlew :libs:kpp-analyzer:kppCheck
```

Directly from a built jar / classes directory:

```
kotlin -classpath build/classes/kotlin/main dev.kpp.analyzer.KppCheckKt src/main/kotlin
```

The CLI takes any number of source-root paths as arguments (default: `.`)
and exits with code `1` if any `ERROR`-severity violation is reported.

## Future work

The production analyzer will be a K2 FIR compiler plugin that:

- Resolves `@MustHandle`, effect annotations, and `@PublicApi` against the
  symbol table.
- Implements full effect inference so `@Pure`/`@Io`/`@Db`/`@Blocking` can
  be enforced transitively.
- Hooks into the build so violations are reported as compiler diagnostics
  rather than via a separate task.
