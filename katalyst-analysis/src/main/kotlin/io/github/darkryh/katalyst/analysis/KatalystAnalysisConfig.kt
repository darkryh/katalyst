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
 * @property sourceRoots optional source roots; when provided, nodes are annotated with file/line
 * [io.github.darkryh.katalyst.analysis.model.SourceLocation]s for nicer reports. The IDE plugin
 * uses PSI for this instead and can leave it empty.
 */
data class KatalystAnalysisConfig(
    val scanPackages: List<String>,
    val classpath: List<File>,
    val sourceRoots: List<File> = emptyList(),
    val options: KatalystAnalysisOptions = KatalystAnalysisOptions(),
)
