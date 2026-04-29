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
        // The decoder reconstructs `internalNote` from its default ("n/a"),
        // so we set it the same here to demonstrate round-trip equality.
        email = "alice@example.com",
        internalNote = "n/a",
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

    // ---- Decoder round-trip demonstration ----
    val addr = Address("221B Baker St", "London", "NW1")
    val addrText = Json.encode(addr)
    val addrDecoded = Address.fromJsonGenerated(addrText)
    println("addr round-trip: " + (addr == addrDecoded))

    val gText = Json.encode(g)
    val gDecoded = Greeting.fromJsonGenerated(gText)
    println("greeting round-trip: " + (g == gDecoded))

    val rText = Json.encode(r)
    val rDecoded = Request.fromJsonGenerated(rText)
    println("request round-trip: " + (r == rDecoded))

    val uText = Json.encode(u)
    val uDecoded = UserProfile.fromJsonGenerated(uText)
    println("user_profile round-trip: " + (u == uDecoded))

    val tText = Json.encode(t)
    val tDecoded = Tags.fromJsonGenerated(tText)
    println("tags round-trip: " + (t == tDecoded))

    val full = FullShape(
        name = "alpha",
        count = 7,
        total = 1234567890123L,
        ratio = 1.5,
        partial = 0.25f,
        tinyByte = 9,
        tinyShort = 300,
        flag = true,
        maybe = "yes",
        tags = listOf("a", "b"),
        addresses = listOf(addr),
        labels = mapOf("k" to "v"),
        home = addr,
        nullableHome = null,
        originalName = "kept",
        computed = "default",
    )
    val fullText = Json.encode(full)
    val fullDecoded = FullShape.fromJsonGenerated(fullText)
    println("full_shape round-trip: " + (full == fullDecoded))
}
