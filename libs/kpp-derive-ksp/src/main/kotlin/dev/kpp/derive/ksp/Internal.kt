package dev.kpp.derive.ksp

// Inlined from kpp-derive/Internal.kt so the processor doesn't depend on
// the runtime module's `internal` API. Keep these two copies in sync —
// the parity tests in :samples:derive-ksp-demo guard correctness.

internal fun camelToSnake(name: String): String {
    if (name.isEmpty()) return name
    val out = StringBuilder(name.length + 4)
    for ((i, c) in name.withIndex()) {
        if (c.isUpperCase()) {
            if (i > 0) out.append('_')
            out.append(c.lowercaseChar())
        } else {
            out.append(c)
        }
    }
    return out.toString()
}

internal fun escapeJsonString(s: String): String {
    val out = StringBuilder(s.length + 2)
    out.append('"')
    for (c in s) {
        when (c) {
            '"' -> out.append("\\\"")
            '\\' -> out.append("\\\\")
            '\n' -> out.append("\\n")
            '\r' -> out.append("\\r")
            '\t' -> out.append("\\t")
            '\b' -> out.append("\\b")
            '' -> out.append("\\f")
            else -> out.append(c)
        }
    }
    out.append('"')
    return out.toString()
}

// FQNs of supported primitive-ish property types in this prototype slice.
internal val SUPPORTED_TYPES: Set<String> = setOf(
    "kotlin.String",
    "kotlin.Int",
    "kotlin.Long",
    "kotlin.Boolean",
    "kotlin.Double",
    "kotlin.Float",
    "kotlin.Short",
    "kotlin.Byte",
)
