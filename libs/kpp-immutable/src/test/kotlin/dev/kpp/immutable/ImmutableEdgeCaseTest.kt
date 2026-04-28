package dev.kpp.immutable

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ImmutableEdgeCaseTest {

    // --- ArrayImmutableList ----------------------------------------------------

    @Test
    fun list_contains_returns_true_when_element_present() {
        val list = immutableListOf("a", "b", "c")
        assertTrue(list.contains("b"))
        assertFalse(list.contains("z"))
    }

    @Test
    fun list_containsAll_returns_true_only_when_all_present() {
        val list = immutableListOf(1, 2, 3, 4)
        assertTrue(list.containsAll(listOf(2, 3)))
        assertFalse(list.containsAll(listOf(2, 99)))
    }

    @Test
    fun list_lastIndexOf_returns_last_position() {
        val list = immutableListOf("a", "b", "a", "c", "a")
        assertEquals(4, list.lastIndexOf("a"))
        assertEquals(-1, list.lastIndexOf("z"))
    }

    @Test
    fun list_indexOf_returns_first_position() {
        val list = immutableListOf("a", "b", "a")
        assertEquals(0, list.indexOf("a"))
        assertEquals(-1, list.indexOf("z"))
    }

    @Test
    fun list_subList_throws_on_invalid_range() {
        val list = immutableListOf(1, 2, 3)
        assertFailsWith<IndexOutOfBoundsException> { list.subList(-1, 2) }
        assertFailsWith<IndexOutOfBoundsException> { list.subList(0, 99) }
        assertFailsWith<IndexOutOfBoundsException> { list.subList(2, 1) }
    }

    @Test
    fun list_get_throws_on_out_of_bounds() {
        val list = immutableListOf(1, 2, 3)
        assertFailsWith<IndexOutOfBoundsException> { list[-1] }
        assertFailsWith<IndexOutOfBoundsException> { list[3] }
    }

    @Test
    fun list_set_throws_on_out_of_bounds() {
        val list = immutableListOf(1, 2, 3)
        assertFailsWith<IndexOutOfBoundsException> { list.set(-1, 9) }
        assertFailsWith<IndexOutOfBoundsException> { list.set(3, 9) }
    }

    @Test
    fun list_listIterator_throws_on_out_of_bounds_index() {
        val list = immutableListOf(1, 2, 3)
        assertFailsWith<IndexOutOfBoundsException> { list.listIterator(-1) }
        assertFailsWith<IndexOutOfBoundsException> { list.listIterator(99) }
    }

    @Test
    fun list_iterator_next_throws_when_exhausted() {
        val list = immutableListOf(1)
        val it = list.iterator()
        it.next()
        assertFailsWith<NoSuchElementException> { it.next() }
    }

    @Test
    fun list_remove_returns_same_when_element_missing() {
        val original = immutableListOf(1, 2, 3)
        val same = original.remove(99)
        assertSame(original, same)
    }

    @Test
    fun list_equals_handles_non_list_and_size_mismatch() {
        val list = immutableListOf(1, 2, 3)
        assertNotEquals<Any>(list, "abc")
        assertNotEquals(list, immutableListOf(1, 2))
        assertEquals(list, immutableListOf(1, 2, 3))
        // Cross-implementation equality with a regular kotlin List.
        assertEquals<List<Int>>(list, listOf(1, 2, 3))
        // Element mismatch.
        assertNotEquals(list, immutableListOf(1, 2, 99))
    }

    @Test
    fun list_hashCode_is_stable_and_matches_equal_lists() {
        val a = immutableListOf(1, 2, 3)
        val b = immutableListOf(1, 2, 3)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun list_hashCode_handles_nulls() {
        // hashCode formula: 31 * h + (e?.hashCode() ?: 0). Null path needed for branch coverage.
        val list = immutableListOf<String?>("x", null, "y")
        // Just ensure no NPE and it returns a stable value.
        assertEquals(list.hashCode(), list.hashCode())
    }

    @Test
    fun list_toString_emits_bracketed_form() {
        assertEquals("[1, 2, 3]", immutableListOf(1, 2, 3).toString())
        assertEquals("[]", immutableListOf<Int>().toString())
    }

    // --- ImmutableListIterator -------------------------------------------------

    @Test
    fun listIterator_supports_forward_and_backward_traversal() {
        val list = immutableListOf("a", "b", "c")
        val it = list.listIterator(1)
        // hasPrevious is true at index 1.
        assertTrue(it.hasPrevious())
        assertEquals(1, it.nextIndex())
        assertEquals(0, it.previousIndex())
        assertEquals("b", it.next())
        assertEquals("c", it.next())
        // Now at end: hasPrevious still true.
        assertEquals("c", it.previous())
        assertEquals("b", it.previous())
        assertEquals("a", it.previous())
        // hasPrevious is now false at start.
        assertFalse(it.hasPrevious())
    }

    @Test
    fun listIterator_previous_throws_when_at_start() {
        val list = immutableListOf(1, 2, 3)
        val it = list.listIterator()
        assertFailsWith<NoSuchElementException> { it.previous() }
    }

    // --- LinkedImmutableMap ---------------------------------------------------

    @Test
    fun map_isEmpty_reflects_size() {
        assertTrue(immutableMapOf<String, Int>().isEmpty())
        assertFalse(immutableMapOf("a" to 1).isEmpty())
    }

    @Test
    fun map_containsKey_returns_correctly() {
        val m = immutableMapOf("a" to 1, "b" to 2)
        assertTrue(m.containsKey("a"))
        assertFalse(m.containsKey("z"))
    }

    @Test
    fun map_containsValue_returns_correctly() {
        val m = immutableMapOf("a" to 1, "b" to 2)
        assertTrue(m.containsValue(2))
        assertFalse(m.containsValue(99))
    }

    @Test
    fun map_remove_of_missing_key_returns_same_instance() {
        val m = immutableMapOf("a" to 1)
        assertSame(m, m.remove("z"))
    }

    @Test
    fun map_equals_handles_non_map_and_value_match() {
        val m = immutableMapOf("a" to 1, "b" to 2)
        assertNotEquals<Any>(m, "abc")
        assertEquals<Map<String, Int>>(m, mapOf("a" to 1, "b" to 2))
        assertNotEquals<Map<String, Int>>(m, mapOf("a" to 1, "b" to 99))
    }

    @Test
    fun map_hashCode_matches_underlying_data() {
        val a = immutableMapOf("a" to 1, "b" to 2)
        val b = immutableMapOf("a" to 1, "b" to 2)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun map_toString_includes_entries() {
        val m = immutableMapOf("a" to 1)
        val s = m.toString()
        assertTrue(s.contains("a") && s.contains("1"), "toString=$s")
    }

    // --- LinkedImmutableSet ---------------------------------------------------

    @Test
    fun set_isEmpty_reflects_size() {
        assertTrue(immutableSetOf<Int>().isEmpty())
        assertFalse(immutableSetOf(1).isEmpty())
    }

    @Test
    fun set_containsAll_returns_correctly() {
        val s = immutableSetOf(1, 2, 3, 4)
        assertTrue(s.containsAll(listOf(1, 2)))
        assertFalse(s.containsAll(listOf(1, 99)))
    }

    @Test
    fun set_equals_handles_non_set_and_element_match() {
        val s = immutableSetOf(1, 2, 3)
        assertNotEquals<Any>(s, "abc")
        assertEquals<Set<Int>>(s, setOf(1, 2, 3))
        assertNotEquals<Set<Int>>(s, setOf(1, 2, 99))
    }

    @Test
    fun set_hashCode_matches_equal_sets() {
        val a = immutableSetOf(1, 2, 3)
        val b = immutableSetOf(1, 2, 3)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun set_toString_includes_elements() {
        val s = immutableSetOf(1, 2, 3).toString()
        assertTrue(s.contains("1"), "toString=$s")
    }
}
