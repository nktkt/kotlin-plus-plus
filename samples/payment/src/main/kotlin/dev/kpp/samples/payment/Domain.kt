package dev.kpp.samples.payment

import java.time.Instant

@JvmInline
value class UserId(val raw: String)

@JvmInline
value class CardToken(val raw: String)

data class Money(val amountMinor: Long, val currency: String)

data class PaymentRequest(val userId: UserId, val amount: Money, val card: CardToken)

data class Receipt(
    val transactionId: String,
    val userId: UserId,
    val amount: Money,
    val at: Instant,
)
