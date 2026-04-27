package dev.kpp.samples.http

import dev.kpp.capability.Capabilities
import dev.kpp.core.Result
import dev.kpp.core.err
import dev.kpp.core.ok
import dev.kpp.derive.Json
import dev.kpp.samples.http.handlers.createUser
import dev.kpp.samples.http.handlers.getUser
import dev.kpp.samples.http.handlers.getUsersBatch

data class Request(
    val method: String,
    val path: String,
    val query: Map<String, String>,
    val body: String?,
)

data class Response(
    val status: Int,
    val body: String,
)

suspend fun Capabilities.handle(req: Request): Response = when {
    req.method == "GET" && req.path == "/users" -> handleListUsers(req)
    req.method == "GET" && req.path.startsWith("/users/") -> {
        val id = req.path.removePrefix("/users/")
        handleGetUser(UserId(id))
    }
    req.method == "POST" && req.path == "/users" -> handleCreateUser(req)
    else -> errorResponse(ApiError.NotFound("route:${req.method} ${req.path}"))
}

private suspend fun Capabilities.handleGetUser(id: UserId): Response =
    when (val r = getUser(id)) {
        is Result.Ok -> Response(200, Json.encode(r.value))
        is Result.Err -> errorResponse(r.error)
    }

private suspend fun Capabilities.handleListUsers(req: Request): Response {
    val raw = req.query["ids"].orEmpty()
    val ids = raw.split(',').filter { it.isNotEmpty() }.map { UserId(it) }
    return when (val r = getUsersBatch(ids)) {
        is Result.Ok -> {
            // The wire boundary uses plain List<User>: Json.encode does not yet
            // recognize ImmutableList. The handler still propagates the
            // ImmutableList through the call path.
            Response(200, Json.encode(UsersResponse(users = r.value.toList())))
        }
        is Result.Err -> errorResponse(r.error)
    }
}

private suspend fun Capabilities.handleCreateUser(req: Request): Response {
    val parsed = parseCreateUserRequest(req.body)
    return when (parsed) {
        is Result.Ok -> when (val created = createUser(parsed.value)) {
            is Result.Ok -> Response(201, Json.encode(created.value))
            is Result.Err -> errorResponse(created.error)
        }
        is Result.Err -> errorResponse(parsed.error)
    }
}

private fun parseCreateUserRequest(body: String?): Result<CreateUserRequest, ApiError> {
    if (body == null) return err(ApiError.BadJson("missing body"))
    return runCatching { Json.decode<CreateUserRequest>(body) }
        .fold(
            { ok(it) },
            { err(ApiError.BadJson(it.message ?: "parse failed")) },
        )
}

private fun errorResponse(error: ApiError): Response {
    val status = when (error) {
        is ApiError.NotFound -> 404
        is ApiError.Validation -> 400
        is ApiError.BadJson -> 400
        // 502 because Upstream means a dependency we called returned a fault.
        is ApiError.Upstream -> 502
        is ApiError.Timeout -> 504
    }
    val payload = mapOf(
        "error" to discriminatorOf(error),
        "details" to detailsOf(error),
    )
    return Response(status, Json.encode(payload))
}

private fun discriminatorOf(error: ApiError): String = when (error) {
    is ApiError.NotFound -> "NotFound"
    is ApiError.Validation -> "Validation"
    is ApiError.BadJson -> "BadJson"
    is ApiError.Upstream -> "Upstream"
    is ApiError.Timeout -> "Timeout"
}

private fun detailsOf(error: ApiError): Map<String, String> = when (error) {
    is ApiError.NotFound -> mapOf("resource" to error.resource)
    is ApiError.Validation -> mapOf("field" to error.field, "reason" to error.reason)
    is ApiError.BadJson -> mapOf("message" to error.message)
    is ApiError.Upstream -> mapOf("cause" to error.cause)
    is ApiError.Timeout -> emptyMap()
}
