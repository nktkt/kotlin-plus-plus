package dev.kpp.derive.ksp

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType

// Recursive description of a property type's shape, in terms the codegen
// understands. Anything not expressible as one of these arms is rejected
// at validation time with a KSP error.
internal sealed class TypeCategory {
    // One of the supported scalar primitives (kotlin.String, kotlin.Int, ...).
    data class Primitive(val fqn: String) : TypeCategory()

    // T? — wraps an inner non-null category. Inner is never Nullable itself
    // (we collapse `T??` to `T?` at parse time).
    data class Nullable(val inner: TypeCategory) : TypeCategory()

    // List<T>. Inner can be Primitive, Nullable, or NestedDeriveJson.
    // We forbid List<List<...>> in this slice — Unsupported is returned
    // when the element type is itself a List.
    data class ListOf(val inner: TypeCategory) : TypeCategory()

    // Reference to another @DeriveJson class. We codegen a call to
    // `<value>.toJsonGenerated()`.
    data class NestedDeriveJson(val fqn: String) : TypeCategory()

    // Anything else — ksType pretty-printed for the error message.
    data class Unsupported(val description: String) : TypeCategory()
}

// FQNs of supported scalar property types in the prototype slice.
internal val SUPPORTED_PRIMITIVE_FQNS: Set<String> = setOf(
    "kotlin.String",
    "kotlin.Int",
    "kotlin.Long",
    "kotlin.Boolean",
    "kotlin.Double",
    "kotlin.Float",
    "kotlin.Short",
    "kotlin.Byte",
)

private const val DERIVE_JSON_SHORT = "DeriveJson"
private const val LIST_FQN = "kotlin.collections.List"

// Walk a KSType into a TypeCategory. Returns Unsupported when the shape
// does not fit one of the supported arms; the caller turns that into a
// KSP error pointing at the offending property.
internal fun categorize(type: KSType): TypeCategory {
    if (type.isMarkedNullable) {
        // Recurse on the same KSType made non-nullable. KSType.makeNotNullable()
        // returns a fresh copy with the marker dropped, leaving generic args intact.
        val inner = categorize(type.makeNotNullable())
        if (inner is TypeCategory.Unsupported) return inner
        // Collapse degenerate T?? → T? defensively (Kotlin syntactically can't,
        // but be safe against weirdness from synthetic types).
        if (inner is TypeCategory.Nullable) return inner
        return TypeCategory.Nullable(inner)
    }

    val decl = type.declaration as? KSClassDeclaration
        ?: return TypeCategory.Unsupported(type.toString())
    val fqn = decl.qualifiedName?.asString() ?: return TypeCategory.Unsupported(type.toString())

    if (fqn in SUPPORTED_PRIMITIVE_FQNS) return TypeCategory.Primitive(fqn)

    if (fqn == LIST_FQN) {
        val argType = type.arguments.firstOrNull()?.type?.resolve()
            ?: return TypeCategory.Unsupported("List with no type argument")
        val innerCat = categorize(argType)
        // Slice limit: List<List<...>> not supported in this round.
        // (The analyzer-style error message lets users restructure.)
        val rejected = isListShape(innerCat)
        if (rejected) {
            return TypeCategory.Unsupported("List<List<...>> is not supported in this slice")
        }
        if (innerCat is TypeCategory.Unsupported) return innerCat
        return TypeCategory.ListOf(innerCat)
    }

    // Map<...> — explicitly out of scope for this slice.
    if (fqn == "kotlin.collections.Map") {
        return TypeCategory.Unsupported("Map<...> is not supported in this slice")
    }

    // Nested @DeriveJson reference. Look up the annotation by short name to
    // avoid materializing the runtime annotation class (KSP keeps it as
    // KSAnnotation). FQN match is overkill here — short name is unique enough
    // because the import resolves it.
    val isDerive = decl.annotations.any { it.shortName.asString() == DERIVE_JSON_SHORT }
    if (isDerive) return TypeCategory.NestedDeriveJson(fqn)

    return TypeCategory.Unsupported("type '$fqn' is not a supported primitive, List, or @DeriveJson class")
}

private fun isListShape(cat: TypeCategory): Boolean = when (cat) {
    is TypeCategory.ListOf -> true
    is TypeCategory.Nullable -> isListShape(cat.inner)
    else -> false
}
