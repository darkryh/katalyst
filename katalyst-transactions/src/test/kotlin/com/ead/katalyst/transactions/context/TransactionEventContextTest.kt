package com.ead.katalyst.transactions.context

import com.ead.katalyst.events.DomainEvent
import com.ead.katalyst.events.EventMetadata
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.*

/**
 * Comprehensive tests for TransactionEventContext.
 *
 * Tests cover:
 * - Event queueing (single, multiple)
 * - Event retrieval (immutable copy)
 * - Event clearing
 * - Pending events detection
 * - Event count tracking
 * - CoroutineContext integration
 * - Extension functions (getTransactionEventContext, isInTransactionContext)
 * - Thread-local behavior
 * - Context lifecycle
 * - Edge cases
 */
class TransactionEventContextTest {

    // ========== EVENT QUEUEING TESTS ==========

    @Test
    fun `queueEvent should add event to pending queue`() = runTest {
        // Given
        val context = TransactionEventContext()
        val event = TestEvent("test-1")

        // When
        context.queueEvent(event)

        // Then
        assertEquals(1, context.getPendingEventCount())
        assertEquals(event, context.getPendingEvents().first())
    }

    @Test
    fun `queueEvent should add multiple events in order`() = runTest {
        // Given
        val context = TransactionEventContext()
        val event1 = TestEvent("test-1")
        val event2 = TestEvent("test-2")
        val event3 = TestEvent("test-3")

        // When
        context.queueEvent(event1)
        context.queueEvent(event2)
        context.queueEvent(event3)

        // Then
        val pending = context.getPendingEvents()
        assertEquals(3, pending.size)
        assertEquals(event1, pending[0])
        assertEquals(event2, pending[1])
        assertEquals(event3, pending[2])
    }

    @Test
    fun `queueEvent should allow queuing same event multiple times`() = runTest {
        // Given
        val context = TransactionEventContext()
        val event = TestEvent("test")

        // When
        context.queueEvent(event)
        context.queueEvent(event)

        // Then
        assertEquals(2, context.getPendingEventCount())
    }

    // ========== GET PENDING EVENTS TESTS ==========

    @Test
    fun `getPendingEvents should return empty list when no events queued`() = runTest {
        // Given
        val context = TransactionEventContext()

        // When
        val events = context.getPendingEvents()

        // Then
        assertTrue(events.isEmpty())
    }

    @Test
    fun `getPendingEvents should return all queued events`() = runTest {
        // Given
        val context = TransactionEventContext()
        val event1 = TestEvent("test-1")
        val event2 = TestEvent("test-2")

        context.queueEvent(event1)
        context.queueEvent(event2)

        // When
        val events = context.getPendingEvents()

        // Then
        assertEquals(2, events.size)
        assertTrue(events.contains(event1))
        assertTrue(events.contains(event2))
    }

    @Test
    fun `getPendingEvents should return immutable copy`() = runTest {
        // Given
        val context = TransactionEventContext()
        context.queueEvent(TestEvent("test-1"))

        // When
        val events1 = context.getPendingEvents()
        context.queueEvent(TestEvent("test-2"))
        val events2 = context.getPendingEvents()

        // Then
        assertEquals(1, events1.size)  // Original list unchanged
        assertEquals(2, events2.size)  // New list reflects changes
    }

    @Test
    fun `getPendingEvents should not remove events from queue`() = runTest {
        // Given
        val context = TransactionEventContext()
        context.queueEvent(TestEvent("test"))

        // When
        context.getPendingEvents()
        context.getPendingEvents()

        // Then
        assertEquals(1, context.getPendingEventCount())  // Still 1 event
    }

    // ========== CLEAR PENDING EVENTS TESTS ==========

    @Test
    fun `clearPendingEvents should remove all queued events`() = runTest {
        // Given
        val context = TransactionEventContext()
        context.queueEvent(TestEvent("test-1"))
        context.queueEvent(TestEvent("test-2"))
        context.queueEvent(TestEvent("test-3"))

        // When
        context.clearPendingEvents()

        // Then
        assertEquals(0, context.getPendingEventCount())
        assertTrue(context.getPendingEvents().isEmpty())
    }

    @Test
    fun `clearPendingEvents on empty queue should not throw`() = runTest {
        // Given
        val context = TransactionEventContext()

        // When/Then - Should not throw
        context.clearPendingEvents()

        assertEquals(0, context.getPendingEventCount())
    }

    @Test
    fun `clearPendingEvents should allow queuing after clearing`() = runTest {
        // Given
        val context = TransactionEventContext()
        context.queueEvent(TestEvent("test-1"))
        context.clearPendingEvents()

        // When
        context.queueEvent(TestEvent("test-2"))

        // Then
        assertEquals(1, context.getPendingEventCount())
    }

    @Test
    fun `multiple clears should work correctly`() = runTest {
        // Given
        val context = TransactionEventContext()

        // When/Then
        context.queueEvent(TestEvent("test-1"))
        context.clearPendingEvents()
        assertEquals(0, context.getPendingEventCount())

        context.queueEvent(TestEvent("test-2"))
        context.clearPendingEvents()
        assertEquals(0, context.getPendingEventCount())

        context.queueEvent(TestEvent("test-3"))
        assertEquals(1, context.getPendingEventCount())
    }

    // ========== HAS PENDING EVENTS TESTS ==========

    @Test
    fun `hasPendingEvents should return false when no events queued`() = runTest {
        // Given
        val context = TransactionEventContext()

        // When/Then
        assertFalse(context.hasPendingEvents())
    }

    @Test
    fun `hasPendingEvents should return true when events are queued`() = runTest {
        // Given
        val context = TransactionEventContext()
        context.queueEvent(TestEvent("test"))

        // When/Then
        assertTrue(context.hasPendingEvents())
    }

    @Test
    fun `hasPendingEvents should return false after clearing`() = runTest {
        // Given
        val context = TransactionEventContext()
        context.queueEvent(TestEvent("test"))
        context.clearPendingEvents()

        // When/Then
        assertFalse(context.hasPendingEvents())
    }

    // ========== GET PENDING EVENT COUNT TESTS ==========

    @Test
    fun `getPendingEventCount should return 0 for empty queue`() = runTest {
        // Given
        val context = TransactionEventContext()

        // When/Then
        assertEquals(0, context.getPendingEventCount())
    }

    @Test
    fun `getPendingEventCount should return correct count`() = runTest {
        // Given
        val context = TransactionEventContext()

        // When/Then
        assertEquals(0, context.getPendingEventCount())

        context.queueEvent(TestEvent("test-1"))
        assertEquals(1, context.getPendingEventCount())

        context.queueEvent(TestEvent("test-2"))
        assertEquals(2, context.getPendingEventCount())

        context.queueEvent(TestEvent("test-3"))
        assertEquals(3, context.getPendingEventCount())
    }

    @Test
    fun `getPendingEventCount should return 0 after clearing`() = runTest {
        // Given
        val context = TransactionEventContext()
        context.queueEvent(TestEvent("test-1"))
        context.queueEvent(TestEvent("test-2"))

        // When
        context.clearPendingEvents()

        // Then
        assertEquals(0, context.getPendingEventCount())
    }

    // ========== COROUTINE CONTEXT INTEGRATION TESTS ==========

    @Test
    fun `TransactionEventContext should be a CoroutineContext Element`() {
        // Given
        val context = TransactionEventContext()

        // Then
        assertTrue(context is CoroutineContext.Element)
    }

    @Test
    fun `TransactionEventContext should have correct key`() {
        // Given
        val context = TransactionEventContext()

        // Then
        assertEquals(TransactionEventContext, context.key)
    }

    @Test
    fun `context should work with withContext`() = runTest {
        // Given
        val txContext = TransactionEventContext()
        val event = TestEvent("test")

        // When
        withContext(txContext) {
            txContext.queueEvent(event)
        }

        // Then
        assertEquals(1, txContext.getPendingEventCount())
        assertEquals(event, txContext.getPendingEvents().first())
    }

    @Test
    fun `nested withContext should share same context`() = runTest {
        // Given
        val txContext = TransactionEventContext()

        // When
        withContext(txContext) {
            txContext.queueEvent(TestEvent("outer"))

            withContext(EmptyCoroutineContext) {
                txContext.queueEvent(TestEvent("inner"))
            }
        }

        // Then
        assertEquals(2, txContext.getPendingEventCount())
    }

    // ========== EXTENSION FUNCTION TESTS ==========

    @Test
    fun `getTransactionEventContext should return context when present`() = runTest {
        // Given
        val txContext = TransactionEventContext()

        // When
        withContext(txContext) {
            val retrieved = coroutineContext.getTransactionEventContext()

            // Then
            assertSame(txContext, retrieved)
        }
    }

    @Test
    fun `getTransactionEventContext should return null when not present`() = runTest {
        // When
        val retrieved = coroutineContext.getTransactionEventContext()

        // Then
        assertNull(retrieved)
    }

    @Test
    fun `isInTransactionContext should return true when context present`() = runTest {
        // Given
        val txContext = TransactionEventContext()

        // When/Then
        withContext(txContext) {
            assertTrue(coroutineContext.isInTransactionContext())
        }
    }

    @Test
    fun `isInTransactionContext should return false when context not present`() = runTest {
        // When/Then
        assertFalse(coroutineContext.isInTransactionContext())
    }

    // ========== TOSTRING TESTS ==========

    @Test
    fun `toString should show pending event count`() = runTest {
        // Given
        val context = TransactionEventContext()
        context.queueEvent(TestEvent("test-1"))
        context.queueEvent(TestEvent("test-2"))

        // When
        val string = context.toString()

        // Then
        assertTrue(string.contains("TransactionEventContext"))
        assertTrue(string.contains("pending=2"))
    }

    @Test
    fun `toString should show zero for empty queue`() = runTest {
        // Given
        val context = TransactionEventContext()

        // When
        val string = context.toString()

        // Then
        assertTrue(string.contains("pending=0"))
    }

    // ========== EDGE CASES ==========

    @Test
    fun `context should handle large number of events`() = runTest {
        // Given
        val context = TransactionEventContext()
        val eventCount = 1000

        // When
        repeat(eventCount) { context.queueEvent(TestEvent("test-$it")) }

        // Then
        assertEquals(eventCount, context.getPendingEventCount())
        assertEquals(eventCount, context.getPendingEvents().size)
    }

    @Test
    fun `context should handle events with same data`() = runTest {
        // Given
        val context = TransactionEventContext()
        val event1 = TestEvent("same-data")
        val event2 = TestEvent("same-data")

        // When
        context.queueEvent(event1)
        context.queueEvent(event2)

        // Then
        assertEquals(2, context.getPendingEventCount())
    }

    @Test
    fun `context should preserve event order across operations`() = runTest {
        // Given
        val context = TransactionEventContext()
        val events = (1..10).map { TestEvent("event-$it") }

        // When
        events.forEach { context.queueEvent(it) }
        val _ = context.getPendingEvents()  // Get events
        val retrieved = context.getPendingEvents()  // Get again

        // Then
        assertEquals(events, retrieved)
    }

    @Test
    fun `context should handle different event types`() = runTest {
        // Given
        val context = TransactionEventContext()
        val event1 = TestEvent("test")
        val event2 = AnotherTestEvent(42)

        // When
        context.queueEvent(event1)
        context.queueEvent(event2)

        // Then
        assertEquals(2, context.getPendingEventCount())
        val pending = context.getPendingEvents()
        assertTrue(pending[0] is TestEvent)
        assertTrue(pending[1] is AnotherTestEvent)
    }

    @Test
    fun `complete lifecycle - queue, get, clear, queue again`() = runTest {
        // Given
        val context = TransactionEventContext()

        // When - First cycle
        context.queueEvent(TestEvent("test-1"))
        assertEquals(1, context.getPendingEventCount())

        var events = context.getPendingEvents()
        assertEquals(1, events.size)

        context.clearPendingEvents()
        assertEquals(0, context.getPendingEventCount())

        // When - Second cycle
        context.queueEvent(TestEvent("test-2"))
        context.queueEvent(TestEvent("test-3"))
        assertEquals(2, context.getPendingEventCount())

        events = context.getPendingEvents()
        assertEquals(2, events.size)
    }

    // ========== TEST EVENT CLASSES ==========

    private data class TestEvent(
        val data: String,
        val metadata: EventMetadata = EventMetadata(eventType = "test.event")
    ) : DomainEvent {
        override fun getMetadata() = metadata
    }

    private data class AnotherTestEvent(
        val value: Int,
        val metadata: EventMetadata = EventMetadata(eventType = "another.test.event")
    ) : DomainEvent {
        override fun getMetadata() = metadata
    }
}
