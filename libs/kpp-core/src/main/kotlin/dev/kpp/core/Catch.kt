package dev.kpp.core

inline fun <T, reified Ex : Throwable, E> runCatchingTyped(
    transform: (Ex) -> E,
    block: () -> T,
): Result<T, E> = try {
    Result.Ok(block())
    // noinspection KPP002
    // The reified type bound is `Throwable`, so the call site decides which
    // subtype to convert to a typed Err. We must catch the full Throwable
    // hierarchy here and rethrow anything that doesn't match `Ex`.
} catch (t: Throwable) {
    if (t is Ex) Result.Err(transform(t)) else throw t
}
