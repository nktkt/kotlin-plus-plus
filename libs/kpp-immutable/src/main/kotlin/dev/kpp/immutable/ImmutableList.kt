package dev.kpp.immutable

sealed interface ImmutableList<out T> : List<T> {
    override fun iterator(): Iterator<T>
    override fun listIterator(): ListIterator<T>
    override fun listIterator(index: Int): ListIterator<T>
    override fun subList(fromIndex: Int, toIndex: Int): ImmutableList<T>
    fun add(element: @UnsafeVariance T): ImmutableList<T>
    fun remove(element: @UnsafeVariance T): ImmutableList<T>
    fun set(index: Int, element: @UnsafeVariance T): ImmutableList<T>
}

@Suppress("UNCHECKED_CAST")
private class ArrayImmutableList<T>(private val data: Array<Any?>) : ImmutableList<T> {

    override val size: Int get() = data.size

    override fun isEmpty(): Boolean = data.isEmpty()

    override fun contains(element: @UnsafeVariance T): Boolean = data.any { it == element }

    override fun containsAll(elements: Collection<@UnsafeVariance T>): Boolean =
        elements.all { contains(it) }

    override fun get(index: Int): T {
        if (index < 0 || index >= data.size) throw IndexOutOfBoundsException("index=$index size=${data.size}")
        return data[index] as T
    }

    override fun indexOf(element: @UnsafeVariance T): Int = data.indexOfFirst { it == element }

    override fun lastIndexOf(element: @UnsafeVariance T): Int = data.indexOfLast { it == element }

    override fun iterator(): Iterator<T> = object : MutableIterator<T> {
        private var i = 0
        override fun hasNext(): Boolean = i < data.size
        override fun next(): T {
            if (!hasNext()) throw NoSuchElementException()
            return data[i++] as T
        }
        override fun remove(): Nothing =
            throw UnsupportedOperationException("ImmutableList iterator does not support remove")
    }

    override fun listIterator(): ListIterator<T> = listIterator(0)

    override fun listIterator(index: Int): ListIterator<T> {
        if (index < 0 || index > data.size) throw IndexOutOfBoundsException("index=$index size=${data.size}")
        return ImmutableListIterator(data, index)
    }

    override fun subList(fromIndex: Int, toIndex: Int): ImmutableList<T> {
        if (fromIndex < 0 || toIndex > data.size || fromIndex > toIndex) {
            throw IndexOutOfBoundsException("fromIndex=$fromIndex toIndex=$toIndex size=${data.size}")
        }
        val copy = arrayOfNulls<Any?>(toIndex - fromIndex)
        for (i in fromIndex until toIndex) copy[i - fromIndex] = data[i]
        return ArrayImmutableList(copy)
    }

    override fun add(element: @UnsafeVariance T): ImmutableList<T> {
        val copy = arrayOfNulls<Any?>(data.size + 1)
        for (i in data.indices) copy[i] = data[i]
        copy[data.size] = element
        return ArrayImmutableList(copy)
    }

    override fun remove(element: @UnsafeVariance T): ImmutableList<T> {
        val idx = indexOf(element)
        if (idx < 0) return this
        val copy = arrayOfNulls<Any?>(data.size - 1)
        for (i in 0 until idx) copy[i] = data[i]
        for (i in idx + 1 until data.size) copy[i - 1] = data[i]
        return ArrayImmutableList(copy)
    }

    override fun set(index: Int, element: @UnsafeVariance T): ImmutableList<T> {
        if (index < 0 || index >= data.size) throw IndexOutOfBoundsException("index=$index size=${data.size}")
        val copy = data.copyOf()
        copy[index] = element
        return ArrayImmutableList(copy)
    }

    override fun equals(other: Any?): Boolean {
        if (other !is List<*>) return false
        if (other.size != size) return false
        val it = other.iterator()
        for (e in data) {
            if (e != it.next()) return false
        }
        return true
    }

    override fun hashCode(): Int {
        var hash = 1
        for (e in data) hash = 31 * hash + (e?.hashCode() ?: 0)
        return hash
    }

    override fun toString(): String = data.joinToString(prefix = "[", postfix = "]")
}

private class ImmutableListIterator<T>(
    private val data: Array<Any?>,
    start: Int,
) : MutableListIterator<T> {
    private var cursor = start

    override fun hasNext(): Boolean = cursor < data.size
    override fun hasPrevious(): Boolean = cursor > 0
    override fun nextIndex(): Int = cursor
    override fun previousIndex(): Int = cursor - 1

    @Suppress("UNCHECKED_CAST")
    override fun next(): T {
        if (!hasNext()) throw NoSuchElementException()
        return data[cursor++] as T
    }

    @Suppress("UNCHECKED_CAST")
    override fun previous(): T {
        if (!hasPrevious()) throw NoSuchElementException()
        return data[--cursor] as T
    }

    override fun add(element: T): Nothing =
        throw UnsupportedOperationException("ImmutableList list-iterator does not support add")

    override fun remove(): Nothing =
        throw UnsupportedOperationException("ImmutableList list-iterator does not support remove")

    override fun set(element: T): Nothing =
        throw UnsupportedOperationException("ImmutableList list-iterator does not support set")
}

private val EMPTY: ImmutableList<Nothing> = ArrayImmutableList(emptyArray())

fun <T> immutableListOf(vararg elements: T): ImmutableList<T> {
    if (elements.isEmpty()) {
        @Suppress("UNCHECKED_CAST")
        return EMPTY as ImmutableList<T>
    }
    val data = arrayOfNulls<Any?>(elements.size)
    for (i in elements.indices) data[i] = elements[i]
    return ArrayImmutableList(data)
}

fun <T> List<T>.toImmutableList(): ImmutableList<T> {
    if (isEmpty()) {
        @Suppress("UNCHECKED_CAST")
        return EMPTY as ImmutableList<T>
    }
    val data = arrayOfNulls<Any?>(size)
    var i = 0
    for (e in this) {
        data[i++] = e
    }
    return ArrayImmutableList(data)
}

@Suppress("UNCHECKED_CAST")
val <T> ImmutableList<T>.empty: ImmutableList<T>
    get() = EMPTY as ImmutableList<T>
