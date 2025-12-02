package com.ead.katalyst.di.error

import com.ead.katalyst.di.analysis.DependencyGraph
import kotlin.reflect.KClass

/**
 * Formats validation errors into human-readable, actionable error messages.
 *
 * The formatter provides:
 * - Clear error descriptions
 * - Root cause analysis
 * - Actionable fix suggestions
 * - Dependency chain visualization
 * - Summary statistics
 *
 * @param graph The dependency graph (for context)
 */
class ErrorFormatter(private val graph: DependencyGraph? = null) {

    /**
     * Formats a single validation error into a detailed message.
     *
     * @param error The validation error to format
     * @return Formatted error message
     */
    fun formatError(error: ValidationError): String = buildString {
        appendLine(formatErrorHeader(error))
        appendLine()
        appendLine(formatErrorBody(error))
        if (error.suggestion != null) {
            appendLine()
            appendLine("HOW TO FIX:")
            error.suggestion!!.lines().forEach { line ->
                appendLine("  $line")
            }
        }
    }

    /**
     * Formats the error header (type and summary).
     */
    private fun formatErrorHeader(error: ValidationError): String = when (error) {
        is MissingDependencyError -> "[MISSING DEPENDENCY] ${error.component.simpleName} → ${error.requiredType.simpleName}"
        is CircularDependencyError -> "[CIRCULAR DEPENDENCY] ${error.message}"
        is UninstantiableTypeError -> "[UNINSTANTIABLE TYPE] ${error.component.simpleName}"
        is WellKnownPropertyError -> "[MISSING FRAMEWORK PROPERTY] ${error.component.simpleName}"
        is SecondaryTypeBindingError -> "[MISSING INTERFACE BINDING] ${error.component.simpleName} → ${error.requiredType.simpleName}"
        is FeatureProvidedTypeError -> "[MISSING FEATURE] ${error.component.simpleName} needs ${error.featureName}"
        is InstantiationFailureError -> "[INSTANTIATION FAILURE] ${error.component.simpleName}"
    }

    /**
     * Formats the error body (detailed information).
     */
    private fun formatErrorBody(error: ValidationError): String = when (error) {
        is MissingDependencyError -> buildString {
            append("Component: ")
            appendLine(error.component.qualifiedName)
            append("Requires: ")
            appendLine(error.requiredType.qualifiedName)
            append("Parameter: ")
            appendLine(error.parameterName)
            appendLine()
            append("Status: ")
            when {
                error.isInKoin && error.isDiscoverable -> appendLine("SHOULD BE AVAILABLE (but isn't)")
                error.isInKoin -> appendLine("IN KOIN but not discoverable")
                error.isDiscoverable -> appendLine("DISCOVERABLE but not in Koin")
                else -> appendLine("NOT FOUND ANYWHERE")
            }
        }
        is CircularDependencyError -> buildString {
            append("Cycle: ")
            appendLine(error.cycle.map { it.simpleName }.joinToString(" → "))
            appendLine()
            appendLine("Details: This circular dependency makes instantiation impossible.")
            appendLine("         Component A depends on B which depends on C which depends on A.")
            appendLine("         None can be instantiated without the other.")
        }
        is UninstantiableTypeError -> buildString {
            append("Type: ")
            appendLine(error.component.qualifiedName)
            append("Issue: ")
            appendLine(error.message)
        }
        is WellKnownPropertyError -> buildString {
            append("Component: ")
            appendLine(error.component.simpleName)
            append("Property: ")
            appendLine("${error.propertyName}: ${error.propertyType.simpleName}")
            append("Status: ")
            appendLine(if (error.isAvailable) "AVAILABLE" else "NOT AVAILABLE")
        }
        is SecondaryTypeBindingError -> buildString {
            append("Component: ")
            appendLine(error.component.simpleName)
            append("Needs Interface: ")
            appendLine(error.requiredType.qualifiedName)
            append("Parameter: ")
            appendLine(error.parameterName)
            if (error.providingComponents.isNotEmpty()) {
                appendLine()
                appendLine("Possible providers:")
                error.providingComponents.forEach { provider ->
                    appendLine("  - ${provider.simpleName}")
                }
            }
        }
        is FeatureProvidedTypeError -> buildString {
            append("Component: ")
            appendLine(error.component.simpleName)
            append("Requires: ")
            appendLine(error.requiredType.qualifiedName)
            append("From Feature: ")
            appendLine(error.featureName)
            append("Parameter: ")
            appendLine(error.parameterName)
        }
        else -> error.message
    }

    /**
     * Formats multiple errors into a comprehensive report.
     *
     * @param errors List of validation errors
     * @return Formatted report
     */
    fun formatReport(errors: List<ValidationError>): String = buildString {
        appendLine()
        appendLine("═".repeat(80))
        appendLine("✗ DEPENDENCY INJECTION VALIDATION FAILED")
        appendLine("═".repeat(80))
        appendLine()

        // Summary statistics
        val errorsByType = errors.groupingBy { it::class.simpleName }.eachCount()
        appendLine("ERROR SUMMARY:")
        appendLine("  Total: ${errors.size}")
        errorsByType.forEach { (type, count) ->
            append("  • ")
            append(type?.replace("Error", "").orEmpty())
            append(": ")
            appendLine(count)
        }
        appendLine()

        // Detailed errors grouped by type
        appendLine("DETAILED ERRORS:")
        appendLine()

        // Missing dependencies
        val missingDeps = errors.filterIsInstance<MissingDependencyError>()
        if (missingDeps.isNotEmpty()) {
            appendLine("Missing Dependencies:")
            missingDeps.forEachIndexed { index, error ->
                formatGroupedError(index + 1, error)
                appendLine()
            }
            appendLine()
        }

        // Circular dependencies
        val circles = errors.filterIsInstance<CircularDependencyError>()
        if (circles.isNotEmpty()) {
            appendLine("Circular Dependencies:")
            circles.forEachIndexed { index, error ->
                formatGroupedError(index + 1, error)
                appendLine()
            }
            appendLine()
        }

        // Uninstantiable types
        val uninstantiable = errors.filterIsInstance<UninstantiableTypeError>()
        if (uninstantiable.isNotEmpty()) {
            appendLine("Uninstantiable Types:")
            uninstantiable.forEachIndexed { index, error ->
                formatGroupedError(index + 1, error)
                appendLine()
            }
            appendLine()
        }

        // Well-known property errors
        val wellKnown = errors.filterIsInstance<WellKnownPropertyError>()
        if (wellKnown.isNotEmpty()) {
            appendLine("Missing Framework Properties:")
            wellKnown.forEachIndexed { index, error ->
                formatGroupedError(index + 1, error)
                appendLine()
            }
            appendLine()
        }

        // Secondary type binding errors
        val secondary = errors.filterIsInstance<SecondaryTypeBindingError>()
        if (secondary.isNotEmpty()) {
            appendLine("Missing Interface Bindings:")
            secondary.forEachIndexed { index, error ->
                formatGroupedError(index + 1, error)
                appendLine()
            }
            appendLine()
        }

        // Feature-provided type errors
        val features = errors.filterIsInstance<FeatureProvidedTypeError>()
        if (features.isNotEmpty()) {
            appendLine("Missing Feature Types:")
            features.forEachIndexed { index, error ->
                formatGroupedError(index + 1, error)
                appendLine()
            }
            appendLine()
        }

        appendLine("═".repeat(80))
        appendLine("Please fix the validation errors above and restart the application.")
        appendLine("═".repeat(80))
    }

    /**
     * Formats an error as part of a grouped error list.
     */
    private fun StringBuilder.formatGroupedError(number: Int, error: ValidationError) {
        append("  [$number] ")
        appendLine(formatErrorHeader(error))
        formatErrorBody(error).lines().forEach { line ->
            append("      ")
            appendLine(line)
        }
        if (error.suggestion != null) {
            appendLine("      ")
            appendLine("      Fix:")
            error.suggestion!!.lines().forEach { line ->
                append("        ")
                appendLine(line)
            }
        }
    }

    /**
     * Creates a dependency chain visualization for a specific component.
     *
     * Shows all dependencies and transitive dependencies in a tree format.
     *
     * @param componentType The component to visualize
     * @return Tree visualization of dependencies
     */
    fun visualizeDependencyChain(componentType: KClass<*>): String = buildString {
        if (graph == null) {
            return "Dependency visualization requires a dependency graph"
        }

        appendLine("Dependency Chain for ${componentType.simpleName}:")
        appendLine()

        fun StringBuilder.appendDependencies(
            type: KClass<*>,
            prefix: String = "",
            visited: MutableSet<KClass<*>> = mutableSetOf()
        ) {
            if (type in visited) {
                appendLine("$prefix└─ ${type.simpleName} (...)  [circular]")
                return
            }

            visited.add(type)

            val deps = graph.getDependencies(type)
            if (deps.isEmpty()) {
                appendLine("$prefix└─ ${type.simpleName}")
            } else {
                appendLine("$prefix└─ ${type.simpleName}")
                deps.forEachIndexed { index, dep ->
                    val isLast = index == deps.size - 1
                    val newPrefix = prefix + if (isLast) "   " else "│  "
                    val connector = if (isLast) "└─ " else "├─ "

                    append(newPrefix)
                    appendDependencies(dep, newPrefix + connector, visited.toMutableSet())
                }
            }
        }

        appendDependencies(componentType)
    }
}
