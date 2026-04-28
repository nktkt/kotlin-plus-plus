@file:Suppress("KPP017") // recording-proxy spy needs KClass to key per-capability records — replaced by FIR plugin in Phase 5

package dev.kpp.test

import dev.kpp.capability.Capability
import java.lang.reflect.InvocationHandler
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import kotlin.reflect.KClass

data class Call(val capability: KClass<out Capability>, val method: String, val args: List<Any?>)

/**
 * A spy capability bag. For each registered Capability subtype, records the
 * sequence of (method, args) calls made through it. Useful for verifying
 * "the audit log was called once with this message".
 */
class CapabilityRecorder {
    private val lock = Any()
    private val buffer: MutableList<Call> = mutableListOf()

    fun records(): List<Call> = synchronized(lock) { buffer.toList() }

    fun recordsFor(capability: KClass<out Capability>): List<Call> = synchronized(lock) {
        buffer.filter { it.capability == capability }
    }

    fun reset() {
        synchronized(lock) { buffer.clear() }
    }

    internal fun record(capability: KClass<out Capability>, method: String, args: List<Any?>) {
        synchronized(lock) { buffer += Call(capability, method, args) }
    }
}

/**
 * Wrap a Capability instance in a JDK dynamic proxy that delegates to [delegate]
 * but records every interface-method invocation on [recorder].
 */
fun <T : Capability> recordingCapability(
    capabilityType: KClass<T>,
    delegate: T,
    recorder: CapabilityRecorder,
): T {
    require(capabilityType.java.isInterface) {
        "recordingCapability requires an interface type, got ${capabilityType.qualifiedName}"
    }
    val handler = InvocationHandler { _, method: Method, rawArgs: Array<out Any?>? ->
        // Filter Object methods (equals/hashCode/toString) so they aren't recorded as capability calls.
        if (method.declaringClass == Any::class.java) {
            return@InvocationHandler when (method.name) {
                "equals" -> delegate === rawArgs?.get(0)
                "hashCode" -> System.identityHashCode(delegate)
                "toString" -> "RecordingProxy(${capabilityType.qualifiedName})"
                else -> method.invoke(delegate, *(rawArgs ?: emptyArray()))
            }
        }
        val argList: List<Any?> = rawArgs?.toList() ?: emptyList()
        // Record BEFORE delegating so an exception thrown by the delegate is still captured.
        recorder.record(capabilityType, method.name, argList)
        try {
            method.invoke(delegate, *(rawArgs ?: emptyArray()))
        } catch (e: InvocationTargetException) {
            throw e.targetException
        }
    }
    @Suppress("UNCHECKED_CAST")
    val proxy = Proxy.newProxyInstance(
        capabilityType.java.classLoader,
        arrayOf<Class<*>>(capabilityType.java),
        handler,
    ) as T
    return proxy
}
