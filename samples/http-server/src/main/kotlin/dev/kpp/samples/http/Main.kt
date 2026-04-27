package dev.kpp.samples.http

import dev.kpp.capability.builtins.ConsoleLogger
import dev.kpp.capability.builtins.SystemClock
import dev.kpp.capability.withCapabilities
import dev.kpp.derive.Json
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    withCapabilities(InMemoryUserRepository(), ConsoleLogger(), SystemClock()) {
        // 1. Create alice
        val createAlice = Request(
            method = "POST",
            path = "/users",
            query = emptyMap(),
            body = """{"email":"alice@example.com","display_name":"Alice"}""",
        )
        val aliceResp = handle(createAlice)
        printResponse("POST /users (alice)", aliceResp)
        val alice = Json.decode<User>(aliceResp.body)

        // 2. Successful GET by id
        val getAlice = Request("GET", "/users/${alice.id}", emptyMap(), null)
        printResponse("GET /users/${alice.id}", handle(getAlice))

        // 3. Failing GET to demonstrate the 404 path
        val getMissing = Request("GET", "/users/missing", emptyMap(), null)
        printResponse("GET /users/missing", handle(getMissing))

        // 4. Seed three users and fan out via parallelMap
        val ids = listOf("u1", "u2", "u3").map {
            val resp = handle(
                Request(
                    method = "POST",
                    path = "/users",
                    query = emptyMap(),
                    body = """{"email":"$it@example.com","display_name":"User $it"}""",
                ),
            )
            Json.decode<User>(resp.body).id
        }
        val batch = Request(
            method = "GET",
            path = "/users",
            query = mapOf("ids" to ids.joinToString(",")),
            body = null,
        )
        printResponse("GET /users?ids=...", handle(batch))

        // 5. Validation error path
        val invalid = Request(
            method = "POST",
            path = "/users",
            query = emptyMap(),
            body = """{"email":"bad","display_name":""}""",
        )
        printResponse("POST /users (invalid)", handle(invalid))
    }
}

private fun printResponse(label: String, resp: Response) {
    println("=== $label -> ${resp.status}")
    println(resp.body)
}
