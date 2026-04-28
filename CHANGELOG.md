# Changelog

All notable changes to Kotlin++ will be documented here.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/).

## [Unreleased]

### Added
- **`libs/kpp-validation`** — new module shipping a typed accumulating
  validation toolkit:
  - `NonEmptyList<T>` (List delegate, `head` / `tail` / `+`, structural
    equality); factories `nonEmptyListOf`, `List.toNonEmptyListOrNull`.
  - `Validator<I, O, E>` fun-interface returning
    `Result<O, NonEmptyList<E>>`; helpers `validId`, `validFail`,
    `require`.
  - Combinators: `and` (accumulates errors from both sides),
    `andThen` (short-circuits on the first failure), `optional`
    (skips null), `required(missing)` (rejects null), `mapError`
    (rewrites the error type).
  - Built-ins keyed on stable `String` codes: `nonEmptyString`,
    `nonBlankString`, `lengthBetween(min, max)`, `rangeInt(min, max)`,
    `matches(regex)`, `email`, `oneOf(set)`. The caller maps to its
    own error type with `.mapError { ... }`.
  - `validate { }` DSL with per-field collection: failing fields
    contribute `FieldError(field, code)` entries to a single
    `NonEmptyList`; the builder body keeps running so all field
    errors accumulate before the final `Err`.
  - 41 tests across `NonEmptyListTest`, `ValidatorTest`,
    `CombinatorsTest`, `ValidatorsTest`, `BuilderTest`.

### Changed
- Module count: 9 → 10 libs (added `kpp-validation`).
- Test total: 299 → 340. Coverage will be re-measured at the next
  release tag.

## [0.3.1] - 2026-04-28

Patch release. No public library API changes — coverage tests, sample
demonstration, and CI infrastructure modernization.

### Changed
- **`samples:http-server`** now exercises `Secret<String>` end-to-end:
  `CreateUserRequest` carries `apiKey: Secret<String>`; clients send
  `"api_key"` on the wire; the handler decodes the body as
  `Map<String, Any?>` (since `kpp-derive`'s decoder rejects
  `Secret<*>`) and lifts the key with `.toSecret()`. The sample now
  prints both an `AuditEntry` (`@DeriveJson` default — `caller_api_key`
  emitted as `"[REDACTED]"`) and an `AuditEntryDiagnostic`
  (`@DeriveJson(allowSecrets = true)` — exposes the inner value),
  proving the redaction contract in production-shaped JSON output.
  3 new sample tests, 1 existing test updated.
- **CI workflow** — bumped GitHub Actions to Node.js 24 compatible
  releases: `actions/checkout` v4 → v6, `actions/setup-java` v4 → v5,
  `gradle/actions/setup-gradle` v4 → v6, `actions/upload-artifact`
  v4 → v7. Resolves the `Node.js 20 actions are deprecated` warning
  and obsoletes Dependabot PRs #1–#4 (auto-closed).

### Added
- 101 new tests boosting line coverage from **83.1% → 90.66%**
  (1174 / 1413 → 1281 / 1413). Test-only changes; no production code
  touched.

  Per-class lifts:
  - `kpp-derive/Json`: 80.3% → 97.6%; full coverage of `JsonLexer`,
    `JsonParser`, and `Internal.kt` escape branches
  - `kpp-immutable/ArrayImmutableList`/`ImmutableListIterator`/
    `LinkedImmutableMap`/`LinkedImmutableSet`: 65–70% → ~100%
  - `kpp-test/ResultAssertionsKt`: 72.7% → ~100%;
    `RecordingCapabilitiesKt`: 73.7% → ~100%
  - `kpp-analyzer/KppScanner`: 93.2% → 97.4%
  - `kpp-capability/builtins/ConsoleLogger`: 25% → 100%
  - `samples/http/HandlerKt`: 87.8% → 100% (Upstream / Timeout
    error-response branches)
  - 8 new edge-case test classes covering JSON escape paths, parser
    error paths, immutable collection equality and iteration,
    capability proxy `equals`/`hashCode`/`toString`, scanner masking
    and dedup edge cases.

  Untouched: top-level sample `main` runners (need process-level
  harness), the `kpp-gradle-plugin`'s plugin classes (need extra
  TestKit fixture beyond what we have), `ConsoleReporter` /
  `KppCheckKt` CLI entry points (need process-I/O fixturing), and a
  few defensive/unreachable scanner branches.

  Coverage at the v0.3.1 tag, including the new sample code, sits at
  **89.56%** (1296 / 1447). The coverage-boost work landed before the
  sample refactor; the small dip below 90% reflects the fresh
  AuditEntry / manual-decode paths in `samples/http-server`, which
  are exercised by the runtime sample but not yet by additional unit
  tests.

## [0.3.0] - 2026-04-28

Minor release. New `kpp-secret` module, new `@DeriveJson` opt-in flag,
new KPP007 analyzer rule. New analyzer rules can surface violations on
existing consumer code, hence the minor bump.

### Added
- **`libs/kpp-secret`** — new module shipping `Secret<T>`, a wrapper
  that hides its value in `toString` (`"Secret(***)"`) and in default
  JSON encoding. `equals` is timing-safe (constant-time per byte) for
  `String` and `ByteArray`, falls through to plain equality otherwise.
  Read with `expose()`. Factories: `secretOf(value)`,
  `String.toSecret()`, `ByteArray.toSecret()`. Typealiases:
  `RedactedString`, `RedactedBytes`. 16 tests.
- **`@DeriveJson(allowSecrets = false)`** — new opt-in flag in
  `kpp-derive`. By default a `Secret<T>` field encodes as the literal
  string `"[REDACTED]"`; set `allowSecrets = true` to encode the
  underlying value. Decoder explicitly rejects `Secret<T>` parameter
  types until the Phase-4 KSP backend lands. 3 integration tests in
  `kpp-derive` covering both flows.
- **KPP007** (`data class` with `var` or mutable collection field) —
  regex rule, severity error. Generalises KPP005: KPP005 stays as
  the `@Immutable`-annotated subset and wins the dedup when both
  apply. 8 new analyzer tests.

### Changed
- Shipped analyzer rules: 9 → 10 (KPP001, KPP002, KPP004, KPP005,
  KPP007, KPP008, KPP011, KPP013, KPP017, KPP018).
- Module count: 8 → 9 libs (added `kpp-secret`).
- Test total: 168 → 195. Line coverage at 0.3.0: 83.1% (1174 / 1413).

## [0.2.0] - 2026-04-28

Minor release. Two new analyzer rules (KPP008, KPP017) and aggregate
code-coverage tooling (Kover). New rules can surface violations on
existing consumer code, hence the minor bump.

### Added
- KPP008 (ignored `@Io`/`@Db` return) — regex rule, severity warning.
  Mirrors KPP001 but keys on the side-effect annotations instead of
  `@MustHandle`. KPP001 wins when both are applicable.
- KPP017 (`kotlin.reflect.*` in production code) — regex rule,
  severity warning. Skips `/src/test/` and `/src/testFixtures/`. Use
  `@file:Suppress("KPP017")` when reflection is intentional.
- 12 new analyzer tests (6 per rule plus a registry-size assertion).
- `org.jetbrains.kotlinx.kover 0.9.1` plugin applied at the root with
  aggregate `koverHtmlReport` / `koverXmlReport` tasks.
- CI emits a line-coverage summary in the GitHub Actions job summary.
  Coverage HTML report is uploaded as a workflow artifact.
- README badges: coverage (links to CI summary).

### Changed
- Shipped analyzer rules: 7 → 9 (KPP001, KPP002, KPP004, KPP005, KPP008,
  KPP011, KPP013, KPP017, KPP018).
- `kpp-capability/Capabilities.kt`, `kpp-derive/{Json,Internal}.kt`,
  and `kpp-test/RecordingCapabilities.kt` now carry
  `@file:Suppress("KPP017")` directives with a justifying comment —
  these uses of reflection are intentional and the future FIR plugin
  will obviate them.
- Total tests: 156 → 168 (line coverage at v0.2.0: 82.2%, 1081 / 1315).

## [0.1.1] - 2026-04-28

### Fixed
- CI: `gradle/actions/setup-gradle@v4` requires the full Gradle version
  (e.g. `9.2.0`) and rejects shorthand (`9.2`). Pin updated. CI on `main`
  now runs `gradle test` and the analyzer dogfood pass to completion.

### Added
- README badges: CI status, latest release, Kotlin 2.2, JDK 21.

## [0.1.0] - 2026-04-28

First public release. Phase-0 MVP of Kotlin++: library-level emulation of
typed errors, capability-based DI, deep immutability, structured
concurrency, compile-time meta (runtime stub), test helpers, a regex-based
strict analyzer, and a Gradle plugin packaging the analyzer.

### Added

#### Modules
- `libs/kpp-core` — `Result<T, E>` sealed type with `Ok`/`Err`,
  `result { ... .bind() }` builder emulating `T ! E`, `KppError` marker,
  `runCatchingTyped`, combinators (`map`, `flatMap`, `mapErr`, `getOrElse`,
  `recover`, `orFail`).
- `libs/kpp-capability` — `Capability` marker, `Capabilities` container
  with reified `get<T>()`, `withCapabilities(...)` runner, builtin
  `Logger` (`ConsoleLogger`, `RecordingLogger`) and `Clock`
  (`SystemClock`, `FixedClock`).
- `libs/kpp-analyzer` — annotations (`@MustHandle`, `@Pure`, `@Io`, `@Db`,
  `@Blocking`, `@PublicApi`) + regex scanner with rules KPP001, KPP002,
  KPP004, KPP005, KPP011, KPP013, KPP018 + suppressions
  (`// noinspection KPPxxx`, `@file:Suppress("KPPxxx", ...)`) +
  string-literal aware masking (no fixture false positives).
- `libs/kpp-immutable` — `@Immutable`, `@Borrow`, `@Move` annotations;
  sealed `ImmutableList<T>` / `ImmutableMap<K, V>` / `ImmutableSet<T>`
  with persistent-style writers; `freeze(...)` helper.
- `libs/kpp-concurrent` — typed structured concurrency primitives that
  preserve `Result<T, E>` across `async`/`await`: `parallelMap`,
  `raceFirstSuccess`, `sequence`/`sequenceAccumulating`,
  `withTimeoutOrErr`.
- `libs/kpp-derive` — `@DeriveJson`, `@JsonName`, `@JsonIgnore` and
  `Json.encode`/`Json.decode` runtime reflection stub. Same surface
  the future Phase-4 KSP/FIR backend will ship; user code does not
  change at the migration.
- `libs/kpp-test` — `assertOk`/`assertErr`/`assertOkValue`/
  `assertErrValue`/`assertErrType`/`Iterable.assertAllOk`,
  JDK-proxy-based `recordingCapability` spy, `VirtualClock`,
  `CaptureLogger`.
- `libs/kpp-gradle-plugin` — `dev.kotlinplusplus.kpp` Gradle plugin
  exposing a `kpp { sourceRoots / suppressedRules / failOnViolation }`
  DSL and a `kppCheck` task. Three Gradle TestKit functional tests
  cover violation, clean, and suppression code paths.

#### Samples
- `samples/payment` — focused demo: typed errors + capability DI +
  exhaustive `when` over a sealed error type.
- `samples/http-server` — flagship demo: every module composing.
  Typed `Result<Response, ApiError>` end-to-end, `parallelMap` for
  batch fetch, `ImmutableList<User>` in responses, `@DeriveJson`
  for JSON wire format, capability-injected repository.

#### Project hygiene
- GitHub Actions CI (`gradle test` + analyzer dogfood on push and PR).
- Dependabot for Gradle and GitHub Actions ecosystems.
- Issue templates: bug, feature, new analyzer rule.
- Pull request template.
- `CONTRIBUTING.md`.
- Manifesto, syntax mapping, roadmap, rules reference under `docs/`.

[Unreleased]: https://github.com/nktkt/kotlin-plus-plus/compare/v0.3.1...HEAD
[0.3.1]: https://github.com/nktkt/kotlin-plus-plus/releases/tag/v0.3.1
[0.3.0]: https://github.com/nktkt/kotlin-plus-plus/releases/tag/v0.3.0
[0.2.0]: https://github.com/nktkt/kotlin-plus-plus/releases/tag/v0.2.0
[0.1.1]: https://github.com/nktkt/kotlin-plus-plus/releases/tag/v0.1.1
[0.1.0]: https://github.com/nktkt/kotlin-plus-plus/releases/tag/v0.1.0
