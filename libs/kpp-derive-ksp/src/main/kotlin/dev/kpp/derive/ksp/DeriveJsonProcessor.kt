package dev.kpp.derive.ksp

import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.getDeclaredProperties
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSValueParameter
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets

private const val DERIVE_JSON_FQN = "dev.kpp.derive.DeriveJson"
private const val JSON_NAME_SHORT = "JsonName"
private const val JSON_IGNORE_SHORT = "JsonIgnore"
private const val SHARED_HELPERS_PACKAGE = "dev.kpp.derive.ksp.generated"
private const val SHARED_HELPERS_FILE = "DeriveJsonGeneratedHelpers"
private const val ESCAPE_FN = "__kppEscapeJsonString"

class DeriveJsonProcessor(
    private val env: SymbolProcessorEnvironment,
) : SymbolProcessor {

    // Emit the shared helper file at most once across the lifetime of this
    // processor. KSP can call process() multiple rounds; we don't want
    // a duplicate-file error on the second pass.
    private var helperEmitted: Boolean = false

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val symbols = resolver.getSymbolsWithAnnotation(DERIVE_JSON_FQN).toList()
        val classes = symbols.filterIsInstance<KSClassDeclaration>()
        if (classes.isEmpty()) return emptyList()

        var anyValid = false
        for (cls in classes) {
            val params = collectEmittedParams(cls) ?: continue
            val ok = generateForClass(cls, params)
            if (ok) anyValid = true
        }

        if (anyValid && !helperEmitted) {
            emitSharedHelper()
            helperEmitted = true
        }

        return emptyList()
    }

    /**
     * Validate the class and pre-categorize each constructor parameter.
     * Returns null if validation fails (errors already logged).
     * Returns the list of parameters that should be emitted in JSON
     * (i.e. excluding @JsonIgnore), each paired with its JSON key and TypeCategory.
     */
    private fun collectEmittedParams(cls: KSClassDeclaration): List<EmittedParam>? {
        if (cls.classKind != ClassKind.CLASS) {
            env.logger.error(
                "@DeriveJson is only supported on classes (got ${cls.classKind}) on ${cls.qualifiedName?.asString()}",
                cls,
            )
            return null
        }
        val ctor = cls.primaryConstructor
        if (ctor == null || ctor.parameters.isEmpty()) {
            env.logger.error(
                "@DeriveJson requires a primary constructor with at least one parameter: ${cls.qualifiedName?.asString()}",
                cls,
            )
            return null
        }

        val snake = readSnakeCase(cls)
        // For data class properties declared in the primary constructor, the
        // annotation lives on the KSPropertyDeclaration (target=PROPERTY), not
        // on the KSValueParameter. Index properties by name so we can join them
        // back to their constructor params.
        val propsByName: Map<String, KSPropertyDeclaration> =
            cls.getDeclaredProperties().associateBy { it.simpleName.asString() }

        var ok = true
        val emitted = ArrayList<EmittedParam>()
        for (param in ctor.parameters) {
            val pname = param.name?.asString() ?: continue
            val prop = propsByName[pname]
            // @JsonIgnore drops the property from the output entirely.
            if (hasAnnotation(param, prop, JSON_IGNORE_SHORT)) continue

            val resolved = param.type.resolve()
            val cat = categorize(resolved)
            if (cat is TypeCategory.Unsupported) {
                env.logger.error(
                    "@DeriveJson does not support property '$pname' on " +
                        "${cls.qualifiedName?.asString()}: ${cat.description}",
                    param,
                )
                ok = false
                continue
            }

            // @JsonName overrides snake_case for that one property.
            val nameOverride = readJsonNameValue(param, prop)
            val key = nameOverride ?: if (snake) camelToSnake(pname) else pname
            emitted.add(EmittedParam(pname, key, cat))
        }
        if (!ok) return null
        return emitted
    }

    private fun hasAnnotation(
        param: KSValueParameter,
        prop: KSPropertyDeclaration?,
        shortName: String,
    ): Boolean {
        if (param.annotations.any { it.shortName.asString() == shortName }) return true
        if (prop != null && prop.annotations.any { it.shortName.asString() == shortName }) return true
        return false
    }

    private fun readJsonNameValue(
        param: KSValueParameter,
        prop: KSPropertyDeclaration?,
    ): String? {
        val ann: KSAnnotation? =
            param.annotations.firstOrNull { it.shortName.asString() == JSON_NAME_SHORT }
                ?: prop?.annotations?.firstOrNull { it.shortName.asString() == JSON_NAME_SHORT }
        if (ann == null) return null
        val arg = ann.arguments.firstOrNull { it.name?.asString() == "value" }
            ?: ann.arguments.firstOrNull()
        return arg?.value as? String
    }

    private fun readSnakeCase(cls: KSClassDeclaration): Boolean {
        val ann = cls.annotations.firstOrNull { it.shortName.asString() == "DeriveJson" }
            ?: return false
        val arg = ann.arguments.firstOrNull { it.name?.asString() == "snakeCase" }
        return (arg?.value as? Boolean) ?: false
    }

    private fun generateForClass(cls: KSClassDeclaration, params: List<EmittedParam>): Boolean {
        val pkg = cls.packageName.asString()
        val simple = cls.simpleName.asString()
        val ksFile = cls.containingFile ?: return false

        val src = buildClassExtensionSource(pkg, simple, params)
        val deps = Dependencies(false, ksFile)
        val out = env.codeGenerator.createNewFile(deps, pkg, "${simple}_DeriveJson")
        OutputStreamWriter(out, StandardCharsets.UTF_8).use { it.write(src) }
        return true
    }

    private fun buildClassExtensionSource(
        pkg: String,
        simple: String,
        params: List<EmittedParam>,
    ): String {
        val sb = StringBuilder()
        if (pkg.isNotEmpty()) {
            sb.append("package ").append(pkg).append("\n\n")
        }
        sb.append("import ").append(SHARED_HELPERS_PACKAGE).append('.').append(ESCAPE_FN).append("\n\n")
        sb.append("internal fun ").append(simple).append(".toJsonGenerated(): String {\n")
        sb.append("    val sb = StringBuilder()\n")
        sb.append("    sb.append('{')\n")

        // Comma handling: with @JsonIgnore in the mix we already filtered those out
        // upstream, so the index here is the position in the EMITTED list — a comma
        // before any element with index > 0 is correct.
        for ((i, ep) in params.withIndex()) {
            val keyJson = escapeJsonString(ep.jsonKey)
            val keyKotlinLit = toKotlinStringLiteral(keyJson)

            if (i > 0) {
                sb.append("    sb.append(',')\n")
            }
            sb.append("    sb.append(").append(keyKotlinLit).append(")\n")
            sb.append("    sb.append(':')\n")

            // Emit the value. We delegate to a recursive emitter that knows how
            // to walk Nullable/List/Nested categories.
            emitValue(sb, "this.${ep.propName}", ep.category, indent = "    ")
        }
        sb.append("    sb.append('}')\n")
        sb.append("    return sb.toString()\n")
        sb.append("}\n")
        return sb.toString()
    }

    /**
     * Recursively emit code that appends the JSON encoding of [expr] (a Kotlin
     * source-level expression of the appropriate type) to a StringBuilder named `sb`.
     * [indent] is the line prefix of generated code (so the output stays tidy).
     */
    private fun emitValue(sb: StringBuilder, expr: String, cat: TypeCategory, indent: String) {
        when (cat) {
            is TypeCategory.Primitive -> emitPrimitive(sb, expr, cat.fqn, indent)
            is TypeCategory.NestedDeriveJson -> {
                // Generated extension lives in the nested class's own package and
                // is `internal`. Within the same Gradle module that's fine; for
                // cross-module use the consumer must also apply the KSP processor
                // (documented constraint).
                sb.append(indent).append("sb.append(").append(expr).append(".toJsonGenerated())\n")
            }
            is TypeCategory.Nullable -> {
                // Use a local val to avoid double-evaluating the expression
                // (it might be a property access with a backing field — cheap —
                // but for nested calls it could be heavier). We pick a unique-ish
                // name; nested Nullables won't happen because we collapse at parse.
                val tmp = freshTmpName()
                sb.append(indent).append("val ").append(tmp).append(" = ").append(expr).append("\n")
                sb.append(indent).append("if (").append(tmp).append(" == null) {\n")
                sb.append(indent).append("    sb.append(\"null\")\n")
                sb.append(indent).append("} else {\n")
                emitValue(sb, tmp, cat.inner, "$indent    ")
                sb.append(indent).append("}\n")
            }
            is TypeCategory.ListOf -> {
                val tmp = freshTmpName()
                sb.append(indent).append("val ").append(tmp).append(" = ").append(expr).append("\n")
                sb.append(indent).append("sb.append('[')\n")
                sb.append(indent).append("for ((__i, __e) in ").append(tmp).append(".withIndex()) {\n")
                sb.append(indent).append("    if (__i > 0) sb.append(',')\n")
                emitValue(sb, "__e", cat.inner, "$indent    ")
                sb.append(indent).append("}\n")
                sb.append(indent).append("sb.append(']')\n")
            }
            is TypeCategory.Unsupported -> {
                // Should have been filtered upstream; emit a no-op so codegen still
                // produces a parseable file if we reach here in some edge case.
                sb.append(indent).append("/* unsupported: ").append(cat.description).append(" */\n")
            }
        }
    }

    private fun emitPrimitive(sb: StringBuilder, expr: String, fqn: String, indent: String) {
        when (fqn) {
            "kotlin.String" -> {
                sb.append(indent).append("sb.append(").append(ESCAPE_FN).append("(").append(expr).append("))\n")
            }
            else -> {
                // Numbers and booleans encode via toString(); StringBuilder.append
                // has overloads for Int/Long/Boolean/Double/Float that match.
                // Byte and Short don't have direct overloads, but `.append(value)`
                // on those goes through Any → toString — same numeric output.
                sb.append(indent).append("sb.append(").append(expr).append(")\n")
            }
        }
    }

    private var tmpCounter = 0
    private fun freshTmpName(): String = "__v${tmpCounter++}"

    private fun emitSharedHelper() {
        // The helper does the same string escaping as kpp-derive/Internal.kt.
        // Because we're writing Kotlin source that itself produces JSON escapes,
        // every escape sequence has to be doubly-escaped in the source string.
        val src = buildString {
            append("package ").append(SHARED_HELPERS_PACKAGE).append("\n\n")
            append("internal fun ").append(ESCAPE_FN).append("(s: String): String {\n")
            append("    val out = StringBuilder(s.length + 2)\n")
            append("    out.append('\"')\n")
            append("    for (c in s) {\n")
            append("        when (c) {\n")
            // Each line: we want the generated source to literally contain
            // `'"'  -> out.append("\\\"")` etc.
            append("            '\\\"' -> out.append(\"\\\\\\\"\")\n")
            append("            '\\\\' -> out.append(\"\\\\\\\\\")\n")
            append("            '\\n' -> out.append(\"\\\\n\")\n")
            append("            '\\r' -> out.append(\"\\\\r\")\n")
            append("            '\\t' -> out.append(\"\\\\t\")\n")
            append("            '\\b' -> out.append(\"\\\\b\")\n")
            append("            '\\u000C' -> out.append(\"\\\\f\")\n")
            append("            else -> out.append(c)\n")
            append("        }\n")
            append("    }\n")
            append("    out.append('\"')\n")
            append("    return out.toString()\n")
            append("}\n")
        }
        // aggregating=true: the helper is shared by every per-class file in the round.
        val deps = Dependencies(true)
        val out = env.codeGenerator.createNewFile(deps, SHARED_HELPERS_PACKAGE, SHARED_HELPERS_FILE)
        OutputStreamWriter(out, StandardCharsets.UTF_8).use { it.write(src) }
    }

    // Turn a String (which may contain backslashes / quotes / control chars from
    // escapeJsonString) into a valid Kotlin source-level string literal.
    private fun toKotlinStringLiteral(s: String): String {
        val sb = StringBuilder(s.length + 2)
        sb.append('"')
        for (c in s) {
            when (c) {
                '\\' -> sb.append("\\\\")
                '"' -> sb.append("\\\"")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                '\b' -> sb.append("\\b")
                '' -> sb.append("\\u000C")
                '$' -> sb.append("\\$")
                else -> sb.append(c)
            }
        }
        sb.append('"')
        return sb.toString()
    }
}

private data class EmittedParam(
    val propName: String,
    val jsonKey: String,
    val category: TypeCategory,
)
