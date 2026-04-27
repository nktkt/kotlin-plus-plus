package dev.kpp.analyzer

interface Reporter {
    /** Reports the violations and returns a process exit code. */
    fun report(violations: List<Violation>): Int
}

class ConsoleReporter(
    private val out: Appendable = System.out,
    private val rules: List<Rule> = KPP_RULES,
) : Reporter {

    override fun report(violations: List<Violation>): Int {
        val byId = rules.associateBy { it.id }
        var errorCount = 0
        for (v in violations) {
            val severity = byId[v.ruleId]?.severity ?: Severity.WARN
            if (severity == Severity.ERROR) errorCount++
            out.append("${v.file.path}:${v.line}:${v.column} ${v.ruleId} ${severity.name.lowercase()}: ${v.message}")
            out.append('\n')
        }
        out.append("kpp-analyzer: ${violations.size} violation(s), $errorCount error(s)\n")
        return if (errorCount > 0) 1 else 0
    }
}
