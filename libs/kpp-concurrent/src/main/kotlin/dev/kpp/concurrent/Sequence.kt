package dev.kpp.concurrent

import dev.kpp.core.Result
import dev.kpp.core.err
import dev.kpp.core.ok

/**
 * Sequence a list of Results: Ok(list) if all are Ok, else first Err.
 * Same shape as Haskell's sequence on Either, suitable for combining
 * already-completed Results from multiple sources.
 */
fun <T, E> List<Result<T, E>>.sequence(): Result<List<T>, E> {
    val out = ArrayList<T>(this.size)
    for (r in this) {
        when (r) {
            is Result.Ok -> out.add(r.value)
            is Result.Err -> return r
        }
    }
    return ok(out)
}

/**
 * Like [sequence] but accumulates ALL errors instead of short-circuiting.
 */
fun <T, E> List<Result<T, E>>.sequenceAccumulating(): Result<List<T>, List<E>> {
    val values = ArrayList<T>(this.size)
    val errors = ArrayList<E>()
    for (r in this) {
        when (r) {
            is Result.Ok -> values.add(r.value)
            is Result.Err -> errors.add(r.error)
        }
    }
    return if (errors.isEmpty()) ok(values) else err(errors)
}
