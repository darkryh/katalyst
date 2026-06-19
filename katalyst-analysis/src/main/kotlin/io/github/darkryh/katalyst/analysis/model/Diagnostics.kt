package io.github.darkryh.katalyst.analysis.model

enum class DiagnosticSeverity { ERROR, WARNING, INFO }

/**
 * The category of a [KatalystDiagnostic].
 *
 * The first group mirrors the runtime's `ValidationError` hierarchy (so analysis reports the
 * same problems the application would fail to boot with); the remainder are static-only checks
 * that the runtime cannot perform because it never sees the code that didn't compile/run.
 */
enum class DiagnosticKind {
    // --- mirrors katalyst-di ValidationError ---
    MISSING_DEPENDENCY,
    CIRCULAR_DEPENDENCY,
    UNINSTANTIABLE_TYPE,
    WELL_KNOWN_PROPERTY,
    SECONDARY_TYPE_BINDING,
    FEATURE_PROVIDED_TYPE,
    INSTANTIATION_FAILURE,

    // --- static-only analysis checks ---
    DUPLICATE_BINDING,
    INVALID_DSL_SIGNATURE,
    DUPLICATE_ROUTE,
    REPOSITORY_WITHOUT_TABLE,
    TABLE_WITHOUT_ENTITY,
    UNSUPPORTED_GENERIC,
}

/**
 * A problem (or note) found during analysis.
 *
 * @property symbolFqName the symbol the diagnostic is attached to, when applicable.
 * @property suggestion an optional fix hint (carried through from the runtime validators).
 */
data class KatalystDiagnostic(
    val severity: DiagnosticSeverity,
    val kind: DiagnosticKind,
    val message: String,
    val symbolFqName: String? = null,
    val suggestion: String? = null,
)
