package dev.kpp.samples.payment

import dev.kpp.capability.Capabilities
import dev.kpp.capability.builtins.Logger
import dev.kpp.core.Result
import dev.kpp.core.result

// Extension on Capabilities emulates Kotlin++ context parameters: every cap lookup
// is resolved from the receiver, so the function reads as if caps were ambient.
fun Capabilities.pay(request: PaymentRequest): Result<Receipt, PaymentError> = result {
    val gateway = get<PaymentGateway>()
    val audit = get<AuditLog>()
    val logger = getOrNull<Logger>()

    val receipt = gateway.charge(request.card, request.amount).bind()
    audit.record("payment succeeded: user=${request.userId.raw} tx=${receipt.transactionId}")
    logger?.info("paid ${request.amount.amountMinor} ${request.amount.currency}")
    receipt
}
