# Kotlin++ Syntax — Future and Today

Each subsection pairs a piece of Kotlin++ syntax with the closest thing
this MVP can express in plain Kotlin. The "today" column is what the
libraries in `libs/*` actually compile and run; the "future" column is
what a Kotlin++ frontend is intended to lower to it.

## 1. Typed return: `T ! E`

Future:

```kotlin
fun charge(amount: Int): Receipt ! PaymentError {
    val token   = authorize(amount)   // auto-propagates ! PaymentError
    val receipt = capture(token)
    receipt
}
```

Today (`kpp-core`):

```kotlin
fun charge(amount: Int): Result<Receipt, PaymentError> = result {
    val token   = authorize(amount).bind()
    val receipt = capture(token).bind()
    receipt
}
```

The `result { }` builder uses a private non-local return to short-
circuit on `Err`. `bind()` is the explicit auto-propagation.

## 2. Error declaration: `error Foo { ... }`

Future:

```kotlin
error PaymentError {
    case CardRejected
    case NetworkUnavailable
    case FraudSuspected(score: Int)
}
```

Today:

```kotlin
sealed interface PaymentError : KppError {
    data object CardRejected      : PaymentError
    data object NetworkUnavailable: PaymentError
    data class  FraudSuspected(val score: Int) : PaymentError
}
```

`KppError` is a marker interface. Sealed-hierarchy exhaustiveness in
`when` gives the same callsite checking the keyword would.

## 3. Failure expression: `fail Foo.A`

Future:

```kotlin
fun authorize(amount: Int): Token ! PaymentError {
    if (amount < 0) fail PaymentError.CardRejected
    Token("tok")
}
```

Today (two equivalent shapes):

```kotlin
// Outside a result { } block:
fun authorize(amount: Int): Result<Token, PaymentError> {
    if (amount < 0) return Err(PaymentError.CardRejected)
    return Ok(Token("tok"))
}

// Inside a result { } block, err() is the short form:
val token = result {
    if (amount < 0) err(PaymentError.CardRejected)
    Token("tok")
}
```

## 4. Capability context: `context(...)` parameters

Future:

```kotlin
context(log: Logger, clock: Clock)
fun audit(msg: String) {
    log.info("[${clock.now()}] $msg")
}

// Caller:
audit("login")  // log, clock resolved from context
```

Today (`kpp-capability`):

```kotlin
fun Capabilities.audit(msg: String) {
    val log   = get<Logger>()
    val clock = get<Clock>()
    log.info("[${clock.now()}] $msg")
}

withCapabilities(ConsoleLogger(), SystemClock()) {
    audit("login")
}
```

`withCapabilities(vararg)` already works. When Kotlin++ ships, the
extension-on-`Capabilities` shape is mechanically rewritten into
`context(log: Logger, clock: Clock)` with no semantic change.

## 5. Capability binding: `withCapabilities { }`

This is the only feature in the list that already runs in production
shape today. The library resolves capabilities by walking each
value's supertype chain and registering it under every interface that
extends `Capability`. Last-wins on conflicts.

```kotlin
withCapabilities(StripeGateway(), InMemoryAuditLog(), ConsoleLogger()) {
    pay(request)   // pay is a Capabilities.pay extension
}
```

See `samples/payment/Main.kt` for an end-to-end run.

## 6. Effect modifiers: `pure` / `io` / `db` / `blocking`

Future:

```kotlin
pure  fun add(a: Int, b: Int) = a + b
io    fun fetch(url: String): String ! IoError
db    fun load(id: UserId): User ! DbError
blocking fun sleep(ms: Long)
```

Today (`kpp-analyzer` annotations):

```kotlin
@Pure     fun add(a: Int, b: Int) = a + b
@Io       fun fetch(url: String): Result<String, IoError>
@Db       fun load(id: UserId): Result<User, DbError>
@Blocking fun sleep(ms: Long)
```

Annotations have `SOURCE` retention. KPP011 already enforces "no
blocking call inside a `suspend fun`" with a regex heuristic. Full
transitive effect inference needs the FIR plugin (Phase 1).

## 7. Immutable data: `immutable data class`

Future:

```kotlin
immutable data class Receipt(
    val id: TxId,
    val items: List<Item>,   // implicitly persistent
)
```

Today (`libs/kpp-immutable`):

```kotlin
import dev.kpp.immutable.Immutable
import dev.kpp.immutable.ImmutableList
import dev.kpp.immutable.immutableListOf

@Immutable
data class Receipt(
    val id: TxId,
    val items: ImmutableList<Item>,
)

val r = Receipt(TxId("tx-1"), immutableListOf(Item("a"), Item("b")))
val r2 = r.copy(items = r.items.add(Item("c")))   // returns NEW list
```

`ImmutableList` is a sealed wrapper over `List<T>`: writers (`add`,
`set`, `remove`) return new instances; `iterator().remove()` throws.
Defensive copy on `toImmutableList()`. KPP004 still catches the
egregious case of returning a `MutableList` from a public function;
KPP005 (planned) will check that `@Immutable` types contain only
immutable fields.

## 8. Ownership: `borrow` / `move`

Future:

```kotlin
fun consume(move buf: Buffer): Result
fun peek(borrow buf: Buffer): Int
```

Today: `@Borrow` / `@Move` annotations from `libs/kpp-immutable` mark
intent on parameters and properties (`@Retention(SOURCE)`); they are
documentary only. Real enforcement requires a compiler; Kotlin's
reference semantics do not let a library forbid aliasing. This stays
a Phase 3 deliverable.

```kotlin
import dev.kpp.immutable.Borrow
import dev.kpp.immutable.Move

fun consume(@Move buf: Buffer): Result<Int, IoError> { ... }
fun peek(@Borrow buf: Buffer): Int { ... }
```

## 9. Collection literals: `[1, 2, 3]`

Future:

```kotlin
val xs: List<Int> = [1, 2, 3]
val m: Map<String, Int> = ["a": 1, "b": 2]
```

Today:

```kotlin
val xs = listOf(1, 2, 3)
val m  = mapOf("a" to 1, "b" to 2)
```

Pure parser-level sugar. Compiler-only feature.

## 10. Compile-time derivation: `@derive(...)`

Future:

```kotlin
@derive(Json, Equals, Diff)
immutable data class Receipt(val id: TxId, val items: List<Item>)
```

Today (`libs/kpp-derive`, runtime stub):

```kotlin
import dev.kpp.derive.DeriveJson
import dev.kpp.derive.JsonName
import dev.kpp.derive.Json

@DeriveJson(snakeCase = true)
data class Receipt(
    val id: String,
    @JsonName("amount_minor") val amountMinor: Long,
)

Json.encode(Receipt("tx-1", 1999))
// {"id":"tx-1","amount_minor":1999}
val back: Receipt = Json.decode("""{"id":"tx-1","amount_minor":1999}""")
```

The current backend uses `kotlin-reflect` at runtime — same surface
the future Phase-4 KSP/FIR backend will ship, so user code does not
need to change when the codegen lands. Performance is reflection-grade
and not for hot paths.

## 11. Typed structured concurrency

Future Kotlin++ keeps `T ! E` typed even when work fans out:

```kotlin
suspend fun chargeAll(orders: List<Order>): List<Receipt> ! PaymentError =
    orders.parallelMap { gateway.charge(it) }
```

Today (`libs/kpp-concurrent`):

```kotlin
import dev.kpp.concurrent.parallelMap
import dev.kpp.concurrent.raceFirstSuccess
import dev.kpp.concurrent.withTimeoutOrErr

suspend fun Capabilities.chargeAll(orders: List<Order>): Result<List<Receipt>, PaymentError> {
    val gw = get<PaymentGateway>()
    return orders.parallelMap(concurrency = 8) { gw.charge(it.card, it.amount) }
}

// First-success pattern across redundant providers:
suspend fun Capabilities.fetch(id: UserId): Result<User, FetchError> =
    raceFirstSuccess(
        { primary.lookup(id) },
        { secondary.lookup(id) },
    ).mapErr { errors -> FetchError.AllFailed(errors) }
```

The first `Err` cancels siblings in `parallelMap`; the first `Ok`
cancels siblings in `raceFirstSuccess`. `withTimeoutOrErr(ms, onTimeout)`
returns `Err(onTimeout())` instead of throwing `TimeoutCancellationException`.
The point is that the error type stays in the signature when work
parallelises; nothing leaks as an unchecked exception.

## Summary mapping table

| Kotlin++                         | Today                                                  |
|----------------------------------|--------------------------------------------------------|
| `T ! E`                          | `Result<T, E>`                                         |
| `error Foo { case A; case B(...) }` | `sealed interface Foo : KppError { ... }`           |
| `fail X`                         | `return Err(X)` or `err(X)` inside `result { }`        |
| `let x = foo()` propagating `!E` | `val x = foo().bind()` inside `result { }`            |
| `context(c: C) fun ...`          | `fun Capabilities.f(...) { val c = get<C>(); ... }`   |
| `withCapabilities(...) { ... }`  | same — already real                                    |
| `pure` / `io` / `db` / `blocking`| `@Pure` / `@Io` / `@Db` / `@Blocking`                  |
| `immutable data class`           | `@Immutable` + `ImmutableList`/`Map`/`Set` wrappers    |
| `borrow` / `move`                | `@Borrow` / `@Move` markers (documentary)              |
| typed parallel `! E` over async  | `parallelMap`, `raceFirstSuccess`, `sequence`          |
| `[1, 2, 3]`                      | `listOf(1, 2, 3)`                                      |
| `@derive(Json, ...)`             | `@DeriveJson` runtime stub (KSP backend = Phase 4)     |
