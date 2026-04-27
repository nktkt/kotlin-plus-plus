# samples:http-server

Flagship integration sample. Demonstrates every shipped Kotlin++ MVP module
composing in one realistic codebase.

## What it demonstrates

- `kpp-core`: typed errors via `Result<T, ApiError>`, `ApiError : KppError`.
- `kpp-capability`: `Capabilities`-based DI for `UserRepository`, `Logger`,
  `Clock`. Handlers are `suspend fun Capabilities.X()` extensions.
- `kpp-immutable`: in-memory batch responses are `ImmutableList<User>` until
  they hit the wire boundary.
- `kpp-concurrent`: `parallelMap` for the batch endpoint, `withTimeoutOrErr`
  for the single-user endpoint.
- `kpp-derive`: `@DeriveJson` plus `Json.encode` / `Json.decode<T>` for the
  request and response bodies.

## No real HTTP

There is no real HTTP server in this sample. The `Request` and `Response`
data classes plus `Capabilities.handle(req)` mimic an HTTP handler shape so
the focus stays on the Kotlin++ idioms, not on networking. A future variant
could plug `handle` into Ktor or Netty without changing the modules above.

## Wire boundary

`UsersResponse.users` is a plain `List<User>` because `Json.encode` does not
yet recognize `ImmutableList<T>` as a special list type. In-memory code
paths (handlers, tests) still use `ImmutableList<User>` and convert with
`.toList()` only when serializing. Teaching `Json` about `ImmutableList`
directly is a future improvement.

## Run

```
gradle :samples:http-server:run
```

## Test

```
gradle :samples:http-server:test
```
