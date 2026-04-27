package dev.kpp.core

inline fun <T, E, R> Result<T, E>.map(transform: (T) -> R): Result<R, E> = when (this) {
    is Result.Ok -> Result.Ok(transform(value))
    is Result.Err -> this
}

inline fun <T, E, R> Result<T, E>.flatMap(transform: (T) -> Result<R, E>): Result<R, E> = when (this) {
    is Result.Ok -> transform(value)
    is Result.Err -> this
}

inline fun <T, E, F> Result<T, E>.mapErr(transform: (E) -> F): Result<T, F> = when (this) {
    is Result.Ok -> this
    is Result.Err -> Result.Err(transform(error))
}

inline fun <T, E> Result<T, E>.getOrElse(fallback: (E) -> T): T = when (this) {
    is Result.Ok -> value
    is Result.Err -> fallback(error)
}

inline fun <T, E> Result<T, E>.recover(fallback: (E) -> Result<T, E>): Result<T, E> = when (this) {
    is Result.Ok -> this
    is Result.Err -> fallback(error)
}

inline fun <T, E> T?.orFail(error: () -> E): Result<T, E> =
    if (this != null) Result.Ok(this) else Result.Err(error())
