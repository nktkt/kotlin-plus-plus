package dev.kpp.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class BindTest {

    sealed interface E : KppError {
        data object A : E
        data object B : E
    }

    @Test
    fun builderReturnsBoundValueWhenAllSucceed() {
        val r: Result<Int, E> = result {
            val a = ok(2).bind()
            val b = ok(3).bind()
            a + b
        }
        assertEquals(ok(5), r)
    }

    @Test
    fun bindShortCircuitsOnErr() {
        var afterErr = false
        val r: Result<Int, E> = result {
            val a = ok(1).bind()
            val b: Int = err<E>(E.A).bind()
            afterErr = true
            a + b
        }
        assertEquals(err(E.A), r)
        assertTrue(!afterErr)
    }

    @Test
    fun nestedResultBuildersDoNotCross() {
        val outer: Result<Int, E> = result {
            val inner: Result<Int, E> = result {
                err<E>(E.B).bind()
                999
            }
            assertIs<Result.Err<E>>(inner)
            42
        }
        assertEquals(ok(42), outer)
    }

    @Test
    fun nestedBuilderInnerSuccessOuterPropagates() {
        val r: Result<Int, E> = result {
            val inner = result<Int, E> { ok(10).bind() + 1 }
            val v = inner.bind()
            v * 2
        }
        assertEquals(ok(22), r)
    }

    @Test
    fun outerErrFromOuterBindStillWorks() {
        val r: Result<Int, E> = result {
            val inner = result<Int, E> { ok(7).bind() }
            inner.bind()
            err<E>(E.A).bind()
            -1
        }
        assertEquals(err(E.A), r)
    }

    @Test
    fun userThrowableIsNotConvertedToErr() {
        try {
            result<Int, E> {
                throw IllegalStateException("user bug")
            }
            error("should have thrown")
        } catch (e: IllegalStateException) {
            assertEquals("user bug", e.message)
        }
    }
}
