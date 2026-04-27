package dev.kpp.core

interface KppError

class KppErrorException(val kppError: Any) : RuntimeException("Unhandled KppError: $kppError")

fun <T, E> Result<T, E>.getOrThrow(): T = when (this) {
    is Result.Ok -> value
    is Result.Err -> throw KppErrorException(error as Any)
}
