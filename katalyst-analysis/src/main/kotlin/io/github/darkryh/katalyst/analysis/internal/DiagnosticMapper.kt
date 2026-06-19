package io.github.darkryh.katalyst.analysis.internal

import io.github.darkryh.katalyst.analysis.model.DiagnosticKind
import io.github.darkryh.katalyst.analysis.model.DiagnosticSeverity
import io.github.darkryh.katalyst.analysis.model.KatalystDiagnostic
import io.github.darkryh.katalyst.di.error.CircularDependencyError
import io.github.darkryh.katalyst.di.error.FeatureProvidedTypeError
import io.github.darkryh.katalyst.di.error.InstantiationFailureError
import io.github.darkryh.katalyst.di.error.MissingDependencyError
import io.github.darkryh.katalyst.di.error.SecondaryTypeBindingError
import io.github.darkryh.katalyst.di.error.UninstantiableTypeError
import io.github.darkryh.katalyst.di.error.ValidationError
import io.github.darkryh.katalyst.di.error.WellKnownPropertyError

/**
 * Translates the runtime's [ValidationError] hierarchy into analysis [KatalystDiagnostic]s.
 *
 * Reusing the runtime's validators (rather than reimplementing them) means the diagnostics a
 * developer sees in tooling are exactly the ones that would cause — or pass — application boot.
 */
internal object DiagnosticMapper {

    fun map(error: ValidationError): KatalystDiagnostic {
        val kind = when (error) {
            is MissingDependencyError -> DiagnosticKind.MISSING_DEPENDENCY
            is CircularDependencyError -> DiagnosticKind.CIRCULAR_DEPENDENCY
            is UninstantiableTypeError -> DiagnosticKind.UNINSTANTIABLE_TYPE
            is WellKnownPropertyError -> DiagnosticKind.WELL_KNOWN_PROPERTY
            is SecondaryTypeBindingError -> DiagnosticKind.SECONDARY_TYPE_BINDING
            is FeatureProvidedTypeError -> DiagnosticKind.FEATURE_PROVIDED_TYPE
            is InstantiationFailureError -> DiagnosticKind.INSTANTIATION_FAILURE
        }
        return KatalystDiagnostic(
            severity = DiagnosticSeverity.ERROR,
            kind = kind,
            message = error.message,
            symbolFqName = error.component.qualifiedName,
            suggestion = error.suggestion?.takeIf { it.isNotBlank() },
        )
    }
}
