package dev.kpp.analyzer

import java.io.File

enum class Severity { INFO, WARN, ERROR }

data class Rule(
    val id: String,
    val name: String,
    val severity: Severity,
    val description: String,
)

data class Violation(
    val ruleId: String,
    val file: File,
    val line: Int,
    val column: Int,
    val message: String,
)

val KPP_RULES: List<Rule> = listOf(
    Rule(
        id = "KPP001",
        name = "ignored-result",
        severity = Severity.ERROR,
        description = "Return value of a @MustHandle function must not be discarded.",
    ),
    Rule(
        id = "KPP002",
        name = "raw-exception-catch",
        severity = Severity.ERROR,
        description = "catch (Throwable|Exception|RuntimeException) hides typed errors",
    ),
    Rule(
        id = "KPP004",
        name = "mutable-public-api",
        severity = Severity.ERROR,
        description = "Public API functions must not return mutable collection types.",
    ),
    Rule(
        id = "KPP005",
        name = "mutable-field-on-immutable",
        severity = Severity.ERROR,
        description = "@Immutable data class must use val + immutable collection types",
    ),
    Rule(
        id = "KPP011",
        name = "blocking-in-suspend",
        severity = Severity.ERROR,
        description = "Blocking calls (Thread.sleep, runBlocking, blocking IO) must not appear inside a suspend function.",
    ),
    Rule(
        id = "KPP013",
        name = "public var",
        severity = Severity.WARN,
        description = "public var defeats Kotlin's preference for read-only properties; use val + private var if mutation is needed",
    ),
    Rule(
        id = "KPP018",
        name = "exception-escapes-public-api",
        severity = Severity.ERROR,
        description = "Public API functions must not let exceptions escape; use Result or @Throws-aware contracts.",
    ),
)
