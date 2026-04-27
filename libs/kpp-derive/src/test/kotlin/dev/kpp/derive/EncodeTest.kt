package dev.kpp.derive

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@DeriveJson
data class User(val id: Int, val name: String)

@DeriveJson(snakeCase = true)
data class CreateUser(val firstName: String, val lastName: String)

@DeriveJson
data class WithOverride(
    @JsonName("displayName") val display: String,
    val other: Int,
)

@DeriveJson
data class WithIgnore(
    val keep: String,
    @JsonIgnore val secret: String,
)

class PlainNoAnno(val x: Int)

class EncodeTest {
    @Test fun encodesNull() {
        assertEquals("null", Json.encode(null))
    }

    @Test fun encodesBoolean() {
        assertEquals("true", Json.encode(true))
        assertEquals("false", Json.encode(false))
    }

    @Test fun encodesNumbers() {
        assertEquals("1", Json.encode(1.toByte()))
        assertEquals("2", Json.encode(2.toShort()))
        assertEquals("3", Json.encode(3))
        assertEquals("4", Json.encode(4L))
        assertEquals("1.5", Json.encode(1.5f))
        assertEquals("2.5", Json.encode(2.5))
    }

    @Test fun encodesStringWithEscapes() {
        assertEquals("\"hi\"", Json.encode("hi"))
        assertEquals("\"a\\\"b\"", Json.encode("a\"b"))
        assertEquals("\"a\\\\b\"", Json.encode("a\\b"))
        assertEquals("\"a\\nb\"", Json.encode("a\nb"))
        assertEquals("\"a\\tb\"", Json.encode("a\tb"))
        assertEquals("\"a\\rb\"", Json.encode("a\rb"))
    }

    @Test fun encodesList() {
        assertEquals("[1,2,3]", Json.encode(listOf(1, 2, 3)))
    }

    @Test fun encodesMap() {
        assertEquals("""{"a":1}""", Json.encode(mapOf("a" to 1)))
    }

    @Test fun mapNonStringKeyThrows() {
        assertFailsWith<IllegalArgumentException> {
            Json.encode(mapOf(1 to "v"))
        }
    }

    @Test fun encodesDataClass() {
        assertEquals("""{"id":1,"name":"alice"}""", Json.encode(User(1, "alice")))
    }

    @Test fun encodesSnakeCase() {
        assertEquals(
            """{"first_name":"x","last_name":"y"}""",
            Json.encode(CreateUser("x", "y")),
        )
    }

    @Test fun encodesJsonNameOverride() {
        assertEquals(
            """{"displayName":"alice","other":7}""",
            Json.encode(WithOverride("alice", 7)),
        )
    }

    @Test fun encodesJsonIgnoreSkipped() {
        assertEquals("""{"keep":"k"}""", Json.encode(WithIgnore("k", "shh")))
    }

    @Test fun nonDeriveClassThrows() {
        assertFailsWith<IllegalArgumentException> {
            Json.encode(PlainNoAnno(1))
        }
    }
}
