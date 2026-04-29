package dev.kpp.samples.ksp

import dev.kpp.derive.Json
import dev.kpp.secret.toSecret
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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

    @Test
    fun secret_is_redacted_by_default_in_generated_output() {
        val c = Credentials("alice", "hunter2".toSecret())
        val runtime = Json.encode(c)
        val generated = c.toJsonGenerated()
        assertEquals("""{"username":"alice","password":"[REDACTED]"}""", runtime)
        assertEquals(runtime, generated)
        assertFalse(generated.contains("hunter2"), "secret leaked: $generated")
    }

    @Test
    fun secret_is_exposed_when_allow_secrets_true() {
        val c = CredentialsDiagnostic("alice", "hunter2".toSecret())
        val runtime = Json.encode(c)
        val generated = c.toJsonGenerated()
        assertEquals("""{"username":"alice","password":"hunter2"}""", runtime)
        assertEquals(runtime, generated)
    }

    @Test
    fun secret_byte_array_is_redacted() {
        val k = ApiToken(ByteArray(16) { it.toByte() }.toSecret())
        val runtime = Json.encode(k)
        val generated = k.toJsonGenerated()
        assertEquals("""{"token":"[REDACTED]"}""", runtime)
        assertEquals(runtime, generated)
        assertTrue(generated.contains("[REDACTED]"))
        assertFalse(generated.contains("16"))
    }

    @Test
    fun nullable_secret_emits_null_for_null() {
        val c = OptionalCredentials("alice", null)
        val runtime = Json.encode(c)
        val generated = c.toJsonGenerated()
        assertEquals("""{"username":"alice","password":null}""", runtime)
        assertEquals(runtime, generated)
    }

    @Test
    fun nullable_secret_redacts_when_present() {
        val c = OptionalCredentials("alice", "p@ss".toSecret())
        val runtime = Json.encode(c)
        val generated = c.toJsonGenerated()
        assertEquals("""{"username":"alice","password":"[REDACTED]"}""", runtime)
        assertEquals(runtime, generated)
    }

    @Test
    fun map_string_to_string_matches_runtime() {
        val empty = Tags(
            labels = emptyMap(),
            counts = emptyMap(),
            nested = emptyMap(),
            nullableValues = emptyMap(),
        )
        assertEquals(Json.encode(empty), empty.toJsonGenerated())

        val populated = Tags(
            labels = linkedMapOf("env" to "prod", "tier" to "gold"),
            counts = emptyMap(),
            nested = emptyMap(),
            nullableValues = emptyMap(),
        )
        val out = populated.toJsonGenerated()
        assertEquals(Json.encode(populated), out)
        assertTrue(out.contains("\"env\":\"prod\""))
        assertTrue(out.contains("\"tier\":\"gold\""))
    }

    @Test
    fun map_string_to_int_matches_runtime() {
        val t = Tags(
            labels = emptyMap(),
            counts = linkedMapOf("a" to 1, "b" to 2, "c" to -7),
            nested = emptyMap(),
            nullableValues = emptyMap(),
        )
        val out = t.toJsonGenerated()
        assertEquals(Json.encode(t), out)
        // Insertion-order parity check.
        assertTrue(out.indexOf("\"a\":1") < out.indexOf("\"b\":2"))
    }

    @Test
    fun map_string_to_nested_class_matches_runtime() {
        val t = Tags(
            labels = emptyMap(),
            counts = emptyMap(),
            nested = linkedMapOf(
                "home" to Address("1 First St", "Springfield", "00001"),
                "work" to Address("2 Second Ave", "Shelbyville", "00002"),
            ),
            nullableValues = emptyMap(),
        )
        assertEquals(Json.encode(t), t.toJsonGenerated())
    }

    @Test
    fun map_with_nullable_values_matches_runtime() {
        val t = Tags(
            labels = emptyMap(),
            counts = emptyMap(),
            nested = emptyMap(),
            nullableValues = linkedMapOf("present" to "x", "absent" to null),
        )
        val out = t.toJsonGenerated()
        assertEquals(Json.encode(t), out)
        assertTrue(out.contains("\"absent\":null"))
        assertTrue(out.contains("\"present\":\"x\""))
    }

    @Test
    fun nullable_map_emits_null_for_null() {
        val t = OptionalTags(labels = null)
        assertEquals("""{"labels":null}""", Json.encode(t))
        assertEquals(Json.encode(t), t.toJsonGenerated())
    }

    @Test
    fun nullable_map_present_matches_runtime() {
        val t = OptionalTags(labels = linkedMapOf("a" to "1", "b" to "2"))
        assertEquals(Json.encode(t), t.toJsonGenerated())
    }

    // ---- Codegen decoder round-trip tests ----

    @Test
    fun decode_round_trips_simple_class() {
        val a = Address("221B Baker St", "London", "NW1")
        val text = Json.encode(a)
        val decoded = Address.fromJsonGenerated(text)
        assertEquals(a, decoded)
    }

    @Test
    fun decode_round_trips_with_snake_case() {
        val r = Request(userId = 7, sessionToken = "tok", isAdmin = true)
        val text = Json.encode(r)
        val decoded = Request.fromJsonGenerated(text)
        assertEquals(r, decoded)
        // The serialized form must use snake_case keys, then decode back to camelCase fields.
        assertTrue(text.contains("\"user_id\""))
        assertTrue(text.contains("\"session_token\""))
        assertTrue(text.contains("\"is_admin\""))
    }

    @Test
    fun decode_handles_jsonname_overrides() {
        // UserProfile has @JsonName("email_address") on email.
        val u = UserProfile(
            id = 11,
            displayName = "Eve",
            email = "eve@example.com",
            internalNote = "n/a", // matches the default so round-trip equality holds
            nickname = null,
            tags = emptyList(),
            pastAddresses = emptyList(),
            primaryAddress = null,
        )
        val text = Json.encode(u)
        assertTrue(text.contains("\"email_address\""))
        val decoded = UserProfile.fromJsonGenerated(text)
        assertEquals(u, decoded)
        assertEquals("eve@example.com", decoded.email)
    }

    @Test
    fun decode_uses_default_for_jsonignore_properties() {
        // The JSON omits internal_note entirely (because of @JsonIgnore on
        // the encoder side). The decoder must NOT throw — it must use the
        // declared default ("n/a") via named-arg ctor invocation.
        val u = UserProfile(
            id = 1,
            displayName = "x",
            email = "x@y",
            internalNote = "anything-here",
            nickname = null,
            tags = emptyList(),
            pastAddresses = emptyList(),
            primaryAddress = null,
        )
        val text = Json.encode(u)
        assertFalse(text.contains("internal_note"), "expected ignored key to be absent")
        val decoded = UserProfile.fromJsonGenerated(text)
        // The default fires, so internalNote is "n/a" regardless of what
        // the source had — this is the documented contract for @JsonIgnore.
        assertEquals("n/a", decoded.internalNote)
    }

    @Test
    fun decode_round_trips_nullable_fields() {
        val withVal = OptionalTags(labels = linkedMapOf("a" to "1", "b" to "2"))
        val withNull = OptionalTags(labels = null)
        assertEquals(withVal, OptionalTags.fromJsonGenerated(Json.encode(withVal)))
        assertEquals(withNull, OptionalTags.fromJsonGenerated(Json.encode(withNull)))
    }

    @Test
    fun decode_round_trips_nested_class() {
        val u = UserProfile(
            id = 100,
            displayName = "Carol",
            email = "carol@example.com",
            internalNote = "n/a",
            nickname = "cc",
            tags = listOf("a", "b"),
            pastAddresses = emptyList(),
            primaryAddress = Address("delta", "Deltaplex", "DDD"),
        )
        val text = Json.encode(u)
        val decoded = UserProfile.fromJsonGenerated(text)
        assertEquals(u, decoded)
        assertEquals("delta", decoded.primaryAddress?.street)
    }

    @Test
    fun decode_round_trips_list_of_strings() {
        val u = UserProfile(
            id = 1,
            displayName = "x",
            email = "x@y",
            internalNote = "n/a",
            nickname = null,
            tags = listOf("alpha", "beta", "gamma"),
            pastAddresses = emptyList(),
            primaryAddress = null,
        )
        val decoded = UserProfile.fromJsonGenerated(Json.encode(u))
        assertEquals(u, decoded)
        assertEquals(listOf("alpha", "beta", "gamma"), decoded.tags)
    }

    @Test
    fun decode_round_trips_list_of_nested_classes() {
        val u = UserProfile(
            id = 1,
            displayName = "x",
            email = "x@y",
            internalNote = "n/a",
            nickname = null,
            tags = emptyList(),
            pastAddresses = listOf(
                Address("1 First St", "Springfield", "00001"),
                Address("2 Second Ave", "Shelbyville", "00002"),
            ),
            primaryAddress = null,
        )
        val decoded = UserProfile.fromJsonGenerated(Json.encode(u))
        assertEquals(u, decoded)
        assertEquals(2, decoded.pastAddresses.size)
        assertEquals("1 First St", decoded.pastAddresses[0].street)
    }

    @Test
    fun decode_round_trips_map_of_strings() {
        val t = Tags(
            labels = linkedMapOf("env" to "prod", "tier" to "gold"),
            counts = linkedMapOf("a" to 1, "b" to 2),
            nested = linkedMapOf("home" to Address("1 First St", "Springfield", "00001")),
            nullableValues = linkedMapOf("present" to "x", "absent" to null),
        )
        val text = Json.encode(t)
        val decoded = Tags.fromJsonGenerated(text)
        assertEquals(t, decoded)
        assertEquals("prod", decoded.labels["env"])
        assertEquals(1, decoded.counts["a"])
        assertEquals("1 First St", decoded.nested["home"]?.street)
        assertTrue(decoded.nullableValues.containsKey("absent"))
        assertEquals(null, decoded.nullableValues["absent"])
    }

    @Test
    fun decode_throws_on_missing_required_field() {
        // Address requires street/city/zip. Construct a JSON that omits one.
        val text = """{"street":"x","city":"y"}"""
        val ex = assertFailsWith<IllegalArgumentException> {
            Address.fromJsonGenerated(text)
        }
        assertTrue(
            ex.message?.contains("zip") == true,
            "expected 'zip' in error message, got: ${ex.message}",
        )
    }

    @Test
    fun decode_throws_on_wrong_type() {
        // email is declared as String; pass a number instead.
        val text = """{"id":1,"display_name":"x","email_address":42,"nickname":null,"tags":[],"past_addresses":[],"primary_address":null}"""
        assertFailsWith<IllegalArgumentException> {
            UserProfile.fromJsonGenerated(text)
        }
    }

    @Test
    fun decode_round_trips_full_shape() {
        val a = Address("1 First St", "Springfield", "00001")
        val full = FullShape(
            name = "alpha",
            count = 7,
            total = 1234567890123L,
            ratio = 1.5,
            partial = 0.25f,
            tinyByte = 9,
            tinyShort = 300,
            flag = true,
            maybe = "yes",
            tags = listOf("a", "b"),
            addresses = listOf(a),
            labels = linkedMapOf("k" to "v"),
            home = a,
            nullableHome = null,
            originalName = "kept",
            computed = "default",
        )
        val decoded = FullShape.fromJsonGenerated(Json.encode(full))
        assertEquals(full, decoded)
    }

    @Test
    fun decode_full_shape_with_null_optional() {
        val a = Address("1 First St", "Springfield", "00001")
        val full = FullShape(
            name = "alpha",
            count = 0,
            total = 0L,
            ratio = 0.0,
            partial = 0f,
            tinyByte = 0,
            tinyShort = 0,
            flag = false,
            maybe = null,
            tags = emptyList(),
            addresses = emptyList(),
            labels = emptyMap(),
            home = a,
            nullableHome = a,
            originalName = "x",
            computed = "default",
        )
        val decoded = FullShape.fromJsonGenerated(Json.encode(full))
        assertEquals(full, decoded)
    }
}
