package io.github.darkryh.katalyst.analysis

import java.io.File

/**
 * Options that tune how analysis runs.
 *
 * @property includeDiDiagnostics reuse katalyst-di's DependencyAnalyzer + DependencyValidator
 * to produce dependency edges and missing/circular/uninstantiable diagnostics. On by default.
 * @property includeStaticChecks run the analysis-only checks (duplicate routes, repository/table
 * pairing, invalid DSL signatures). On by default.
 * @property treatWarningsAsErrors elevate every WARNING diagnostic to ERROR (useful in CI gates).
 */
data class KatalystAnalysisOptions(
    val includeDiDiagnostics: Boolean = true,
    val includeStaticChecks: Boolean = true,
    val treatWarningsAsErrors: Boolean = false,
)

/**
 * Input to [KatalystAnalyzer.analyze].
 *
 * @property scanPackages the discovery roots, exactly as passed to `scanPackages(...)` in the
 * application bootstrap. A symbol outside these roots is invisible to Katalyst and to analysis.
 * @property classpath the compiled classpath to analyse — the application's own output plus its
 * dependencies. Classes are loaded for reflection with `Class.forName(name, initialize = false)`,
 * so static initialisers never run.
 * @property sourceRoots reserved for future use. The bytecode-based analyzer does not currently
 * resolve source files/lines from this classpath-only pipeline, so every symbol's
 * [io.github.darkryh.katalyst.analysis.model.SourceLocation] is always `null` regardless of what is
 * passed here. The IDE plugin resolves source locations itself via PSI and does not depend on this
 * property; non-IDE callers should not rely on it either until source-based resolution lands.
 */
data class KatalystAnalysisConfig(
    val scanPackages: List<String>,
    val classpath: List<File>,
    val sourceRoots: List<File> = emptyList(),
    val options: KatalystAnalysisOptions = KatalystAnalysisOptions(),
)
