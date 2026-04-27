package dev.kpp.test

import dev.kpp.core.Result

/** Asserts the Result is Ok and returns the wrapped value. Fails with a typed message including the Err on mismatch. */
fun <T, E> Result<T, E>.assertOk(): T = when (this) {
    is Result.Ok -> value
    is Result.Err -> throw AssertionError(
        "expected: Ok<*>, actual: Err($error) [error type=${error?.let { it!!::class.qualifiedName } ?: "null"}]",
    )
}

/** Asserts the Result is Err and returns the wrapped error. Fails with a message including the Ok value on mismatch. */
fun <T, E> Result<T, E>.assertErr(): E = when (this) {
    is Result.Err -> error
    is Result.Ok -> throw AssertionError(
        "expected: Err<*>, actual: Ok($value)",
    )
}

/** Asserts the Result is Ok with a specific value (data-class equality). */
fun <T, E> Result<T, E>.assertOkValue(expected: T) {
    when (this) {
        is Result.Ok -> if (value != expected) {
            throw AssertionError("expected: Ok($expected), actual: Ok($value)")
        }
        is Result.Err -> throw AssertionError("expected: Ok($expected), actual: Err($error)")
    }
}

/** Asserts the Result is Err whose error is exactly equal to [expected]. */
fun <T, E> Result<T, E>.assertErrValue(expected: E) {
    when (this) {
        is Result.Err -> if (error != expected) {
            throw AssertionError("expected: Err($expected), actual: Err($error)")
        }
        is Result.Ok -> throw AssertionError("expected: Err($expected), actual: Ok($value)")
    }
}

/** Asserts the Result is Err whose error is an instance of [ErrType]. Returns it for further inspection. */
inline fun <T, E, reified ErrType : E & Any> Result<T, E>.assertErrType(): ErrType = when (this) {
    is Result.Err -> {
        val e = error
        if (e is ErrType) {
            e
        } else {
            throw AssertionError(
                "expected: Err of type ${ErrType::class.qualifiedName}, " +
                    "actual: Err($e) [error type=${e?.let { it!!::class.qualifiedName } ?: "null"}]",
            )
        }
    }
    is Result.Ok -> throw AssertionError(
        "expected: Err of type ${ErrType::class.qualifiedName}, actual: Ok($value)",
    )
}

/** Asserts every Result in the iterable is Ok and returns their values in order. */
fun <T, E> Iterable<Result<T, E>>.assertAllOk(): List<T> {
    val out = ArrayList<T>()
    for ((index, r) in this.withIndex()) {
        when (r) {
            is Result.Ok -> out += r.value
            is Result.Err -> throw AssertionError(
                "expected: all Ok, actual: index=$index was Err(${r.error})",
            )
        }
    }
    return out
}
