package dev.kpp.core

// Non-local exit from inside `result { }` is implemented by throwing a private
// exception keyed to the enclosing builder's marker; the builder rethrows any
// abort whose marker doesn't match so nested `result { }` calls don't cross.
@PublishedApi
internal class BindAbort(
    @JvmField @PublishedApi internal val marker: Any,
    @JvmField @PublishedApi internal val error: Any?,
) : RuntimeException() {
    override fun fillInStackTrace(): Throwable = this
}

/** Receiver of a `result { }` block; provides `bind()` for short-circuiting on `Err`. */
class BindScope<E> @PublishedApi internal constructor(@PublishedApi internal val marker: Any) {
    /** Unwraps `Ok.value` or short-circuits the enclosing `result { }` to `Err`. */
    fun <T> Result<T, E>.bind(): T = when (this) {
        is Result.Ok -> value
        is Result.Err -> throw BindAbort(marker, error)
    }
}

/** Builder emulating Kotlin++ auto-propagation of `! E`; `bind()` calls inside exit early as `Err`. */
inline fun <T, E> result(block: BindScope<E>.() -> T): Result<T, E> {
    val marker = Any()
    return try {
        Result.Ok(BindScope<E>(marker).block())
    } catch (abort: BindAbort) {
        if (abort.marker === marker) {
            @Suppress("UNCHECKED_CAST")
            Result.Err(abort.error as E)
        } else {
            throw abort
        }
    }
}
