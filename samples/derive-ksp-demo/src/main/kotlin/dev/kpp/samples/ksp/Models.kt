package dev.kpp.samples.ksp

import dev.kpp.derive.DeriveJson
import dev.kpp.derive.JsonIgnore
import dev.kpp.derive.JsonName

@DeriveJson
data class Greeting(val message: String, val recipient: String, val priority: Int)

@DeriveJson(snakeCase = true)
data class Request(val userId: Int, val sessionToken: String, val isAdmin: Boolean)

@DeriveJson
data class Address(val street: String, val city: String, val zip: String)

@DeriveJson(snakeCase = true)
data class UserProfile(
    val id: Int,
    val displayName: String,
    @JsonName("email_address") val email: String,
    @JsonIgnore val internalNote: String,
    val nickname: String?,
    val tags: List<String>,
    val pastAddresses: List<Address>,
    val primaryAddress: Address?,
)

@DeriveJson
data class IntegerList(val values: List<Int>)
