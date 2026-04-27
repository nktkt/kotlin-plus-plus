# Kotlin++ Manifesto

Kotlin++ is a strict, opinionated extension of Kotlin. The language is
not new in spirit — it is Kotlin with the rough edges sanded off and
with the boring runtime mistakes promoted to compile-time errors.

## The compatibility axis

```
Kotlin code  ⊂  Kotlin++ code
```

Every legal Kotlin program is a legal Kotlin++ program. The reverse is
not true. Kotlin++ adds syntax (typed errors, effect modifiers,
capabilities, ownership lite) and forbids patterns (mutable public
APIs, raw `throw` across boundaries, ignored `Result` returns).

Three modes select how strict the rules are:

- **compatible** — pure Kotlin source, all checks off. Default for
  legacy modules.
- **kotlin++** — Kotlin++ syntax allowed, default rule set on. The
  expected mode for new code.
- **strict** — every rule on, no opt-outs. The mode in which `kpp-core`
  and `kpp-capability` themselves compile.

Mode is per-module, not per-file. A module is the unit of trust.

## What Kotlin++ refuses

Lessons taken from C++ and from Kotlin's own scarred surfaces. None of
the following are allowed under `kotlin++` or `strict`:

- **Implicit conversion hell** — no silent `Int`/`Long`/`Double`
  coercion, no string-to-number autoboxing, no `Any?` swallowing.
- **Readable-template metaprogramming** — no SFINAE, no expression
  templates, no preprocessor. Compile-time meta is opt-in, name-bound,
  and source-visible.
- **Undefined behavior** — no UB. Out-of-bounds, signed overflow, data
  races: these are runtime errors or compile errors, never UB.
- **Over-exposed ownership** — no public mutable returns, no
  `MutableList` on the API surface, no leaking `var` properties.
- **Operator overload abuse** — operators have algebraic meaning. No
  `<<` for streams, no `+` for I/O, no `invoke` as a poor man's macro.

## What Kotlin++ preserves

The properties that made Kotlin worth extending:

- **簡潔 / Conciseness** — no ceremony tax, no boilerplate for routine
  cases.
- **明示 / Explicitness** — types, errors, and effects are visible at
  the call site.
- **IDEフレンドリー / IDE-friendly** — every feature is statically
  resolvable, refactor-safe, and indexable.
- **型安全 / Type safety** — null, error, and effect channels are all
  part of the type.
- **マルチプラットフォーム / Multiplatform** — JVM, Native, Wasm. The
  ABI is part of the language definition, not a build configuration.
- **段階的 / Incremental** — Kotlin code keeps working. Migration is
  per-module, opt-in, reversible.

## Structural pillars

Each pillar is a non-negotiable feature axis. Phases 1-6 each promote
one pillar from "library emulation" to "first-class compiler feature".

### 1. Rich errors

`fun f(): T ! E` declares both the success and failure types. Errors
are sum types (`error E { case A; case B(...) }`), exhaustive at the
call site, and propagate without exceptions. No checked exceptions, no
`Throwable`, no `Result.failure(Throwable)`.

Today: `Result<T, E>`, `KppError`, `result { ... .bind() }`.

### 2. Effects

Functions advertise their effect surface (`pure`, `io`, `db`,
`blocking`, `suspend`). Effects propagate transitively and are checked
by the analyzer. A `pure` function cannot call an `io` function. A
`suspend` function cannot call a `blocking` function without a wrapper.

Today: `@Pure`, `@Io`, `@Db`, `@Blocking` annotations + KPP011.
Tomorrow: FIR-level inference and propagation.

### 3. Capabilities

Dependencies are values carried by an explicit capability bag. There
is no service locator, no global singleton, no annotation-driven DI
container. A function that needs a `Logger` says so, and the caller
provides one.

Today: `Capabilities`, `withCapabilities`, `get<T>()`. The shape will
fold into Kotlin 2.2 context parameters when the frontend is ready.

### 4. Immutability

`immutable data class` means every field is `val` and every collection
is persistent (`kotlinx.collections.immutable`). Mutability is
explicit and local, never a return type.

Today: `data class` + discipline. Tomorrow: a real keyword and
analyzer enforcement (KPP004 already covers the public-surface case).

### 5. Compile-time meta

`@derive(Json, Equals, Diff)` runs at compile time, generates real
source, and is greppable. No reflection at runtime, no annotation
processors that hide their output.

Today: out of scope (KSP exists but is not part of this MVP).
Tomorrow: a first-class `derive` mechanism with hermetic, audited
generators.

### 6. Value-oriented performance

`value class`, inline classes, escape analysis, and stack-allocated
data are part of the language semantics. Performance characteristics
do not depend on JIT heuristics.

Today: Kotlin's existing `@JvmInline value class`. Tomorrow: stronger
guarantees and `borrow`/`move` for non-copyable values.

### 7. Platform ABI

Multiplatform is a feature of the language, not a Gradle plugin. The
ABI for JVM, Native, and Wasm is specified, versioned, and stable.

Today: Kotlin/JVM only. Tomorrow: ABI++ across all targets.

## Non-goals

- Replacing Java. Kotlin++ targets the Kotlin author, not the Java
  rewrite.
- Replacing Rust. We do not intend full ownership/borrow checking;
  Phase 3's "ownership lite" is opt-in and structural, not a borrow
  checker.
- Replacing Haskell. There is no monad transformer stack, no HKTs.
  Effects are nominal, not encoded.

## Reading order

`SYNTAX.md` shows every feature paired with its today-emulation.
`ROADMAP.md` says when each pillar promotes to compiler-level.
`RULES.md` lists the analyzer's rule catalogue.
