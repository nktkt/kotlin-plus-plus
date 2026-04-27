package dev.kpp.samples.payment

import dev.kpp.capability.builtins.FixedClock
import dev.kpp.capability.builtins.RecordingLogger
import dev.kpp.capability.withCapabilities
import dev.kpp.core.Result
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

class PayTest {

    private val request = PaymentRequest(
        userId = UserId("u-1"),
        amount = Money(amountMinor = 4200, currency = "USD"),
        card = CardToken("tok_test"),
    )

    @Test
    fun success_path() {
        val clock = FixedClock(Instant.parse("2026-04-26T10:00:00Z"))
        val expected = Receipt(
            transactionId = "tx-success",
            userId = request.userId,
            amount = request.amount,
            at = clock.now(),
        )
        val gateway = StubPaymentGateway { _, _ -> Result.Ok(expected) }
        val audit = InMemoryAuditLog()
        val logger = RecordingLogger()

        val outcome = withCapabilities(gateway, audit, logger, clock) {
            pay(request)
        }

        when (outcome) {
            is Result.Ok -> assertEquals(expected, outcome.value)
            is Result.Err -> fail("expected Ok but got Err(${outcome.error})")
        }
        assertEquals(1, audit.entries.size)
        assertTrue(audit.entries[0].contains("tx-success"))
    }

    @Test
    fun card_rejected_short_circuits_audit() {
        val gateway = StubPaymentGateway { _, _ -> Result.Err(PaymentError.CardRejected) }
        val audit = InMemoryAuditLog()
        val logger = RecordingLogger()

        val outcome = withCapabilities(gateway, audit, logger) {
            pay(request)
        }

        when (outcome) {
            is Result.Ok -> fail("expected Err(CardRejected) but got Ok(${outcome.value})")
            is Result.Err -> assertEquals(PaymentError.CardRejected, outcome.error)
        }
        // bind() must short-circuit before any audit/log writes happen.
        assertTrue(audit.entries.isEmpty(), "audit should be empty on early failure")
    }

    @Test
    fun fraud_path_propagates_score() {
        val gateway = StubPaymentGateway { _, _ -> Result.Err(PaymentError.FraudSuspected(0.91)) }
        val audit = InMemoryAuditLog()
        val logger = RecordingLogger()

        val outcome = withCapabilities(gateway, audit, logger) {
            pay(request)
        }

        when (outcome) {
            is Result.Ok -> fail("expected Err(FraudSuspected) but got Ok(${outcome.value})")
            is Result.Err -> {
                val err = outcome.error
                assertTrue(err is PaymentError.FraudSuspected, "error should be FraudSuspected")
                assertEquals(0.91, err.score)
            }
        }
        assertTrue(audit.entries.isEmpty())
    }
}
