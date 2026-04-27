package dev.kpp.derive

/**
 * Marker for "Kotlin++ would generate a JSON serializer at compile time".
 * Today: dev.kpp.derive.Json reads this via runtime reflection.
 * Tomorrow: a KSP/FIR processor reads it at build time and emits typed code.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class DeriveJson(
    val snakeCase: Boolean = false,
)

@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
annotation class JsonName(val value: String)

@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
annotation class JsonIgnore
