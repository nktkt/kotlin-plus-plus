package dev.kpp.samples.http

import dev.kpp.derive.Json
import dev.kpp.secret.toSecret
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AuditEntryTest {

    private val key = "caller-key-9b1f"

    @Test
    fun audit_entry_redacts_api_key_in_json() {
        val entry = AuditEntry(
            timestamp = "2026-04-26T00:00:00Z",
            event = "user.created",
            userId = "u-1",
            callerApiKey = key.toSecret(),
        )
        val json = Json.encode(entry)
        assertTrue(
            json.contains("\"caller_api_key\":\"[REDACTED]\""),
            "expected redacted marker, got: $json",
        )
        assertFalse(json.contains(key), "secret key leaked into JSON: $json")
    }

    @Test
    fun diagnostic_entry_reveals_api_key_when_allow_secrets_true() {
        val entry = AuditEntryDiagnostic(
            timestamp = "2026-04-26T00:00:00Z",
            event = "user.created",
            userId = "u-1",
            callerApiKey = key.toSecret(),
        )
        val json = Json.encode(entry)
        assertTrue(json.contains("\"caller_api_key\":\"$key\""), "expected raw key, got: $json")
        assertFalse(
            json.contains("[REDACTED]"),
            "diagnostic encoding should not redact, got: $json",
        )
    }
}
