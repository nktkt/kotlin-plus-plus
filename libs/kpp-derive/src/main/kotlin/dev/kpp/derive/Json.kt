@file:Suppress("KPP017") // reflection-based serializer derivation — replaced by FIR plugin in Phase 5

package dev.kpp.derive

import dev.kpp.secret.Secret
import kotlin.reflect.KClass
import kotlin.reflect.KParameter
import kotlin.reflect.KProperty1
import kotlin.reflect.KType
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.isAccessible

object Json {
    fun encode(value: Any?): String {
        val sb = StringBuilder()
        encodeInto(value, sb)
        return sb.toString()
    }

    inline fun <reified T : Any> decode(text: String): T {
        val raw = parseText(text)
        @Suppress("UNCHECKED_CAST")
        return decodeAs(raw, T::class) as T
    }

    // Exposed for use from inline `decode`. Not part of the documented surface.
    @PublishedApi
    internal fun parseText(text: String): Any? = JsonParser(text).parseRoot()

    @PublishedApi
    internal fun decodeAs(raw: Any?, klass: KClass<*>): Any? = decodeInto(raw, klass)

    private fun encodeInto(value: Any?, out: StringBuilder) {
        when (value) {
            null -> out.append("null")
            is Boolean -> out.append(if (value) "true" else "false")
            is Byte, is Short, is Int, is Long -> out.append(value.toString())
            is Float, is Double -> out.append(value.toString())
            is String -> out.append(escapeJsonString(value))
            is List<*> -> {
                out.append('[')
                for ((i, item) in value.withIndex()) {
                    if (i > 0) out.append(',')
                    encodeInto(item, out)
                }
                out.append(']')
            }
            is Map<*, *> -> {
                out.append('{')
                var first = true
                for ((k, v) in value) {
                    if (k !is String) throw IllegalArgumentException("Map keys must be String, got ${k?.let { it::class }}")
                    if (!first) out.append(',')
                    first = false
                    out.append(escapeJsonString(k))
                    out.append(':')
                    encodeInto(v, out)
                }
                out.append('}')
            }
            else -> encodeDataLike(value, out)
        }
    }

    private fun encodeDataLike(value: Any, out: StringBuilder) {
        val klass = value::class
        val derive = klass.findAnnotation<DeriveJson>()
            ?: throw IllegalArgumentException("@DeriveJson required on ${klass.qualifiedName}")
        val ctor = klass.primaryConstructor
            ?: throw IllegalArgumentException("@DeriveJson class needs a primary constructor: ${klass.qualifiedName}")

        // We walk constructor parameters (not memberProperties) because data classes
        // generate componentN() and copy() helpers, and `memberProperties` ordering
        // is not guaranteed. Constructor params give us the declaration order users wrote.
        val props: Map<String, KProperty1<out Any, *>> =
            klass.memberProperties.associateBy { it.name }

        out.append('{')
        var first = true
        for (param in ctor.parameters) {
            val pname = param.name ?: continue
            val prop = props[pname] ?: continue
            if (prop.findAnnotation<JsonIgnore>() != null) continue
            val nameOverride = prop.findAnnotation<JsonName>()?.value
            val key = nameOverride ?: if (derive.snakeCase) camelToSnake(pname) else pname
            if (!first) out.append(',')
            first = false
            out.append(escapeJsonString(key))
            out.append(':')
            prop.isAccessible = true
            @Suppress("UNCHECKED_CAST")
            val pv = (prop as KProperty1<Any, *>).get(value)
            // Intercept Secret<*> before normal encoding. The default is to
            // emit "[REDACTED]" so a secret cannot leak via toString/JSON
            // by accident; the enclosing class must opt in with
            // @DeriveJson(allowSecrets = true) to encode the underlying value.
            if (pv is Secret<*>) {
                if (derive.allowSecrets) {
                    encodeInto(pv.expose(), out)
                } else {
                    out.append(escapeJsonString("[REDACTED]"))
                }
            } else {
                encodeInto(pv, out)
            }
        }
        out.append('}')
    }

    private fun decodeInto(raw: Any?, klass: KClass<*>): Any? {
        if (raw == null) return null
        // Built-in passthroughs
        when (klass) {
            String::class -> return raw as String
            Boolean::class -> return raw as Boolean
            Byte::class -> return (raw as Long).toByte()
            Short::class -> return (raw as Long).toShort()
            Int::class -> return (raw as Long).toInt()
            Long::class -> return raw as Long
            Float::class -> return when (raw) {
                is Double -> raw.toFloat()
                is Long -> raw.toFloat()
                else -> throw IllegalArgumentException("expected number, got ${raw::class}")
            }
            Double::class -> return when (raw) {
                is Double -> raw
                is Long -> raw.toDouble()
                else -> throw IllegalArgumentException("expected number, got ${raw::class}")
            }
            Any::class -> return raw
            List::class, MutableList::class, ArrayList::class -> {
                @Suppress("UNCHECKED_CAST")
                return raw as List<Any?>
            }
            Map::class, MutableMap::class, LinkedHashMap::class, HashMap::class -> {
                @Suppress("UNCHECKED_CAST")
                return raw as Map<String, Any?>
            }
        }

        val derive = klass.findAnnotation<DeriveJson>()
            ?: throw IllegalArgumentException("@DeriveJson required on ${klass.qualifiedName}")
        val obj = raw as? Map<*, *>
            ?: throw IllegalArgumentException("expected JSON object for ${klass.qualifiedName}, got ${raw::class}")
        val ctor = klass.primaryConstructor
            ?: throw IllegalArgumentException("no primary constructor on ${klass.qualifiedName}")

        val props: Map<String, KProperty1<out Any, *>> =
            klass.memberProperties.associateBy { it.name }
        val args = HashMap<KParameter, Any?>()
        for (param in ctor.parameters) {
            val pname = param.name ?: continue
            val prop = props[pname]
            val nameOverride = prop?.findAnnotation<JsonName>()?.value
            val key = nameOverride ?: if (derive.snakeCase) camelToSnake(pname) else pname
            val present = obj.containsKey(key)
            if (!present) {
                if (param.isOptional) continue
                if (param.type.isMarkedNullable) {
                    args[param] = null
                    continue
                }
                throw IllegalArgumentException("missing required field '$key' for ${klass.qualifiedName}")
            }
            val rawVal = obj[key]
            val converted = decodeByType(rawVal, param.type)
            args[param] = converted
        }
        return ctor.callBy(args)
    }

    // Use the full KType so we can recurse into generic args (e.g. List<Int>).
    private fun decodeByType(raw: Any?, type: KType): Any? {
        if (raw == null) return null
        val klass = type.classifier as? KClass<*>
            ?: throw IllegalArgumentException("cannot resolve type $type")
        if (klass == Secret::class) {
            throw IllegalArgumentException(
                "Json.decode does not support Secret<T> fields yet — " +
                    "use the KSP-backed @DeriveJson once Phase 4 lands"
            )
        }
        return when (klass) {
            List::class, MutableList::class, ArrayList::class -> {
                val list = raw as? List<*>
                    ?: throw IllegalArgumentException("expected JSON array, got ${raw::class}")
                val argType = type.arguments.firstOrNull()?.type
                if (argType == null) list else list.map { decodeByType(it, argType) }
            }
            Map::class, MutableMap::class, LinkedHashMap::class, HashMap::class -> {
                val map = raw as? Map<*, *>
                    ?: throw IllegalArgumentException("expected JSON object, got ${raw::class}")
                val valType = type.arguments.getOrNull(1)?.type
                if (valType == null) map else {
                    val out = LinkedHashMap<String, Any?>()
                    for ((k, v) in map) {
                        if (k !is String) throw IllegalArgumentException("non-string key")
                        out[k] = decodeByType(v, valType)
                    }
                    out
                }
            }
            else -> decodeInto(raw, klass)
        }
    }
}
