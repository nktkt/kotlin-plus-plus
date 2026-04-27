package dev.kpp.immutable

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ImmutableSetTest {

    @Test
    fun `immutableSetOf returns ImmutableSet`() {
        val s = immutableSetOf(1, 2, 3)
        assertTrue(s is ImmutableSet<Int>)
        assertEquals(3, s.size)
        assertTrue(2 in s)
    }

    @Test
    fun `add returns a new set with element added and original is unchanged`() {
        val original = immutableSetOf("a", "b")
        val updated = original.add("c")
        assertNotSame(original, updated)
        assertEquals(2, original.size)
        assertFalse("c" in original)
        assertEquals(3, updated.size)
        assertTrue("c" in updated)
    }

    @Test
    fun `add of existing element returns the same set`() {
        val original = immutableSetOf("a", "b")
        val same = original.add("a")
        assertSame(original, same)
    }

    @Test
    fun `remove returns a new set without the element`() {
        val original = immutableSetOf(1, 2, 3)
        val updated = original.remove(2)
        assertNotSame(original, updated)
        assertEquals(3, original.size)
        assertEquals(2, updated.size)
        assertFalse(2 in updated)
    }

    @Test
    fun `remove of missing element returns the same set`() {
        val original = immutableSetOf(1, 2, 3)
        val same = original.remove(99)
        assertSame(original, same)
    }

    @Test
    fun `iterator remove throws UnsupportedOperationException`() {
        val s = immutableSetOf("a", "b", "c")
        val it = s.iterator()
        it.next()
        assertFailsWith<UnsupportedOperationException> {
            (it as MutableIterator<String>).remove()
        }
    }

    @Test
    fun `toImmutableSet takes a defensive copy`() {
        val source = mutableSetOf(1, 2, 3)
        val snapshot = source.toImmutableSet()
        source.add(4)
        source.remove(1)
        assertEquals(3, snapshot.size)
        assertTrue(1 in snapshot)
        assertFalse(4 in snapshot)
    }
}
