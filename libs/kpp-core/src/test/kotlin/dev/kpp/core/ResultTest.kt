package dev.kpp.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ResultTest {

    sealed interface DemoError : KppError {
        data object NotFound : DemoError
        data class Invalid(val reason: String) : DemoError
    }

    @Test
    fun okConstructsOk() {
        val r = ok(42)
        assertIs<Result.Ok<Int>>(r)
        assertEquals(42, r.value)
    }

    @Test
    fun errConstructsErr() {
        val r: Result<Int, DemoError> = err(DemoError.NotFound)
        assertIs<Result.Err<DemoError>>(r)
        assertEquals(DemoError.NotFound, r.error)
    }

    @Test
    fun mapTransformsOk() {
        val r: Result<Int, DemoError> = ok(2)
        assertEquals(ok(4), r.map { it * 2 })
    }

    @Test
    fun mapPassesThroughErr() {
        val r: Result<Int, DemoError> = err(DemoError.NotFound)
        assertEquals(err(DemoError.NotFound), r.map { it * 2 })
    }

    @Test
    fun flatMapChains() {
        val r: Result<Int, DemoError> = ok(3)
        val out = r.flatMap { if (it > 0) ok(it + 1) else err(DemoError.Invalid("neg")) }
        assertEquals(ok(4), out)
    }

    @Test
    fun flatMapShortCircuitsOnErr() {
        val r: Result<Int, DemoError> = err(DemoError.NotFound)
        var called = false
        val out = r.flatMap { called = true; ok(it + 1) }
        assertEquals(err(DemoError.NotFound), out)
        assertTrue(!called)
    }

    @Test
    fun mapErrTransformsError() {
        val r: Result<Int, DemoError> = err(DemoError.Invalid("x"))
        val mapped: Result<Int, String> = r.mapErr { it.toString() }
        assertIs<Result.Err<String>>(mapped)
    }

    @Test
    fun getOrElseReturnsFallback() {
        val r: Result<Int, DemoError> = err(DemoError.NotFound)
        assertEquals(-1, r.getOrElse { -1 })
    }

    @Test
    fun getOrElseReturnsValue() {
        val r: Result<Int, DemoError> = ok(7)
        assertEquals(7, r.getOrElse { -1 })
    }

    @Test
    fun recoverReplacesErr() {
        val r: Result<Int, DemoError> = err(DemoError.NotFound)
        assertEquals(ok(0), r.recover { ok(0) })
    }

    @Test
    fun recoverLeavesOk() {
        val r: Result<Int, DemoError> = ok(5)
        assertEquals(ok(5), r.recover { ok(0) })
    }

    @Test
    fun orFailWrapsNonNull() {
        val v: String? = "hi"
        assertEquals(ok("hi"), v.orFail { DemoError.NotFound })
    }

    @Test
    fun orFailErrsOnNull() {
        val v: String? = null
        assertEquals(err(DemoError.NotFound), v.orFail { DemoError.NotFound })
    }

    @Test
    fun getOrThrowReturnsValue() {
        val r: Result<Int, DemoError> = ok(9)
        assertEquals(9, r.getOrThrow())
    }

    @Test
    fun getOrThrowThrowsForErr() {
        val r: Result<Int, DemoError> = err(DemoError.NotFound)
        val ex = assertFailsWith<KppErrorException> { r.getOrThrow() }
        assertEquals(DemoError.NotFound, ex.kppError)
    }

    @Test
    fun runCatchingTypedConvertsKnownException() {
        val r: Result<Int, DemoError> = runCatchingTyped<Int, IllegalStateException, DemoError>(
            transform = { DemoError.Invalid(it.message ?: "") },
        ) { throw IllegalStateException("boom") }
        assertEquals(err(DemoError.Invalid("boom")), r)
    }

    @Test
    fun runCatchingTypedRethrowsUnknown() {
        assertFailsWith<IllegalArgumentException> {
            runCatchingTyped<Int, IllegalStateException, DemoError>(
                transform = { DemoError.Invalid(it.message ?: "") },
            ) { throw IllegalArgumentException("nope") }
        }
    }

    @Test
    fun runCatchingTypedReturnsOk() {
        val r: Result<Int, DemoError> = runCatchingTyped<Int, IllegalStateException, DemoError>(
            transform = { DemoError.Invalid(it.message ?: "") },
        ) { 1 + 1 }
        assertEquals(ok(2), r)
    }
}
