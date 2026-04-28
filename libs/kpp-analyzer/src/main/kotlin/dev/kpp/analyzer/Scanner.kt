package dev.kpp.analyzer

import java.io.File

/**
 * Per-file suppression directives.
 *
 * - [fileLevel] holds rule ids suppressed by `@file:Suppress("KPPxxx", ...)` at the top of the file.
 * - [perLine] maps a 1-based line number to the rule ids suppressed on that line. The set of lines
 *   covered by a `// noinspection` comment includes the line directly below the comment AND the
 *   line carrying the comment itself (for trailing same-line suppressions on the offending statement).
 */
data class Suppressions(
    val fileLevel: Set<String>,
    val perLine: Map<Int, Set<String>>,
) {
    fun isSuppressed(ruleId: String, line: Int): Boolean {
        if (ruleId in fileLevel) return true
        val onLine = perLine[line] ?: return false
        return ruleId in onLine
    }
}

class KppScanner(val roots: List<File>) {

    fun scan(): List<Violation> {
        val ktFiles = roots.flatMap { root ->
            if (!root.exists()) emptyList()
            else root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        }

        // Pre-pass: discover @MustHandle functions across the whole tree so KPP001 can
        // recognise calls to them by name. Name-based only — we have no real symbol table.
        // Use masked content so @MustHandle / fun ... appearing inside test-fixture
        // string literals doesn't add fake names to the must-handle set.
        val mustHandleNames = collectMustHandleNames(ktFiles)
        // Same idea for KPP008: collect @Io / @Db function names across the tree.
        // Names that also appear in mustHandleNames are removed below so KPP001
        // (higher severity) is the only rule that fires for those call sites.
        val ioOrDbNames = collectIoOrDbNames(ktFiles) - mustHandleNames

        val out = mutableListOf<Violation>()
        // file -> suppressions, computed once per file.
        val suppressionsByFile = mutableMapOf<File, Suppressions>()
        // Maps a (file, violationLine) for KPP005 to its construct-anchor line (the `class`
        // header line) so a suppression on the class line silences violations recorded
        // anywhere in that class's primary-constructor slice.
        val kpp005Anchors = mutableMapOf<Pair<File, Int>, Int>()

        for (file in ktFiles) {
            val rawLines = runCatching { file.readLines() }.getOrNull() ?: continue
            // Suppressions are read from raw text so the rule-id string literals
            // inside @file:Suppress("KPPxxx") and // noinspection comments survive.
            suppressionsByFile[file] = collectSuppressions(rawLines)
            // Mask string literals (incl. triple-quoted) and line comments before
            // running rule heuristics. This avoids the analyzer dogfooding itself:
            // ScannerTest.kt embeds Kotlin code samples inside triple-quoted strings,
            // and without masking those samples trip the same regex rules they assert.
            val maskedLines = maskFile(rawLines)
            scanFile(file, maskedLines, mustHandleNames, ioOrDbNames, out, kpp005Anchors)
        }

        return out.filter { v ->
            val sup = suppressionsByFile[v.file] ?: return@filter true
            if (sup.isSuppressed(v.ruleId, v.line)) return@filter false
            // KPP005 may also be silenced by a suppression on the construct anchor line.
            val anchor = kpp005Anchors[v.file to v.line]
            if (anchor != null && sup.isSuppressed(v.ruleId, anchor)) return@filter false
            true
        }
    }

    private fun collectMustHandleNames(files: List<File>): Set<String> {
        val names = mutableSetOf<String>()
        val funDecl = Regex("""\bfun\s+(?:<[^>]+>\s+)?([A-Za-z_][A-Za-z0-9_]*)\s*\(""")
        for (f in files) {
            val raw = runCatching { f.readLines() }.getOrNull() ?: continue
            val lines = maskFile(raw)
            var armed = false
            for (line in lines) {
                val trimmed = line.trim()
                if (trimmed.startsWith("//")) continue
                if (trimmed.contains("@MustHandle")) {
                    armed = true
                    // annotation may be on its own line OR same line as the fun
                    val m = funDecl.find(trimmed)
                    if (m != null) {
                        names += m.groupValues[1]
                        armed = false
                    }
                    continue
                }
                if (armed) {
                    val m = funDecl.find(trimmed)
                    if (m != null) {
                        names += m.groupValues[1]
                        armed = false
                    } else if (trimmed.isNotBlank() && !trimmed.startsWith("@")) {
                        // armed state only carries across blank/annotation lines
                        armed = false
                    }
                }
            }
        }
        return names
    }

    // Sibling of collectMustHandleNames for KPP008. Collects function names
    // declared with @Io and/or @Db on their annotation block. The arming
    // discipline mirrors the must-handle collector: stay armed across blank
    // and other annotation lines, disarm on any other declaration.
    private fun collectIoOrDbNames(files: List<File>): Set<String> {
        val names = mutableSetOf<String>()
        val funDecl = Regex("""\bfun\s+(?:<[^>]+>\s+)?([A-Za-z_][A-Za-z0-9_]*)\s*\(""")
        for (f in files) {
            val raw = runCatching { f.readLines() }.getOrNull() ?: continue
            val lines = maskFile(raw)
            var armed = false
            for (line in lines) {
                val trimmed = line.trim()
                if (trimmed.startsWith("//")) continue
                if (trimmed.contains("@Io") || trimmed.contains("@Db")) {
                    armed = true
                    val m = funDecl.find(trimmed)
                    if (m != null) {
                        names += m.groupValues[1]
                        armed = false
                    }
                    continue
                }
                if (armed) {
                    val m = funDecl.find(trimmed)
                    if (m != null) {
                        names += m.groupValues[1]
                        armed = false
                    } else if (trimmed.isNotBlank() && !trimmed.startsWith("@")) {
                        armed = false
                    }
                }
            }
        }
        return names
    }

    // `lines` here is already masked (string literals + line comments replaced
    // with spaces, length-preserving) so substring/regex tests fire on logical
    // Kotlin code only. Indices/columns still align with the original file.
    private fun scanFile(
        file: File,
        lines: List<String>,
        mustHandleNames: Set<String>,
        ioOrDbNames: Set<String>,
        out: MutableList<Violation>,
        kpp005Anchors: MutableMap<Pair<File, Int>, Int>,
    ) {
        // KPP017: production-code reflection. Skip test/testFixtures sources
        // entirely; otherwise emit a single violation pointing at the first
        // `import kotlin.reflect...` line in the file.
        run {
            val abs = file.absolutePath
            val isTestSource = abs.contains("/src/test/") || abs.contains("/src/testFixtures/")
            if (!isTestSource) {
                val reflectImport = Regex("""^import\s+kotlin\.reflect(?:\.|\b)""")
                for ((idx, raw) in lines.withIndex()) {
                    if (reflectImport.containsMatchIn(raw)) {
                        out += Violation(
                            ruleId = "KPP017",
                            file = file,
                            line = idx + 1,
                            column = 1,
                            message = "production code imports kotlin.reflect.*; use @file:Suppress(\"KPP017\") if intentional",
                        )
                        break
                    }
                }
            }
        }

        // Brace-depth tracker for "inside suspend fun" detection.
        var suspendDepth = -1 // depth at which the current suspend body starts; -1 = none
        var depth = 0
        var inTry = false
        var tryDepth = -1

        val mutablePublic = Regex(
            """^\s*(public\s+)?fun\s+[^(]*\([^)]*\)\s*:\s*Mutable(List|Map|Set)\s*<""",
        )
        val publicFunDecl = Regex("""^\s*(public\s+)?fun\s+([A-Za-z_][A-Za-z0-9_]*)\s*\(""")
        // KPP013: a leading `var` declaration not preceded by an access modifier.
        // Matches at line start (after indent) so constructor params (which sit
        // inside `(...)` and never start a line with `var`) are excluded.
        val publicVarDecl = Regex("""^[\t ]*var\s+([A-Za-z_][A-Za-z0-9_]*)""")
        val varAccessModifier = Regex("""^[\t ]*(private|internal|protected)\b""")
        val standaloneCall = Regex("""^([A-Za-z_][A-Za-z0-9_]*)\s*\(""")
        val suspendFunDecl = Regex("""\bsuspend\s+fun\b""")
        // KPP002: catch-clause parameter type. Allow optional package qualifier
        // (e.g. java.lang.Exception) before the bare type name. Also accept `_`
        // as the parameter name.
        val rawCatchRegex = Regex(
            """\bcatch\s*\(\s*(?:[A-Za-z_][A-Za-z0-9_]*|_)\s*:\s*(?:[A-Za-z_][A-Za-z0-9_]*\.)*(Throwable|Exception|RuntimeException)\b""",
        )
        val blockingCalls = listOf(
            "Thread.sleep(",
            "runBlocking(",
            "runBlocking{",
            "URL(", // pair with .readText below
        )

        // Track @Throws annotation on the previous non-blank line for KPP018.
        var prevWasThrowsAnno = false
        // Track private/internal modifier so we don't flag non-public surface.
        val privateRegex = Regex("""^\s*(private|internal)\b""")

        // KPP005: walk @Immutable annotation markers to their class headers up-front.
        // Doing this in a dedicated forward pass keeps the multi-line constructor
        // slice logic isolated from the line-by-line loop below.
        scanImmutableClasses(file, lines, out, kpp005Anchors)

        // KPP007: superset of KPP005 — fire on ANY data class with a mutable
        // primary-constructor field, except those already covered by KPP005
        // (which is the @Immutable-annotated subset). Done in its own pass for
        // the same multi-line bookkeeping reason as KPP005.
        scanDataClasses(file, lines, out)

        // KPP013 prep: precompute, for each line, whether it sits inside any
        // function body. We walk the masked file once with proper brace + paren
        // tracking so multi-line `fun` signatures (where `(` and `{` are on
        // different lines) and nested fun definitions are handled correctly.
        val insideFunBody = computeInsideFunBody(lines)

        for ((idx, raw) in lines.withIndex()) {
            val line = raw
            val trimmed = line.trim()
            val lineNo = idx + 1

            // Update brace depth using a sanitized line so braces in strings don't skew.
            val sanitized = stripStringsAndComments(line)
            val opens = sanitized.count { it == '{' }
            val closes = sanitized.count { it == '}' }

            // KPP004: mutable public API return type.
            if (mutablePublic.containsMatchIn(line) && !privateRegex.containsMatchIn(line)) {
                out += Violation(
                    ruleId = "KPP004",
                    file = file,
                    line = lineNo,
                    column = 1,
                    message = "public function returns a mutable collection type",
                )
            }

            // KPP002: catching raw Throwable / Exception / RuntimeException.
            // Match against the sanitized line so `catch` tokens inside string
            // literals (e.g. fixture text) don't trip the rule.
            run {
                val rcMatch = rawCatchRegex.find(sanitized)
                if (rcMatch != null) {
                    val type = rcMatch.groupValues[1]
                    out += Violation(
                        ruleId = "KPP002",
                        file = file,
                        line = lineNo,
                        column = rcMatch.range.first + 1,
                        message = "catching '$type' is too broad — catch a specific exception class or use Result<T, E>",
                    )
                }
            }

            // KPP018: public fun that throws or has @Throws.
            val pf = publicFunDecl.find(line)
            if (pf != null && !privateRegex.containsMatchIn(line)) {
                if (prevWasThrowsAnno) {
                    out += Violation(
                        ruleId = "KPP018",
                        file = file,
                        line = lineNo,
                        column = 1,
                        message = "public function declares @Throws; exceptions should not escape public API",
                    )
                }
                // Inline body with `throw` and no surrounding try.
                if (line.contains("throw ") && !line.contains("try ")) {
                    out += Violation(
                        ruleId = "KPP018",
                        file = file,
                        line = lineNo,
                        column = 1,
                        message = "public function throws without surrounding try/catch",
                    )
                }
            }

            // KPP018: bare `throw` inside a public fun body (multi-line case).
            // Re-detect public fun start by scanning recent context: we approximate by
            // tracking a flag set when we enter a public fun block.
            // (simple state below)

            // KPP011: blocking inside suspend.
            if (suspendDepth >= 0) {
                for (b in blockingCalls) {
                    if (line.contains(b)) {
                        out += Violation(
                            ruleId = "KPP011",
                            file = file,
                            line = lineNo,
                            column = line.indexOf(b) + 1,
                            message = "blocking call '${b.removeSuffix("(").removeSuffix("{")}' inside suspend function",
                        )
                    }
                }
                if (line.contains(".readText(")) {
                    out += Violation(
                        ruleId = "KPP011",
                        file = file,
                        line = lineNo,
                        column = line.indexOf(".readText(") + 1,
                        message = "blocking call '.readText' inside suspend function",
                    )
                }
            }

            // KPP001: ignored @MustHandle return.
            // Heuristic: line starts with funcName(...) where funcName is in the must-handle set.
            // Skip if line looks like an assignment, return, val/var, chained call, or comment.
            val m = standaloneCall.find(trimmed)
            if (m != null) {
                val name = m.groupValues[1]
                // Detect chaining: after the matched `name(`, find the closing
                // `)` at paren depth 0 and check if the next non-space char is
                // `.` (e.g. `fetch().toLowerCase()`). If so, the result flows
                // into the chain and isn't actually discarded.
                val chained = run {
                    val open = trimmed.indexOf('(', startIndex = m.range.last)
                    if (open < 0) false else {
                        var depth = 0
                        var k = open
                        var closeIdx = -1
                        while (k < trimmed.length) {
                            val ch = trimmed[k]
                            if (ch == '(') depth++
                            else if (ch == ')') {
                                depth--
                                if (depth == 0) { closeIdx = k; break }
                            }
                            k++
                        }
                        if (closeIdx < 0) false else {
                            var j = closeIdx + 1
                            while (j < trimmed.length && trimmed[j] == ' ') j++
                            j < trimmed.length && trimmed[j] == '.'
                        }
                    }
                }
                val excluded = trimmed.startsWith("//") ||
                    trimmed.startsWith("/*") ||
                    trimmed.startsWith("return") ||
                    trimmed.startsWith("val ") ||
                    trimmed.startsWith("var ") ||
                    trimmed.startsWith("fun ") ||
                    trimmed.startsWith(".") ||
                    trimmed.startsWith("it.") ||
                    trimmed.startsWith("@") ||
                    trimmed.startsWith("if ") ||
                    trimmed.startsWith("when ") ||
                    trimmed.startsWith("for ") ||
                    trimmed.startsWith("while ") ||
                    name == "if" || name == "when" || name == "for" || name == "while" ||
                    trimmed.contains(" = ") ||
                    trimmed.startsWith("=") ||
                    chained
                if (!excluded && name in mustHandleNames) {
                    out += Violation(
                        ruleId = "KPP001",
                        file = file,
                        line = lineNo,
                        column = (line.indexOf(name) + 1).coerceAtLeast(1),
                        message = "return value of @MustHandle function '$name' is discarded",
                    )
                } else if (!excluded && name in ioOrDbNames) {
                    // KPP008. Disjoint from KPP001 by construction: ioOrDbNames had
                    // mustHandleNames removed up front, so a function annotated both
                    // @MustHandle and @Io/@Db only fires KPP001 (higher severity).
                    out += Violation(
                        ruleId = "KPP008",
                        file = file,
                        line = lineNo,
                        column = (line.indexOf(name) + 1).coerceAtLeast(1),
                        message = "return value of side-effecting function '$name' (@Io/@Db) is discarded",
                    )
                }
            }

            // KPP013: leading `var` declaration. We only fire when the line is NOT
            // inside any function body (precomputed in `insideFunBody`) so locals
            // are excluded. The leading-`var` regex naturally excludes constructor
            // parameters since those sit inside a `(...)` list and never start a
            // line with `var` — they're a different rule's responsibility.
            if (!insideFunBody[idx]) {
                val pv = publicVarDecl.find(sanitized)
                if (pv != null && !varAccessModifier.containsMatchIn(sanitized)) {
                    out += Violation(
                        ruleId = "KPP013",
                        file = file,
                        line = lineNo,
                        column = sanitized.indexOf("var") + 1,
                        message = "public var '${pv.groupValues[1]}' — prefer val (with private var backing) for mutability",
                    )
                }
            }

            // Update suspend-body tracking after analysis so the declaration line itself
            // is not flagged as "inside" the body.
            if (suspendFunDecl.containsMatchIn(line) && line.contains("{")) {
                // Entering a suspend body; remember the depth BEFORE this line's opens.
                suspendDepth = depth
            }

            depth += opens - closes
            if (suspendDepth >= 0 && depth <= suspendDepth) {
                suspendDepth = -1
            }

            // Track @Throws annotation carrying to next line.
            prevWasThrowsAnno = trimmed.startsWith("@Throws(") || trimmed == "@Throws"

            // try/finally housekeeping is intentionally omitted; KPP018 detection
            // uses the simpler "throw on same line" heuristic plus @Throws marker.
            @Suppress("UNUSED_VARIABLE") val _unused = inTry to tryDepth
        }
    }

    // KPP005 helper. We do this in a forward sweep over @Immutable annotation
    // markers because the construct can span many lines (annotation, optional other
    // annotations, then the `class`/`data class` header, then a multi-line primary
    // constructor parameter list). Scanning here keeps that bookkeeping out of
    // the line-by-line loop in scanFile.
    private fun scanImmutableClasses(
        file: File,
        lines: List<String>,
        out: MutableList<Violation>,
        anchors: MutableMap<Pair<File, Int>, Int>,
    ) {
        val classHeader = Regex("""^\s*(?:public\s+|internal\s+|private\s+)?(?:open\s+|abstract\s+|sealed\s+|final\s+)?(?:data\s+)?class\s+([A-Za-z_][A-Za-z0-9_]*)""")
        // Match a field whose declared type is one of the mutable collection forms.
        val mutableCollectionParam = Regex(
            """(?:^|[(,\s])(?:val|var)\s+([A-Za-z_][A-Za-z0-9_]*)\s*:\s*(MutableList|MutableMap|MutableSet|ArrayList|HashMap|HashSet)\b""",
        )
        // Match a `var` parameter (any type). Anchored on `(`, `,`, or whitespace
        // so we don't catch the keyword `var` appearing elsewhere.
        val varParam = Regex(
            """(?:^|[(,\s])var\s+([A-Za-z_][A-Za-z0-9_]*)\s*:""",
        )

        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            val trimmed = line.trim()
            if (!trimmed.contains("@Immutable")) { i++; continue }

            // Walk forward until we find the class header (could be the same line).
            var j = i
            var headerLine = -1
            while (j < lines.size) {
                if (classHeader.containsMatchIn(lines[j])) { headerLine = j; break }
                // Allow only annotation/blank lines between @Immutable and the class.
                val tj = lines[j].trim()
                val ok = j == i || tj.isEmpty() || tj.startsWith("@") || tj.startsWith("//")
                if (!ok) break
                j++
            }
            if (headerLine == -1) { i++; continue }

            // Collect the slice covering the primary-constructor parameter list.
            // Concatenate from the class header until the matching `)` (depth 0).
            val sliceLines = mutableListOf<Pair<Int, String>>() // (1-based lineNo, sanitized text)
            var paren = 0
            var seenOpen = false
            var k = headerLine
            while (k < lines.size) {
                val sanitized = stripStringsAndComments(lines[k])
                sliceLines += (k + 1) to sanitized
                for (ch in sanitized) {
                    if (ch == '(') { paren++; seenOpen = true }
                    else if (ch == ')') { paren-- }
                }
                if (seenOpen && paren <= 0) break
                k++
            }
            // If we never saw an opening `(`, the class has no primary constructor; nothing to flag.
            if (!seenOpen) { i = headerLine + 1; continue }

            val anchorLineNo = headerLine + 1
            // Track field names already flagged so a `var x: MutableList<...>` doesn't
            // produce two violations for the same parameter.
            val flagged = mutableSetOf<String>()
            for ((lineNo, text) in sliceLines) {
                // Skip the part of the header text BEFORE the first `(` so we don't
                // accidentally interpret class type-parameters as fields.
                val effective = if (lineNo == anchorLineNo) {
                    val open = text.indexOf('(')
                    if (open >= 0) text.substring(open) else continue
                } else text

                for (m in mutableCollectionParam.findAll(effective)) {
                    val fieldName = m.groupValues[1]
                    val typeName = m.groupValues[2]
                    if (!flagged.add(fieldName)) continue
                    out += Violation(
                        ruleId = "KPP005",
                        file = file,
                        line = lineNo,
                        column = 1,
                        message = "@Immutable data class has '$fieldName: $typeName' which is mutable; use val + ImmutableList/Map/Set",
                    )
                    anchors[file to lineNo] = anchorLineNo
                }
                for (m in varParam.findAll(effective)) {
                    val fieldName = m.groupValues[1]
                    if (!flagged.add(fieldName)) continue
                    out += Violation(
                        ruleId = "KPP005",
                        file = file,
                        line = lineNo,
                        column = 1,
                        message = "@Immutable data class has 'var $fieldName' which is mutable; use val + ImmutableList/Map/Set",
                    )
                    anchors[file to lineNo] = anchorLineNo
                }
            }

            // Advance past this class to avoid re-matching the same @Immutable.
            i = k + 1
        }
    }

    // KPP007. Same heuristic as KPP005 (walk the primary-constructor parameter
    // slice and flag `var` or mutable-collection-typed fields), but the class
    // precondition is "any data class" rather than "@Immutable-annotated".
    //
    // Dedup with KPP005: we look back from the `data class` header over blank
    // lines, line comments, and other annotation lines; if we encounter an
    // `@Immutable` annotation in that look-back, we skip the class entirely so
    // the more-specific KPP005 is the only rule that fires.
    private fun scanDataClasses(
        file: File,
        lines: List<String>,
        out: MutableList<Violation>,
    ) {
        val dataClassHeader = Regex("""^\s*(?:[A-Za-z]+\s+)*data\s+class\s+([A-Za-z_][A-Za-z0-9_]*)""")
        val mutableCollectionParam = Regex(
            """(?:^|[(,\s])(?:val|var)\s+([A-Za-z_][A-Za-z0-9_]*)\s*:\s*(MutableList|MutableMap|MutableSet|ArrayList|HashMap|HashSet)\b""",
        )
        val varParam = Regex(
            """(?:^|[(,\s])var\s+([A-Za-z_][A-Za-z0-9_]*)\s*:""",
        )

        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            if (!dataClassHeader.containsMatchIn(line)) { i++; continue }

            // Look back over blank lines, line comments, and annotation lines
            // to determine whether this data class is @Immutable-marked. If it
            // is, KPP005 covers it and we skip to avoid double-firing.
            var hasImmutable = false
            var b = i - 1
            while (b >= 0) {
                val tb = lines[b].trim()
                if (tb.isEmpty() || tb.startsWith("//")) { b--; continue }
                if (tb.startsWith("@")) {
                    if (tb.contains("@Immutable")) { hasImmutable = true; break }
                    b--; continue
                }
                break
            }
            if (hasImmutable) { i++; continue }

            // Collect the primary-constructor slice — same shape as KPP005.
            val sliceLines = mutableListOf<Pair<Int, String>>()
            var paren = 0
            var seenOpen = false
            var k = i
            while (k < lines.size) {
                val sanitized = stripStringsAndComments(lines[k])
                sliceLines += (k + 1) to sanitized
                for (ch in sanitized) {
                    if (ch == '(') { paren++; seenOpen = true }
                    else if (ch == ')') { paren-- }
                }
                if (seenOpen && paren <= 0) break
                k++
            }
            if (!seenOpen) { i++; continue }

            val anchorLineNo = i + 1
            val flagged = mutableSetOf<String>()
            for ((lineNo, text) in sliceLines) {
                val effective = if (lineNo == anchorLineNo) {
                    val open = text.indexOf('(')
                    if (open >= 0) text.substring(open) else continue
                } else text

                for (m in mutableCollectionParam.findAll(effective)) {
                    val fieldName = m.groupValues[1]
                    val typeName = m.groupValues[2]
                    if (!flagged.add(fieldName)) continue
                    out += Violation(
                        ruleId = "KPP007",
                        file = file,
                        line = lineNo,
                        column = 1,
                        message = "data class has '$fieldName: $typeName' which is mutable; use val + ImmutableList/Map/Set",
                    )
                }
                for (m in varParam.findAll(effective)) {
                    val fieldName = m.groupValues[1]
                    if (!flagged.add(fieldName)) continue
                    out += Violation(
                        ruleId = "KPP007",
                        file = file,
                        line = lineNo,
                        column = 1,
                        message = "data class has 'var $fieldName' which is mutable; use val + ImmutableList/Map/Set",
                    )
                }
            }

            i = k + 1
        }
    }

    // Compute, for each masked line, whether it sits inside the body of any
    // function (`fun ... (...) { ... }`). Used by KPP013 to suppress firings
    // on local `var` declarations.
    //
    // Strategy: walk each masked line character by character, maintaining
    // (a) a global brace depth and (b) a stack of "fun-body frames" recording
    // the brace depth at which each currently-open function body was entered.
    // We separately track whether we're inside a `fun` signature (between
    // `fun` keyword and the `{` that opens its body) so multi-line signatures
    // are handled. A line is "inside fun body" if at any point during its
    // scan the fun-body frame stack was non-empty BEFORE the position the
    // `var` keyword would appear; for simplicity we mark the entire line as
    // inside if the stack is non-empty at the START of the line, OR if a
    // body opens earlier on the same line. The KPP013 regex anchors at the
    // start of the line, so this approximation is safe for our heuristic.
    private fun computeInsideFunBody(lines: List<String>): BooleanArray {
        val res = BooleanArray(lines.size)
        var depth = 0
        // Stack of brace depths at which a fun body was entered.
        val frames = ArrayDeque<Int>()
        // States for a pending `fun` signature: SAW_FUN -> waiting for `(`,
        // IN_PARAMS -> consuming `(...)`, AWAIT_BRACE -> waiting for `{`.
        var sawFun = false
        var paren = 0
        var awaitBrace = false

        val funKw = Regex("""\bfun\b""")

        // Tokens that, when starting a new line, signal we've left the previous
        // function declaration without ever opening a body — so a stale
        // `awaitBrace` from an abstract / single-expression / interface method
        // declaration must be cleared so the NEXT `{` (e.g. a class body) is
        // not misinterpreted as the function body.
        val declStartRegex = Regex("""^\s*(class\b|interface\b|object\b|enum\b|companion\b|abstract\b|open\b|sealed\b|final\b|override\b|private\b|internal\b|public\b|protected\b|val\b|var\b|fun\b|@|}|init\b|constructor\b)""")

        for ((idx, raw) in lines.withIndex()) {
            // If a previous fun signature is still awaiting a `{` but this line
            // starts a new declaration, the previous fun has no body (abstract
            // or single-expression). Clear the pending state.
            if (awaitBrace && declStartRegex.containsMatchIn(raw)) {
                awaitBrace = false
            }

            // Mark the line based on fun-stack state at line start.
            res[idx] = frames.isNotEmpty()

            // Find any `fun` keyword occurrences on this line; they arm the
            // signature scan. We use a regex match list to avoid false positives
            // on identifiers like `funky` (the \b boundaries handle this).
            val funStarts = funKw.findAll(raw).map { it.range.first }.toList().toMutableList()
            var nextFunIdx = 0

            var i = 0
            while (i < raw.length) {
                // Activate sawFun when we cross a `fun` keyword position on this line.
                while (nextFunIdx < funStarts.size && i >= funStarts[nextFunIdx]) {
                    sawFun = true
                    paren = 0
                    awaitBrace = false
                    nextFunIdx++
                }
                val c = raw[i]
                when (c) {
                    '(' -> {
                        if (sawFun) paren++
                    }
                    ')' -> {
                        if (sawFun && paren > 0) {
                            paren--
                            if (paren == 0) {
                                awaitBrace = true
                                sawFun = false
                            }
                        }
                    }
                    '=' -> {
                        // Single-expression function body (`fun foo() = expr`):
                        // there is no `{`, so cancel the pending body wait so we
                        // don't misclassify a later unrelated `{` as the body.
                        if (awaitBrace) awaitBrace = false
                    }
                    '{' -> {
                        if (awaitBrace) {
                            // Body opens here at the current brace depth.
                            frames.addLast(depth)
                            awaitBrace = false
                            // Lines that open a fun body partway through still
                            // contain local code after the `{`. The KPP013 regex
                            // anchors at line start (var must be at indent), so
                            // this case only matters if a `var` appears before
                            // the `{` on the same line — which would be a fun
                            // *parameter*, not a leading `var` line. Safe.
                        }
                        depth++
                    }
                    '}' -> {
                        depth--
                        // Pop any fun-body frames we've now closed.
                        while (frames.isNotEmpty() && depth <= frames.last()) {
                            frames.removeLast()
                        }
                    }
                }
                i++
            }
        }
        return res
    }

    // Pre-scan suppression directives. Recognises:
    //   @file:Suppress("KPPxxx", "KPPyyy")    (file-level)
    //   // noinspection KPPxxx, KPPyyy        (applies to NEXT non-comment line and
    //                                          to the comment line itself for
    //                                          trailing same-line use).
    private fun collectSuppressions(lines: List<String>): Suppressions {
        val fileLevel = mutableSetOf<String>()
        val perLine = mutableMapOf<Int, MutableSet<String>>()

        val fileSuppress = Regex("""@file:Suppress\s*\(([^)]*)\)""")
        val noinspection = Regex("""//\s*noinspection\s+([A-Za-z0-9_,\s]+)""", RegexOption.IGNORE_CASE)
        val ruleId = Regex("""KPP\d+""")

        for ((idx, raw) in lines.withIndex()) {
            val lineNo = idx + 1
            val fs = fileSuppress.find(raw)
            if (fs != null) {
                ruleId.findAll(fs.groupValues[1]).forEach { fileLevel += it.value }
            }
            val ni = noinspection.find(raw)
            if (ni != null) {
                val ids = ruleId.findAll(ni.groupValues[1]).map { it.value }.toSet()
                if (ids.isNotEmpty()) {
                    // Trailing on same statement line.
                    perLine.getOrPut(lineNo) { mutableSetOf() }.addAll(ids)
                    // Standalone comment above the next non-blank, non-comment line.
                    var nxt = idx + 1
                    while (nxt < lines.size) {
                        val t = lines[nxt].trim()
                        if (t.isEmpty() || t.startsWith("//")) { nxt++; continue }
                        perLine.getOrPut(nxt + 1) { mutableSetOf() }.addAll(ids)
                        break
                    }
                }
            }
        }
        return Suppressions(fileLevel = fileLevel, perLine = perLine.mapValues { it.value.toSet() })
    }

    // Length-preserving mask of every line in [rawLines]. String contents
    // (single-quoted "...", char-literals '...', and triple-quoted """...""")
    // and `// ...` line comments are replaced with spaces. Triple-quoted
    // strings span lines, so this carries open-state across the array.
    //
    // Why: ScannerTest.kt (this analyzer's own test source) holds Kotlin
    // sample code inside triple-quoted string fixtures. Without masking,
    // those samples trip the very rules the tests assert — a dogfooding
    // false-positive class. Masking means the rules see only logical
    // Kotlin code, never text inside string literals.
    private fun maskFile(rawLines: List<String>): List<String> {
        val out = ArrayList<String>(rawLines.size)
        var inTriple = false
        for (raw in rawLines) {
            val sb = StringBuilder(raw.length)
            var i = 0
            if (inTriple) {
                // Already inside a triple-quoted string from a prior line.
                // Look for the closing """; mask everything up to and
                // including it.
                val close = raw.indexOf("\"\"\"")
                if (close < 0) {
                    // entire line is inside the string
                    repeat(raw.length) { sb.append(' ') }
                    out += sb.toString()
                    continue
                }
                repeat(close + 3) { sb.append(' ') }
                i = close + 3
                inTriple = false
            }
            // Outside any triple string from here onward (until we see one open).
            var inStr = false
            var strChar = '"'
            while (i < raw.length) {
                val c = raw[i]
                if (!inStr && c == '/' && i + 1 < raw.length && raw[i + 1] == '/') {
                    while (i < raw.length) { sb.append(' '); i++ }
                    break
                }
                if (!inStr && c == '"' && i + 2 < raw.length && raw[i + 1] == '"' && raw[i + 2] == '"') {
                    // Opening triple quote on this line. Mask the opener,
                    // then scan for a closing """ on the same line.
                    sb.append("   "); i += 3
                    val close = raw.indexOf("\"\"\"", i)
                    if (close < 0) {
                        // remainder of line is inside the triple string
                        while (i < raw.length) { sb.append(' '); i++ }
                        inTriple = true
                        break
                    } else {
                        // mask body and closing """
                        while (i < close + 3) { sb.append(' '); i++ }
                        continue
                    }
                }
                if (!inStr && (c == '"' || c == '\'')) {
                    inStr = true; strChar = c; sb.append(' '); i++; continue
                }
                if (inStr) {
                    if (c == '\\' && i + 1 < raw.length) { sb.append("  "); i += 2; continue }
                    if (c == strChar) { inStr = false; sb.append(' '); i++; continue }
                    sb.append(' '); i++; continue
                }
                sb.append(c); i++
            }
            out += sb.toString()
        }
        return out
    }

    // Replace string contents and line comments with spaces of equal length
    // so brace counts and pattern matches don't pick up tokens inside strings.
    private fun stripStringsAndComments(s: String): String {
        val sb = StringBuilder(s.length)
        var i = 0
        var inStr = false
        var strChar = '"'
        while (i < s.length) {
            val c = s[i]
            if (!inStr && c == '/' && i + 1 < s.length && s[i + 1] == '/') {
                // rest is a comment
                while (i < s.length) { sb.append(' '); i++ }
                break
            }
            if (!inStr && (c == '"' || c == '\'')) {
                inStr = true; strChar = c; sb.append(' '); i++; continue
            }
            if (inStr) {
                if (c == '\\' && i + 1 < s.length) { sb.append("  "); i += 2; continue }
                if (c == strChar) { inStr = false; sb.append(' '); i++; continue }
                sb.append(' '); i++; continue
            }
            sb.append(c); i++
        }
        return sb.toString()
    }
}
