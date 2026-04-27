package dev.kpp.concurrent

import dev.kpp.core.Result
import dev.kpp.core.err
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Run [block] with a timeout. On timeout, returns Err(onTimeout()) instead of
 * throwing. Cancellation is cooperative - block must check yield/active points.
 */
suspend fun <T, E> withTimeoutOrErr(
    timeoutMillis: Long,
    onTimeout: () -> E,
    block: suspend () -> Result<T, E>,
): Result<T, E> {
    val r: Result<T, E>? = withTimeoutOrNull(timeoutMillis) { block() }
    return r ?: err(onTimeout())
}
