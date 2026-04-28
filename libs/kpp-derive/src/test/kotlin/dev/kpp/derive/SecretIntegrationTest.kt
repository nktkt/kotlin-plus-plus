package dev.kpp.derive

import dev.kpp.secret.Secret
import dev.kpp.secret.toSecret
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@DeriveJson
data class Login(val email: String, val password: Secret<String>)

@DeriveJson(allowSecrets = true)
data class DiagLogin(val email: String, val password: Secret<String>)

@DeriveJson
data class ApiKey(val token: Secret<ByteArray>)

class SecretIntegrationTest {
    @Test fun secret_field_encodes_as_redacted_by_default() {
        val text = Json.encode(Login("a@b", "p".toSecret()))
        assertEquals("""{"email":"a@b","password":"[REDACTED]"}""", text)
    }

    @Test fun secret_field_encodes_inner_value_when_allow_secrets_true() {
        val text = Json.encode(DiagLogin("a@b", "hunter2".toSecret()))
        assertEquals("""{"email":"a@b","password":"hunter2"}""", text)
    }

    @Test fun secret_bytearray_encodes_as_redacted() {
        // The token is 16 bytes; the JSON output must NOT reveal its length.
        val text = Json.encode(ApiKey(ByteArray(16) { it.toByte() }.toSecret()))
        assertEquals("""{"token":"[REDACTED]"}""", text)
        // Sanity: no digit from "16" or any byte value should appear.
        assertTrue(text.contains("[REDACTED]"))
        assertFalse(text.contains("16"))
    }
}
