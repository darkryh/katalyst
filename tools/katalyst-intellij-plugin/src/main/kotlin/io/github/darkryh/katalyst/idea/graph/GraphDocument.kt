package io.github.darkryh.katalyst.idea.graph

/**
 * Vendored mirror of `io.github.darkryh.katalyst.analysis.export.GraphDocument` — the schema of
 * `katalyst-graph.json` written by `katalyst-analysis`.
 *
 * The plugin reads (never writes) this document for whole-app features that PSI alone cannot
 * compute: navigation between graph nodes and surfacing dependency diagnostics. Keeping it as plain
 * nullable-friendly data classes lets us parse it with the platform-bundled Gson, with no extra
 * dependency and tolerant of forward-compatible schema additions.
 */
internal data class GraphDocument(
    val schemaVersion: Int = 0,
    val scanPackages: List<String> = emptyList(),
    val nodes: List<NodeRecord> = emptyList(),
    val dependencies: List<EdgeRecord> = emptyList(),
    val diagnostics: List<DiagnosticRecord> = emptyList(),
)

internal data class NodeRecord(
    val fqName: String = "",
    val simpleName: String = "",
    val packageName: String = "",
    val kind: String = "",
    val discoveryRule: String = "",
    val discoveryExplanation: String = "",
    val discoveryDetail: String? = null,
    val attributes: Map<String, String> = emptyMap(),
)

internal data class EdgeRecord(
    val from: String = "",
    val to: String = "",
    val parameterName: String = "",
    val optional: Boolean = false,
    val resolvable: Boolean = true,
    val source: String = "",
)

internal data class DiagnosticRecord(
    val severity: String = "",
    val kind: String = "",
    val message: String = "",
    val symbolFqName: String? = null,
    val suggestion: String? = null,
)
