package dev.kpp.samples.http

import dev.kpp.core.Result
import dev.kpp.secret.toSecret
import dev.kpp.validation.FieldError
import dev.kpp.validation.NonEmptyList
import dev.kpp.validation.and
import dev.kpp.validation.email
import dev.kpp.validation.lengthBetween
import dev.kpp.validation.nonBlankString
import dev.kpp.validation.nonEmptyString
import dev.kpp.validation.validate

/**
 * Lift a parsed JSON body (Map<String, Any?>) into a CreateUserRequest,
 * accumulating per-field errors. Wire fields are snake_case ("api_key");
 * the in-memory representation wraps api_key in Secret<String>.
 *
 * Returns Ok(request) if every field passes its validators, or
 * Err(NonEmptyList<FieldError>) listing every offence — the caller does
 * not get just the first failure.
 */
fun validateCreateUserRequest(body: Map<String, Any?>): Result<CreateUserRequest, NonEmptyList<FieldError>> =
    validate {
        val emailValue = field(
            "email",
            (body["email"] as? String) ?: "",
            nonEmptyString and lengthBetween(3, 254) and email,
        )
        val displayNameValue = field(
            "display_name",
            (body["display_name"] as? String) ?: "",
            nonBlankString and lengthBetween(1, 64),
        )
        val apiKeyRaw = field(
            "api_key",
            (body["api_key"] as? String) ?: "",
            nonBlankString and lengthBetween(8, 128),
        )
        CreateUserRequest(emailValue, displayNameValue, apiKeyRaw.toSecret())
    }
