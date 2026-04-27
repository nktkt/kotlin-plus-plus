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

class BindScope<E> @PublishedApi internal constructor(@PublishedApi internal val marker: Any) {
    fun <T> Result<T, E>.bind(): T = when (this) {
        is Result.Ok -> value
        is Result.Err -> throw BindAbort(marker, error)
    }
}

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
