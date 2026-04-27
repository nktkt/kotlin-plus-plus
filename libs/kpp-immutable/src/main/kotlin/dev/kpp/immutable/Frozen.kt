package dev.kpp.immutable

/**
 * Marks an object graph as logically frozen. Returns the same instance for
 * convenience. The `@Immutable` analyzer rule checks the type tree.
 */
inline fun <T : Any> freeze(value: T): T = value

class FrozenViolation(message: String) : IllegalStateException(message)
