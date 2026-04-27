package dev.kpp.concurrent

import dev.kpp.core.Result
import dev.kpp.core.err
import dev.kpp.core.ok
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.util.Collections

/**
 * Returns the first Ok result among [tasks]. If every task returns Err, returns
 * Err with a list of all errors in completion order.
 */
suspend fun <T, E> raceFirstSuccess(
    vararg tasks: suspend () -> Result<T, E>,
): Result<T, List<E>> {
    if (tasks.isEmpty()) return err(emptyList())

    val errorsInOrder: MutableList<E> = Collections.synchronizedList(mutableListOf())
    val winner = CompletableDeferred<T>()

    return try {
        coroutineScope {
            val parentJob = this.coroutineContext[Job]!!
            tasks.forEach { task ->
                launch {
                    val r: Result<T, E> = try {
                        task()
                    } catch (ce: CancellationException) {
                        throw ce
                    }
                    when (r) {
                        is Result.Ok -> {
                            if (winner.complete(r.value)) {
                                parentJob.cancelChildren(
                                    CancellationException("raceFirstSuccess: winner found")
                                )
                            }
                        }
                        is Result.Err -> {
                            errorsInOrder.add(r.error)
                        }
                    }
                }
            }
        }
        if (winner.isCompleted) ok(winner.await())
        else err(errorsInOrder.toList())
    } catch (ce: CancellationException) {
        if (winner.isCompleted) ok(winner.await()) else throw ce
    }
}
