package dev.kpp.samples.payment

import dev.kpp.core.KppError

sealed interface PaymentError : KppError {
    data object CardRejected : PaymentError
    data object NetworkUnavailable : PaymentError
    data class FraudSuspected(val score: Double) : PaymentError
}
