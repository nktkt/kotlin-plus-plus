package dev.kpp.derive

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@DeriveJson
data class Mixed(
    val id: Int,
    val title: String,
    val active: Boolean,
    val ratio: Double,
    val tags: List<Int>,
)

@DeriveJson
data class Order(val id: Int, val customer: User)

class DecodeTest {
    @Test fun roundTripMixed() {
        val original = Mixed(
            id = 42,
            title = "hello",
            active = true,
            ratio = 0.5,
            tags = listOf(1, 2, 3),
        )
        val text = Json.encode(original)
        val decoded = Json.decode<Mixed>(text)
        assertEquals(original, decoded)
    }

    @Test fun decodesTopLevelMap() {
        val text = """{"a":1,"b":"x","c":true}"""
        val m = Json.decode<Map<String, Any?>>(text)
        assertEquals(1L, m["a"])
        assertEquals("x", m["b"])
        assertEquals(true, m["c"])
    }

    @Test fun decodesNestedDeriveClass() {
        val o = Order(1, User(2, "alice"))
        val text = Json.encode(o)
        val decoded = Json.decode<Order>(text)
        assertEquals(o, decoded)
    }

    @Test fun whitespaceTolerant() {
        val text = """{"id" : 1 , "name" : "x"}"""
        val u = Json.decode<User>(text)
        assertEquals(User(1, "x"), u)
    }

    @Test fun decodesTopLevelList() {
        val text = "[1, 2, 3]"
        val l = Json.decode<List<Any?>>(text)
        assertTrue(l == listOf(1L, 2L, 3L))
    }
}
