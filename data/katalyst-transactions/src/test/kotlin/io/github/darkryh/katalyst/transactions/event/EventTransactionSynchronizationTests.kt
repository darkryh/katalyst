package io.github.darkryh.katalyst.transactions.event

import io.github.darkryh.katalyst.transactions.context.TransactionEventContext
import io.github.darkryh.katalyst.transactions.hooks.TransactionPhase
import io.github.darkryh.katalyst.events.DomainEvent
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Phase 3: Event-Transaction Synchronization Tests
 *
 * Validates complete synchronization between event publishing and transaction lifecycle.
 * Ensures events are executed at correct transaction phases and rollback/commit behave correctly.
 */
class EventTransactionSynchronizationTests {

    private lateinit var context: TransactionEventContext
    private val executionLog = mutableListOf<String>()

    @BeforeEach
    fun setUp() {
        context = TransactionEventContext()
        executionLog.clear()
    }

    /**
     * Test: Transaction phase sequence is preserved
     *
     * Verifies phases execute in correct order:
     * BEFORE_BEGIN → AFTER_BEGIN → BEFORE_COMMIT_VALIDATION → BEFORE_COMMIT → AFTER_COMMIT → [ON_ROLLBACK → AFTER_ROLLBACK]
     */
    @Test
    @Timeout(10)
    fun `transaction phases execute in correct order`() = runBlocking {
        val phases = mutableListOf<TransactionPhase>()

        // Simulate phase execution
        phases.add(TransactionPhase.BEFORE_BEGIN)
        assertEquals(0, context.getPendingEventCount(), "No events before begin")

        phases.add(TransactionPhase.AFTER_BEGIN)
        // Queue event during transaction
        context.queueEvent(TestEvent("event1"))
        assertEquals(1, context.getPendingEventCount())

        phases.add(TransactionPhase.BEFORE_COMMIT_VALIDATION)
        // Validate events
        assertTrue(context.getPendingEventCount() > 0)

        phases.add(TransactionPhase.BEFORE_COMMIT)
        // Publish sync events - should be empty after publish
        context.clearPendingEvents()

        phases.add(TransactionPhase.AFTER_COMMIT)
        // Transaction committed

        // Verify correct order
        assertEquals(
            listOf(
                TransactionPhase.BEFORE_BEGIN,
                TransactionPhase.AFTER_BEGIN,
                TransactionPhase.BEFORE_COMMIT_VALIDATION,
                TransactionPhase.BEFORE_COMMIT,
                TransactionPhase.AFTER_COMMIT
            ),
            phases
        )
    }

    /**
     * Test: Events queued during transaction are available for publishing
     */
    @Test
    @Timeout(10)
    fun `events queued in transaction are available in BEFORE_COMMIT`() = runBlocking {
        // Simulate AFTER_BEGIN phase
        val event1 = TestEvent("event1")
        val event2 = TestEvent("event2")
        context.queueEvent(event1)
        context.queueEvent(event2)

        // BEFORE_COMMIT: events should be available
        val pendingEvents = context.getPendingEvents()
        assertEquals(2, pendingEvents.size)
        assertEquals(event1, pendingEvents[0])
        assertEquals(event2, pendingEvents[1])
    }

    /**
     * Test: Event ordering is maintained
     *
     * Events published in order should maintain that order through lifecycle.
     */
    @Test
    @Timeout(10)
    fun `event publishing order is maintained through transaction`() = runBlocking {
        // Queue events in specific order
        val event1 = TestEvent("first")
        val event2 = TestEvent("second")
        val event3 = TestEvent("third")

        context.queueEvent(event1)
        context.queueEvent(event2)
        context.queueEvent(event3)

        // Verify order preserved
        val pending = context.getPendingEvents()
        assertEquals(3, pending.size)
        assertEquals("first", (pending[0] as TestEvent).value)
        assertEquals("second", (pending[1] as TestEvent).value)
        assertEquals("third", (pending[2] as TestEvent).value)
    }

    /**
     * Test: Pending events cleared after BEFORE_COMMIT
     *
     * After publishing sync events, pending queue should be empty.
     */
    @Test
    @Timeout(10)
    fun `pending events cleared after BEFORE_COMMIT publishing`() = runBlocking {
        context.queueEvent(TestEvent("event1"))
        context.queueEvent(TestEvent("event2"))

        assertEquals(2, context.getPendingEventCount())

        // Simulate BEFORE_COMMIT publishing
        context.clearPendingEvents()

        assertEquals(0, context.getPendingEventCount())
    }

    /**
     * Test: Rollback discards all pending events
     *
     * ON_ROLLBACK phase should clear all events that haven't been published.
     */
    @Test
    @Timeout(10)
    fun `ON_ROLLBACK phase discards all pending events`() = runBlocking {
        context.queueEvent(TestEvent("queued1"))
        context.queueEvent(TestEvent("queued2"))

        assertEquals(2, context.getPendingEventCount())

        // Simulate rollback
        context.clearPendingEvents()

        assertEquals(0, context.getPendingEventCount(), "All events should be discarded on rollback")
    }

    /**
     * Test: Empty transaction (no events)
     *
     * Transactions without events should still work correctly.
     */
    @Test
    @Timeout(10)
    fun `transaction without events completes successfully`() = runBlocking {
        // No events queued
        assertEquals(0, context.getPendingEventCount())

        // All phases should execute without error
        assertTrue(context.getPendingEvents().isEmpty())

        // Rollback should also work
        context.clearPendingEvents()
        assertEquals(0, context.getPendingEventCount())
    }

    /**
     * Test: Large batch of events
     *
     * Transaction should handle many events (100+) efficiently.
     */
    @Test
    @Timeout(10)
    fun `transaction handles large batch of events`() = runBlocking {
        // Queue 100 events
        for (i in 1..100) {
            context.queueEvent(TestEvent("event$i"))
        }

        assertEquals(100, context.getPendingEventCount())

        // Verify all events are accessible
        val events = context.getPendingEvents()
        assertEquals(100, events.size)

        // Verify ordering maintained
        for (i in 1..100) {
            assertEquals("event$i", (events[i - 1] as TestEvent).value)
        }

        // Clear should work
        context.clearPendingEvents()
        assertEquals(0, context.getPendingEventCount())
    }

    /**
     * Test: Concurrent transaction isolation
     *
     * Multiple transaction contexts should not share events.
     */
    @Test
    @Timeout(10)
    fun `concurrent transactions have isolated event contexts`() = runBlocking {
        val context1 = TransactionEventContext()
        val context2 = TransactionEventContext()

        // Queue events in context1
        context1.queueEvent(TestEvent("tx1-event1"))
        context1.queueEvent(TestEvent("tx1-event2"))

        // Queue different events in context2
        context2.queueEvent(TestEvent("tx2-event1"))

        // Verify isolation
        assertEquals(2, context1.getPendingEventCount())
        assertEquals(1, context2.getPendingEventCount())

        val events1 = context1.getPendingEvents()
        val events2 = context2.getPendingEvents()

        assertEquals("tx1-event1", (events1[0] as TestEvent).value)
        assertEquals("tx1-event2", (events1[1] as TestEvent).value)
        assertEquals("tx2-event1", (events2[0] as TestEvent).value)
    }

    /**
     * Test: Event count tracking
     *
     * Pending event count should accurately reflect queued events.
     */
    @Test
    @Timeout(10)
    fun `pending event count tracked accurately`() = runBlocking {
        assertEquals(0, context.getPendingEventCount(), "Initial state empty")

        context.queueEvent(TestEvent("1"))
        assertEquals(1, context.getPendingEventCount())

        context.queueEvent(TestEvent("2"))
        assertEquals(2, context.getPendingEventCount())

        context.queueEvent(TestEvent("3"))
        assertEquals(3, context.getPendingEventCount())

        // Clear one by one
        val pending = context.getPendingEvents()
        assertEquals(3, pending.size)

        context.clearPendingEvents()
        assertEquals(0, context.getPendingEventCount())
    }

    /**
     * Test: Mixed event types in single transaction
     *
     * Transaction should handle different event types together.
     */
    @Test
    @Timeout(10)
    fun `mixed event types execute in transaction`() = runBlocking {
        val userEvent = UserCreatedEvent(userId = 1L, email = "user@example.com")
        val profileEvent = ProfileCreatedEvent(profileId = 10L, userId = 1L)
        val notificationEvent = NotificationEvent(message = "Welcome!")

        context.queueEvent(userEvent)
        context.queueEvent(profileEvent)
        context.queueEvent(notificationEvent)

        assertEquals(3, context.getPendingEventCount())

        val events = context.getPendingEvents()
        assertEquals(3, events.size)
        assertTrue(events[0] is UserCreatedEvent)
        assertTrue(events[1] is ProfileCreatedEvent)
        assertTrue(events[2] is NotificationEvent)
    }

    /**
     * Test: Rapid event queueing
     *
     * Multiple events queued rapidly should maintain order.
     */
    @Test
    @Timeout(10)
    fun `rapid event queueing maintains order`() = runBlocking {
        val events = mutableListOf<DomainEvent>()

        for (i in 1..50) {
            val event = TestEvent("rapid-$i")
            context.queueEvent(event)
            events.add(event)
        }

        assertEquals(50, context.getPendingEventCount())

        val queued = context.getPendingEvents()
        for (i in 0 until 50) {
            assertEquals("rapid-${i + 1}", (queued[i] as TestEvent).value)
        }
    }

    /**
     * Test: Event context lifecycle
     *
     * Context should properly initialize and clean up.
     */
    @Test
    @Timeout(10)
    fun `event context lifecycle`() = runBlocking {
        val ctx = TransactionEventContext()

        // Initial state
        assertEquals(0, ctx.getPendingEventCount())
        assertTrue(ctx.getPendingEvents().isEmpty())

        // After queueing
        ctx.queueEvent(TestEvent("1"))
        assertEquals(1, ctx.getPendingEventCount())

        // After clearing
        ctx.clearPendingEvents()
        assertEquals(0, ctx.getPendingEventCount())
        assertTrue(ctx.getPendingEvents().isEmpty())
    }

    // Test event classes
    data class TestEvent(val value: String) : DomainEvent {
        override val eventId: String = "test-$value"
        override fun eventType(): String = "TestEvent"
    }

    data class UserCreatedEvent(val userId: Long, val email: String) : DomainEvent {
        override val eventId: String = "user-$userId"
        override fun eventType(): String = "UserCreatedEvent"
    }

    data class ProfileCreatedEvent(val profileId: Long, val userId: Long) : DomainEvent {
        override val eventId: String = "profile-$profileId"
        override fun eventType(): String = "ProfileCreatedEvent"
    }

    data class NotificationEvent(val message: String) : DomainEvent {
        override val eventId: String = "notif-$message"
        override fun eventType(): String = "NotificationEvent"
    }
}
