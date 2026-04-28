package dev.kpp.derive

import dev.kpp.secret.Secret
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

@DeriveJson
data class WithFloat(val ratio: Float)

@DeriveJson
data class WithBytes(val a: Byte, val b: Short, val c: Long)

@DeriveJson
data class WithNullable(val name: String?, val count: Int?)

@DeriveJson
data class WithDefault(val name: String, val count: Int = 7)

@DeriveJson
data class WithListOfList(val matrix: List<List<Int>>)

@DeriveJson
data class WithMapField(val attrs: Map<String, Int>)

@DeriveJson
data class WithSecretField(val token: Secret<String>)

class JsonEdgeCaseTest {
    @Test fun decodesByte() {
        assertEquals(WithBytes(1, 2, 3L), Json.decode<WithBytes>("""{"a":1,"b":2,"c":3}"""))
    }

    @Test fun decodesFloatFromDouble() {
        assertEquals(WithFloat(0.5f), Json.decode<WithFloat>("""{"ratio":0.5}"""))
    }

    @Test fun decodesFloatFromLong() {
        assertEquals(WithFloat(3f), Json.decode<WithFloat>("""{"ratio":3}"""))
    }

    @Test fun decodesDoubleFromLong() {
        // Number-to-double conversion path through the built-in Double passthrough.
        val raw = JsonParser("3").parseRoot()
        val out = Json.decodeAs(raw, Double::class) as Double
        assertEquals(3.0, out)
    }

    @Test fun decodesDoubleFromDoubleScalar() {
        val raw = JsonParser("1.25").parseRoot()
        val out = Json.decodeAs(raw, Double::class) as Double
        assertEquals(1.25, out)
    }

    @Test fun floatRejectsNonNumber() {
        val ex = assertFailsWith<IllegalArgumentException> {
            Json.decode<WithFloat>("""{"ratio":"x"}""")
        }
        assertTrue(ex.message!!.contains("expected number"))
    }

    @Test fun doubleRejectsNonNumber() {
        // Direct exercise through decodeAs → built-in Double passthrough error path.
        assertFailsWith<IllegalArgumentException> {
            Json.decodeAs("not a number", Double::class)
        }
    }

    @Test fun decodesListOfList() {
        val text = """{"matrix":[[1,2],[3,4],[]]}"""
        val r = Json.decode<WithListOfList>(text)
        assertEquals(listOf(listOf(1, 2), listOf(3, 4), emptyList()), r.matrix)
    }

    @Test fun decodesMapField() {
        val text = """{"attrs":{"a":1,"b":2}}"""
        val r = Json.decode<WithMapField>(text)
        assertEquals(mapOf("a" to 1, "b" to 2), r.attrs)
    }

    @Test fun decodesAsAny() {
        // Any::class passthrough branch in decodeInto.
        val raw: Any = "hello"
        assertEquals("hello", Json.decodeAs(raw, Any::class))
    }

    @Test fun decodeNullReturnsNull() {
        // raw == null path: built-ins still take a non-null path, but raw == null returns null directly.
        assertNull(Json.decodeAs(null, String::class))
    }

    @Test fun decodeNullableFieldMissingDefaultsToNull() {
        // missing "count" field with nullable Int? assigns null.
        val r = Json.decode<WithNullable>("""{"name":"alice"}""")
        assertEquals("alice", r.name)
        assertNull(r.count)
    }

    @Test fun decodeOptionalFieldMissingUsesDefault() {
        // missing "count" field with default value uses default (param.isOptional path).
        val r = Json.decode<WithDefault>("""{"name":"alice"}""")
        assertEquals("alice", r.name)
        assertEquals(7, r.count)
    }

    @Test fun decodeMissingRequiredFieldThrows() {
        val ex = assertFailsWith<IllegalArgumentException> {
            Json.decode<User>("""{"id":1}""")
        }
        assertTrue(ex.message!!.contains("missing required field"))
    }

    @Test fun decodeNonObjectForDeriveClassThrows() {
        // Pass a JSON array where an object is expected.
        val ex = assertFailsWith<IllegalArgumentException> {
            Json.decode<User>("[1,2]")
        }
        assertTrue(ex.message!!.contains("expected JSON object"))
    }

    @Test fun decodeNonDeriveClassThrows() {
        // PlainNoAnno is declared in EncodeTest; it has no @DeriveJson.
        val ex = assertFailsWith<IllegalArgumentException> {
            Json.decode<PlainNoAnno>("""{"x":1}""")
        }
        assertTrue(ex.message!!.contains("@DeriveJson required"))
    }

    @Test fun decodeListWrongShapeThrows() {
        // Field declared as List<Int> but raw is not an array.
        @DeriveJson
        data class HasList(val xs: List<Int>)
        val ex = assertFailsWith<IllegalArgumentException> {
            Json.decode<HasList>("""{"xs":"not an array"}""")
        }
        assertTrue(ex.message!!.contains("expected JSON array"))
    }

    @Test fun decodeMapWrongShapeThrows() {
        @DeriveJson
        data class HasMap(val m: Map<String, Int>)
        val ex = assertFailsWith<IllegalArgumentException> {
            Json.decode<HasMap>("""{"m":[1,2]}""")
        }
        assertTrue(ex.message!!.contains("expected JSON object"))
    }

    @Test fun decodeSecretFieldRejected() {
        val ex = assertFailsWith<IllegalArgumentException> {
            Json.decode<WithSecretField>("""{"token":"x"}""")
        }
        assertTrue(ex.message!!.contains("Secret"))
    }

    @Test fun encodeNestedListOfList() {
        val text = Json.encode(WithListOfList(listOf(listOf(1, 2), listOf(3))))
        assertEquals("""{"matrix":[[1,2],[3]]}""", text)
    }

    @Test fun decodeEmptyListPassesThroughAsRawList() {
        // List::class with no type arg uses the raw passthrough branch.
        val raw = JsonParser("[1,2,3]").parseRoot()
        val out = Json.decodeAs(raw, List::class) as List<*>
        assertEquals(listOf(1L, 2L, 3L), out)
    }

    @Test fun decodeEmptyMapPassesThroughAsRawMap() {
        val raw = JsonParser("""{"a":1}""").parseRoot()
        val out = Json.decodeAs(raw, Map::class) as Map<*, *>
        assertEquals(mapOf("a" to 1L), out)
    }
}
