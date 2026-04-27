package dev.kpp.capability

/**
 * Marker interface for any value that participates in capability-based DI.
 *
 * In Kotlin++ this is roughly the bound of a `context(...)` parameter.
 * Here it is a plain marker so [Capabilities] can index instances by KClass.
 */
interface Capability
