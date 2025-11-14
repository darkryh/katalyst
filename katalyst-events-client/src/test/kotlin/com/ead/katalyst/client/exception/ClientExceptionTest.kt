package com.ead.katalyst.client.exception

import com.ead.katalyst.client.PublishResult
import kotlin.test.*

/**
 * Comprehensive tests for ClientException hierarchy.
 *
 * Tests cover:
 * - Base ClientException
 * - PublishException
 * - RetryExhaustedException
 * - ClientConfigurationException
 * - InterceptorAbortException
 * - BatchPublishException
 * - ExternalPublishException
 * - EventValidationFailedException
 * - InvalidClientStateException
 * - Exception properties and hierarchy
 */
class ClientExceptionTest {

    // ========== BASE CLIENT EXCEPTION TESTS ==========

    @Test
    fun `ClientException should have message`() {
        val exception = ClientException("Test error")
        assertEquals("Test error", exception.message)
    }

    @Test
    fun `ClientException should support cause`() {
        val cause = RuntimeException("Root cause")
        val exception = ClientException("Test error", cause)

        assertEquals("Test error", exception.message)
        assertEquals(cause, exception.cause)
    }

    @Test
    fun `ClientException should be RuntimeException`() {
        val exception = ClientException("Test")
        assertTrue(exception is RuntimeException)
    }

    // ========== PUBLISH EXCEPTION TESTS ==========

    @Test
    fun `PublishException should contain event information`() {
        val exception = PublishException(
            message = "Failed to publish event",
            eventId = "evt-123",
            eventType = "user.created",
            reason = "Network timeout"
        )

        assertEquals("Failed to publish event", exception.message)
        assertEquals("evt-123", exception.eventId)
        assertEquals("user.created", exception.eventType)
        assertEquals("Network timeout", exception.reason)
    }

    @Test
    fun `PublishException should support null event ID`() {
        val exception = PublishException(
            message = "Failed",
            eventType = "test.event"
        )

        assertNull(exception.eventId)
    }

    @Test
    fun `PublishException should track attempt count`() {
        val exception = PublishException(
            message = "Failed",
            eventType = "test.event",
            attemptCount = 5
        )

        assertEquals(5, exception.attemptCount)
    }

    @Test
    fun `PublishException should have default attempt count of 1`() {
        val exception = PublishException(
            message = "Failed",
            eventType = "test.event"
        )

        assertEquals(1, exception.attemptCount)
    }

    @Test
    fun `PublishException should support cause`() {
        val cause = RuntimeException("Network error")
        val exception = PublishException(
            message = "Failed",
            eventType = "test.event",
            cause = cause
        )

        assertEquals(cause, exception.cause)
    }

    @Test
    fun `PublishException should be ClientException`() {
        val exception = PublishException("Failed", eventType = "test")
        assertTrue(exception is ClientException)
    }

    // ========== RETRY EXHAUSTED EXCEPTION TESTS ==========

    @Test
    fun `RetryExhaustedException should contain retry information`() {
        val exception = RetryExhaustedException(
            message = "All retries failed",
            eventType = "order.placed",
            maxAttempts = 5
        )

        assertEquals("All retries failed", exception.message)
        assertEquals("order.placed", exception.eventType)
        assertEquals(5, exception.maxAttempts)
    }

    @Test
    fun `RetryExhaustedException should support last exception`() {
        val lastException = RuntimeException("Final error")
        val exception = RetryExhaustedException(
            message = "Retries exhausted",
            eventType = "test.event",
            maxAttempts = 3,
            lastException = lastException
        )

        assertEquals(lastException, exception.lastException)
        assertEquals(lastException, exception.cause)
    }

    @Test
    fun `RetryExhaustedException should be ClientException`() {
        val exception = RetryExhaustedException("Failed", "test", 3)
        assertTrue(exception is ClientException)
    }

    // ========== CLIENT CONFIGURATION EXCEPTION TESTS ==========

    @Test
    fun `ClientConfigurationException should contain config key`() {
        val exception = ClientConfigurationException(
            message = "Invalid configuration",
            configKey = "retry.maxAttempts"
        )

        assertEquals("Invalid configuration", exception.message)
        assertEquals("retry.maxAttempts", exception.configKey)
    }

    @Test
    fun `ClientConfigurationException should support null config key`() {
        val exception = ClientConfigurationException(
            message = "General config error"
        )

        assertNull(exception.configKey)
    }

    @Test
    fun `ClientConfigurationException should support cause`() {
        val cause = IllegalArgumentException("Invalid value")
        val exception = ClientConfigurationException(
            message = "Config error",
            cause = cause
        )

        assertEquals(cause, exception.cause)
    }

    @Test
    fun `ClientConfigurationException should be ClientException`() {
        val exception = ClientConfigurationException("Failed")
        assertTrue(exception is ClientException)
    }

    // ========== INTERCEPTOR ABORT EXCEPTION TESTS ==========

    @Test
    fun `InterceptorAbortException should contain interceptor information`() {
        val exception = InterceptorAbortException(
            message = "Interceptor aborted publish",
            eventType = "sensitive.data",
            interceptorName = "SecurityInterceptor",
            reason = "Insufficient permissions"
        )

        assertEquals("Interceptor aborted publish", exception.message)
        assertEquals("sensitive.data", exception.eventType)
        assertEquals("SecurityInterceptor", exception.interceptorName)
        assertEquals("Insufficient permissions", exception.reason)
    }

    @Test
    fun `InterceptorAbortException should support null interceptor name`() {
        val exception = InterceptorAbortException(
            message = "Aborted",
            eventType = "test.event"
        )

        assertNull(exception.interceptorName)
        assertNull(exception.reason)
    }

    @Test
    fun `InterceptorAbortException should be ClientException`() {
        val exception = InterceptorAbortException("Aborted", "test")
        assertTrue(exception is ClientException)
    }

    // ========== BATCH PUBLISH EXCEPTION TESTS ==========

    @Test
    fun `BatchPublishException should track success and failure counts`() {
        val exception = BatchPublishException(
            message = "Batch partially failed",
            successCount = 7,
            failureCount = 3
        )

        assertEquals("Batch partially failed", exception.message)
        assertEquals(7, exception.successCount)
        assertEquals(3, exception.failureCount)
    }

    @Test
    fun `BatchPublishException should contain failure details`() {
        val failures = listOf(
            PublishResult.Failure(eventType = "test1", reason = "Error 1"),
            PublishResult.Failure(eventType = "test2", reason = "Error 2")
        )

        val exception = BatchPublishException(
            message = "Batch failed",
            successCount = 0,
            failureCount = 2,
            failures = failures
        )

        assertEquals(2, exception.failures.size)
        assertEquals("Error 1", exception.failures[0].reason)
    }

    @Test
    fun `BatchPublishException should have default counts of zero`() {
        val exception = BatchPublishException(message = "Failed")

        assertEquals(0, exception.successCount)
        assertEquals(0, exception.failureCount)
        assertTrue(exception.failures.isEmpty())
    }

    @Test
    fun `BatchPublishException should be ClientException`() {
        val exception = BatchPublishException("Failed")
        assertTrue(exception is ClientException)
    }

    // ========== EXTERNAL PUBLISH EXCEPTION TESTS ==========

    @Test
    fun `ExternalPublishException should contain destination information`() {
        val exception = ExternalPublishException(
            message = "External publish failed",
            destination = "kafka://events.topic",
            contentType = "application/json"
        )

        assertEquals("External publish failed", exception.message)
        assertEquals("kafka://events.topic", exception.destination)
        assertEquals("application/json", exception.contentType)
    }

    @Test
    fun `ExternalPublishException should support null destination`() {
        val exception = ExternalPublishException(
            message = "Failed"
        )

        assertNull(exception.destination)
        assertNull(exception.contentType)
    }

    @Test
    fun `ExternalPublishException should support cause`() {
        val cause = RuntimeException("Broker unavailable")
        val exception = ExternalPublishException(
            message = "Failed",
            cause = cause
        )

        assertEquals(cause, exception.cause)
    }

    @Test
    fun `ExternalPublishException should be ClientException`() {
        val exception = ExternalPublishException("Failed")
        assertTrue(exception is ClientException)
    }

    // ========== EVENT VALIDATION FAILED EXCEPTION TESTS ==========

    @Test
    fun `EventValidationFailedException should contain validation errors`() {
        val validationErrors = listOf(
            "Email format invalid",
            "Age must be positive"
        )

        val exception = EventValidationFailedException(
            message = "Validation failed",
            eventType = "user.created",
            validationErrors = validationErrors
        )

        assertEquals("Validation failed", exception.message)
        assertEquals("user.created", exception.eventType)
        assertEquals(2, exception.validationErrors.size)
        assertTrue(exception.validationErrors.contains("Email format invalid"))
    }

    @Test
    fun `EventValidationFailedException should have empty errors by default`() {
        val exception = EventValidationFailedException(
            message = "Failed",
            eventType = "test.event"
        )

        assertTrue(exception.validationErrors.isEmpty())
    }

    @Test
    fun `EventValidationFailedException should be ClientException`() {
        val exception = EventValidationFailedException("Failed", "test")
        assertTrue(exception is ClientException)
    }

    // ========== INVALID CLIENT STATE EXCEPTION TESTS ==========

    @Test
    fun `InvalidClientStateException should contain state information`() {
        val exception = InvalidClientStateException(
            message = "Client not initialized",
            state = "UNINITIALIZED"
        )

        assertEquals("Client not initialized", exception.message)
        assertEquals("UNINITIALIZED", exception.state)
    }

    @Test
    fun `InvalidClientStateException should support null state`() {
        val exception = InvalidClientStateException(
            message = "Invalid state"
        )

        assertNull(exception.state)
    }

    @Test
    fun `InvalidClientStateException should be ClientException`() {
        val exception = InvalidClientStateException("Failed")
        assertTrue(exception is ClientException)
    }

    // ========== EXCEPTION HIERARCHY TESTS ==========

    @Test
    fun `all client exceptions should extend ClientException`() {
        val exceptions = listOf(
            PublishException("test", eventType = "test"),
            RetryExhaustedException("test", "test", 3),
            ClientConfigurationException("test"),
            InterceptorAbortException("test", "test"),
            BatchPublishException("test"),
            ExternalPublishException("test"),
            EventValidationFailedException("test", "test"),
            InvalidClientStateException("test")
        )

        exceptions.forEach { exception ->
            assertTrue(exception is ClientException)
        }
    }

    @Test
    fun `all client exceptions should be RuntimeException`() {
        val exceptions = listOf(
            PublishException("test", eventType = "test"),
            RetryExhaustedException("test", "test", 3),
            ClientConfigurationException("test"),
            InterceptorAbortException("test", "test"),
            BatchPublishException("test"),
            ExternalPublishException("test"),
            EventValidationFailedException("test", "test"),
            InvalidClientStateException("test")
        )

        exceptions.forEach { exception ->
            assertTrue(exception is RuntimeException)
        }
    }

    // ========== PRACTICAL USAGE SCENARIOS ==========

    @Test
    fun `network timeout publish failure scenario`() {
        val networkError = RuntimeException("Socket timeout")
        val exception = PublishException(
            message = "Failed to publish user.created event after 5 attempts",
            eventId = "evt-user-123",
            eventType = "user.created",
            reason = "Network timeout",
            attemptCount = 5,
            cause = networkError
        )

        assertEquals("evt-user-123", exception.eventId)
        assertEquals(5, exception.attemptCount)
        assertEquals(networkError, exception.cause)
    }

    @Test
    fun `retry exhaustion scenario`() {
        val lastError = RuntimeException("Connection refused")
        val exception = RetryExhaustedException(
            message = "All 5 retry attempts failed for order.placed",
            eventType = "order.placed",
            maxAttempts = 5,
            lastException = lastError
        )

        assertEquals(5, exception.maxAttempts)
        assertEquals("order.placed", exception.eventType)
        assertNotNull(exception.lastException)
    }

    @Test
    fun `invalid configuration scenario`() {
        val exception = ClientConfigurationException(
            message = "Invalid retry policy: maxAttempts must be positive",
            configKey = "retry.maxAttempts",
            cause = IllegalArgumentException("Value: -1")
        )

        assertEquals("retry.maxAttempts", exception.configKey)
        assertTrue(exception.cause is IllegalArgumentException)
    }

    @Test
    fun `security interceptor abort scenario`() {
        val exception = InterceptorAbortException(
            message = "Event publish blocked by security policy",
            eventType = "admin.action",
            interceptorName = "SecurityValidationInterceptor",
            reason = "User lacks required permission: ADMIN_WRITE"
        )

        assertEquals("SecurityValidationInterceptor", exception.interceptorName)
        assertTrue(exception.reason!!.contains("ADMIN_WRITE"))
    }

    @Test
    fun `batch publish partial failure scenario`() {
        val failures = listOf(
            PublishResult.Failure(
                eventId = "evt-1",
                eventType = "notification.sent",
                reason = "Invalid recipient"
            ),
            PublishResult.Failure(
                eventId = "evt-2",
                eventType = "notification.sent",
                reason = "Rate limit exceeded"
            )
        )

        val exception = BatchPublishException(
            message = "Batch publish completed with failures: 8 succeeded, 2 failed",
            successCount = 8,
            failureCount = 2,
            failures = failures
        )

        assertEquals(8, exception.successCount)
        assertEquals(2, exception.failureCount)
        assertEquals(2, exception.failures.size)
    }

    @Test
    fun `kafka publish failure scenario`() {
        val brokerError = RuntimeException("Broker not available")
        val exception = ExternalPublishException(
            message = "Failed to publish to Kafka topic",
            destination = "kafka://prod-cluster/events.users",
            contentType = "application/avro",
            cause = brokerError
        )

        assertTrue(exception.destination!!.contains("kafka"))
        assertEquals("application/avro", exception.contentType)
        assertEquals(brokerError, exception.cause)
    }

    @Test
    fun `event validation failure scenario`() {
        val errors = listOf(
            "Field 'userId' is required",
            "Field 'email' must be valid email format",
            "Field 'age' must be between 0 and 120"
        )

        val exception = EventValidationFailedException(
            message = "Event validation failed with 3 errors",
            eventType = "user.registration",
            validationErrors = errors
        )

        assertEquals(3, exception.validationErrors.size)
        assertTrue(exception.validationErrors.any { it.contains("userId") })
        assertTrue(exception.validationErrors.any { it.contains("email") })
    }

    @Test
    fun `client not initialized scenario`() {
        val exception = InvalidClientStateException(
            message = "Cannot publish: EventClient has not been initialized",
            state = "CREATED"
        )

        assertEquals("CREATED", exception.state)
        assertTrue(exception.message!!.contains("not been initialized"))
    }

    @Test
    fun `client closed scenario`() {
        val exception = InvalidClientStateException(
            message = "Cannot publish: EventClient has been closed",
            state = "CLOSED"
        )

        assertEquals("CLOSED", exception.state)
    }
}
