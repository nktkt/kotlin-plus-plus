package dev.kpp.samples.ksp

import dev.kpp.derive.Json

fun main() {
    val g = Greeting("hello", "world", 1)
    println("runtime:    " + Json.encode(g))
    println("generated:  " + g.toJsonGenerated())

    val r = Request(42, "abc", true)
    println("runtime:    " + Json.encode(r))
    println("generated:  " + r.toJsonGenerated())
}
