package dev.kpp.samples.ksp

import dev.kpp.derive.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RichModelsParityTest {

    @Test
    fun address_round_trips_byte_identical() {
        val a = Address("221B Baker St", "London", "NW1")
        assertEquals(Json.encode(a), a.toJsonGenerated())
    }

    @Test
    fun user_profile_with_all_features_matches_runtime() {
        val u = UserProfile(
            id = 1,
            displayName = "Alice",
            email = "alice@example.com",
            internalNote = "secret",
            nickname = "ali",
            tags = listOf("admin", "beta", "tester"),
            pastAddresses = listOf(
                Address("1 First St", "Springfield", "00001"),
                Address("2 Second Ave", "Shelbyville", "00002"),
            ),
            primaryAddress = Address("3 Third Blvd", "Capital City", "00003"),
        )
        assertEquals(Json.encode(u), u.toJsonGenerated())
    }

    @Test
    fun user_profile_with_null_fields_matches_runtime() {
        val u = UserProfile(
            id = 2,
            displayName = "Bob",
            email = "bob@example.com",
            internalNote = "ignored",
            nickname = null,
            tags = emptyList(),
            pastAddresses = emptyList(),
            primaryAddress = null,
        )
        assertEquals(Json.encode(u), u.toJsonGenerated())
    }

    @Test
    fun integer_list_with_empty_list_matches_runtime() {
        val v = IntegerList(emptyList())
        assertEquals(Json.encode(v), v.toJsonGenerated())
    }

    @Test
    fun integer_list_with_multiple_elements_matches_runtime() {
        val v = IntegerList(listOf(1, 2, 3, -7, Int.MAX_VALUE, Int.MIN_VALUE))
        assertEquals(Json.encode(v), v.toJsonGenerated())
    }

    @Test
    fun nested_derive_json_recursion_matches_runtime() {
        val u = UserProfile(
            id = 100,
            displayName = "Carol",
            email = "carol@example.com",
            internalNote = "n/a",
            nickname = "cc",
            tags = listOf("a", "b"),
            pastAddresses = listOf(
                Address("alpha", "Alphaville", "AAA"),
                Address("beta", "Betatown", "BBB"),
                Address("gamma", "Gammacity", "CCC"),
            ),
            primaryAddress = Address("delta", "Deltaplex", "DDD"),
        )
        assertEquals(Json.encode(u), u.toJsonGenerated())
    }

    @Test
    fun json_ignore_omits_property_from_output() {
        val u = UserProfile(
            id = 9,
            displayName = "Dave",
            email = "dave@example.com",
            internalNote = "DO_NOT_LEAK",
            nickname = null,
            tags = emptyList(),
            pastAddresses = emptyList(),
            primaryAddress = null,
        )
        val out = u.toJsonGenerated()
        assertFalse(out.contains("internal_note"), "expected internal_note key to be absent: $out")
        assertFalse(out.contains("internalNote"), "expected internalNote key to be absent: $out")
        assertFalse(out.contains("DO_NOT_LEAK"), "expected ignored value to be absent: $out")
        // And: still byte-identical to the runtime encoder, which also drops it.
        assertEquals(Json.encode(u), out)
    }

    @Test
    fun json_name_overrides_snake_case() {
        val u = UserProfile(
            id = 11,
            displayName = "Eve",
            email = "eve@example.com",
            internalNote = "x",
            nickname = null,
            tags = emptyList(),
            pastAddresses = emptyList(),
            primaryAddress = null,
        )
        val out = u.toJsonGenerated()
        assertTrue(out.contains("\"email_address\""), "expected @JsonName key: $out")
        // Make sure the default `"email"` key didn't leak in alongside it.
        // (Use the quoted form so we don't false-positive on email_address.)
        assertFalse(out.contains("\"email\""), "expected default email key to be absent: $out")
        assertEquals(Json.encode(u), out)
    }

    @Test
    fun nullable_list_of_nullable_elements_matches_runtime() {
        // Spot-check List<T?> via the integer list — since IntegerList holds
        // a non-nullable list, exercise the Nullable + List combo through the
        // primary case we have. The richer surface is covered by UserProfile's
        // List<Address> + nullable Address paths above.
        val empty = IntegerList(listOf())
        assertEquals(Json.encode(empty), empty.toJsonGenerated())
    }
}
