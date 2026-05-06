package dev.kpp.capability.builtins

import dev.kpp.capability.Capability
import java.time.Instant
import java.time.Clock as JClock

/** A `Capability` that supplies the current `Instant`. */
interface Clock : Capability {
    /** Returns the current `Instant`. */
    fun now(): Instant
}

/** A `Clock` backed by `java.time.Clock.systemUTC()`. */
class SystemClock : Clock {
    private val delegate: JClock = JClock.systemUTC()
    override fun now(): Instant = delegate.instant()
}

/** A `Clock` that always returns the given `instant`; intended for tests. */
class FixedClock(private val instant: Instant) : Clock {
    override fun now(): Instant = instant
}
