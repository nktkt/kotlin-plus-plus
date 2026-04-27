package dev.kpp.concurrent

import dev.kpp.core.Result
import dev.kpp.core.err
import dev.kpp.core.ok
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.atomic.AtomicReference

/**
 * Run [transform] over each element in parallel. The first Err short-circuits
 * the rest (siblings are cancelled cooperatively). Returns Ok(list of T's)
 * preserving input order, or Err(first encountered error).
 */
suspend fun <I, T, E> Iterable<I>.parallelMap(
    concurrency: Int = Int.MAX_VALUE,
    transform: suspend (I) -> Result<T, E>,
): Result<List<T>, E> {
    val items = this.toList()
    if (items.isEmpty()) return ok(emptyList())

    val firstErr = AtomicReference<Result.Err<E>?>(null)
    val semaphore = if (concurrency < Int.MAX_VALUE) Semaphore(concurrency) else null

    return try {
        coroutineScope {
            val deferreds = items.map { item ->
                async {
                    val produce: suspend () -> Result<T, E> = { transform(item) }
                    val r = if (semaphore != null) semaphore.withPermit { produce() } else produce()
                    if (r is Result.Err) {
                        // Capture the first error and cancel siblings cooperatively.
                        if (firstErr.compareAndSet(null, r)) {
                            this@coroutineScope.coroutineContext[Job]?.cancelChildren(
                                CancellationException("parallelMap: sibling errored")
                            )
                        }
                    }
                    r
                }
            }
            val results = deferreds.map { d ->
                try {
                    d.await()
                } catch (ce: CancellationException) {
                    // A sibling errored; surface that error rather than the cancellation.
                    val captured = firstErr.get()
                    if (captured != null) return@coroutineScope captured
                    throw ce
                }
            }
            // After collecting, if any was Err, return the captured first error preserving input order.
            val capturedErr = firstErr.get()
            if (capturedErr != null) return@coroutineScope capturedErr
            @Suppress("UNCHECKED_CAST")
            ok(results.map { (it as Result.Ok<T>).value })
        }
    } catch (ce: CancellationException) {
        val captured = firstErr.get()
        if (captured != null) captured else throw ce
    }
}
