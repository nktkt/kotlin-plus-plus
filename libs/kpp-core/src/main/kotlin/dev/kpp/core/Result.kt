package dev.kpp.core

sealed interface Result<out T, out E> {
    data class Ok<out T>(val value: T) : Result<T, Nothing>
    data class Err<out E>(val error: E) : Result<Nothing, E>
}

fun <T> ok(value: T): Result<T, Nothing> = Result.Ok(value)
fun <E> err(error: E): Result<Nothing, E> = Result.Err(error)
