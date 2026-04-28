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

fun <T : Any> secretOf(value: T): Secret<T> = Secret(value)
fun String.toSecret(): Secret<String> = Secret(this)
fun ByteArray.toSecret(): Secret<ByteArray> = Secret(this)

typealias RedactedString = Secret<String>
typealias RedactedBytes = Secret<ByteArray>
