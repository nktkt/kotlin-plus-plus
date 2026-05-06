package dev.kpp.core

/** Maps `Ok.value` through `transform`; passes `Err` through unchanged. */
inline fun <T, E, R> Result<T, E>.map(transform: (T) -> R): Result<R, E> = when (this) {
    is Result.Ok -> Result.Ok(transform(value))
    is Result.Err -> this
}

/** Chains another `Result`-producing step on `Ok`; passes `Err` through unchanged. */
inline fun <T, E, R> Result<T, E>.flatMap(transform: (T) -> Result<R, E>): Result<R, E> = when (this) {
    is Result.Ok -> transform(value)
    is Result.Err -> this
}

/** Maps `Err.error` through `transform`; passes `Ok` through unchanged. */
inline fun <T, E, F> Result<T, E>.mapErr(transform: (E) -> F): Result<T, F> = when (this) {
    is Result.Ok -> this
    is Result.Err -> Result.Err(transform(error))
}

/** Returns `Ok.value`, or `fallback(error)` on `Err`. */
inline fun <T, E> Result<T, E>.getOrElse(fallback: (E) -> T): T = when (this) {
    is Result.Ok -> value
    is Result.Err -> fallback(error)
}

/** Replaces `Err` with another `Result` from `fallback`; passes `Ok` through unchanged. */
inline fun <T, E> Result<T, E>.recover(fallback: (E) -> Result<T, E>): Result<T, E> = when (this) {
    is Result.Ok -> this
    is Result.Err -> fallback(error)
}

/** Lifts a nullable `T?` into `Result`: non-null becomes `Ok`, `null` becomes `Err(error())`. */
inline fun <T, E> T?.orFail(error: () -> E): Result<T, E> =
    if (this != null) Result.Ok(this) else Result.Err(error())
