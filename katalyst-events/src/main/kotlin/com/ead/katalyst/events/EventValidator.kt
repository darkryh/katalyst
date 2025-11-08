package com.ead.katalyst.events

import kotlin.reflect.KClass

/**
 * Validates domain events before publishing.
 *
 * Implement this interface to validate event invariants and ensure
 * events are correct before they're published to the event bus.
 *
 * **Usage:**
 *
 * ```kotlin
 * @Component
 * class UserCreatedEventValidator : EventValidator<UserCreatedEvent> {
 *
 *     override val eventType = UserCreatedEvent::class
 *
 *     override suspend fun validate(event: UserCreatedEvent): ValidationResult {
 *         val errors = mutableListOf<String>()
 *
 *         if (event.email.isBlank()) {
 *             errors.add("Email cannot be empty")
 *         }
 *
 *         if (!event.email.contains("@")) {
 *             errors.add("Email must be valid")
 *         }
 *
 *         if (event.name.isBlank()) {
 *             errors.add("Name cannot be empty")
 *         }
 *
 *         return if (errors.isEmpty()) {
 *             ValidationResult.Valid
 *         } else {
 *             ValidationResult.Invalid(errors)
 *         }
 *     }
 * }
 * ```
 *
 * **Integration with EventClient:**
 *
 * Validators are invoked by EventClient before publishing:
 *
 * ```kotlin
 * val result = eventClient.publish(userCreatedEvent)
 * // Behind the scenes:
 * // 1. Find validators for UserCreatedEvent
 * // 2. Run all validators
 * // 3. If all valid, publish to bus
 * // 4. If any invalid, return Failure with validation errors
 * ```
 *
 * @param T The type of event to validate
 */
interface EventValidator<T : DomainEvent> {
    /**
     * The event type this validator handles.
     */
    val eventType: KClass<T>

    /**
     * Validate the event.
     *
     * Implement to check business invariants and constraints.
     *
     * @param event The event to validate
     * @return ValidationResult.Valid or ValidationResult.Invalid
     */
    suspend fun validate(event: T): ValidationResult
}

/**
 * Result of event validation.
 *
 * Can be Valid (no errors) or Invalid (with error messages).
 */
sealed class ValidationResult {
    /**
     * Event is valid and can be published.
     */
    object Valid : ValidationResult()

    /**
     * Event failed validation.
     *
     * @param errors List of validation error messages
     */
    data class Invalid(private val errors: List<String>) : ValidationResult() {
        /**
         * Get the list of validation errors.
         *
         * @return List of error messages
         */
        fun getErrors(): List<String> = errors

        /**
         * Get errors as a single formatted string.
         *
         * @return Comma-separated list of errors
         */
        fun errorMessage(): String = errors.joinToString(", ")
    }

    /**
     * Check if validation passed.
     */
    fun isValid(): Boolean = this is Valid

    /**
     * Get errors, or empty list if valid.
     */
    fun errors(): List<String> = when (this) {
        is Valid -> emptyList()
        is Invalid -> this.getErrors()
    }
}

/**
 * Convenience base class for event validators.
 *
 * **Usage:**
 *
 * ```kotlin
 * @Component
 * class OrderCreatedValidator : BaseEventValidator<OrderCreatedEvent>(
 *     OrderCreatedEvent::class
 * ) {
 *     override suspend fun validate(event: OrderCreatedEvent): ValidationResult {
 *         val errors = mutableListOf<String>()
 *         if (event.orderId.isBlank()) errors.add("Order ID required")
 *         if (event.items.isEmpty()) errors.add("Order must have items")
 *         return if (errors.isEmpty()) ValidationResult.Valid
 *                else ValidationResult.Invalid(errors)
 *     }
 * }
 * ```
 *
 * @param T The type of event
 * @param eventType The KClass of the event type
 */
abstract class BaseEventValidator<T : DomainEvent>(
    override val eventType: KClass<T>
) : EventValidator<T>

/**
 * Composite validator that runs multiple validators.
 *
 * Useful for combining validators from different concerns.
 *
 * **Usage:**
 *
 * ```kotlin
 * val compositeValidator = CompositeEventValidator<UserCreatedEvent>(
 *     listOf(
 *         EmailFormatValidator(),
 *         NameValidator(),
 *         UniqueEmailValidator()
 *     )
 * )
 * ```
 *
 * @param T The type of event
 * @param validators List of validators to run
 */
class CompositeEventValidator<T : DomainEvent>(
    override val eventType: KClass<T>,
    private val validators: List<EventValidator<T>>
) : EventValidator<T> {
    override suspend fun validate(event: T): ValidationResult {
        val allErrors = mutableListOf<String>()

        for (validator in validators) {
            val result = validator.validate(event)
            if (!result.isValid()) {
                allErrors.addAll(result.errors())
            }
        }

        return if (allErrors.isEmpty()) {
            ValidationResult.Valid
        } else {
            ValidationResult.Invalid(allErrors)
        }
    }
}

/**
 * Validator that always passes (no-op).
 *
 * Useful for testing or as a placeholder.
 *
 * @param T The type of event
 * @param eventType The KClass of the event type
 */
class NoOpEventValidator<T : DomainEvent>(
    override val eventType: KClass<T>
) : EventValidator<T> {
    override suspend fun validate(event: T): ValidationResult = ValidationResult.Valid
}
