package com.ead.katalyst.di.exception

import com.ead.katalyst.di.error.ValidationError
import org.koin.core.Koin
import org.slf4j.LoggerFactory
import kotlin.reflect.KClass

private val logger = LoggerFactory.getLogger("FatalDependencyValidationException")

/**
 * Fatal validation errors that prevent server startup.
 *
 * When dependency injection validation fails, this exception is thrown with a comprehensive
 * report of all validation errors. The server cannot start until these errors are resolved.
 *
 * This exception is used in Phase 3 (Dependency Validation) of the bootstrap process.
 * If validation fails, the server immediately terminates with a detailed error report.
 *
 * @param validationErrors List of all validation errors found
 * @param discoveredTypes Map of all discovered component types
 * @param koin The Koin DI container (for context)
 */
class FatalDependencyValidationException(
    val validationErrors: List<ValidationError>,
    val discoveredTypes: Map<String, Set<KClass<*>>>,
    val koin: Koin
) : KatalystDIException(
    message = buildErrorMessage(validationErrors),
    cause = null
) {

    /**
     * Generates a detailed human-readable error report.
     *
     * The report includes:
     * - Summary of how many errors of each type
     * - Detailed breakdown of each error with context
     * - Suggestions for fixing each error
     * - Dependency chain visualization
     * - Summary statistics
     *
     * @return Formatted error report as a string
     */
    fun generateDetailedReport(): String = buildString {
        appendLine()
        appendLine("═".repeat(80))
        appendLine("✗ FATAL DEPENDENCY INJECTION VALIDATION ERROR")
        appendLine("═".repeat(80))
        appendLine()

        // Count errors by type
        val errorsByType = validationErrors.groupingBy { it::class.simpleName }.eachCount()
        appendLine("SUMMARY:")
        appendLine("  Total errors: ${validationErrors.size}")
        errorsByType.forEach { (type, count) ->
            appendLine("  - $type: $count")
        }
        appendLine()

        // Group errors by type for detailed reporting
        val errorGroups = validationErrors.groupBy { it::class.simpleName }

        // Report each error type
        var errorNumber = 1
        errorGroups.forEach { (errorType, errors) ->
            when (errorType) {
                "MissingDependencyError" -> {
                    appendLine("[MISSING DEPENDENCIES]")
                    errors.forEach { error ->
                        @Suppress("UNCHECKED_CAST")
                        val missingError = error as? com.ead.katalyst.di.error.MissingDependencyError
                        appendLine("  Error #$errorNumber:")
                        appendLine("    Component: ${error.component.qualifiedName}")
                        if (missingError != null) {
                            appendLine("    Required Type: ${missingError.requiredType.qualifiedName}")
                            appendLine("    Parameter Name: ${missingError.parameterName}")
                            appendLine("    Reason: ${missingError.context ?: "Type not available in Koin or discovered components"}")
                        }
                        appendLine("    Status: NOT FOUND")
                        appendLine()
                        if (missingError != null && missingError.suggestion.isNotEmpty()) {
                            appendLine("    HOW TO FIX:")
                            missingError.suggestion.lines().forEach { line ->
                                appendLine("      $line")
                            }
                        }
                        appendLine()
                        errorNumber++
                    }
                }
                "CircularDependencyError" -> {
                    appendLine("[CIRCULAR DEPENDENCIES]")
                    errors.forEach { error ->
                        appendLine("  Error #$errorNumber:")
                        appendLine("    Cycle: ${error.message}")
                        if (error.suggestion != null) {
                            appendLine("    How to fix:")
                            error.suggestion!!.lines().forEach { line ->
                                appendLine("      $line")
                            }
                        }
                        appendLine()
                        errorNumber++
                    }
                }
                "UninstantiableTypeError" -> {
                    appendLine("[UNINSTANTIABLE TYPES]")
                    errors.forEach { error ->
                        appendLine("  Error #$errorNumber:")
                        appendLine("    Type: ${error.component.qualifiedName}")
                        appendLine("    Reason: ${error.message}")
                        if (error.suggestion != null) {
                            appendLine("    How to fix:")
                            error.suggestion!!.lines().forEach { line ->
                                appendLine("      $line")
                            }
                        }
                        appendLine()
                        errorNumber++
                    }
                }
                "WellKnownPropertyError" -> {
                    appendLine("[MISSING WELL-KNOWN PROPERTIES]")
                    errors.forEach { error ->
                        appendLine("  Error #$errorNumber:")
                        appendLine("    Component: ${error.component.simpleName}")
                        appendLine("    Issue: ${error.message}")
                        if (error.suggestion != null) {
                            appendLine("    Fix: ${error.suggestion}")
                        }
                        appendLine()
                        errorNumber++
                    }
                }
                "SecondaryTypeBindingError" -> {
                    appendLine("[MISSING SECONDARY TYPE BINDINGS]")
                    errors.forEach { error ->
                        appendLine("  Error #$errorNumber:")
                        appendLine("    Component: ${error.component.simpleName}")
                        appendLine("    Needs: ${error.message}")
                        if (error.suggestion != null) {
                            appendLine("    How to fix:")
                            error.suggestion!!.lines().forEach { line ->
                                appendLine("      $line")
                            }
                        }
                        appendLine()
                        errorNumber++
                    }
                }
                "FeatureProvidedTypeError" -> {
                    appendLine("[MISSING FEATURE-PROVIDED TYPES]")
                    errors.forEach { error ->
                        appendLine("  Error #$errorNumber:")
                        appendLine("    Component: ${error.component.simpleName}")
                        appendLine("    Issue: ${error.message}")
                        if (error.suggestion != null) {
                            appendLine("    Fix:")
                            error.suggestion!!.lines().forEach { line ->
                                appendLine("      $line")
                            }
                        }
                        appendLine()
                        errorNumber++
                    }
                }
                else -> {
                    appendLine("[$errorType]")
                    errors.forEach { error ->
                        appendLine("  Error #$errorNumber: ${error.message}")
                        if (error.suggestion != null) {
                            appendLine("  Suggestion: ${error.suggestion}")
                        }
                        appendLine()
                        errorNumber++
                    }
                }
            }
        }

        appendLine()
        appendLine("═".repeat(80))
        appendLine("DISCOVERED COMPONENTS:")
        discoveredTypes.forEach { (type, classes) ->
            if (classes.isNotEmpty()) {
                appendLine("  $type: ${classes.size} discovered")
                classes.take(5).forEach { clazz ->
                    appendLine("    - ${clazz.simpleName}")
                }
                if (classes.size > 5) {
                    appendLine("    ... and ${classes.size - 5} more")
                }
            }
        }
        appendLine()
        appendLine("═".repeat(80))
        appendLine("✗ SERVER STARTUP ABORTED")
        appendLine("═".repeat(80))
        appendLine("Please fix the validation errors above and restart the application.")
        appendLine("═".repeat(80))
    }

    /**
     * Logs the detailed error report to the logger and returns it.
     *
     * This is useful for ensuring the detailed report appears in application logs
     * even if the exception message itself is truncated.
     */
    fun printDetailedReport(): String {
        val report = generateDetailedReport()
        logger.error(report)
        println(report)  // Also print to stdout for visibility
        return report
    }

    companion object {
        private fun buildErrorMessage(errors: List<ValidationError>): String {
            val errorCount = errors.size
            val missingDeps = errors.count { it::class.simpleName == "MissingDependencyError" }
            val cycles = errors.count { it::class.simpleName == "CircularDependencyError" }
            val uninstantiable = errors.count { it::class.simpleName == "UninstantiableTypeError" }

            return buildString {
                append("Dependency injection validation failed with $errorCount error(s)")
                if (missingDeps > 0) append(" - $missingDeps missing dependencies")
                if (cycles > 0) append(" - $cycles circular dependencies")
                if (uninstantiable > 0) append(" - $uninstantiable uninstantiable types")
            }
        }
    }
}
