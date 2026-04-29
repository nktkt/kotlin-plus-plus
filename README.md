# Kotlin++

[![CI](https://github.com/nktkt/kotlin-plus-plus/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/nktkt/kotlin-plus-plus/actions/workflows/ci.yml)
[![Latest release](https://img.shields.io/github/v/release/nktkt/kotlin-plus-plus?display_name=tag&sort=semver)](https://github.com/nktkt/kotlin-plus-plus/releases)
[![Coverage](https://img.shields.io/badge/coverage-see%20CI%20summary-blue)](https://github.com/nktkt/kotlin-plus-plus/actions/workflows/ci.yml)
[![Kotlin](https://img.shields.io/badge/kotlin-2.2-blueviolet.svg?logo=kotlin)](https://kotlinlang.org/)
[![JDK](https://img.shields.io/badge/JDK-21-orange.svg)](https://adoptium.net/)

> Typed errors. Capability-based DI. A strict analyzer that refuses
> foot-guns. Library-level emulation of a hypothetical Kotlin successor —
> usable today on plain Kotlin/JVM 2.2.

Kotlin++ is a proposed superset of Kotlin centered on three guarantees:
typed errors as part of the return type, capability-based dependency
context, and a strict static analyzer that refuses to compile
foot-guns. This repository is the **Phase-0 MVP**: it does not ship a
new compiler. It ships pure Kotlin/JVM libraries that emulate the most
load-bearing pieces of the future syntax with today's tools
(`Result<T, E>`, `Capabilities`, `@MustHandle`, a regex-based
`kppCheck` task) plus two reference samples that wire them together.

The point: explore what Kotlin would look like if typed errors,
capability context, deep immutability, and effect annotations were
first-class — without forking the compiler.

## Layout

```
.
├── build.gradle.kts          # root, configures Kotlin 2.2 + -Xcontext-parameters
├── settings.gradle.kts       # 11 libs + 3 samples
├── gradle.properties
├── docs/
│   ├── MANIFESTO.md          # design principles
│   ├── SYNTAX.md             # future-syntax to today-emulation map
│   ├── ROADMAP.md            # 6-phase plan, checkboxes vs MVP reality
│   └── RULES.md              # KPP001..KPP018 reference table
├── libs/
│   ├── kpp-core/             # Result<T, E>, result { bind() }, KppError
│   ├── kpp-capability/       # Capabilities, withCapabilities, Logger, Clock
│   ├── kpp-analyzer/         # @MustHandle, @Pure, @Io, @Db, @Blocking, kppCheck CLI
│   ├── kpp-immutable/        # ImmutableList/Map/Set, @Immutable, @Borrow, @Move
│   ├── kpp-concurrent/       # parallelMap, raceFirstSuccess, sequence, withTimeoutOrErr
│   ├── kpp-derive/           # @DeriveJson runtime stub (Phase-4 KSP placeholder)
│   ├── kpp-test/             # assertOk/assertErr, recordingCapability, VirtualClock, CaptureLogger
│   ├── kpp-secret/           # Secret<T> redacting wrapper, timing-safe equals, JSON integration
│   ├── kpp-validation/       # Validator<I,O,E>, NonEmptyList, accumulating combinators
│   ├── kpp-derive-ksp/       # @DeriveJson KSP processor (Phase-4 prototype)
│   └── kpp-gradle-plugin/    # `dev.kotlinplusplus.kpp` Gradle plugin: kppCheck task + DSL
└── samples/
    ├── payment/              # focused demo: typed errors + caps + analyzer
    ├── http-server/          # flagship demo: every module composing
    └── derive-ksp-demo/      # @DeriveJson KSP codegen demo (parity vs runtime encoder)
```

The repo currently has 376 tests, all green across 14 modules.
Line coverage at v0.4.0: **90.21%** (1382 / 1532); will be re-measured at the next release.
Analyzer dogfood against the repo: **0 violations**.

## Quick start

### Step 1 — install the libraries to your Maven Local cache

From the repo root:

```sh
gradle publishToMavenLocal
```

This installs `dev.kotlinplusplus:kpp-core:0.4.0` and the other 8
libraries (excluding `kpp-gradle-plugin`, which publishes via
`java-gradle-plugin`'s own convention) to `~/.m2/repository/`.

### Step 2 — depend on them from any other Gradle project

```kotlin
repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    implementation("dev.kotlinplusplus:kpp-core:0.4.0")
    implementation("dev.kotlinplusplus:kpp-capability:0.4.0")
    implementation("dev.kotlinplusplus:kpp-immutable:0.4.0")
    implementation("dev.kotlinplusplus:kpp-concurrent:0.4.0")
    implementation("dev.kotlinplusplus:kpp-derive:0.4.0")
    implementation("dev.kotlinplusplus:kpp-secret:0.4.0")
    implementation("dev.kotlinplusplus:kpp-validation:0.4.0")
    testImplementation("dev.kotlinplusplus:kpp-test:0.4.0")
}
```

### Then write a function whose error channel is part of the type:

```kotlin
sealed interface PaymentError : KppError {
    data object CardRejected : PaymentError
    data class Network(val reason: String) : PaymentError
}

fun Capabilities.pay(req: PaymentRequest): Result<Receipt, PaymentError> = result {
    val gateway = get<PaymentGateway>()
    val receipt = gateway.charge(req.card, req.amount).bind()
    get<AuditLog>().record("paid ${receipt.transactionId}")
    receipt
}

fun main() {
    withCapabilities(StripeGateway(), InMemoryAuditLog(), ConsoleLogger()) {
        when (val r = pay(request)) {
            is Result.Ok  -> println("ok ${r.value.transactionId}")
            is Result.Err -> println("err ${r.error}")
        }
    }
}
```

## Build and test

Requires JDK 21 and Gradle 9 (any `gradle` ≥ 8.5 should work; tested
with 9.2). The build provisions Kotlin 2.2 via the plugin.

```
gradle test
gradle :samples:payment:run
gradle :samples:http-server:run
gradle :libs:kpp-analyzer:kppCheck
gradle koverHtmlReport       # aggregated coverage at build/reports/kover/html/
gradle publishToMavenLocal
```

376 tests, 0 failures. The analyzer's dogfood pass over the entire
repo currently reports 0 violations.

## Status

| Surface                                  | Today                                        | Status        |
|------------------------------------------|----------------------------------------------|---------------|
| Typed errors `T ! E`                     | `Result<T, E>` + `result { ... .bind() }`    | shipped       |
| Error declaration `error Foo { ... }`    | `sealed interface Foo : KppError`            | shipped       |
| Capability context                       | `Capabilities`, `withCapabilities(...)`      | shipped       |
| Built-in capabilities                    | `Logger`, `Clock`                            | shipped       |
| Analyzer rules                           | KPP001, KPP002, KPP004, KPP005, KPP007, KPP008, KPP011, KPP013, KPP017, KPP018 | shipped |
| Redacted secret type                     | `Secret<T>` + `expose()` + JSON `[REDACTED]`  | shipped       |
| Typed accumulating validation            | `Validator<I,O,E>` + `NonEmptyList` + `validate { }` builder | shipped |
| Analyzer suppressions                    | `// noinspection KPPxxx` and `@file:Suppress`| shipped       |
| Effect modifiers `pure`/`io`/`db`        | `@Pure`/`@Io`/`@Db`/`@Blocking` annotations  | partial       |
| Effect inference / propagation           | requires K2 FIR plugin                       | planned       |
| Deep immutable collections               | `ImmutableList/Map/Set` + `@Immutable`       | shipped       |
| `borrow`/`move` ownership keywords       | `@Borrow`/`@Move` annotation placeholders    | partial       |
| Typed structured concurrency             | `parallelMap`, `raceFirstSuccess`, `sequence`| shipped       |
| `@derive(Json)` encoder (compile-time)   | runtime + KSP encoder at full parity         | shipped       |
| `@derive(Json)` decoder (compile-time)   | runtime only; KSP-generated decoder is Phase-4 follow-up | partial       |
| Real `T ! E` syntax                      | requires compiler change                     | compiler-only |
| `[1, 2, 3]` collection literals          | `listOf(1, 2, 3)`                            | compiler-only |
| Sample wiring it all together            | `samples/payment`                            | shipped       |

## Documents

- `docs/MANIFESTO.md` — what Kotlin++ promises and what it refuses
- `docs/SYNTAX.md` — every Kotlin++ feature paired with its today-emulation
- `docs/ROADMAP.md` — 6 phases, with checkboxes against this MVP
- `docs/RULES.md` — KPP001..KPP018 reference, severity, and shipped/planned status

The per-module READMEs under `libs/*/README.md` document the runtime
APIs in detail.

## License

No license file yet. Until one is added, all rights are reserved by
default — please open an issue if you want to use code from this repo
in your own project so I can pick an appropriate license (likely
Apache-2.0 or MIT).

## Status of this work

Experimental. The libraries compile, the tests pass, and the analyzer
runs. The compiler-level features marked `compiler-only` in the
status table genuinely require a K2 FIR plugin and are tracked in
`docs/ROADMAP.md`. PRs welcome on shipped pieces; please open an
issue first for compiler-touching work.
