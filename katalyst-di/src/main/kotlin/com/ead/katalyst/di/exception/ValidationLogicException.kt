package com.ead.katalyst.di.exception

/**
 * Validation logic produced incorrect results.
 *
 * This exception is thrown in Phase 5 (Instantiation) when a component that passed
 * validation fails to instantiate. This indicates a bug in the validation logic itself,
 * not a user configuration issue.
 *
 * The validation phase should catch all possible instantiation failures. If instantiation
 * fails after validation passed, the validation logic has a bug.
 *
 * @param message Description of the logic error
 * @param errors List of errors that occurred during instantiation
 * @param cause The underlying exception that caused this failure
 */
class ValidationLogicException(
    message: String,
    val errors: List<Exception> = emptyList(),
    cause: Throwable? = null
) : KatalystDIException(message, cause) {

    /**
     * Creates a report of the validation logic failure.
     *
     * This report includes information about the inconsistency between validation
     * and instantiation that occurred.
     *
     * @return Detailed failure report
     */
    fun getDetailedReport(): String = buildString {
        appendLine()
        appendLine("═".repeat(80))
        appendLine("✗ INTERNAL VALIDATION LOGIC ERROR")
        appendLine("═".repeat(80))
        appendLine()
        appendLine("The dependency validation phase succeeded, but instantiation failed.")
        appendLine("This indicates a bug in the validation logic.")
        appendLine()
        appendLine("Error: $message")
        appendLine()
        if (errors.isNotEmpty()) {
            appendLine("Instantiation errors:")
            errors.forEach { error ->
                appendLine("  - ${error.message}")
            }
            appendLine()
        }
        appendLine("═".repeat(80))
        appendLine("Please report this issue with the following context:")
        appendLine("  - Error message above")
        if (errors.isNotEmpty()) {
            appendLine("  - Instantiation errors listed above")
        }
        val rootCause = cause
        if (rootCause != null) {
            appendLine("  - Root cause: ${rootCause.message}")
        }
        appendLine("═".repeat(80))
    }
}
