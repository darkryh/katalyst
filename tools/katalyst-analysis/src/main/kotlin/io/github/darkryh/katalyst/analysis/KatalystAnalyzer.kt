package io.github.darkryh.katalyst.analysis

import io.github.darkryh.katalyst.analysis.internal.GraphBuilder
import io.github.darkryh.katalyst.analysis.model.KatalystApplicationGraph

/**
 * The entrypoint to Katalyst semantic analysis.
 *
 * Given a [KatalystAnalysisConfig] (scan packages + compiled classpath) it produces a
 * [KatalystApplicationGraph]: the static, reusable description of how a Katalyst application is
 * assembled and what, if anything, is wrong with its wiring.
 *
 * It is deliberately small and side-effect free: it does not boot an application, does not depend
 * on the IntelliJ Platform, and never throws for ordinary analysis problems — those are reported as
 * [io.github.darkryh.katalyst.analysis.model.KatalystDiagnostic]s inside the returned graph. This
 * makes it safe to call from tests, CLIs, Gradle tasks and the IDE plugin alike.
 *
 * ```kotlin
 * val graph = KatalystAnalyzer().analyze(
 *     KatalystAnalysisConfig(
 *         scanPackages = listOf("com.example.app"),
 *         classpath = runtimeClasspathFiles,
 *     )
 * )
 * graph.routes.forEach { println("route ${it.symbol.simpleName} (${it.dslCalls})") }
 * graph.diagnostics.filter { it.severity == DiagnosticSeverity.ERROR }.forEach(::report)
 * ```
 */
class KatalystAnalyzer {
    fun analyze(config: KatalystAnalysisConfig): KatalystApplicationGraph =
        GraphBuilder(config).build()
}
