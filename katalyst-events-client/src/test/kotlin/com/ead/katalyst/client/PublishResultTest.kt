package com.ead.katalyst.client

import kotlin.test.*

/**
 * Comprehensive tests for PublishResult and its subclasses.
 *
 * Tests cover:
 * - Success result
 * - Failure result
 * - Partial result
 * - Type checking methods
 * - Type casting methods
 * - Metadata handling
 * - Success rate calculation
 * - Practical usage scenarios
 */
class PublishResultTest {

    // ========== SUCCESS RESULT TESTS ==========

    @Test
    fun `PublishResult Success should contain event information`() {
        val result = PublishResult.Success(
            eventId = "evt-123",
            eventType = "user.created",
            destination = "events.users"
        )

        assertEquals("evt-123", result.eventId)
        assertEquals("user.created", result.eventType)
        assertEquals("events.users", result.destination)
    }

    @Test
    fun `PublishResult Success should support custom timestamp`() {
        val customTimestamp = 1234567890L
        val result = PublishResult.Success(
            eventId = "evt-123",
            eventType = "user.created",
            destination = "events.users",
            timestamp = customTimestamp
        )

        assertEquals(customTimestamp, result.timestamp)
    }

    @Test
    fun `PublishResult Success should support metadata`() {
        val metadata = mapOf("key1" to "value1", "key2" to "value2")
        val result = PublishResult.Success(
            eventId = "evt-123",
            eventType = "user.created",
            destination = "events.users",
            metadata = metadata
        )

        assertEquals(2, result.metadata.size)
        assertEquals("value1", result.getMetadata("key1"))
        assertEquals("value2", result.getMetadata("key2"))
    }

    @Test
    fun `PublishResult Success getMetadata should return null for missing key`() {
        val result = PublishResult.Success(
            eventId = "evt-123",
            eventType = "user.created",
            destination = "events.users"
        )

        assertNull(result.getMetadata("missing"))
    }

    @Test
    fun `PublishResult Success should have empty metadata by default`() {
        val result = PublishResult.Success(
            eventId = "evt-123",
            eventType = "user.created",
            destination = "events.users"
        )

        assertTrue(result.metadata.isEmpty())
    }

    // ========== FAILURE RESULT TESTS ==========

    @Test
    fun `PublishResult Failure should contain error information`() {
        val result = PublishResult.Failure(
            eventType = "user.created",
            reason = "Connection timeout"
        )

        assertEquals("user.created", result.eventType)
        assertEquals("Connection timeout", result.reason)
        assertNull(result.eventId)
        assertTrue(result.retriable)
    }

    @Test
    fun `PublishResult Failure should support event ID`() {
        val result = PublishResult.Failure(
            eventId = "evt-123",
            eventType = "user.created",
            reason = "Validation failed"
        )

        assertEquals("evt-123", result.eventId)
    }

    @Test
    fun `PublishResult Failure should support cause`() {
        val cause = RuntimeException("Network error")
        val result = PublishResult.Failure(
            eventType = "user.created",
            reason = "Network failure",
            cause = cause
        )

        assertEquals(cause, result.cause)
    }

    @Test
    fun `PublishResult Failure should support non-retriable errors`() {
        val result = PublishResult.Failure(
            eventType = "user.created",
            reason = "Invalid event format",
            retriable = false
        )

        assertFalse(result.retriable)
    }

    @Test
    fun `PublishResult Failure should be retriable by default`() {
        val result = PublishResult.Failure(
            eventType = "user.created",
            reason = "Temporary error"
        )

        assertTrue(result.retriable)
    }

    @Test
    fun `PublishResult Failure should support metadata`() {
        val metadata = mapOf("attempt" to "3", "errorCode" to "500")
        val result = PublishResult.Failure(
            eventType = "user.created",
            reason = "Server error",
            metadata = metadata
        )

        assertEquals("3", result.getMetadata("attempt"))
        assertEquals("500", result.getMetadata("errorCode"))
    }

    // ========== PARTIAL RESULT TESTS ==========

    @Test
    fun `PublishResult Partial should track success and failure counts`() {
        val result = PublishResult.Partial(
            successful = 7,
            failed = 3,
            results = emptyList()
        )

        assertEquals(7, result.successful)
        assertEquals(3, result.failed)
    }

    @Test
    fun `PublishResult Partial total() should sum successful and failed`() {
        val result = PublishResult.Partial(
            successful = 7,
            failed = 3,
            results = emptyList()
        )

        assertEquals(10, result.total())
    }

    @Test
    fun `PublishResult Partial successRate() should calculate percentage`() {
        val result = PublishResult.Partial(
            successful = 7,
            failed = 3,
            results = emptyList()
        )

        assertEquals(70.0, result.successRate())
    }

    @Test
    fun `PublishResult Partial successRate() should handle zero total`() {
        val result = PublishResult.Partial(
            successful = 0,
            failed = 0,
            results = emptyList()
        )

        assertEquals(0.0, result.successRate())
    }

    @Test
    fun `PublishResult Partial isAllSuccessful() should return true when no failures`() {
        val result = PublishResult.Partial(
            successful = 10,
            failed = 0,
            results = emptyList()
        )

        assertTrue(result.isAllSuccessful())
    }

    @Test
    fun `PublishResult Partial isAllSuccessful() should return false when failures exist`() {
        val result = PublishResult.Partial(
            successful = 7,
            failed = 3,
            results = emptyList()
        )

        assertFalse(result.isAllSuccessful())
    }

    @Test
    fun `PublishResult Partial isPartiallySuccessful() should return true when some succeed`() {
        val result = PublishResult.Partial(
            successful = 3,
            failed = 7,
            results = emptyList()
        )

        assertTrue(result.isPartiallySuccessful())
    }

    @Test
    fun `PublishResult Partial isPartiallySuccessful() should return false when none succeed`() {
        val result = PublishResult.Partial(
            successful = 0,
            failed = 10,
            results = emptyList()
        )

        assertFalse(result.isPartiallySuccessful())
    }

    @Test
    fun `PublishResult Partial should contain individual results`() {
        val individualResults = listOf(
            PublishResult.Success("evt-1", "user.created", "queue"),
            PublishResult.Failure(eventType = "user.created", reason = "Error"),
            PublishResult.Success("evt-2", "user.created", "queue")
        )

        val result = PublishResult.Partial(
            successful = 2,
            failed = 1,
            results = individualResults
        )

        assertEquals(3, result.results.size)
        assertTrue(result.results[0] is PublishResult.Success)
        assertTrue(result.results[1] is PublishResult.Failure)
    }

    @Test
    fun `PublishResult Partial should support custom timestamp`() {
        val customTimestamp = 9876543210L
        val result = PublishResult.Partial(
            successful = 5,
            failed = 2,
            results = emptyList(),
            timestamp = customTimestamp
        )

        assertEquals(customTimestamp, result.timestamp)
    }

    // ========== TYPE CHECKING TESTS ==========

    @Test
    fun `isSuccess() should return true for Success result`() {
        val result = PublishResult.Success("evt-1", "user.created", "queue")
        assertTrue(result.isSuccess())
        assertFalse(result.isFailure())
        assertFalse(result.isPartial())
    }

    @Test
    fun `isFailure() should return true for Failure result`() {
        val result = PublishResult.Failure(eventType = "user.created", reason = "Error")
        assertFalse(result.isSuccess())
        assertTrue(result.isFailure())
        assertFalse(result.isPartial())
    }

    @Test
    fun `isPartial() should return true for Partial result`() {
        val result = PublishResult.Partial(successful = 5, failed = 2, results = emptyList())
        assertFalse(result.isSuccess())
        assertFalse(result.isFailure())
        assertTrue(result.isPartial())
    }

    // ========== TYPE CASTING TESTS ==========

    @Test
    fun `asSuccess() should return Success for Success result`() {
        val result: PublishResult = PublishResult.Success("evt-1", "user.created", "queue")
        val success = result.asSuccess()

        assertNotNull(success)
        assertEquals("evt-1", success.eventId)
    }

    @Test
    fun `asSuccess() should return null for non-Success result`() {
        val result: PublishResult = PublishResult.Failure(eventType = "user.created", reason = "Error")
        assertNull(result.asSuccess())
    }

    @Test
    fun `asFailure() should return Failure for Failure result`() {
        val result: PublishResult = PublishResult.Failure(eventType = "user.created", reason = "Error")
        val failure = result.asFailure()

        assertNotNull(failure)
        assertEquals("Error", failure.reason)
    }

    @Test
    fun `asFailure() should return null for non-Failure result`() {
        val result: PublishResult = PublishResult.Success("evt-1", "user.created", "queue")
        assertNull(result.asFailure())
    }

    @Test
    fun `asPartial() should return Partial for Partial result`() {
        val result: PublishResult = PublishResult.Partial(successful = 5, failed = 2, results = emptyList())
        val partial = result.asPartial()

        assertNotNull(partial)
        assertEquals(5, partial.successful)
    }

    @Test
    fun `asPartial() should return null for non-Partial result`() {
        val result: PublishResult = PublishResult.Success("evt-1", "user.created", "queue")
        assertNull(result.asPartial())
    }

    // ========== DATA CLASS BEHAVIOR TESTS ==========

    @Test
    fun `PublishResult Success should support copy`() {
        val original = PublishResult.Success(
            eventId = "evt-123",
            eventType = "user.created",
            destination = "queue"
        )
        val copied = original.copy(eventType = "user.updated")

        assertEquals("evt-123", copied.eventId)
        assertEquals("user.updated", copied.eventType)
        assertEquals("queue", copied.destination)
    }

    @Test
    fun `PublishResult Success should support equality`() {
        val result1 = PublishResult.Success(
            eventId = "evt-123",
            eventType = "user.created",
            destination = "queue",
            timestamp = 1000
        )
        val result2 = PublishResult.Success(
            eventId = "evt-123",
            eventType = "user.created",
            destination = "queue",
            timestamp = 1000
        )

        assertEquals(result1, result2)
    }

    @Test
    fun `PublishResult Failure should support copy`() {
        val original = PublishResult.Failure(
            eventType = "user.created",
            reason = "Error"
        )
        val copied = original.copy(retriable = false)

        assertEquals("user.created", copied.eventType)
        assertFalse(copied.retriable)
    }

    @Test
    fun `PublishResult Partial should support copy`() {
        val original = PublishResult.Partial(
            successful = 5,
            failed = 2,
            results = emptyList()
        )
        val copied = original.copy(successful = 7)

        assertEquals(7, copied.successful)
        assertEquals(2, copied.failed)
    }

    // ========== PRACTICAL USAGE SCENARIOS ==========

    @Test
    fun `successful user creation event publish`() {
        val result = PublishResult.Success(
            eventId = "evt-user-123",
            eventType = "user.created",
            destination = "events.users",
            metadata = mapOf(
                "partition" to "0",
                "offset" to "12345"
            )
        )

        assertTrue(result.isSuccess())
        assertEquals("evt-user-123", result.eventId)
        assertEquals("12345", result.getMetadata("offset"))
    }

    @Test
    fun `retriable network failure scenario`() {
        val result = PublishResult.Failure(
            eventId = "evt-order-456",
            eventType = "order.placed",
            reason = "Connection timeout after 5s",
            retriable = true,
            metadata = mapOf("attemptNumber" to "3")
        )

        assertTrue(result.retriable)
        assertEquals("3", result.getMetadata("attemptNumber"))
    }

    @Test
    fun `non-retriable validation failure scenario`() {
        val result = PublishResult.Failure(
            eventType = "product.updated",
            reason = "Invalid product ID format",
            retriable = false
        )

        assertFalse(result.retriable)
        assertNull(result.eventId)
    }

    @Test
    fun `batch publish with 80 percent success rate`() {
        val results = (1..10).map { i ->
            if (i <= 8) {
                PublishResult.Success("evt-$i", "batch.event", "queue")
            } else {
                PublishResult.Failure(eventType = "batch.event", reason = "Error $i")
            }
        }

        val partial = PublishResult.Partial(
            successful = 8,
            failed = 2,
            results = results
        )

        assertEquals(80.0, partial.successRate())
        assertTrue(partial.isPartiallySuccessful())
        assertFalse(partial.isAllSuccessful())
    }

    @Test
    fun `complete batch success scenario`() {
        val results = (1..5).map {
            PublishResult.Success("evt-$it", "notification.sent", "queue")
        }

        val partial = PublishResult.Partial(
            successful = 5,
            failed = 0,
            results = results
        )

        assertTrue(partial.isAllSuccessful())
        assertEquals(100.0, partial.successRate())
        assertEquals(5, partial.total())
    }

    @Test
    fun `complete batch failure scenario`() {
        val results = (1..3).map {
            PublishResult.Failure(eventType = "email.sent", reason = "SMTP unavailable")
        }

        val partial = PublishResult.Partial(
            successful = 0,
            failed = 3,
            results = results
        )

        assertFalse(partial.isPartiallySuccessful())
        assertFalse(partial.isAllSuccessful())
        assertEquals(0.0, partial.successRate())
    }

    @Test
    fun `publish result with cause exception`() {
        val networkError = RuntimeException("Socket timeout")
        val result = PublishResult.Failure(
            eventType = "payment.processed",
            reason = "Failed to connect to payment gateway",
            cause = networkError,
            retriable = true
        )

        assertEquals(networkError, result.cause)
        assertEquals("Socket timeout", result.cause?.message)
    }

    @Test
    fun `high-volume batch publish statistics`() {
        val partial = PublishResult.Partial(
            successful = 9500,
            failed = 500,
            results = emptyList()  // Individual results omitted for performance
        )

        assertEquals(10000, partial.total())
        assertEquals(95.0, partial.successRate())
        assertTrue(partial.isPartiallySuccessful())
    }
}
