package dev.kpp.test

import dev.kpp.capability.builtins.Clock
import java.time.Duration
import java.time.Instant

/**
 * A deterministic [Clock] for tests. Starts at [initial], advances only when
 * [advanceBy] is called. Returns the same instant for every [now] in between.
 */
class VirtualClock(initial: Instant) : Clock {
    private val lock = Any()
    private var current: Instant = initial

    override fun now(): Instant = synchronized(lock) { current }

    fun advanceBy(duration: Duration) {
        synchronized(lock) { current = current.plus(duration) }
    }

    fun setNow(instant: Instant) {
        synchronized(lock) { current = instant }
    }
}
