package dev.kpp.samples.payment

import dev.kpp.capability.builtins.ConsoleLogger
import dev.kpp.capability.builtins.SystemClock
import dev.kpp.capability.withCapabilities
import dev.kpp.core.Result

fun main() {
    val clock = SystemClock()
    val logger = ConsoleLogger()

    val successGateway = StubPaymentGateway { _, amount ->
        Result.Ok(
            Receipt(
                transactionId = "tx-1001",
                userId = UserId("u-42"),
                amount = amount,
                at = clock.now(),
            ),
        )
    }
    val rejectingGateway = StubPaymentGateway { _, _ ->
        Result.Err(PaymentError.CardRejected)
    }

    val request = PaymentRequest(
        userId = UserId("u-42"),
        amount = Money(amountMinor = 1999, currency = "USD"),
        card = CardToken("tok_visa"),
    )

    println("--- success flow ---")
    withCapabilities(successGateway, InMemoryAuditLog(), logger) {
        printOutcome(pay(request))
    }

    println("--- card rejected flow ---")
    withCapabilities(rejectingGateway, InMemoryAuditLog(), logger) {
        printOutcome(pay(request))
    }
}

private fun printOutcome(outcome: Result<Receipt, PaymentError>) {
    when (outcome) {
        is Result.Ok -> {
            val r = outcome.value
            println("OK tx=${r.transactionId} amount=${r.amount.amountMinor} ${r.amount.currency} at=${r.at}")
        }
        is Result.Err -> when (val e = outcome.error) {
            is PaymentError.CardRejected -> println("ERR card rejected")
            is PaymentError.NetworkUnavailable -> println("ERR network unavailable")
            is PaymentError.FraudSuspected -> println("ERR fraud suspected score=${e.score}")
        }
    }
}