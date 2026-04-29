package dev.kpp.samples.ksp

import dev.kpp.derive.DeriveJson
import dev.kpp.derive.JsonIgnore
import dev.kpp.derive.JsonName
import dev.kpp.secret.Secret

@DeriveJson
data class Greeting(val message: String, val recipient: String, val priority: Int) {
    companion object
}

@DeriveJson(snakeCase = true)
data class Request(val userId: Int, val sessionToken: String, val isAdmin: Boolean) {
    companion object
}

@DeriveJson
data class Address(val street: String, val city: String, val zip: String) {
    companion object
}

// `internalNote` has @JsonIgnore but also a default — the decoder uses
// named-arg ctor invocation so the default fires for ignored params.
@DeriveJson(snakeCase = true)
data class UserProfile(
    val id: Int,
    val displayName: String,
    @JsonName("email_address") val email: String,
    @JsonIgnore val internalNote: String = "n/a",
    val nickname: String?,
    val tags: List<String>,
    val pastAddresses: List<Address>,
    val primaryAddress: Address?,
) {
    companion object
}

@DeriveJson
data class IntegerList(val values: List<Int>) {
    companion object
}

// No companion — encoder still works; decoder is skipped (with KSP warning)
// because we can't add `Companion.fromJsonGenerated`. Also has Secret<*> so
// even with a companion the decoder would refuse.
@DeriveJson
data class Credentials(
    val username: String,
    val password: Secret<String>,
)

@DeriveJson(allowSecrets = true)
data class CredentialsDiagnostic(
    val username: String,
    val password: Secret<String>,
)

@DeriveJson
data class ApiToken(val token: Secret<ByteArray>)

@DeriveJson
data class OptionalCredentials(
    val username: String,
    val password: Secret<String>?,
)

@DeriveJson(snakeCase = true)
data class Tags(
    val labels: Map<String, String>,
    val counts: Map<String, Int>,
    val nested: Map<String, Address>,
    val nullableValues: Map<String, String?>,
) {
    companion object
}

@DeriveJson
data class OptionalTags(val labels: Map<String, String>?) {
    companion object
}

// Exercises every decoder-supported shape WITHOUT secrets:
//   - Primitive(String, Int, Long, Boolean, Double, Float, Short, Byte)
//   - Nullable(String)
//   - ListOf(String)
//   - ListOf(NestedDeriveJson)
//   - MapOf(String -> String)
//   - NestedDeriveJson
//   - @JsonName override
//   - @JsonIgnore with default
@DeriveJson(snakeCase = true)
data class FullShape(
    val name: String,
    val count: Int,
    val total: Long,
    val ratio: Double,
    val partial: Float,
    val tinyByte: Byte,
    val tinyShort: Short,
    val flag: Boolean,
    val maybe: String?,
    val tags: List<String>,
    val addresses: List<Address>,
    val labels: Map<String, String>,
    val home: Address,
    val nullableHome: Address?,
    @JsonName("renamed") val originalName: String,
    @JsonIgnore val computed: String = "default",
) {
    companion object
}
