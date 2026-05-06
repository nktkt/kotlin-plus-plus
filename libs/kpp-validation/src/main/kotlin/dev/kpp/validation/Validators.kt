package dev.kpp.validation

import dev.kpp.core.err
import dev.kpp.core.ok

/** Rejects `""` with code `"empty"`. */
val nonEmptyString: Validator<String, String, String> =
    Validator { s -> if (s.isNotEmpty()) ok(s) else err(nonEmptyListOf("empty")) }

/** Rejects strings of only whitespace with code `"blank"`. */
val nonBlankString: Validator<String, String, String> =
    Validator { s -> if (s.isNotBlank()) ok(s) else err(nonEmptyListOf("blank")) }

/** String length must lie in `min..max`; emits `"length:expected[min..max],got=<n>"`. */
fun lengthBetween(min: Int, max: Int): Validator<String, String, String> {
    // Precondition asserts on developer-supplied parameters; not a validator failure.
    kotlin.require(min >= 0) { "min must be >= 0" }
    kotlin.require(max >= min) { "max must be >= min" }
    return Validator { s ->
        if (s.length in min..max) ok(s)
        else err(nonEmptyListOf("length:expected[$min..$max],got=${s.length}"))
    }
}

/** Integer must lie in `min..max`; emits `"out-of-range:[min..max],got=<v>"`. */
fun rangeInt(min: Int, max: Int): Validator<Int, Int, String> {
    kotlin.require(max >= min) { "max must be >= min" }
    return Validator { v ->
        if (v in min..max) ok(v)
        else err(nonEmptyListOf("out-of-range:[$min..$max],got=$v"))
    }
}

/** String must match `regex`; emits `"does-not-match:<label>"`. */
fun matches(regex: Regex, label: String = regex.pattern): Validator<String, String, String> =
    Validator { s ->
        if (regex.matches(s)) ok(s)
        else err(nonEmptyListOf("does-not-match:$label"))
    }

private val EMAIL_REGEX = Regex("""^[A-Za-z0-9._%+\-]+@[A-Za-z0-9.\-]+\.[A-Za-z]{2,}$""")

/** Basic email shape; emits `"not-email"`. */
val email: Validator<String, String, String> =
    Validator { s ->
        if (EMAIL_REGEX.matches(s)) ok(s) else err(nonEmptyListOf("not-email"))
    }

/** Value must be in `allowed`; emits `"not-in-allowed-set"`. */
fun <T> oneOf(allowed: Set<T>): Validator<T, T, String> =
    Validator { v ->
        if (v in allowed) ok(v) else err(nonEmptyListOf("not-in-allowed-set"))
    }
