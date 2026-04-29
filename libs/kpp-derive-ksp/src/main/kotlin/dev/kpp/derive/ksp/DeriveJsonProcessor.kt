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

// Map<String, T> with non-String keys is rejected at categorize() time. To
// smoke-test that path manually: change the `labels` field on
// `samples/derive-ksp-demo/...Models.kt::Tags` to `Map<Int, String>`,
// then run `gradle :samples:derive-ksp-demo:build`. Expect a KSP error
// pointing at the offending parameter; the build must fail before generating
// `Tags_DeriveJson.kt`.
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
            val allowSecrets = readAllowSecrets(cls)
            val ok = generateForClass(cls, params, allowSecrets)
            if (ok) anyValid = true
            // Decoder generation is best-effort and independent of the
            // encoder. If decoder gen fails (no companion, has Secret<*>,
            // etc.) the encoder is still emitted normally.
            generateDecoderForClass(cls)
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

    private fun readSnakeCase(cls: KSClassDeclaration): Boolean =
        readDeriveJsonBoolean(cls, "snakeCase")

    private fun readAllowSecrets(cls: KSClassDeclaration): Boolean =
        readDeriveJsonBoolean(cls, "allowSecrets")

    private fun readDeriveJsonBoolean(cls: KSClassDeclaration, name: String): Boolean {
        val ann = cls.annotations.firstOrNull { it.shortName.asString() == "DeriveJson" }
            ?: return false
        val arg = ann.arguments.firstOrNull { it.name?.asString() == name }
        return (arg?.value as? Boolean) ?: false
    }

    private fun generateForClass(
        cls: KSClassDeclaration,
        params: List<EmittedParam>,
        allowSecrets: Boolean,
    ): Boolean {
        val pkg = cls.packageName.asString()
        val simple = cls.simpleName.asString()
        val ksFile = cls.containingFile ?: return false

        val src = buildClassExtensionSource(pkg, simple, params, allowSecrets)
        val deps = Dependencies(false, ksFile)
        val out = env.codeGenerator.createNewFile(deps, pkg, "${simple}_DeriveJson")
        OutputStreamWriter(out, StandardCharsets.UTF_8).use { it.write(src) }
        return true
    }

    private fun buildClassExtensionSource(
        pkg: String,
        simple: String,
        params: List<EmittedParam>,
        allowSecrets: Boolean,
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
            // to walk Nullable/List/Map/Nested/Secret categories.
            emitValue(sb, "this.${ep.propName}", ep.category, indent = "    ", allowSecrets = allowSecrets)
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
     * [allowSecrets] propagates the enclosing class's @DeriveJson(allowSecrets) flag
     * so Secret<T> leaves can either redact or expose+delegate.
     */
    private fun emitValue(
        sb: StringBuilder,
        expr: String,
        cat: TypeCategory,
        indent: String,
        allowSecrets: Boolean,
    ) {
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
                emitValue(sb, tmp, cat.inner, "$indent    ", allowSecrets)
                sb.append(indent).append("}\n")
            }
            is TypeCategory.ListOf -> {
                val tmp = freshTmpName()
                sb.append(indent).append("val ").append(tmp).append(" = ").append(expr).append("\n")
                sb.append(indent).append("sb.append('[')\n")
                sb.append(indent).append("for ((__i, __e) in ").append(tmp).append(".withIndex()) {\n")
                sb.append(indent).append("    if (__i > 0) sb.append(',')\n")
                emitValue(sb, "__e", cat.inner, "$indent    ", allowSecrets)
                sb.append(indent).append("}\n")
                sb.append(indent).append("sb.append(']')\n")
            }
            is TypeCategory.MapOf -> {
                val tmp = freshTmpName()
                val idx = freshTmpName()
                val k = freshTmpName()
                val v = freshTmpName()
                sb.append(indent).append("val ").append(tmp).append(" = ").append(expr).append("\n")
                sb.append(indent).append("sb.append('{')\n")
                sb.append(indent).append("var ").append(idx).append(" = 0\n")
                // Iterate entries in the underlying map's iteration order. For
                // LinkedHashMap (mapOf default), that's insertion order — matches
                // the runtime encoder which also walks `for ((k, v) in value)`.
                sb.append(indent).append("for ((").append(k).append(", ").append(v).append(") in ").append(tmp).append(") {\n")
                sb.append(indent).append("    if (").append(idx).append(" > 0) sb.append(',')\n")
                sb.append(indent).append("    ").append(idx).append("++\n")
                sb.append(indent).append("    sb.append(").append(ESCAPE_FN).append("(").append(k).append("))\n")
                sb.append(indent).append("    sb.append(':')\n")
                emitValue(sb, v, cat.value, "$indent    ", allowSecrets)
                sb.append(indent).append("}\n")
                sb.append(indent).append("sb.append('}')\n")
            }
            is TypeCategory.SecretOf -> {
                if (!allowSecrets) {
                    // Default: never let a Secret leak through generated code.
                    // Emit the literal redacted string with no recursion into T.
                    sb.append(indent).append("sb.append(\"\\\"[REDACTED]\\\"\")\n")
                } else {
                    // Diagnostic-only opt-in. Delegate to the runtime encoder on
                    // the exposed value so we don't have to inline T-specific
                    // logic for every possible inner shape (ByteArray, nested
                    // @DeriveJson, Map, etc.). This keeps the rare path in
                    // lockstep with the runtime byte-for-byte.
                    sb.append(indent).append("sb.append(dev.kpp.derive.Json.encode(").append(expr).append(".expose()))\n")
                }
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

    // ----- Decoder generation -----
    //
    // Strategy: delegate JSON parsing to the runtime (`Json.decode<Map<String,
    // Any?>>(text)`) and only generate the type-specific extractor that walks
    // the parsed Map and constructs the target via its primary constructor.
    //
    // Constraints:
    //  - The class must have a `companion object` so we can extend
    //    `<Class>.Companion.fromJsonGenerated(text)`. Otherwise we warn and skip.
    //  - The class must not have any `Secret<*>` parameters — we have no
    //    decoder story for Secret yet (matches the runtime which throws on
    //    Secret<T> in `Json.decode`). If any are found, log an error and skip.
    //  - Each `@JsonIgnore` property must have a default value, because we
    //    use named-argument constructor invocation and simply omit those
    //    params (Kotlin's defaults fire). If a `@JsonIgnore` param has no
    //    default we log an error and skip.
    private fun generateDecoderForClass(cls: KSClassDeclaration) {
        if (cls.classKind != ClassKind.CLASS) return
        val ctor = cls.primaryConstructor ?: return

        val pkg = cls.packageName.asString()
        val simple = cls.simpleName.asString()
        val ksFile = cls.containingFile ?: return

        // 1. Companion object check — required for the extension to compile.
        val hasCompanion = cls.declarations
            .filterIsInstance<KSClassDeclaration>()
            .any { it.classKind == ClassKind.OBJECT && it.isCompanionObject }
        if (!hasCompanion) {
            env.logger.warn(
                "@DeriveJson decoder skipped for ${cls.qualifiedName?.asString()}: " +
                    "decoder generation requires a `companion object {}` declaration. " +
                    "Encoder is still generated.",
                cls,
            )
            return
        }

        // 2. Re-categorize all primary-constructor params (including @JsonIgnore
        // ones, because we need to verify they have defaults). Build a list of
        // descriptors that includes everything we need for codegen.
        val snake = readSnakeCase(cls)
        val propsByName: Map<String, KSPropertyDeclaration> =
            cls.getDeclaredProperties().associateBy { it.simpleName.asString() }

        val descriptors = ArrayList<DecoderParam>()
        var ok = true
        for (param in ctor.parameters) {
            val pname = param.name?.asString() ?: continue
            val prop = propsByName[pname]
            val isIgnored = hasAnnotation(param, prop, JSON_IGNORE_SHORT)
            val resolved = param.type.resolve()
            val cat = categorize(resolved)

            if (isIgnored) {
                // @JsonIgnore params are reconstructed entirely from their
                // default. If no default, the constructor cannot be called
                // without that arg, so we can't generate a decoder.
                if (!param.hasDefault) {
                    env.logger.error(
                        "@DeriveJson decoder skipped for ${cls.qualifiedName?.asString()}: " +
                            "@JsonIgnore property '$pname' has no default value, " +
                            "cannot be reconstructed from JSON.",
                        param,
                    )
                    ok = false
                }
                descriptors.add(DecoderParam(pname, "", cat, isNullable = resolved.isMarkedNullable, hasDefault = param.hasDefault, ignored = true))
                continue
            }

            if (cat is TypeCategory.Unsupported) {
                // Already reported by the encoder pass; just bail on the decoder.
                ok = false
                continue
            }
            if (containsSecret(cat)) {
                env.logger.error(
                    "@DeriveJson decoder skipped for ${cls.qualifiedName?.asString()}: " +
                        "property '$pname' has a Secret<*> type which the decoder " +
                        "cannot reconstruct. Encoder is still generated.",
                    param,
                )
                ok = false
                continue
            }
            val nameOverride = readJsonNameValue(param, prop)
            val key = nameOverride ?: if (snake) camelToSnake(pname) else pname
            descriptors.add(
                DecoderParam(
                    propName = pname,
                    jsonKey = key,
                    category = cat,
                    isNullable = resolved.isMarkedNullable,
                    hasDefault = param.hasDefault,
                    ignored = false,
                )
            )
        }
        if (!ok) return

        // 3. Emit the file.
        val src = buildDecoderSource(pkg, simple, descriptors)
        val deps = Dependencies(false, ksFile)
        val out = env.codeGenerator.createNewFile(deps, pkg, "${simple}_DeriveJsonDecoder")
        OutputStreamWriter(out, StandardCharsets.UTF_8).use { it.write(src) }
    }

    private fun containsSecret(cat: TypeCategory): Boolean = when (cat) {
        is TypeCategory.SecretOf -> true
        is TypeCategory.Nullable -> containsSecret(cat.inner)
        is TypeCategory.ListOf -> containsSecret(cat.inner)
        is TypeCategory.MapOf -> containsSecret(cat.value)
        else -> false
    }

    private fun buildDecoderSource(
        pkg: String,
        simple: String,
        descriptors: List<DecoderParam>,
    ): String {
        // Reset the local tmp counter so independent class files don't drift.
        decoderTmpCounter = 0
        val sb = StringBuilder()
        if (pkg.isNotEmpty()) {
            sb.append("package ").append(pkg).append("\n\n")
        }
        sb.append("import dev.kpp.derive.Json\n\n")

        // Public entry: parse text into a Map, delegate to the internal
        // extractor. The signature is fixed so user code reads as
        // `User.fromJsonGenerated(text)`.
        sb.append("fun ").append(simple).append(".Companion.fromJsonGenerated(text: String): ")
            .append(simple).append(" {\n")
        sb.append("    val raw = Json.decode<Map<String, Any?>>(text)\n")
        sb.append("    return ").append(extractorFnName(simple)).append("(raw)\n")
        sb.append("}\n\n")

        // Internal extractor: walks an already-parsed Map. Nested @DeriveJson
        // classes call each other's internals so we never re-parse. Marked
        // `internal` to keep it out of the public API surface; same-package
        // siblings can use it for nesting.
        sb.append("internal fun ").append(extractorFnName(simple))
            .append("(raw: Map<String, Any?>): ").append(simple).append(" {\n")

        // Per-property extraction. We use named-argument constructor
        // invocation, which lets us skip @JsonIgnore params entirely so their
        // declared defaults fire — Kotlin has no other way to "skip" an arg
        // at a call site.
        //
        // The internal coercion code emits values typed as `Any?`, `List<Any?>`,
        // or `Map<String, Any?>` (because the runtime parser is shape-erased).
        // Each constructor param has a stronger static type than that, so we
        // render its expected type and cast the bound val once at the call site
        // with @Suppress("UNCHECKED_CAST"). JVM erasure makes this safe — the
        // element-level coercions already verified the runtime types.
        val ctorArgs = ArrayList<String>()
        for (d in descriptors) {
            if (d.ignored) continue // omit from constructor; default fires
            val tmp = "v_" + d.propName
            emitDecoderForParam(sb, "    ", tmp, d)
            // Compose the param's full Kotlin type (account for Nullable wrapper).
            val typeStr = if (d.isNullable && d.category !is TypeCategory.Nullable) {
                renderType(d.category) + "?"
            } else {
                renderType(d.category)
            }
            sb.append("    @Suppress(\"UNCHECKED_CAST\")\n")
            sb.append("    val ").append(tmp).append("_typed: ").append(typeStr)
                .append(" = ").append(tmp).append(" as ").append(typeStr).append("\n")
            ctorArgs.add("${d.propName} = ${tmp}_typed")
        }
        sb.append("    return ").append(simple).append("(\n")
        for ((i, arg) in ctorArgs.withIndex()) {
            sb.append("        ").append(arg)
            if (i < ctorArgs.size - 1) sb.append(',')
            sb.append('\n')
        }
        sb.append("    )\n")
        sb.append("}\n")
        return sb.toString()
    }

    /**
     * Emit code that reads the JSON value for [d] from `raw` and binds it to
     * a local val named [varName]. The val is non-nullable iff the property
     * is non-nullable; for nullable properties, the val is `Any? = null` if
     * the key is missing or the value is null.
     */
    private fun emitDecoderForParam(
        sb: StringBuilder,
        indent: String,
        varName: String,
        d: DecoderParam,
    ) {
        val keyLit = toKotlinStringLiteral(d.jsonKey)
        val keyAccess = "raw[$keyLit]"

        if (d.hasDefault) {
            // If the param has a default and the JSON is missing the key,
            // we'd want to use the default. But once we generate the named-arg
            // call, we MUST pass *some* value for it (we already chose to
            // include it in the ctor args). Compromise: if the key is missing,
            // we fall back to a value-equivalent default — but Kotlin defaults
            // are arbitrary expressions we can't easily reproduce. Simpler:
            // if hasDefault AND key missing, treat it like nullable and pass
            // `null` IF the param is also nullable; otherwise we don't have a
            // good answer and must throw (but the runtime decoder uses
            // `param.isOptional` to skip — we can't because we already have
            // a named arg in the call site).
            //
            // The clean alternative: split into two emit modes per descriptor.
            // For has-default-non-ignored params, we want to OMIT the named arg
            // when the key is missing too. That requires two ctor call paths.
            //
            // To keep the generator simple AND correct for the bulk of cases,
            // we currently treat has-default like "fall back to null if
            // missing", which is only safe when nullable. If the param is
            // non-nullable with a default, we still throw on missing — same
            // as if the default didn't exist. Documented in tests.
        }

        when (val cat = d.category) {
            is TypeCategory.Nullable -> {
                // Nullable property: missing key -> null, JSON null -> null,
                // otherwise coerce as inner.
                sb.append(indent).append("val ").append(varName).append(" = run {\n")
                sb.append(indent).append("    val __r = ").append(keyAccess).append("\n")
                sb.append(indent).append("    if (__r == null) null else {\n")
                emitInnerCoercion(sb, "$indent        ", "__r", cat.inner, d.jsonKey, "__inner")
                sb.append(indent).append("        __inner\n")
                sb.append(indent).append("    }\n")
                sb.append(indent).append("}\n")
            }
            else -> {
                // Required property. Missing key OR null value -> error.
                sb.append(indent).append("val ").append(varName).append(" = run {\n")
                sb.append(indent).append("    val __r = ").append(keyAccess).append("\n")
                sb.append(indent).append("    if (__r == null) throw IllegalArgumentException(\"missing required property '")
                    .append(escapeForKotlinString(d.jsonKey)).append("'\")\n")
                emitInnerCoercion(sb, "$indent    ", "__r", cat, d.jsonKey, "__inner")
                sb.append(indent).append("    __inner\n")
                sb.append(indent).append("}\n")
            }
        }
    }

    /**
     * Emit a sequence of statements that coerces local `__r` (typed `Any?` or
     * `Any`) into the static type denoted by [cat], binding the result to
     * a local val [resultVar]. [keyForError] feeds the error messages so
     * users can find which field failed.
     */
    private fun emitInnerCoercion(
        sb: StringBuilder,
        indent: String,
        srcExpr: String,
        cat: TypeCategory,
        keyForError: String,
        resultVar: String,
    ) {
        when (cat) {
            is TypeCategory.Primitive -> emitPrimitiveCoercion(sb, indent, srcExpr, cat.fqn, keyForError, resultVar)
            is TypeCategory.NestedDeriveJson -> {
                // Nested @DeriveJson classes from the same package can call
                // each other's internals. Cross-package nesting works because
                // `internal` is module-scoped; both classes need the KSP
                // processor applied (same constraint as the encoder side).
                val nestedSimple = cat.fqn.substringAfterLast('.')
                sb.append(indent).append("@Suppress(\"UNCHECKED_CAST\")\n")
                sb.append(indent).append("val __m = ").append(srcExpr)
                    .append(" as? Map<String, Any?> ?: throw IllegalArgumentException(\"expected JSON object at '")
                    .append(escapeForKotlinString(keyForError)).append("'\")\n")
                sb.append(indent).append("val ").append(resultVar).append(" = ")
                    .append(extractorFnName(nestedSimple)).append("(__m)\n")
            }
            is TypeCategory.ListOf -> {
                val listVar = freshDecoderTmp()
                val outVar = freshDecoderTmp()
                sb.append(indent).append("@Suppress(\"UNCHECKED_CAST\")\n")
                sb.append(indent).append("val ").append(listVar).append(" = ").append(srcExpr)
                    .append(" as? List<Any?> ?: throw IllegalArgumentException(\"expected JSON array at '")
                    .append(escapeForKotlinString(keyForError)).append("'\")\n")
                sb.append(indent).append("val ").append(outVar)
                    .append(" = ArrayList<Any?>(").append(listVar).append(".size)\n")
                sb.append(indent).append("for (__elem in ").append(listVar).append(") {\n")
                emitListElementCoercion(sb, "$indent    ", "__elem", cat.inner, "$keyForError[*]", outVar)
                sb.append(indent).append("}\n")
                // Erase the Any? back to a typed list. We rely on JVM erasure;
                // the eventual constructor-call site declares the typed param.
                sb.append(indent).append("@Suppress(\"UNCHECKED_CAST\")\n")
                sb.append(indent).append("val ").append(resultVar).append(" = ").append(outVar)
                    .append(" as List<Any?>\n")
            }
            is TypeCategory.MapOf -> {
                val mapVar = freshDecoderTmp()
                val outVar = freshDecoderTmp()
                sb.append(indent).append("@Suppress(\"UNCHECKED_CAST\")\n")
                sb.append(indent).append("val ").append(mapVar).append(" = ").append(srcExpr)
                    .append(" as? Map<String, Any?> ?: throw IllegalArgumentException(\"expected JSON object at '")
                    .append(escapeForKotlinString(keyForError)).append("'\")\n")
                sb.append(indent).append("val ").append(outVar)
                    .append(" = LinkedHashMap<String, Any?>(").append(mapVar).append(".size)\n")
                sb.append(indent).append("for ((__k, __v) in ").append(mapVar).append(") {\n")
                emitMapValueCoercion(sb, "$indent    ", "__v", cat.value, "$keyForError[__k]", outVar)
                sb.append(indent).append("}\n")
                sb.append(indent).append("@Suppress(\"UNCHECKED_CAST\")\n")
                sb.append(indent).append("val ").append(resultVar).append(" = ").append(outVar)
                    .append(" as Map<String, Any?>\n")
            }
            is TypeCategory.Nullable -> {
                // Reached when the outer is, e.g. Nullable→inner; but the
                // outer wrapper is unwrapped in emitDecoderForParam. If we
                // somehow get here (e.g., List<T?>), unwrap inline.
                sb.append(indent).append("val ").append(resultVar).append(" = if (")
                    .append(srcExpr).append(" == null) null else {\n")
                emitInnerCoercion(sb, "$indent    ", srcExpr, cat.inner, keyForError, "__nn")
                sb.append(indent).append("    __nn\n")
                sb.append(indent).append("}\n")
            }
            is TypeCategory.SecretOf, is TypeCategory.Unsupported -> {
                // Should have been filtered upstream.
                sb.append(indent).append("val ").append(resultVar)
                    .append(" = throw IllegalStateException(\"unreachable secret/unsupported\")\n")
            }
        }
    }

    /** Bind result of coercion into [collector].add(...) instead of a val. */
    private fun emitListElementCoercion(
        sb: StringBuilder,
        indent: String,
        srcExpr: String,
        cat: TypeCategory,
        keyForError: String,
        collector: String,
    ) {
        // For nullable elements we need to short-circuit on null first.
        if (cat is TypeCategory.Nullable) {
            sb.append(indent).append("if (").append(srcExpr).append(" == null) {\n")
            sb.append(indent).append("    ").append(collector).append(".add(null)\n")
            sb.append(indent).append("} else {\n")
            emitInnerCoercion(sb, "$indent    ", srcExpr, cat.inner, keyForError, "__nn")
            sb.append(indent).append("    ").append(collector).append(".add(__nn)\n")
            sb.append(indent).append("}\n")
        } else {
            sb.append(indent).append("if (").append(srcExpr)
                .append(" == null) throw IllegalArgumentException(\"unexpected null in list at '")
                .append(escapeForKotlinString(keyForError)).append("'\")\n")
            emitInnerCoercion(sb, indent, srcExpr, cat, keyForError, "__nn")
            sb.append(indent).append(collector).append(".add(__nn)\n")
        }
    }

    /** Bind result of coercion into [collector][__k] = ... instead of a val. */
    private fun emitMapValueCoercion(
        sb: StringBuilder,
        indent: String,
        srcExpr: String,
        cat: TypeCategory,
        keyForError: String,
        collector: String,
    ) {
        if (cat is TypeCategory.Nullable) {
            sb.append(indent).append("if (").append(srcExpr).append(" == null) {\n")
            sb.append(indent).append("    ").append(collector).append("[__k] = null\n")
            sb.append(indent).append("} else {\n")
            emitInnerCoercion(sb, "$indent    ", srcExpr, cat.inner, keyForError, "__nn")
            sb.append(indent).append("    ").append(collector).append("[__k] = __nn\n")
            sb.append(indent).append("}\n")
        } else {
            sb.append(indent).append("if (").append(srcExpr)
                .append(" == null) throw IllegalArgumentException(\"unexpected null in map at '")
                .append(escapeForKotlinString(keyForError)).append("'\")\n")
            emitInnerCoercion(sb, indent, srcExpr, cat, keyForError, "__nn")
            sb.append(indent).append(collector).append("[__k] = __nn\n")
        }
    }

    private fun emitPrimitiveCoercion(
        sb: StringBuilder,
        indent: String,
        srcExpr: String,
        fqn: String,
        keyForError: String,
        resultVar: String,
    ) {
        // The runtime parser produces:
        //   - String for JSON strings
        //   - Boolean for true/false
        //   - Long for integer numbers (always — even ones a user typed expecting Int)
        //   - Double for fractional/exponent numbers
        // So Int/Short/Byte/Float fields require an explicit coercion off Long
        // (or in Float's case, off Double). Match the runtime's permissive
        // accept-Long-as-Double rule for floating fields too.
        val errMsg = "expected ${fqn.substringAfterLast('.').lowercase()} at '" +
            escapeForKotlinString(keyForError) + "'"
        when (fqn) {
            "kotlin.String" -> {
                sb.append(indent).append("val ").append(resultVar).append(" = ").append(srcExpr)
                    .append(" as? String ?: throw IllegalArgumentException(\"")
                    .append(errMsg).append("\")\n")
            }
            "kotlin.Boolean" -> {
                sb.append(indent).append("val ").append(resultVar).append(" = ").append(srcExpr)
                    .append(" as? Boolean ?: throw IllegalArgumentException(\"")
                    .append(errMsg).append("\")\n")
            }
            "kotlin.Int" -> {
                sb.append(indent).append("val ").append(resultVar).append(" = (").append(srcExpr)
                    .append(" as? Long)?.toInt() ?: throw IllegalArgumentException(\"")
                    .append(errMsg).append("\")\n")
            }
            "kotlin.Long" -> {
                sb.append(indent).append("val ").append(resultVar).append(" = ").append(srcExpr)
                    .append(" as? Long ?: throw IllegalArgumentException(\"")
                    .append(errMsg).append("\")\n")
            }
            "kotlin.Short" -> {
                sb.append(indent).append("val ").append(resultVar).append(" = (").append(srcExpr)
                    .append(" as? Long)?.toShort() ?: throw IllegalArgumentException(\"")
                    .append(errMsg).append("\")\n")
            }
            "kotlin.Byte" -> {
                sb.append(indent).append("val ").append(resultVar).append(" = (").append(srcExpr)
                    .append(" as? Long)?.toByte() ?: throw IllegalArgumentException(\"")
                    .append(errMsg).append("\")\n")
            }
            "kotlin.Double" -> {
                // Accept either Double (real fractional) or Long (integer
                // typed as a JSON number without a decimal point), matching
                // the runtime decoder.
                sb.append(indent).append("val ").append(resultVar).append(" = when (val __n = ")
                    .append(srcExpr).append(") {\n")
                sb.append(indent).append("    is Double -> __n\n")
                sb.append(indent).append("    is Long -> __n.toDouble()\n")
                sb.append(indent).append("    else -> throw IllegalArgumentException(\"")
                    .append(errMsg).append("\")\n")
                sb.append(indent).append("}\n")
            }
            "kotlin.Float" -> {
                sb.append(indent).append("val ").append(resultVar).append(" = when (val __n = ")
                    .append(srcExpr).append(") {\n")
                sb.append(indent).append("    is Double -> __n.toFloat()\n")
                sb.append(indent).append("    is Long -> __n.toFloat()\n")
                sb.append(indent).append("    else -> throw IllegalArgumentException(\"")
                    .append(errMsg).append("\")\n")
                sb.append(indent).append("}\n")
            }
            else -> {
                sb.append(indent).append("val ").append(resultVar)
                    .append(" = throw IllegalStateException(\"unsupported primitive ").append(fqn).append("\")\n")
            }
        }
    }

    private var decoderTmpCounter = 0
    private fun freshDecoderTmp(): String = "__d${decoderTmpCounter++}"

    // Render a TypeCategory back into its Kotlin source-level type string.
    // Used to drive `@Suppress("UNCHECKED_CAST") val v: T = ... as T` so the
    // constructor call compiles when the param is e.g. List<String>.
    private fun renderType(cat: TypeCategory): String = when (cat) {
        is TypeCategory.Primitive -> cat.fqn.removePrefix("kotlin.")
        is TypeCategory.NestedDeriveJson -> cat.fqn
        is TypeCategory.Nullable -> renderType(cat.inner) + "?"
        is TypeCategory.ListOf -> "List<" + renderType(cat.inner) + ">"
        is TypeCategory.MapOf -> "Map<String, " + renderType(cat.value) + ">"
        is TypeCategory.SecretOf -> "dev.kpp.secret.Secret<*>"
        is TypeCategory.Unsupported -> "kotlin.Any?"
    }

    private fun extractorFnName(simple: String): String = "${simple}fromMap_internal"

    private fun escapeForKotlinString(s: String): String {
        val sb = StringBuilder()
        for (c in s) {
            when (c) {
                '\\' -> sb.append("\\\\")
                '"' -> sb.append("\\\"")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                '$' -> sb.append("\\$")
                else -> sb.append(c)
            }
        }
        return sb.toString()
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

private data class DecoderParam(
    val propName: String,
    val jsonKey: String,
    val category: TypeCategory,
    val isNullable: Boolean,
    val hasDefault: Boolean,
    val ignored: Boolean,
)
