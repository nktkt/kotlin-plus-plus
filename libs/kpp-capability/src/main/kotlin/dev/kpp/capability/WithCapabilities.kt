package dev.kpp.capability

/** Builds a `Capabilities` from `caps` and runs `block` with it as the receiver. */
inline fun <R> withCapabilities(vararg caps: Capability, block: Capabilities.() -> R): R =
    Capabilities.of(*caps).run(block)

/** Runs `block` with this `Capabilities` as the receiver. */
inline fun <R> Capabilities.use(block: Capabilities.() -> R): R = run(block)
