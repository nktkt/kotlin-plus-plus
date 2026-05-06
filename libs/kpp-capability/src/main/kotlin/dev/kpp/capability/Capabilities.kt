@file:Suppress("KPP017") // reflection-based capability dispatch — replaced by FIR plugin in Phase 5

package dev.kpp.capability

import kotlin.reflect.KClass
import kotlin.reflect.full.allSupertypes
import kotlin.reflect.full.isSubclassOf

/** Typed container for capability-based DI; resolves capabilities by their interface type. */
class Capabilities private constructor(
    private val map: Map<KClass<out Capability>, Capability>,
) {
    /** Returns the capability of type `T`; throws `IllegalStateException` if absent. */
    inline fun <reified T : Capability> get(): T =
        getOrNull<T>()
            ?: error("Capability ${T::class.qualifiedName} not present in this Capabilities")

    /** Returns the capability of type `T`, or `null` if absent. */
    inline fun <reified T : Capability> getOrNull(): T? {
        @Suppress("UNCHECKED_CAST")
        return rawGet(T::class) as T?
    }

    @PublishedApi
    internal fun rawGet(key: KClass<out Capability>): Capability? = map[key]

    /** Returns a new `Capabilities` with `other` indexed in; later entries win for the same interface key. */
    operator fun plus(other: Capability): Capabilities {
        val merged = LinkedHashMap(map)
        indexCapability(other, merged)
        return Capabilities(merged)
    }

    companion object {
        /** A `Capabilities` with no entries. */
        val EMPTY: Capabilities = Capabilities(emptyMap())

        /** Builds a `Capabilities` from `caps`, indexing each under every `Capability` supertype; last-wins on conflicts. */
        fun of(vararg caps: Capability): Capabilities {
            if (caps.isEmpty()) return EMPTY
            val merged = LinkedHashMap<KClass<out Capability>, Capability>()
            // Last-wins: later varargs overwrite earlier ones for the same interface key.
            for (cap in caps) indexCapability(cap, merged)
            return Capabilities(merged)
        }

        // Walk the type hierarchy and index this capability under every interface
        // that extends Capability (excluding Capability itself). This lets a caller
        // pass `ConsoleLogger()` and look it up via `get<Logger>()`.
        private fun indexCapability(
            cap: Capability,
            target: MutableMap<KClass<out Capability>, Capability>,
        ) {
            val seen = mutableSetOf<KClass<*>>()
            val concrete = cap::class
            collectCapabilityKeys(concrete, seen)
            for (key in seen) {
                @Suppress("UNCHECKED_CAST")
                target[key as KClass<out Capability>] = cap
            }
        }

        private fun collectCapabilityKeys(klass: KClass<*>, into: MutableSet<KClass<*>>) {
            for (sup in klass.allSupertypes) {
                val classifier = sup.classifier as? KClass<*> ?: continue
                if (classifier == Capability::class) continue
                if (!classifier.java.isInterface) continue
                if (!classifier.isSubclassOf(Capability::class)) continue
                into.add(classifier)
            }
        }
    }
}
