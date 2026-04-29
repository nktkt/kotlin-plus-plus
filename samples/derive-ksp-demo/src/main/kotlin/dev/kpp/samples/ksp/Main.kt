package dev.kpp.samples.ksp

import dev.kpp.derive.Json

fun main() {
    val g = Greeting("hello", "world", 1)
    println("runtime:    " + Json.encode(g))
    println("generated:  " + g.toJsonGenerated())

    val r = Request(42, "abc", true)
    println("runtime:    " + Json.encode(r))
    println("generated:  " + r.toJsonGenerated())

    val u = UserProfile(
        id = 7,
        displayName = "Alice",
        email = "alice@example.com",
        internalNote = "vip",
        nickname = "ali",
        tags = listOf("admin", "beta"),
        pastAddresses = listOf(
            Address("1 First St", "Springfield", "00001"),
            Address("2 Second Ave", "Shelbyville", "00002"),
        ),
        primaryAddress = Address("3 Third Blvd", "Capital City", "00003"),
    )
    println("runtime:    " + Json.encode(u))
    println("generated:  " + u.toJsonGenerated())
}
