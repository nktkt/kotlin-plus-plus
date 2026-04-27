package dev.kpp.immutable

import java.util.Collections
import java.util.LinkedHashSet

sealed interface ImmutableSet<out T> : Set<T> {
    fun add(element: @UnsafeVariance T): ImmutableSet<T>
    fun remove(element: @UnsafeVariance T): ImmutableSet<T>
}

private class LinkedImmutableSet<T>(source: Collection<T>) : ImmutableSet<T> {

    private val data: LinkedHashSet<T> = LinkedHashSet(source)

    override val size: Int get() = data.size

    override fun isEmpty(): Boolean = data.isEmpty()

    override fun contains(element: @UnsafeVariance T): Boolean = data.contains(element)

    override fun containsAll(elements: Collection<@UnsafeVariance T>): Boolean = data.containsAll(elements)

    override fun iterator(): Iterator<T> = Collections.unmodifiableSet(data).iterator()

    override fun add(element: @UnsafeVariance T): ImmutableSet<T> {
        if (data.contains(element)) return this
        val next = LinkedHashSet<T>(data)
        next.add(element)
        return LinkedImmutableSet(next)
    }

    override fun remove(element: @UnsafeVariance T): ImmutableSet<T> {
        if (!data.contains(element)) return this
        val next = LinkedHashSet<T>(data)
        next.remove(element)
        return LinkedImmutableSet(next)
    }

    override fun equals(other: Any?): Boolean {
        if (other !is Set<*>) return false
        return data == other
    }

    override fun hashCode(): Int = data.hashCode()

    override fun toString(): String = data.toString()
}

fun <T> immutableSetOf(vararg elements: T): ImmutableSet<T> =
    LinkedImmutableSet(elements.toList())

fun <T> Set<T>.toImmutableSet(): ImmutableSet<T> = LinkedImmutableSet(this)
