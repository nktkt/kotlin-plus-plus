package dev.kpp.core

/** Marker for typed errors usable as the `E` in `Result<T, E>`. */
interface KppError

/** Exception form of a `KppError`, thrown by `getOrThrow` when the result is `Err`. */
class KppErrorException(val kppError: Any) : RuntimeException("Unhandled KppError: $kppError")

/** Returns `Ok.value`, or throws `KppErrorException` wrapping `Err.error`. */
fun <T, E> Result<T, E>.getOrThrow(): T = when (this) {
    is Result.Ok -> value
    is Result.Err -> throw KppErrorException(error as Any)
}
