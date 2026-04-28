package dev.kpp.validation

import dev.kpp.core.Result
import dev.kpp.core.err
import dev.kpp.core.ok

/**
 * Validate an object with per-field validators, accumulating field errors.
 *
 * Example:
 *
 *   data class Profile(val name: String, val age: Int)
 *
 *   val profile: Result<Profile, NonEmptyList<FieldError>> = validate {
 *       val name = field("name", input.name, nonBlankString and lengthBetween(1, 32))
 *       val age = field("age", input.age, rangeInt(0, 150))
 *       Profile(name, age)
 *   }
 */
class ValidationScope @PublishedApi internal constructor() {
    @PublishedApi internal val collectedErrors: MutableList<FieldError> = mutableListOf()

    fun <T> field(name: String, value: T, validator: Validator<T, T, String>): T {
        return when (val r = validator.validate(value)) {
            is Result.Ok -> r.value
            is Result.Err -> {
                collectedErrors.addAll(r.error.map { code -> FieldError(name, code) })
                // Return the input so the builder body can keep running and every
                // subsequent field still gets validated; the scope's collected errors
                // dominate the final outcome of `validate { ... }`.
                value
            }
        }
    }
}

data class FieldError(val field: String, val code: String)

inline fun <T> validate(block: ValidationScope.() -> T): Result<T, NonEmptyList<FieldError>> {
    val scope = ValidationScope()
    val output = scope.block()
    val errors = scope.collectedErrors.toNonEmptyListOrNull()
    return if (errors == null) ok(output) else err(errors)
}
