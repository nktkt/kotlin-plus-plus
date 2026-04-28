package dev.kpp.samples.http

import dev.kpp.core.Result
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

class CreateUserValidatorTest {

    @Test
    fun valid_body_returns_ok() {
        val body = mapOf(
            "email" to "alice@example.com",
            "display_name" to "Alice",
            "api_key" to "alice-key-001",
        )
        when (val r = validateCreateUserRequest(body)) {
            is Result.Ok -> {
                assertEquals("alice@example.com", r.value.email)
                assertEquals("Alice", r.value.displayName)
                assertEquals("alice-key-001", r.value.apiKey.expose())
            }
            is Result.Err -> fail("expected Ok but got Err: ${r.error.toList()}")
        }
    }

    @Test
    fun invalid_email_returns_err_with_field_email() {
        val body = mapOf(
            "email" to "not-an-email",
            "display_name" to "Alice",
            "api_key" to "alice-key-001",
        )
        when (val r = validateCreateUserRequest(body)) {
            is Result.Ok -> fail("expected Err but got Ok")
            is Result.Err -> {
                val errors = r.error.toList()
                assertTrue(errors.any { it.field == "email" }, "expected an email error: $errors")
                assertTrue(
                    errors.none { it.field == "display_name" || it.field == "api_key" },
                    "expected only email errors but got: $errors",
                )
            }
        }
    }

    @Test
    fun multiple_errors_accumulate() {
        val body = mapOf(
            "email" to "",
            "display_name" to "",
            "api_key" to "x",
        )
        when (val r = validateCreateUserRequest(body)) {
            is Result.Ok -> fail("expected Err but got Ok")
            is Result.Err -> {
                val errors = r.error.toList()
                assertTrue(errors.size >= 3, "expected >= 3 entries, got: $errors")
                assertTrue(errors.any { it.field == "email" }, "missing email entry: $errors")
                assertTrue(errors.any { it.field == "display_name" }, "missing display_name entry: $errors")
                assertTrue(errors.any { it.field == "api_key" }, "missing api_key entry: $errors")
            }
        }
    }

    @Test
    fun apiKey_too_short_is_caught() {
        val body = mapOf(
            "email" to "alice@example.com",
            "display_name" to "Alice",
            "api_key" to "short",
        )
        when (val r = validateCreateUserRequest(body)) {
            is Result.Ok -> fail("expected Err but got Ok")
            is Result.Err -> {
                val errors = r.error.toList()
                assertTrue(
                    errors.any { it.field == "api_key" && it.code.startsWith("length:") },
                    "expected a length error on api_key, got: $errors",
                )
            }
        }
    }
}
