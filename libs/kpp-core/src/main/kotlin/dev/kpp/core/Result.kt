package dev.kpp.core

/** Typed-error result: `Ok(value)` or `Err(error)`. Models Kotlin++ `T ! E`. */
sealed interface Result<out T, out E> {
    /** Success branch of `Result`; carries the produced value. */
    data class Ok<out T>(val value: T) : Result<T, Nothing>
    /** Failure branch of `Result`; carries a typed error (no throwing). */
    data class Err<out E>(val error: E) : Result<Nothing, E>
}

/** Wraps `value` as a successful `Result`. */
fun <T> ok(value: T): Result<T, Nothing> = Result.Ok(value)
/** Wraps `error` as a failed `Result`; the `return Err(X)` form for Kotlin++ `fail X`. */
fun <E> err(error: E): Result<Nothing, E> = Result.Err(error)
