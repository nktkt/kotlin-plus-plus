package dev.kpp.concurrent

import dev.kpp.core.Result
import dev.kpp.core.err
import dev.kpp.core.ok
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class SequenceTest {

    @Test
    fun allOkProducesOkList() {
        val r = listOf(ok<Int>(1), ok(2), ok(3)).sequence()
        assertIs<Result.Ok<List<Int>>>(r)
        assertEquals(listOf(1, 2, 3), r.value)
    }

    @Test
    fun firstErrShortCircuitsSequence() {
        val list: List<Result<Int, String>> = listOf(ok(1), err("first"), ok(3), err("second"))
        val r = list.sequence()
        assertIs<Result.Err<String>>(r)
        assertEquals("first", r.error)
    }

    @Test
    fun sequenceAccumulatingCollectsAllErrors() {
        val list: List<Result<Int, String>> = listOf(ok(1), err("a"), ok(2), err("b"))
        val r = list.sequenceAccumulating()
        assertIs<Result.Err<List<String>>>(r)
        assertEquals(listOf("a", "b"), r.error)
    }

    @Test
    fun sequenceAccumulatingAllOk() {
        val list: List<Result<Int, String>> = listOf(ok(1), ok(2))
        val r = list.sequenceAccumulating()
        assertIs<Result.Ok<List<Int>>>(r)
        assertEquals(listOf(1, 2), r.value)
    }
}
