package dev.kpp.samples.payment

import dev.kpp.capability.Capability
import dev.kpp.core.Result

interface PaymentGateway : Capability {
    fun charge(card: CardToken, amount: Money): Result<Receipt, PaymentError>
}

interface AuditLog : Capability {
    fun record(message: String)
}

class StubPaymentGateway(
    private val behavior: (CardToken, Money) -> Result<Receipt, PaymentError>,
) : PaymentGateway {
    override fun charge(card: CardToken, amount: Money): Result<Receipt, PaymentError> =
        behavior(card, amount)
}

class InMemoryAuditLog : AuditLog {
    private val backing = mutableListOf<String>()
    val entries: List<String> get() = backing.toList()

    override fun record(message: String) {
        backing.add(message)
    }
}
