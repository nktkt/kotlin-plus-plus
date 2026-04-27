# kpp-capability

Pre-compiler emulation of Kotlin++ capability-based DI on top of Kotlin 2.2's
`-Xcontext-parameters`. A future Kotlin++ frontend will rewrite

```kotlin
context(log: Logger, clock: Clock)
fun audit(msg: String) {
    log.info("[${clock.now()}] $msg")
}
```

into ordinary Kotlin 2.2 context parameters. Until that frontend exists, this
module gives you the same shape with a typed container.

## Usage

```kotlin
import dev.kpp.capability.*
import dev.kpp.capability.builtins.*

fun Capabilities.audit(msg: String) {
    val log = get<Logger>()
    val clock = get<Clock>()
    log.info("[${clock.now()}] $msg")
}

withCapabilities(ConsoleLogger(), SystemClock()) {
    audit("login")
}
```

`withCapabilities(vararg)` builds a `Capabilities` and runs the block with it
as the receiver. `Capabilities.use { ... }` is the same on an existing bag.

## Indexing rules

`Capabilities.of(...)` walks each capability's supertypes and registers it
under every interface that extends `Capability` (excluding `Capability`
itself). So a `ConsoleLogger` is reachable via `get<Logger>()`.

When two capabilities resolve to the same interface key, **the later vararg
wins** (last-wins). The same applies to `caps + cap`.

`get<T>()` throws `IllegalStateException` if the capability is missing;
`getOrNull<T>()` returns `null`.

## Built-ins

- `Logger` — `ConsoleLogger`, `RecordingLogger` (in-memory, for tests)
- `Clock` — `SystemClock` (delegates to `java.time.Clock.systemUTC()`),
  `FixedClock(instant)` (for tests)
