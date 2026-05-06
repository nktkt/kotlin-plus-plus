package dev.kpp.secret

/**
 * A value that must not leak through logs, toString, default JSON
 * encoding, or accidental exception messages. Read with [expose]
 * only when the value is actually needed; treat the boundary like
 * unwrapping a sharp object.
 *
 * Equality is value-equal but timing-safe for String / ByteArray
 * (constant-time per byte) so equality checks on credentials do not
 * leak length or position via short-circuit timing.
 */
class Secret<T : Any> internal constructor(private val value: T) {
    /** Returns the wrapped secret value; call only at the boundary where the raw value is actually needed. */
    fun expose(): T = value

    override fun toString(): String = "Secret(***)"

    override fun equals(other: Any?): Boolean {
        if (other !is Secret<*>) return false
        val a = value
        val b = other.value
        // Timing-safe paths only kick in when both sides have the same
        // credential-shaped runtime type. Mixed types (e.g. String vs
        // ByteArray) fall through to the plain-equals branch and return
        // false; that is the documented behavior.
        return when {
            a is String && b is String -> constantTimeEquals(a, b)
            a is ByteArray && b is ByteArray -> constantTimeEquals(a, b)
            else -> a == b
        }
    }

    override fun hashCode(): Int = value.hashCode()
}

/** Wraps `value` as a `Secret<T>`. */
fun <T : Any> secretOf(value: T): Secret<T> = Secret(value)
/** Wraps this `String` as a `RedactedString`. */
fun String.toSecret(): Secret<String> = Secret(this)
/** Wraps this `ByteArray` as a `RedactedBytes`. */
fun ByteArray.toSecret(): Secret<ByteArray> = Secret(this)

/** A `Secret<String>`; redacts in `toString` and uses constant-time equality. */
typealias RedactedString = Secret<String>
/** A `Secret<ByteArray>`; redacts in `toString` and uses constant-time equality. */
typealias RedactedBytes = Secret<ByteArray>
