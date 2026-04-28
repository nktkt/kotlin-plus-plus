package dev.kpp.test

import dev.kpp.capability.builtins.Logger
import dev.kpp.capability.builtins.RecordingLogger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RecordingCapabilitiesEdgeTest {

    @Test
    fun proxy_equals_uses_identity_against_delegate() {
        val recorder = CapabilityRecorder()
        val delegate = RecordingLogger()
        val proxy: Logger = recordingCapability(Logger::class, delegate, recorder)
        // The Object.equals branch returns delegate === rawArgs[0]. Comparing the proxy
        // against the wrapped delegate via Java equals goes through the InvocationHandler
        // and returns true when the delegate is on the right-hand side.
        assertTrue(proxy.equals(delegate))
        assertFalse(proxy.equals(RecordingLogger())) // different instance
        assertFalse(proxy.equals("not a logger"))
        // No call should have been recorded for Object.equals.
        assertEquals(0, recorder.records().size)
    }

    @Test
    fun proxy_hashCode_returns_identity_hash_of_delegate() {
        val recorder = CapabilityRecorder()
        val delegate = RecordingLogger()
        val proxy: Logger = recordingCapability(Logger::class, delegate, recorder)
        // hashCode goes through the Object branch using System.identityHashCode(delegate).
        assertEquals(System.identityHashCode(delegate), proxy.hashCode())
        assertEquals(0, recorder.records().size)
    }

    @Test
    fun proxy_toString_describes_capability_type() {
        val recorder = CapabilityRecorder()
        val proxy: Logger = recordingCapability(Logger::class, RecordingLogger(), recorder)
        val text = proxy.toString()
        assertTrue(text.contains("RecordingProxy"), "expected proxy toString to mention RecordingProxy: $text")
        assertTrue(text.contains("Logger"), "expected proxy toString to mention Logger: $text")
        assertEquals(0, recorder.records().size)
    }
}
