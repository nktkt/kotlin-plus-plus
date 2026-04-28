package dev.kpp.validation

/**
 * A list guaranteed to have at least one element. Use [head] for the first
 * element and [tail] for the rest. Constructed via [nonEmptyListOf] or
 * [List.toNonEmptyListOrNull]. Equality is structural.
 */
class NonEmptyList<out T> private constructor(
    private val items: List<T>,
) : List<T> by items {
    val head: T get() = items.first()
    val tail: List<T> get() = items.drop(1)

    operator fun plus(other: NonEmptyList<@UnsafeVariance T>): NonEmptyList<T> =
        NonEmptyList(items + other.items)

    operator fun plus(other: @UnsafeVariance T): NonEmptyList<T> = NonEmptyList(items + other)

    override fun equals(other: Any?): Boolean = other is NonEmptyList<*> && items == other.items
    override fun hashCode(): Int = items.hashCode()
    override fun toString(): String = "NonEmptyList$items"

    companion object {
        @PublishedApi internal fun <T> unsafe(items: List<T>): NonEmptyList<T> {
            kotlin.require(items.isNotEmpty()) { "NonEmptyList requires at least one element" }
            return NonEmptyList(items)
        }
    }
}

fun <T> nonEmptyListOf(head: T, vararg tail: T): NonEmptyList<T> =
    NonEmptyList.unsafe(listOf(head) + tail.toList())

fun <T> List<T>.toNonEmptyListOrNull(): NonEmptyList<T>? =
    if (isEmpty()) null else NonEmptyList.unsafe(this)
