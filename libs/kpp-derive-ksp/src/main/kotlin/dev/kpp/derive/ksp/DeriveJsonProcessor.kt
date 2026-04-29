package dev.kpp.derive.ksp

import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSValueParameter
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets

private const val DERIVE_JSON_FQN = "dev.kpp.derive.DeriveJson"
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
            if (!validateClass(cls)) continue
            val ok = generateForClass(cls)
            if (ok) anyValid = true
        }

        if (anyValid && !helperEmitted) {
            emitSharedHelper()
            helperEmitted = true
        }

        return emptyList()
    }

    private fun validateClass(cls: KSClassDeclaration): Boolean {
        if (cls.classKind != ClassKind.CLASS) {
            env.logger.error(
                "@DeriveJson is only supported on classes (got ${cls.classKind}) on ${cls.qualifiedName?.asString()}",
                cls,
            )
            return false
        }
        val ctor = cls.primaryConstructor
        if (ctor == null || ctor.parameters.isEmpty()) {
            env.logger.error(
                "@DeriveJson requires a primary constructor with at least one parameter: ${cls.qualifiedName?.asString()}",
                cls,
            )
            return false
        }

        var ok = true
        for (param in ctor.parameters) {
            val fqn = paramTypeFqn(param)
            if (fqn !in SUPPORTED_TYPES) {
                env.logger.error(
                    "@DeriveJson (Phase-4 prototype) does not yet support type '$fqn' " +
                        "on property '${param.name?.asString()}' of ${cls.qualifiedName?.asString()}. " +
                        "Supported: $SUPPORTED_TYPES",
                    param,
                )
                ok = false
            }
        }
        return ok
    }

    private fun paramTypeFqn(param: KSValueParameter): String? {
        val resolved = param.type.resolve()
        if (resolved.isMarkedNullable) return null
        return resolved.declaration.qualifiedName?.asString()
    }

    private fun readSnakeCase(cls: KSClassDeclaration): Boolean {
        val ann = cls.annotations.firstOrNull { it.shortName.asString() == "DeriveJson" }
            ?: return false
        val arg = ann.arguments.firstOrNull { it.name?.asString() == "snakeCase" }
        return (arg?.value as? Boolean) ?: false
    }

    private fun generateForClass(cls: KSClassDeclaration): Boolean {
        val pkg = cls.packageName.asString()
        val simple = cls.simpleName.asString()
        val snake = readSnakeCase(cls)
        val ctor = cls.primaryConstructor ?: return false
        val ksFile = cls.containingFile ?: return false

        val src = buildClassExtensionSource(pkg, simple, ctor.parameters, snake)
        val deps = Dependencies(false, ksFile)
        val out = env.codeGenerator.createNewFile(deps, pkg, "${simple}_DeriveJson")
        OutputStreamWriter(out, StandardCharsets.UTF_8).use { it.write(src) }
        return true
    }

    private fun buildClassExtensionSource(
        pkg: String,
        simple: String,
        params: List<KSValueParameter>,
        snakeCase: Boolean,
    ): String {
        val sb = StringBuilder()
        if (pkg.isNotEmpty()) {
            sb.append("package ").append(pkg).append("\n\n")
        }
        sb.append("import ").append(SHARED_HELPERS_PACKAGE).append('.').append(ESCAPE_FN).append("\n\n")
        sb.append("internal fun ").append(simple).append(".toJsonGenerated(): String {\n")
        sb.append("    val sb = StringBuilder()\n")
        sb.append("    sb.append('{')\n")

        for ((i, param) in params.withIndex()) {
            val pname = param.name?.asString() ?: continue
            val key = if (snakeCase) camelToSnake(pname) else pname
            // The key is itself a JSON string. We pre-escape at codegen time
            // so the generated code doesn't have to call escape on a constant.
            val keyJson = escapeJsonString(key)
            // keyJson includes surrounding quotes already. We need it as a Kotlin string literal:
            val keyKotlinLit = toKotlinStringLiteral(keyJson)

            if (i > 0) {
                sb.append("    sb.append(',')\n")
            }
            sb.append("    sb.append(").append(keyKotlinLit).append(")\n")
            sb.append("    sb.append(':')\n")

            val fqn = param.type.resolve().declaration.qualifiedName?.asString()
            when (fqn) {
                "kotlin.String" -> {
                    sb.append("    sb.append(").append(ESCAPE_FN).append("(this.").append(pname).append("))\n")
                }
                else -> {
                    // Numbers and booleans encode via toString(); StringBuilder.append already does this.
                    sb.append("    sb.append(this.").append(pname).append(")\n")
                }
            }
        }
        sb.append("    sb.append('}')\n")
        sb.append("    return sb.toString()\n")
        sb.append("}\n")
        return sb.toString()
    }

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
