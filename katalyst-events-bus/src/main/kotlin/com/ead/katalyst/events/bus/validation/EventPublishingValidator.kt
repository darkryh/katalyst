package com.ead.katalyst.events.bus.validation

import com.ead.katalyst.events.DomainEvent
import org.slf4j.LoggerFactory

/**
 * Validates that an event can be published before the transaction commits.
 *
 * This validator ensures:
 * - Event handlers are registered for the event type
 * - Event is in a valid state for publishing
 * - All prerequisites for publishing are met
 *
 * **Usage:**
 * Used automatically in EventsTransactionAdapter during BEFORE_COMMIT_VALIDATION phase
 *
 * **Error Handling:**
 * If validation fails, throws EventValidationException which will rollback the transaction
 */
interface EventPublishingValidator {

    /**
     * Validate that an event can be published.
     *
     * @param event The event to validate
     * @return ValidationResult with isValid flag and optional error message
     * @throws EventValidationException if validation fails
     */
    suspend fun validate(event: DomainEvent): ValidationResult
}

/**
 * Result of event validation
 */
data class ValidationResult(
    val isValid: Boolean,
    val eventId: String = "",
    val eventType: String = "",
    val error: String? = null
)

/**
 * Default implementation that validates event handlers exist
 */
class DefaultEventPublishingValidator(
    private val hasHandlersCheck: (event: DomainEvent) -> Boolean
) : EventPublishingValidator {

    private val logger = LoggerFactory.getLogger(DefaultEventPublishingValidator::class.java)

    override suspend fun validate(event: DomainEvent): ValidationResult {
        // Check if handlers exist for this event type
        if (!hasHandlersCheck(event)) {
            val errorMsg = "No handlers registered for event: ${event::class.simpleName} (type: ${event.eventType()})"
            logger.error("Event validation failed: $errorMsg")
            return ValidationResult(
                isValid = false,
                eventId = event.eventId,
                eventType = event.eventType(),
                error = errorMsg
            )
        }

        logger.debug("Event validation passed: {} (type: {})", event::class.simpleName, event.eventType())
        return ValidationResult(
            isValid = true,
            eventId = event.eventId,
            eventType = event.eventType()
        )
    }
}

/**
 * Exception thrown when event validation fails.
 * This is caught by the transaction system and causes the transaction to rollback.
 */
class EventValidationException(
    val event: DomainEvent,
    message: String,
    cause: Exception? = null
) : RuntimeException(
    "Event validation failed for ${event::class.simpleName} (${event.eventType()}): $message",
    cause
)
