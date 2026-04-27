package dev.kpp.immutable

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotSame
import kotlin.test.assertTrue

class ImmutableListTest {

    @Test
    fun `immutableListOf returns ImmutableList`() {
        val list = immutableListOf(1, 2, 3)
        assertTrue(list is ImmutableList<Int>)
        assertEquals(3, list.size)
        assertEquals(1, list[0])
        assertEquals(2, list[1])
        assertEquals(3, list[2])
    }

    @Test
    fun `add returns a new list with element appended and original is unchanged`() {
        val original = immutableListOf("a", "b")
        val updated = original.add("c")
        assertNotSame(original, updated)
        assertEquals(2, original.size)
        assertEquals(listOf("a", "b"), original.toList())
        assertEquals(3, updated.size)
        assertEquals(listOf("a", "b", "c"), updated.toList())
    }

    @Test
    fun `set returns a new list with element replaced`() {
        val original = immutableListOf(10, 20, 30)
        val updated = original.set(1, 99)
        assertNotSame(original, updated)
        assertEquals(listOf(10, 20, 30), original.toList())
        assertEquals(listOf(10, 99, 30), updated.toList())
    }

    @Test
    fun `remove returns a new list without the element`() {
        val original = immutableListOf("a", "b", "c")
        val updated = original.remove("b")
        assertNotSame(original, updated)
        assertEquals(listOf("a", "b", "c"), original.toList())
        assertEquals(listOf("a", "c"), updated.toList())
    }

    @Test
    fun `iterator remove throws UnsupportedOperationException`() {
        val list = immutableListOf(1, 2, 3)
        val it = list.iterator()
        it.next()
        assertFailsWith<UnsupportedOperationException> {
            (it as MutableIterator<Int>).remove()
        }
    }

    @Test
    fun `listIterator does not support set add or remove`() {
        val list = immutableListOf(1, 2, 3)
        val it = list.listIterator()
        it.next()
        assertFailsWith<UnsupportedOperationException> {
            @Suppress("UNCHECKED_CAST")
            (it as MutableListIterator<Int>).set(99)
        }
        assertFailsWith<UnsupportedOperationException> {
            @Suppress("UNCHECKED_CAST")
            (it as MutableListIterator<Int>).add(99)
        }
        assertFailsWith<UnsupportedOperationException> {
            @Suppress("UNCHECKED_CAST")
            (it as MutableListIterator<Int>).remove()
        }
    }

    @Test
    fun `subList returns an ImmutableList`() {
        val list = immutableListOf(1, 2, 3, 4, 5)
        val sub = list.subList(1, 4)
        assertTrue(sub is ImmutableList<Int>)
        assertEquals(listOf(2, 3, 4), sub.toList())
    }

    @Test
    fun `toImmutableList takes a defensive copy`() {
        val source = mutableListOf("x", "y", "z")
        val snapshot = source.toImmutableList()
        source.add("w")
        source[0] = "X"
        assertEquals(listOf("x", "y", "z"), snapshot.toList())
    }

    @Test
    fun `empty extension returns an empty ImmutableList`() {
        val list = immutableListOf(1, 2, 3)
        val e = list.empty
        assertTrue(e.isEmpty())
        assertEquals(0, e.size)
    }
}
