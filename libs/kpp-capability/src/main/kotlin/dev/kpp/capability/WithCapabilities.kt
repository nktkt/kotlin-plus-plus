package dev.kpp.capability

inline fun <R> withCapabilities(vararg caps: Capability, block: Capabilities.() -> R): R =
    Capabilities.of(*caps).run(block)

inline fun <R> Capabilities.use(block: Capabilities.() -> R): R = run(block)
