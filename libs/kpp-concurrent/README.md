# kpp-concurrent

Typed structured concurrency for Kotlin++. Errors stay typed across
`async`/`await` boundaries: parallelizing work over `Result<T, E>` produces
another `Result<T, E>` rather than collapsing into thrown exceptions.

## Public API

- `Iterable<I>.parallelMap(concurrency, transform): Result<List<T>, E>` -
  parallel map; first `Err` short-circuits and cancels siblings.
- `raceFirstSuccess(vararg tasks): Result<T, List<E>>` - returns the first
  `Ok`; cancels siblings on a winner. If all fail, returns errors in
  completion order.
- `List<Result<T, E>>.sequence(): Result<List<T>, E>` - turns a list of
  results into a result of list, short-circuiting on first `Err`.
- `List<Result<T, E>>.sequenceAccumulating(): Result<List<T>, List<E>>` -
  same shape, but collects every error.
- `withTimeoutOrErr(timeoutMillis, onTimeout, block): Result<T, E>` -
  applies a deadline; on timeout returns `Err(onTimeout())` instead of
  throwing.

## Cancellation policy

- `parallelMap`: the first `Err` returned by any worker cancels remaining
  siblings cooperatively. `CancellationException` is always rethrown from
  workers; the outer call surfaces the captured first error.
- `raceFirstSuccess`: the first `Ok` cancels remaining tasks. Tasks that
  return `Err` simply append their error and exit.
- `withTimeoutOrErr`: built on `withTimeoutOrNull`. The block must respect
  cooperative cancellation (yield / suspending calls).

## Future direction

In a fully realised Kotlin++ these primitives would be tied to effect
annotations such as `@Async` and `@Cancellable`, letting the analyzer
verify cancellation safety at the call site. Today the guarantees are
library-level only - using `coroutineScope` plus the conventions above.
