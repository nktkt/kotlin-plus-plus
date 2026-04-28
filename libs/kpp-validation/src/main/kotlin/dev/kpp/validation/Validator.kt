package dev.kpp.validation

import dev.kpp.core.Result
import dev.kpp.core.err
import dev.kpp.core.ok

/**
 * Pure validator: takes an [I], returns Ok([O]) on success or Err with a
 * non-empty list of errors. Errors accumulate when validators are combined
 * with [and] (every failing branch contributes); short-circuit semantics
 * are reserved for [andThen] (only the first failure is observed).
 */
fun interface Validator<in I, out O, out E> {
    fun validate(input: I): Result<O, NonEmptyList<E>>
}

/** Always succeeds, returning the input unchanged. */
fun <T, E> validId(): Validator<T, T, E> = Validator { ok(it) }

/** Always fails with a single error. */
fun <I, O, E> validFail(error: E): Validator<I, O, E> =
    Validator { err(nonEmptyListOf(error)) }

/** Lift a Boolean predicate to a Validator. */
fun <T, E> require(predicate: (T) -> Boolean, error: (T) -> E): Validator<T, T, E> =
    Validator { input -> if (predicate(input)) ok(input) else err(nonEmptyListOf(error(input))) }
