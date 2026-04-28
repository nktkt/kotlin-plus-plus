# Kotlin++ Roadmap

Six phases, each ending in a checkpoint where one structural pillar
graduates from "library emulation" to "compiler-level feature". The
checkboxes reflect the state of *this* repository, not the spec.

## Phase 0 — Constitution & MVP

The phase this repository delivers.

- [x] `settings.gradle.kts` + `build.gradle.kts` bootstrapping a
      multi-module Kotlin/JVM 2.2 build with `-Xcontext-parameters`
- [x] `libs/kpp-core` — `Result<T, E>`, `Ok`/`Err`, `result { }` /
      `bind()` / `err()`, `KppError`, `runCatchingTyped`, combinators
- [x] `libs/kpp-capability` — `Capability`, `Capabilities`,
      `withCapabilities`, builtin `Logger` / `Clock`
- [x] `libs/kpp-analyzer` — annotations + regex-based `kppCheck` CLI
      with KPP001 / KPP004 / KPP011 / KPP018
- [x] `samples/payment` — end-to-end demonstration of the three libs
      composing
- [x] `docs/MANIFESTO.md` design principles
- [x] `docs/ROADMAP.md` (this file)
- [x] `docs/SYNTAX.md` future-syntax / today-emulation map
- [x] `docs/RULES.md` analyzer rule reference

## Phase 1 — Strict Analyzer MVP

Goal: stop foot-guns at lint time, even before the compiler frontend
exists.

- [x] KPP001 — ignored `@MustHandle` return value (regex)
- [x] KPP002 — catching raw `Throwable`/`Exception`/`RuntimeException`
- [x] KPP004 — `MutableList`/`MutableMap`/`MutableSet` on public API
- [x] KPP005 — `@Immutable` data class with `var`/`MutableList`/etc.
- [x] KPP007 — `data class` (any) with `var` or mutable collection
      field; deduped against KPP005 for `@Immutable`-marked classes
- [x] KPP008 — `@Io`/`@Db` return value discarded as a statement
- [x] KPP011 — blocking call inside `suspend fun`
- [x] KPP013 — `public var` property
- [x] KPP017 — `kotlin.reflect.*` import in non-test source
- [x] KPP018 — exception escapes public API
- [x] Suppression: `// noinspection KPPxxx` per line + per-file
      `@file:Suppress("KPPxxx", ...)`
- [ ] KPP003, KPP006, KPP009, KPP010, KPP012, KPP014..KPP016
      — catalogued in `RULES.md`, not yet implemented
- [ ] IntelliJ inspections that surface KPP* in the editor
- [ ] K2 FIR compiler plugin — replaces all regex heuristics with real
      type-resolved diagnostics; eliminates false positives and
      negatives that the line-based scanner cannot avoid
- [x] Gradle plugin (`dev.kotlinplusplus.kpp`) wrapping `kppCheck`,
      with `kpp { sourceRoots / suppressedRules / failOnViolation }` DSL
      and TestKit-driven functional tests
- [x] String-literal aware scanner: triple-quoted strings no longer
      trip rules from inside their own test fixtures (dogfood = 0)
- [ ] Migration from `SOURCE`-retention markers to compiler-recognised
      effect modifiers

## Phase 2 — Rich Errors + Capability Context

Goal: make typed errors and capabilities part of the type system, not
a library convention.

- [x] Library-level emulation:
      - `Result<T, E>` with `result { ... .bind() }`
      - `sealed interface ... : KppError` for error declaration
      - `Capabilities` + `withCapabilities` for context
- [ ] Real `T ! E` syntax — return type carries the error sum
- [ ] Real `error Foo { case A; case B(...) }` declaration
- [ ] `fail` keyword desugaring to `Err(...)` short-circuit
- [ ] Direct support for `context(c: C)` to replace
      `fun Capabilities.f(...)`; auto-resolve from the enclosing scope
- [ ] Exhaustiveness: forbid catching `Throwable`; require handling
      every case of `! E`

## Phase 3 — Deep Immutability + Ownership Lite

Goal: structural immutability and a minimal aliasing discipline that
does not require a Rust-grade borrow checker.

- [x] Library: `libs/kpp-immutable` — `ImmutableList`/`ImmutableMap`/
      `ImmutableSet` sealed wrappers with persistent-style writers
      (returning new instances) and `iterator().remove()` throwing
- [x] `@Immutable` annotation marker (paired with shipped KPP005)
- [x] `@Borrow` / `@Move` annotation placeholders for the future
      keywords; today purely documentary, no enforcement
- [x] Compiler-level KPP004 superset reached at the regex tier:
      KPP007 fires on any `data class` with a `var` or mutable
      collection field (full data-class coverage; KPP005 stays as
      the `@Immutable`-marked subset for explicit intent)
- [ ] `immutable data class` keyword; auto-uses persistent collections
- [ ] Compiler-level KPP004 (no mutable types reachable from public)
- [ ] Real `borrow` / `move` parameter modifiers with no implicit copy
- [ ] Structural sharing in immutable collection writers (today they
      copy; persistent tries are a Phase-3 follow-up)
- [ ] No `var` in public API surface (KPP013 promoted to error)

## Phase 4 — Compile-Time Meta

Goal: replace runtime reflection and KSP indirection with a hermetic,
greppable, source-visible derivation system.

- [x] Library stub: `libs/kpp-derive` — `@DeriveJson(snakeCase)`,
      `@JsonName`, `@JsonIgnore`, `Json.encode` / `Json.decode`.
      Runtime reflection only, hand-rolled lexer + parser, kept
      identical in surface to what the future codegen will ship
- [ ] Migrate `@DeriveJson` from runtime reflection to a KSP/FIR
      compile-time generator with output in build/generated/source
- [ ] `@derive(Json, Equals, Hash, Diff, ...)` shape — multiple
      derives per declaration
- [ ] Generators ship as `KppDerive` plugins; output written next to
      source for review
- [ ] No runtime reflection in derived code
- [ ] Standard derives: `Json`, `Equals`, `Hash`, `Show`, `Diff`,
      `Lens`

## Phase 5 — Value-Oriented Performance + Platform ABI + Security

Goal: deterministic performance characteristics across JVM, Native,
Wasm; security primitives that prevent accidental leakage.

- [x] Library: `libs/kpp-secret` — `Secret<T>` wrapper with redacting
      `toString`, timing-safe `equals` for `String` and `ByteArray`,
      explicit `expose()` to read, `RedactedString` / `RedactedBytes`
      typealiases. Integrates with `kpp-derive`: `Secret` fields
      encode as `"[REDACTED]"` by default, opt-in via
      `@DeriveJson(allowSecrets = true)`.
- [x] Library: `libs/kpp-validation` — `Validator<I, O, E>` typed
      validators returning `Result<O, NonEmptyList<E>>`. `and`
      accumulates errors, `andThen` short-circuits, `optional` /
      `required` handle nullability, `mapError` rewrites the error
      type. Built-ins: `nonEmptyString`, `nonBlankString`,
      `lengthBetween`, `rangeInt`, `matches(Regex)`, `email`,
      `oneOf(Set)`. `validate { }` DSL accumulates per-field errors
      across an entire data class.
- [ ] Stronger `value class` with stack-allocation guarantees
- [ ] Specified ABI for JVM, Native, Wasm
- [ ] Cross-platform value layout
- [ ] No JIT-dependent performance promises

## Phase 6 — 1.0

- [ ] Stability guarantee on syntax, ABI, rule IDs
- [ ] Migration tooling from Kotlin to Kotlin++ at module granularity
- [ ] Self-hosted strict-mode standard library

## Calendar checkpoints

The spec's wall-clock buckets, restated:

### First 90 days

- [x] Bootstrap repo, six libs (`kpp-core`, `kpp-capability`,
      `kpp-analyzer`, `kpp-immutable`, `kpp-concurrent`, `kpp-derive`),
      one sample
- [x] Manifesto + roadmap + syntax + rules docs
- [x] Initial four analyzer rules (KPP001/004/011/018)
- [x] 149 tests across all modules, all green; analyzer dogfood
      against the whole repo reports zero violations
- [x] Second sample `samples:http-server` exercising every module
      (`Result` + `Capabilities` + `ImmutableList` + `parallelMap` +
      `@DeriveJson` JSON wire format) — the integration stress-test
- [x] `kpp-test` library: `assertOk`/`assertErr`/`assertOkValue`/
      `assertErrType`, `CapabilityRecorder` + `recordingCapability`
      JDK-proxy spy, `VirtualClock`, `CaptureLogger`
- [x] `dev.kotlinplusplus.kpp` Gradle plugin packages the analyzer for
      adoption by other projects
- [x] Public on GitHub at `nktkt/kotlin-plus-plus`
- [x] GitHub Actions CI: runs `gradle test` + analyzer dogfood on every
      push to main and pull request; uploads test reports on failure
- [x] Issue templates (bug / feature / new analyzer rule), PR template,
      Dependabot for Gradle and GitHub Actions, `CONTRIBUTING.md`
- [x] Library-level structured concurrency that preserves typed errors
      across `async`/`await` boundaries (`kpp-concurrent`)
- [x] Phase-4 runtime reflection stub for `@DeriveJson` so user code
      will not change when the KSP backend lands
- [ ] Land at least one IntelliJ inspection backed by `kpp-analyzer`
- [ ] Publish 0.1.0 of `kpp-core` and `kpp-capability` to a local
      Maven repository

### 3-6 months

- [ ] Complete the KPP* rule catalogue at regex granularity
- [ ] Begin the FIR plugin spike on a fork of K2
- [ ] Add a second sample (`http-server` or similar) to stress-test
      capability composition

### 6-12 months

- [ ] FIR plugin replaces the regex scanner for KPP001/004/011/018
- [ ] Real `T ! E` parsing in a frontend prototype
- [ ] Effect inference end-to-end on `pure`/`io`/`db`/`blocking`

### 12-24 months

- [ ] Phase 3 deliverables (immutability + ownership lite)
- [ ] Phase 4 deliverables (compile-time meta)
- [ ] First Native / Wasm targets

### 24-36 months

- [ ] Phase 5 ABI freeze
- [ ] Phase 6 1.0 cut
