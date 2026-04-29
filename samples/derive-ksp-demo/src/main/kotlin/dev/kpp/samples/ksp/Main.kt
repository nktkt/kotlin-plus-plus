package dev.kpp.samples.ksp

import dev.kpp.derive.Json
import dev.kpp.secret.toSecret

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

    val c = Credentials("alice", "p@ss".toSecret())
    println("runtime:    " + Json.encode(c))
    println("generated:  " + c.toJsonGenerated())

    val cd = CredentialsDiagnostic("alice", "p@ss".toSecret())
    println("runtime:    " + Json.encode(cd))
    println("generated:  " + cd.toJsonGenerated())

    val t = Tags(
        labels = mapOf("env" to "prod", "tier" to "gold"),
        counts = mapOf("a" to 1, "b" to 2),
        nested = mapOf("home" to Address("1 First St", "Springfield", "00001")),
        nullableValues = mapOf("present" to "x", "absent" to null),
    )
    println("runtime:    " + Json.encode(t))
    println("generated:  " + t.toJsonGenerated())
}
