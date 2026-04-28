package dev.kpp.samples.http

import dev.kpp.capability.Capability
import dev.kpp.core.Result
import dev.kpp.core.err
import dev.kpp.core.ok
import java.util.UUID

interface UserRepository : Capability {
    suspend fun findById(id: UserId): Result<User, ApiError>
    suspend fun create(req: CreateUserRequest): Result<User, ApiError>
}

class InMemoryUserRepository : UserRepository {
    // Plain MutableMap guarded by the instance lock. Concurrent reads and writes
    // go through synchronized blocks; the sample stays in-process so a more
    // sophisticated structure would be over-engineering.
    private val store: MutableMap<String, User> = LinkedHashMap()

    override suspend fun findById(id: UserId): Result<User, ApiError> {
        val user = synchronized(store) { store[id.raw] }
        return user?.let { ok(it) } ?: err(ApiError.NotFound("user:${id.raw}"))
    }

    override suspend fun create(req: CreateUserRequest): Result<User, ApiError> {
        // Validation is enforced at the wire boundary by validateCreateUserRequest
        // (kpp-validation pipeline), so the repo focuses purely on persistence.
        val user = User(
            id = UUID.randomUUID().toString(),
            email = req.email,
            displayName = req.displayName,
        )
        synchronized(store) { store[user.id] = user }
        return ok(user)
    }

    // Test-only seam: insert a user with a known id so tests can assert
    // deterministic behaviour for GET fan-out.
    fun seed(user: User) {
        synchronized(store) { store[user.id] = user }
    }
}
