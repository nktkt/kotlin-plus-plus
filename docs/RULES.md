# Kotlin++ Analyzer Rules — KPP001..KPP018

Status legend:

- `shipped` — implemented in the regex-based scanner today
- `planned` — catalogued, not yet implemented; will arrive as regex
  first, then upgrade once the FIR plugin lands
- `compiler-only` — cannot be reasonably approximated by a line-based
  scanner; deferred until the K2 FIR plugin

All rule IDs are stable. Severities marked `error` block CI;
`warning` is reportable but non-fatal.

## Reference table

| ID     | Name                                  | Severity | Status        | Rationale                                                                 |
|--------|---------------------------------------|----------|---------------|---------------------------------------------------------------------------|
| KPP001 | Ignored `Result` return               | error    | shipped       | Calls to `@MustHandle` functions whose return value is discarded.         |
| KPP002 | Raw `Throwable` in `catch`            | error    | shipped       | `catch (e: Throwable\|Exception\|RuntimeException)` — too broad; catch a precise type or use `runCatchingTyped`. |
| KPP003 | Missing `! E` declaration             | error    | compiler-only | Function may fail (calls `bind()` of an `Err` arm) but return type lacks an error channel. |
| KPP004 | Mutable type on public API            | error    | shipped       | `public fun` returns `MutableList`/`MutableMap`/`MutableSet`.             |
| KPP005 | Mutable field on `@Immutable`         | error    | shipped       | `@Immutable` data class has `var` or `MutableList`/`MutableMap`/`MutableSet` field. |
| KPP006 | Suspend not cancellable               | warning  | planned       | `suspend fun` body does not yield (no `yield()`, no real suspension point) over a long-running loop. |
| KPP007 | Unbounded mutability inside `data class` | error  | shipped       | `data class` field typed as `var` or as a mutable collection. Deduplicated against KPP005 — when a class is `@Immutable`, only KPP005 fires. |
| KPP008 | Ignored side-effecting return         | warning  | shipped       | `@Io`/`@Db` function called as a statement; result not bound or consumed. |
| KPP009 | Missing capability binding            | error    | planned       | Function uses `get<C>()` for a `C` not declared in `@RequiresCapabilities`. |
| KPP010 | Capability shadowing                  | warning  | planned       | `withCapabilities` block re-binds a capability already present in the parent bag with a different runtime type. |
| KPP011 | Blocking call inside suspend          | error    | shipped       | `Thread.sleep`, `runBlocking`, `URL(...).readText`, `File.readText` inside a `suspend fun`. |
| KPP012 | Effect downgrade                      | error    | compiler-only | A `pure` function calls an `io` / `db` / `blocking` function.             |
| KPP013 | `var` on public API                   | warning  | shipped       | `public var` property — promoted to error in Phase 3.                    |
| KPP014 | Non-exhaustive `when` over `error`    | error    | compiler-only | `when` over a sealed `KppError` lacks an `else` arm and misses a case.   |
| KPP015 | `null` on `! E` channel               | error    | planned       | A function returning `T ! E` returns `null` instead of an `Err`.         |
| KPP016 | Operator overload abuse               | warning  | planned       | Operator function with non-algebraic semantics (e.g. `plus` performs IO). |
| KPP017 | Reflection in production code         | warning  | shipped       | Use of `kotlin.reflect.*` outside test sources; suppress per file with `@file:Suppress("KPP017")` when reflection is intentional. |
| KPP018 | Exception escapes public API          | error    | shipped       | `public fun` with `@Throws(...)` or with a `throw` not wrapped in `try`. |

## Notes on the regex tier

The shipped rules (KPP001 / KPP004 / KPP011 / KPP018) all run as
regex heuristics over `*.kt` files. This has known limits:

- Multi-line constructs (a `try` opening on line 10 covering a `throw`
  on line 14) can produce false positives.
- Aliased imports and same-named symbols in unrelated packages can
  produce both false positives and false negatives.
- String-literal contents are masked before pattern matching, but the
  scanner does not lex Kotlin in full.

These limits are why every `compiler-only` rule waits for the FIR
plugin: regex cannot answer "is this `T` actually `MutableList`?"
without resolving imports.

## How to silence a rule

Per-call: annotate the offending site with `@Suppress("KPP00X")`.
The scanner respects `@Suppress` on the enclosing declaration.

Per-module: set a Gradle property (planned, not yet wired):

```
kpp.disabledRules = KPP013,KPP016
```

Disabling a rule must be justified in code review. The MVP analyzer
treats every shipped rule as `error` severity by default.

## Adding a new rule

1. Reserve an ID by adding a row to the table above.
2. Implement under `libs/kpp-analyzer/src/main/kotlin/dev/kpp/analyzer/`.
3. Add a positive and negative test under
   `libs/kpp-analyzer/src/test/kotlin/dev/kpp/analyzer/`.
4. Update the per-module README.

When the FIR plugin lands, a rule's status moves from `shipped`
(regex) to `shipped` (FIR) without changing its ID.
