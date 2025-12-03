package io.github.darkryh.katalyst.core.validation

import io.github.darkryh.katalyst.core.component.Component

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
