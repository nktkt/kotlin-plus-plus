package dev.kpp.samples.http

import dev.kpp.core.KppError

sealed interface ApiError : KppError {
    data class NotFound(val resource: String) : ApiError
    data class Validation(val field: String, val reason: String) : ApiError
    data class BadJson(val message: String) : ApiError
    data class Upstream(val cause: String) : ApiError
    data object Timeout : ApiError
}
