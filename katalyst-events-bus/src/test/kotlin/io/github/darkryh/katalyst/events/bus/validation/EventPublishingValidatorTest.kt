package io.github.darkryh.katalyst.events.bus.validation

import io.github.darkryh.katalyst.events.DomainEvent
import io.github.darkryh.katalyst.events.EventMetadata
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Unit tests for EventPublishingValidator.
 *
 * Tests the validation logic that ensures events have registered handlers
 * before they are published as part of a transaction.
 */
@DisplayName("Event Publishing Validator Tests")
class EventPublishingValidatorTest {

    // Test event implementation
    private data class TestEvent(
        override val eventId: String = "test-event-1",
        val testData: String = "data"
    ) : DomainEvent {
        override fun getMetadata(): EventMetadata = EventMetadata(eventType = "test.event")
    }

    private lateinit var validator: DefaultEventPublishingValidator
    private var hasHandlersCheckCalled = false
    private var hasHandlersCheckResult = true

    @BeforeEach
    fun setup() {
        hasHandlersCheckCalled = false
        hasHandlersCheckResult = true
        validator = DefaultEventPublishingValidator { event ->
            hasHandlersCheckCalled = true
            hasHandlersCheckResult
        }
    }

    @Test
    @DisplayName("Should validate event when handlers exist")
    fun testValidateEventWithHandlers() = runTest {
        // Arrange
        val event = TestEvent()
        hasHandlersCheckResult = true

        // Act
        val result = validator.validate(event)

        // Assert
        assertTrue(result.isValid)
        assertEquals(event.eventId, result.eventId)
        assertEquals("test.event", result.eventType)
        assertTrue(hasHandlersCheckCalled)
    }

    @Test
    @DisplayName("Should fail validation when handlers don't exist")
    fun testValidateEventWithoutHandlers() = runTest {
        // Arrange
        val event = TestEvent()
        hasHandlersCheckResult = false

        // Act
        val result = validator.validate(event)

        // Assert
        assertFalse(result.isValid)
        assertNotNull(result.error)
        assertTrue(result.error!!.contains("No handlers"))
        assertTrue(hasHandlersCheckCalled)
    }

    @Test
    @DisplayName("Should include event ID in validation result")
    fun testValidationResultIncludesEventId() = runTest {
        // Arrange
        val customEventId = "custom-event-id-123"
        val event = TestEvent(eventId = customEventId)
        hasHandlersCheckResult = true

        // Act
        val result = validator.validate(event)

        // Assert
        assertEquals(customEventId, result.eventId)
    }

    @Test
    @DisplayName("Should include event type in validation result")
    fun testValidationResultIncludesEventType() = runTest {
        // Arrange
        val event = TestEvent()
        hasHandlersCheckResult = true

        // Act
        val result = validator.validate(event)

        // Assert
        assertEquals("test.event", result.eventType)
    }

    @Test
    @DisplayName("Should call hasHandlers check for validation")
    fun testValidatorCallsHandlerCheck() = runTest {
        // Arrange
        val event = TestEvent()

        // Act
        validator.validate(event)

        // Assert
        assertTrue(hasHandlersCheckCalled)
    }

    @Test
    @DisplayName("Should validate multiple events independently")
    fun testValidateMultipleEvents() = runTest {
        // Arrange
        val event1 = TestEvent(eventId = "event-1")
        val event2 = TestEvent(eventId = "event-2")
        var callCount = 0
        val multiValidator = DefaultEventPublishingValidator { event ->
            callCount++
            true
        }

        // Act
        val result1 = multiValidator.validate(event1)
        val result2 = multiValidator.validate(event2)

        // Assert
        assertTrue(result1.isValid)
        assertTrue(result2.isValid)
        assertEquals(2, callCount)
    }

    @Test
    @DisplayName("Should preserve event ID across multiple validations")
    fun testPreserveEventIdAcrossValidations() = runTest {
        // Arrange
        val eventIds = listOf("event-1", "event-2", "event-3")
        val validator = DefaultEventPublishingValidator { true }

        // Act
        val results = eventIds.map { eventId ->
            validator.validate(TestEvent(eventId = eventId))
        }

        // Assert
        eventIds.forEachIndexed { index, expectedId ->
            assertEquals(expectedId, results[index].eventId)
        }
    }

    @Test
    @DisplayName("ValidationResult should provide meaningful error message")
    fun testValidationErrorMessageIsNonEmpty() = runTest {
        // Arrange
        val event = TestEvent()
        val validator = DefaultEventPublishingValidator { false }

        // Act
        val result = validator.validate(event)

        // Assert
        assertFalse(result.isValid)
        assertNotNull(result.error)
        assertTrue(result.error!!.isNotEmpty())
        assertTrue(result.error!!.contains("handlers", ignoreCase = true))
    }

    @Test
    @DisplayName("Should handle validator exceptions gracefully")
    fun testValidatorHandlesExceptions() = runTest {
        // Arrange
        val event = TestEvent()
        val failingValidator = DefaultEventPublishingValidator { event ->
            throw IllegalStateException("Handler check failed")
        }

        // Act & Assert
        try {
            failingValidator.validate(event)
            assertTrue(false, "Should have thrown exception")
        } catch (e: IllegalStateException) {
            assertEquals("Handler check failed", e.message)
        }
    }
}
