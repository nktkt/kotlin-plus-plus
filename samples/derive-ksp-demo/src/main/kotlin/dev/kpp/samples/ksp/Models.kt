package dev.kpp.samples.ksp

import dev.kpp.derive.DeriveJson

@DeriveJson
data class Greeting(val message: String, val recipient: String, val priority: Int)

@DeriveJson(snakeCase = true)
data class Request(val userId: Int, val sessionToken: String, val isAdmin: Boolean)
