package dev.kpp.immutable

import java.util.Collections
import java.util.LinkedHashMap

sealed interface ImmutableMap<K, out V> : Map<K, V> {
    fun put(key: K, value: @UnsafeVariance V): ImmutableMap<K, V>
    fun remove(key: K): ImmutableMap<K, V>
}

private class LinkedImmutableMap<K, V>(source: Map<K, V>) : ImmutableMap<K, V> {

    private val data: LinkedHashMap<K, V> = LinkedHashMap(source)

    override val size: Int get() = data.size

    override fun isEmpty(): Boolean = data.isEmpty()

    override fun containsKey(key: K): Boolean = data.containsKey(key)

    override fun containsValue(value: @UnsafeVariance V): Boolean = data.containsValue(value)

    override fun get(key: K): V? = data[key]

    override val entries: Set<Map.Entry<K, V>>
        get() = Collections.unmodifiableSet(data.entries)

    override val keys: Set<K>
        get() = Collections.unmodifiableSet(data.keys)

    override val values: Collection<V>
        get() = Collections.unmodifiableCollection(data.values)

    override fun put(key: K, value: @UnsafeVariance V): ImmutableMap<K, V> {
        val next = LinkedHashMap<K, V>(data)
        next[key] = value
        return LinkedImmutableMap(next)
    }

    override fun remove(key: K): ImmutableMap<K, V> {
        if (!data.containsKey(key)) return this
        val next = LinkedHashMap<K, V>(data)
        next.remove(key)
        return LinkedImmutableMap(next)
    }

    override fun equals(other: Any?): Boolean {
        if (other !is Map<*, *>) return false
        return data == other
    }

    override fun hashCode(): Int = data.hashCode()

    override fun toString(): String = data.toString()
}

fun <K, V> immutableMapOf(vararg pairs: Pair<K, V>): ImmutableMap<K, V> {
    val src = LinkedHashMap<K, V>(pairs.size)
    for ((k, v) in pairs) src[k] = v
    return LinkedImmutableMap(src)
}

fun <K, V> Map<K, V>.toImmutableMap(): ImmutableMap<K, V> = LinkedImmutableMap(this)
