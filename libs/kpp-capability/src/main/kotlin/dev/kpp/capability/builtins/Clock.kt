package dev.kpp.capability.builtins

import dev.kpp.capability.Capability
import java.time.Instant
import java.time.Clock as JClock

interface Clock : Capability {
    fun now(): Instant
}

class SystemClock : Clock {
    private val delegate: JClock = JClock.systemUTC()
    override fun now(): Instant = delegate.instant()
}

class FixedClock(private val instant: Instant) : Clock {
    override fun now(): Instant = instant
}
