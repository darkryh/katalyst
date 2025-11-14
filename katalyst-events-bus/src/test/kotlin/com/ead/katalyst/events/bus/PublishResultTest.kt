package com.ead.katalyst.events.bus

import com.ead.katalyst.events.DomainEvent
import com.ead.katalyst.events.EventMetadata
import kotlin.test.*

/**
 * Comprehensive tests for PublishResult and InterceptResult.
 *
 * Tests cover:
 * - PublishResult creation and field access
 * - Success/failure detection methods
 * - Success rate calculation
 * - Handler failure tracking
 * - InterceptResult variants (Continue, Abort)
 * - Edge cases (zero handlers, all failed, etc.)
 */
class PublishResultTest {

    // ========== PUBLISHRESULT CREATION TESTS ==========

    @Test
    fun `PublishResult should store all fields correctly`() {
        // Given
        val event = TestEvent("test")
        val failure = HandlerFailure("TestHandler", RuntimeException("Test error"))

        // When
        val result = PublishResult(
            event = event,
            handlersInvoked = 5,
            handlersSucceeded = 3,
            handlersFailed = 2,
            failures = listOf(failure),
            durationMs = 150L
        )

        // Then
        assertEquals(event, result.event)
        assertEquals(5, result.handlersInvoked)
        assertEquals(3, result.handlersSucceeded)
        assertEquals(2, result.handlersFailed)
        assertEquals(1, result.failures.size)
        assertEquals(150L, result.durationMs)
    }

    @Test
    fun `PublishResult should use default values`() {
        // Given
        val event = TestEvent("test")

        // When
        val result = PublishResult(event = event)

        // Then
        assertEquals(0, result.handlersInvoked)
        assertEquals(0, result.handlersSucceeded)
        assertEquals(0, result.handlersFailed)
        assertTrue(result.failures.isEmpty())
        assertEquals(0L, result.durationMs)
    }

    // ========== SUCCESS/FAILURE DETECTION TESTS ==========

    @Test
    fun `allSucceeded should return true when no failures`() {
        // Given
        val result = PublishResult(
            event = TestEvent("test"),
            handlersInvoked = 3,
            handlersSucceeded = 3,
            handlersFailed = 0
        )

        // When/Then
        assertTrue(result.allSucceeded())
    }

    @Test
    fun `allSucceeded should return false when there are failures`() {
        // Given
        val result = PublishResult(
            event = TestEvent("test"),
            handlersInvoked = 3,
            handlersSucceeded = 2,
            handlersFailed = 1
        )

        // When/Then
        assertFalse(result.allSucceeded())
    }

    @Test
    fun `allSucceeded should return true when no handlers invoked`() {
        // Given
        val result = PublishResult(
            event = TestEvent("test"),
            handlersInvoked = 0,
            handlersSucceeded = 0,
            handlersFailed = 0
        )

        // When/Then
        assertTrue(result.allSucceeded())
    }

    @Test
    fun `hasFailed should return true when there are failures`() {
        // Given
        val result = PublishResult(
            event = TestEvent("test"),
            handlersInvoked = 3,
            handlersSucceeded = 2,
            handlersFailed = 1
        )

        // When/Then
        assertTrue(result.hasFailed())
    }

    @Test
    fun `hasFailed should return false when no failures`() {
        // Given
        val result = PublishResult(
            event = TestEvent("test"),
            handlersInvoked = 3,
            handlersSucceeded = 3,
            handlersFailed = 0
        )

        // When/Then
        assertFalse(result.hasFailed())
    }

    // ========== SUCCESS RATE CALCULATION TESTS ==========

    @Test
    fun `successRate should return 100 when all handlers succeeded`() {
        // Given
        val result = PublishResult(
            event = TestEvent("test"),
            handlersInvoked = 5,
            handlersSucceeded = 5,
            handlersFailed = 0
        )

        // When
        val rate = result.successRate()

        // Then
        assertEquals(100.0, rate)
    }

    @Test
    fun `successRate should return 0 when all handlers failed`() {
        // Given
        val result = PublishResult(
            event = TestEvent("test"),
            handlersInvoked = 5,
            handlersSucceeded = 0,
            handlersFailed = 5
        )

        // When
        val rate = result.successRate()

        // Then
        assertEquals(0.0, rate)
    }

    @Test
    fun `successRate should calculate partial success correctly`() {
        // Given
        val result = PublishResult(
            event = TestEvent("test"),
            handlersInvoked = 4,
            handlersSucceeded = 3,
            handlersFailed = 1
        )

        // When
        val rate = result.successRate()

        // Then
        assertEquals(75.0, rate)
    }

    @Test
    fun `successRate should return 100 when no handlers invoked`() {
        // Given
        val result = PublishResult(
            event = TestEvent("test"),
            handlersInvoked = 0,
            handlersSucceeded = 0,
            handlersFailed = 0
        )

        // When
        val rate = result.successRate()

        // Then
        assertEquals(100.0, rate)  // No handlers = 100% success (no failures)
    }

    @Test
    fun `successRate should handle single handler success`() {
        // Given
        val result = PublishResult(
            event = TestEvent("test"),
            handlersInvoked = 1,
            handlersSucceeded = 1,
            handlersFailed = 0
        )

        // When
        val rate = result.successRate()

        // Then
        assertEquals(100.0, rate)
    }

    @Test
    fun `successRate should handle single handler failure`() {
        // Given
        val result = PublishResult(
            event = TestEvent("test"),
            handlersInvoked = 1,
            handlersSucceeded = 0,
            handlersFailed = 1
        )

        // When
        val rate = result.successRate()

        // Then
        assertEquals(0.0, rate)
    }

    // ========== HANDLER FAILURE TESTS ==========

    @Test
    fun `failures list should contain all handler failures`() {
        // Given
        val failure1 = HandlerFailure("Handler1", RuntimeException("Error 1"))
        val failure2 = HandlerFailure("Handler2", RuntimeException("Error 2"))
        val failure3 = HandlerFailure("Handler3", RuntimeException("Error 3"))

        // When
        val result = PublishResult(
            event = TestEvent("test"),
            failures = listOf(failure1, failure2, failure3)
        )

        // Then
        assertEquals(3, result.failures.size)
        assertTrue(result.failures.contains(failure1))
        assertTrue(result.failures.contains(failure2))
        assertTrue(result.failures.contains(failure3))
    }

    @Test
    fun `failures list should be empty when no failures`() {
        // Given
        val result = PublishResult(
            event = TestEvent("test"),
            handlersInvoked = 5,
            handlersSucceeded = 5,
            handlersFailed = 0
        )

        // Then
        assertTrue(result.failures.isEmpty())
    }

    // ========== HANDLER FAILURE DATA CLASS TESTS ==========

    @Test
    fun `HandlerFailure should store handler class and exception`() {
        // Given
        val handlerClass = "com.example.MyEventHandler"
        val exception = RuntimeException("Test error")

        // When
        val failure = HandlerFailure(handlerClass, exception)

        // Then
        assertEquals(handlerClass, failure.handlerClass)
        assertEquals(exception, failure.exception)
    }

    @Test
    fun `HandlerFailure should support equality`() {
        // Given
        val exception = RuntimeException("Test error")
        val failure1 = HandlerFailure("Handler1", exception)
        val failure2 = HandlerFailure("Handler1", exception)
        val failure3 = HandlerFailure("Handler2", exception)

        // Then
        assertEquals(failure1, failure2)
        assertNotEquals(failure1, failure3)
    }

    // ========== DATA CLASS BEHAVIOR TESTS ==========

    @Test
    fun `PublishResult should support data class copy`() {
        // Given
        val original = PublishResult(
            event = TestEvent("test"),
            handlersInvoked = 3,
            handlersSucceeded = 2,
            handlersFailed = 1
        )

        // When
        val copy = original.copy(handlersSucceeded = 3, handlersFailed = 0)

        // Then
        assertEquals(3, copy.handlersSucceeded)
        assertEquals(0, copy.handlersFailed)
        assertEquals(3, copy.handlersInvoked)
        assertEquals(2, original.handlersSucceeded)  // Original unchanged
    }

    @Test
    fun `PublishResult should support equality`() {
        // Given
        val event = TestEvent("test")
        val result1 = PublishResult(event, handlersInvoked = 3)
        val result2 = PublishResult(event, handlersInvoked = 3)
        val result3 = PublishResult(event, handlersInvoked = 4)

        // Then
        assertEquals(result1, result2)
        assertNotEquals(result1, result3)
    }

    // ========== EDGE CASES ==========

    @Test
    fun `PublishResult should handle zero handlers`() {
        // Given
        val result = PublishResult(
            event = TestEvent("test"),
            handlersInvoked = 0,
            handlersSucceeded = 0,
            handlersFailed = 0
        )

        // Then
        assertTrue(result.allSucceeded())
        assertFalse(result.hasFailed())
        assertEquals(100.0, result.successRate())
    }

    @Test
    fun `PublishResult should handle large number of handlers`() {
        // Given
        val result = PublishResult(
            event = TestEvent("test"),
            handlersInvoked = 1000,
            handlersSucceeded = 999,
            handlersFailed = 1
        )

        // Then
        assertFalse(result.allSucceeded())
        assertTrue(result.hasFailed())
        assertEquals(99.9, result.successRate())
    }

    @Test
    fun `PublishResult should handle very long duration`() {
        // Given
        val result = PublishResult(
            event = TestEvent("test"),
            durationMs = Long.MAX_VALUE
        )

        // Then
        assertEquals(Long.MAX_VALUE, result.durationMs)
    }

    // ========== INTERCEPTRESULT TESTS ==========

    @Test
    fun `InterceptResult Continue should be singleton`() {
        // When
        val result1 = InterceptResult.Continue
        val result2 = InterceptResult.Continue

        // Then
        assertSame(result1, result2)
    }

    @Test
    fun `InterceptResult Abort should contain reason`() {
        // Given
        val reason = "Event validation failed"

        // When
        val result = InterceptResult.Abort(reason)

        // Then
        assertEquals(reason, result.reason)
    }

    @Test
    fun `InterceptResult Abort should support equality`() {
        // Given
        val abort1 = InterceptResult.Abort("Same reason")
        val abort2 = InterceptResult.Abort("Same reason")
        val abort3 = InterceptResult.Abort("Different reason")

        // Then
        assertEquals(abort1, abort2)
        assertNotEquals(abort1, abort3)
    }

    @Test
    fun `InterceptResult should support sealed class exhaustiveness`() {
        // Given
        val continue: InterceptResult = InterceptResult.Continue
        val abort: InterceptResult = InterceptResult.Abort("test")

        // When/Then - Should compile (exhaustive when expression)
        val continueResult = when (continue) {
            is InterceptResult.Continue -> "continue"
            is InterceptResult.Abort -> "abort"
        }

        val abortResult = when (abort) {
            is InterceptResult.Continue -> "continue"
            is InterceptResult.Abort -> "abort"
        }

        assertEquals("continue", continueResult)
        assertEquals("abort", abortResult)
    }

    @Test
    fun `InterceptResult Abort should handle empty reason`() {
        // Given
        val result = InterceptResult.Abort("")

        // Then
        assertEquals("", result.reason)
    }

    @Test
    fun `InterceptResult Abort should handle long reason`() {
        // Given
        val longReason = "x".repeat(1000)

        // When
        val result = InterceptResult.Abort(longReason)

        // Then
        assertEquals(longReason, result.reason)
    }

    // ========== TEST EVENT CLASS ==========

    private data class TestEvent(
        val data: String,
        val metadata: EventMetadata = EventMetadata(eventType = "test.event")
    ) : DomainEvent {
        override fun getMetadata() = metadata
    }
}
