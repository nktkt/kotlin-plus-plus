package dev.kpp.immutable

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ImmutableMapTest {

    @Test
    fun `immutableMapOf returns ImmutableMap`() {
        val m = immutableMapOf("a" to 1, "b" to 2)
        assertTrue(m is ImmutableMap<String, Int>)
        assertEquals(2, m.size)
        assertEquals(1, m["a"])
        assertEquals(2, m["b"])
    }

    @Test
    fun `put returns a new map with the entry added and original is unchanged`() {
        val original = immutableMapOf("a" to 1, "b" to 2)
        val updated = original.put("c", 3)
        assertNotSame(original, updated)
        assertEquals(2, original.size)
        assertNull(original["c"])
        assertEquals(3, updated.size)
        assertEquals(3, updated["c"])
    }

    @Test
    fun `put on existing key returns a new map with replaced value`() {
        val original = immutableMapOf("a" to 1)
        val updated = original.put("a", 99)
        assertNotSame(original, updated)
        assertEquals(1, original["a"])
        assertEquals(99, updated["a"])
    }

    @Test
    fun `remove returns a new map without the key`() {
        val original = immutableMapOf("a" to 1, "b" to 2)
        val updated = original.remove("a")
        assertNotSame(original, updated)
        assertEquals(2, original.size)
        assertEquals(1, updated.size)
        assertNull(updated["a"])
    }

    @Test
    fun `entries iterator remove throws UnsupportedOperationException`() {
        val m = immutableMapOf("a" to 1, "b" to 2)
        val it = m.entries.iterator()
        it.next()
        assertFailsWith<UnsupportedOperationException> {
            (it as MutableIterator<Map.Entry<String, Int>>).remove()
        }
    }

    @Test
    fun `keys and values are unmodifiable views`() {
        val m = immutableMapOf("a" to 1, "b" to 2)
        assertFailsWith<UnsupportedOperationException> {
            @Suppress("UNCHECKED_CAST")
            (m.keys as MutableSet<String>).add("c")
        }
        assertFailsWith<UnsupportedOperationException> {
            @Suppress("UNCHECKED_CAST")
            (m.values as MutableCollection<Int>).add(3)
        }
    }

    @Test
    fun `toImmutableMap takes a defensive copy`() {
        val source = mutableMapOf("a" to 1, "b" to 2)
        val snapshot = source.toImmutableMap()
        source["c"] = 3
        source["a"] = 99
        assertEquals(2, snapshot.size)
        assertEquals(1, snapshot["a"])
        assertNull(snapshot["c"])
    }
}
