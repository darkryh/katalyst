package io.github.darkryh.katalyst.telemetry.model

import kotlinx.serialization.Serializable

/** A dependency-graph validation failure a developer must fix. */
@Serializable
data class ValidationErrorEntry(
    val kind: String,
    val component: String,
    val requiredType: String? = null,
    val message: String,
    val suggestion: String? = null,
    /** For circular errors, the A -> B -> C -> A path. */
    val cyclePath: List<String> = emptyList(),
)

/** Per-category discovery counts and per-scan timing that reveal a wrong `scanPackages` at a glance. */
@Serializable
data class DiscoverySnapshot(
    val perCategoryCounts: Map<String, Int> = emptyMap(),
    val totalTypes: Int = 0,
    val scans: List<ScanEntry> = emptyList(),
    val emptyDiscoveries: List<String> = emptyList(),
)

/** One classpath scan of a base type: how long it took and how many implementations it found. */
@Serializable
data class ScanEntry(
    val baseType: String,
    val packages: List<String> = emptyList(),
    val durationMs: Long,
    val matchCount: Int,
)

/** Live inventories independent of the Koin registry (kept because Koin getAll is unreliable). */
@Serializable
data class LiveRegistries(
    val containerReady: Boolean,
    val activeEngineId: String? = null,
    val beanCount: Int = 0,
    val serviceCount: Int = 0,
    val tableCount: Int = 0,
    val ktorModuleCount: Int = 0,
    val startupHooks: Int = 0,
    val readyHooks: Int = 0,
    val features: List<String> = emptyList(),
)

/**
 * The dependency graph, validation report, instantiation order and registries that boot computes
 * then throws away — captured so the "why won't it wire / where's the cycle" drill-down survives.
 * Shallow-first carries the shape + errors + order; deep per-node dependency edges arrive later.
 */
@Serializable
data class WiringSnapshot(
    val nodeCount: Int = 0,
    val edgeCount: Int = 0,
    val secondaryBindings: Int = 0,
    val koinProvidedTypes: Int = 0,
    val isValid: Boolean = true,
    val totalErrors: Int = 0,
    val errorsByKind: Map<String, Int> = emptyMap(),
    val validationErrors: List<ValidationErrorEntry> = emptyList(),
    val instantiationOrder: List<String> = emptyList(),
    val analysisMs: Long? = null,
    val validationMs: Long? = null,
    val discovery: DiscoverySnapshot? = null,
    val registries: LiveRegistries? = null,
)
