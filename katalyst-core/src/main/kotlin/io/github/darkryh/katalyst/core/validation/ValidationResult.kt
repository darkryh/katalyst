package io.github.darkryh.katalyst.core.validation


/**
 * Validation utilities shared across Katalyst components.
 */

// ============= Validation Result =============

/**
 * Result of validation operation.
 */
data class ValidationResult(
    val isValid: Boolean,
    val errors: List<String> = emptyList()
) {
    companion object {
        fun valid(): ValidationResult = ValidationResult(true, emptyList())
        fun invalid(vararg errors: String): ValidationResult =
            ValidationResult(false, errors.toList())
    }
}