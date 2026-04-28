package dev.kpp.validation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertNotNull

class NonEmptyListTest {

    @Test fun nonEmptyListOf_constructs_with_head_and_tail() {
        val nel = nonEmptyListOf(1, 2, 3)
        assertEquals(3, nel.size)
        assertEquals(listOf(1, 2, 3), nel.toList())
    }

    @Test fun head_returns_first_element() {
        val nel = nonEmptyListOf("a", "b", "c")
        assertEquals("a", nel.head)
    }

    @Test fun tail_returns_rest() {
        val nel = nonEmptyListOf("a", "b", "c")
        assertEquals(listOf("b", "c"), nel.tail)
    }

    @Test fun plus_concatenates_two_nels() {
        val a = nonEmptyListOf(1, 2)
        val b = nonEmptyListOf(3, 4)
        val joined = a + b
        assertEquals(listOf(1, 2, 3, 4), joined.toList())
    }

    @Test fun plus_with_single_element_appends() {
        val a = nonEmptyListOf(1, 2)
        val joined = a + 3
        assertEquals(listOf(1, 2, 3), joined.toList())
    }

    @Test fun toNonEmptyListOrNull_returns_null_for_empty() {
        assertNull(emptyList<Int>().toNonEmptyListOrNull())
    }

    @Test fun toNonEmptyListOrNull_returns_nel_for_non_empty() {
        val nel = listOf(1, 2, 3).toNonEmptyListOrNull()
        assertNotNull(nel)
        assertEquals(1, nel.head)
    }

    @Test fun equals_is_structural() {
        val a = nonEmptyListOf(1, 2, 3)
        val b = nonEmptyListOf(1, 2, 3)
        val c = nonEmptyListOf(1, 2, 4)
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertNotEquals(a, c)
    }
}
