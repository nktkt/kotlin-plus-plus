# Changelog

All notable changes to Kotlin++ will be documented here.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/).

## [Unreleased]

### Added
- KPP013 (`public var` property) — regex-based, severity warning. Brings
  shipped rule count to 7 (KPP001/002/004/005/011/013/018).

### Changed
- `docs/RULES.md` realigned with shipped reality: KPP002 now documents
  "Raw `Throwable` in catch" (matching the implementation) and KPP005
  documents "Mutable field on @Immutable" (matching the implementation).
  KPP008 reassigned to "Ignored side-effecting return", KPP007 keeps
  "Unbounded mutability inside `data class`" (the planned superset of
  KPP005).

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
  KPP004, KPP005, KPP011, KPP018 + suppressions
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

[Unreleased]: https://github.com/nktkt/kotlin-plus-plus/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/nktkt/kotlin-plus-plus/releases/tag/v0.1.0
