# kpp-test

Test helpers for Kotlin++ MVP. Provides idiomatic assertions for `Result<T, E>`,
a recording proxy for capability bags, a deterministic virtual clock, and a
structured capture logger. This module exposes `kotlin-test` via `api`, so
consumers do not need to redeclare the dependency.

## Result assertions

```kotlin
val r: Result<User, DomainErr> = repository.find(id)
val user = r.assertOk()
r.assertErrValue(DomainErr.NotFound(id))
val notFound = r.assertErrType<User, DomainErr, DomainErr.NotFound>()
```

## Recording capabilities

```kotlin
val recorder = CapabilityRecorder()
val log = recordingCapability(Logger::class, ConsoleLogger(), recorder)
withCapabilities(log) { service.run() }
assertEquals(1, recorder.recordsFor(Logger::class).size)
```

## Virtual clock

```kotlin
val clock = VirtualClock(Instant.parse("2025-01-01T00:00:00Z"))
clock.advanceBy(Duration.ofMinutes(5))
assertEquals(Instant.parse("2025-01-01T00:05:00Z"), clock.now())
```

## Capture logger

```kotlin
val log = CaptureLogger()
log.info("started")
log.error("failed", IllegalStateException("x"))
assertEquals(CaptureLogger.Level.ERROR, log.entries[1].level)
```

## Iterable Result helper

```kotlin
val results: List<Result<Int, String>> = listOf(ok(1), ok(2), ok(3))
val values: List<Int> = results.assertAllOk()
```
