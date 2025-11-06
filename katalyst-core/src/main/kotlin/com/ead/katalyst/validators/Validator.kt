package com.ead.katalyst.validators

import com.ead.katalyst.components.Component

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

// ============= Validator Interface =============

/**
 * Optional contract for strongly-typed validators.
 *
 * **Usage Example:**
 * ```kotlin
 * class UserValidator : Validator<User> {
 *     override suspend fun validate(entity: User): ValidationResult {
 *         val errors = mutableListOf<String>()
 *         if (entity.name.isEmpty()) {
 *             errors.add("User name cannot be empty")
 *         }
 *         return if (errors.isEmpty()) {
 *             ValidationResult.valid()
 *         } else {
 *             ValidationResult.invalid(*errors.toTypedArray())
 *         }
 *     }
 * }
 * ```
 *
 * Validators automatically behave like components thanks to the [Component] marker,
 * so you can use constructor injection or property injection without extra wiring.
 *
 * @param T The entity type to validate.
 */
interface Validator<T : Any> : Component {
    /**
     * Validates an entity and returns result.
     *
     * @param entity The entity to validate
     * @return Validation result with any errors
     */
    suspend fun validate(entity: T): ValidationResult

    /**
     * Gets list of validation errors for an entity.
     *
     * @param entity The entity to validate
     * @return List of error messages
     */
    suspend fun getValidationErrors(entity: T): List<String> =
        validate(entity).errors
}
