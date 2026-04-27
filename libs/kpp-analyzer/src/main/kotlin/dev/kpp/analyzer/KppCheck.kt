package dev.kpp.analyzer

import java.io.File
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    val roots = if (args.isEmpty()) listOf(File(".")) else args.map { File(it) }
    val scanner = KppScanner(roots)
    val violations = scanner.scan()
    val reporter = ConsoleReporter()
    val exitCode = reporter.report(violations)
    exitProcess(exitCode)
}
