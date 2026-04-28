package dev.kpp.validation

import dev.kpp.core.Result
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BuilderTest {

    private data class Profile(val name: String, val age: Int)
    private data class ProfileInput(val name: String, val age: Int)

    @Test fun validate_returns_ok_when_all_fields_pass() {
        val input = ProfileInput("Alice", 30)
        val r: Result<Profile, NonEmptyList<FieldError>> = validate {
            val name = field("name", input.name, nonBlankString and lengthBetween(1, 32))
            val age = field("age", input.age, rangeInt(0, 150))
            Profile(name, age)
        }
        assertTrue(r is Result.Ok)
        assertEquals(Profile("Alice", 30), r.value)
    }

    @Test fun validate_returns_err_with_field_errors_when_one_field_fails() {
        val input = ProfileInput("", 30)
        val r = validate {
            val name = field("name", input.name, nonBlankString and lengthBetween(1, 32))
            val age = field("age", input.age, rangeInt(0, 150))
            Profile(name, age)
        }
        assertTrue(r is Result.Err)
        val errors = r.error.toList()
        assertTrue(errors.any { it.field == "name" && it.code == "blank" })
        assertTrue(errors.any { it.field == "name" && it.code.startsWith("length:") })
    }

    @Test fun validate_accumulates_errors_from_multiple_fields() {
        val input = ProfileInput("", -5)
        val r = validate {
            val name = field("name", input.name, nonBlankString)
            val age = field("age", input.age, rangeInt(0, 150))
            Profile(name, age)
        }
        assertTrue(r is Result.Err)
        val errors = r.error.toList()
        assertEquals(2, errors.size)
        assertTrue(errors.any { it.field == "name" && it.code == "blank" })
        assertTrue(errors.any { it.field == "age" && it.code.startsWith("out-of-range:") })
    }
}
