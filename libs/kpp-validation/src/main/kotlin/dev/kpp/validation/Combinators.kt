package dev.kpp.validation

import dev.kpp.core.Result
import dev.kpp.core.err
import dev.kpp.core.ok

/**
 * Run [this] and [other] both. If either fails, return Err with all errors
 * concatenated. If both succeed, return Ok of [other]'s output (assumes the
 * pair operates on the same input I; the second validator can refine the
 * type from O1 to O2).
 *
 * Common pattern: chain field-level checks where every check inspects the
 * same input but each contributes its own potential error.
 */
infix fun <I, O1, O2, E> Validator<I, O1, E>.and(other: Validator<I, O2, E>): Validator<I, O2, E> =
    Validator { input ->
        val left = this.validate(input)
        val right = other.validate(input)
        when {
            left is Result.Ok && right is Result.Ok -> right
            left is Result.Err && right is Result.Ok -> err(left.error)
            left is Result.Ok && right is Result.Err -> err(right.error)
            else -> {
                val both = (left as Result.Err).error + (right as Result.Err).error
                err(both)
            }
        }
    }

/**
 * Sequential composition. Run [this] first; if it fails, return that Err.
 * If it succeeds, feed its output to [next]. Short-circuiting: [next] is
 * not run when [this] fails.
 */
infix fun <I, O1, O2, E> Validator<I, O1, E>.andThen(next: Validator<O1, O2, E>): Validator<I, O2, E> =
    Validator { input ->
        when (val first = this.validate(input)) {
            is Result.Err -> err(first.error)
            is Result.Ok -> next.validate(first.value)
        }
    }

/**
 * Apply [inner] only when the input is non-null; return Ok(null) otherwise.
 * Use for fields that are legitimately optional.
 */
fun <T, E> optional(inner: Validator<T, T, E>): Validator<T?, T?, E> =
    Validator { input ->
        if (input == null) ok(null)
        else when (val r = inner.validate(input)) {
            is Result.Ok -> ok(r.value)
            is Result.Err -> err(r.error)
        }
    }

/**
 * Reject null with [missing]; otherwise apply [inner].
 */
fun <T, E> required(inner: Validator<T, T, E>, missing: () -> E): Validator<T?, T, E> =
    Validator { input ->
        if (input == null) err(nonEmptyListOf(missing()))
        else inner.validate(input)
    }

/** Map the error type of a Validator. */
fun <I, O, E1, E2> Validator<I, O, E1>.mapError(f: (E1) -> E2): Validator<I, O, E2> =
    Validator { input ->
        when (val r = this.validate(input)) {
            is Result.Ok -> ok(r.value)
            is Result.Err -> {
                // NonEmptyList preserves at-least-one across the map.
                val mapped = r.error.map(f)
                err(NonEmptyList.unsafe(mapped))
            }
        }
    }
