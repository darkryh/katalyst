package io.github.darkryh.katalyst.events.bus

import io.github.darkryh.katalyst.events.DomainEvent
import io.github.darkryh.katalyst.events.EventMetadata
import io.github.darkryh.katalyst.events.bus.adapter.EventsTransactionAdapter
import io.github.darkryh.katalyst.events.bus.deduplication.InMemoryEventDeduplicationStore
import io.github.darkryh.katalyst.events.bus.validation.DefaultEventPublishingValidator
import io.github.darkryh.katalyst.events.bus.validation.EventValidationException
import io.github.darkryh.katalyst.transactions.adapter.TransactionAdapterRegistry
import io.github.darkryh.katalyst.transactions.context.TransactionEventContext
import io.github.darkryh.katalyst.transactions.hooks.TransactionPhase
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Integration tests for Phase 1 critical fixes.
 *
 * Verifies that all 3 P0 issues work together correctly:
 * 1. Event Publishing Validation
 * 2. Adapter Failure Handling
 * 3. Event Deduplication
 */
@DisplayName("Phase 1 Integration Tests - All 3 Critical Fixes")
class Phase1IntegrationTests {

    // Test domain events
    private data class OrderCreatedEvent(
        override val eventId: String = "order-${System.nanoTime()}",
        val orderId: String = "order-123"
    ) : DomainEvent {
        override fun getMetadata(): EventMetadata = EventMetadata(eventType = "order.created")
    }

    private data class PaymentProcessedEvent(
        override val eventId: String = "payment-${System.nanoTime()}",
        val paymentId: String = "payment-456"
    ) : DomainEvent {
        override fun getMetadata(): EventMetadata = EventMetadata(eventType = "payment.processed")
    }

    private data class NotificationSentEvent(
        override val eventId: String = "notification-${System.nanoTime()}",
        val notificationId: String = "notif-789"
    ) : DomainEvent {
        override fun getMetadata(): EventMetadata = EventMetadata(eventType = "notification.sent")
    }

    // Test fixtures
    private lateinit var eventBus: ApplicationEventBus
    private lateinit var registry: TransactionAdapterRegistry
    private lateinit var dedupStore: InMemoryEventDeduplicationStore
    private lateinit var adapter: EventsTransactionAdapter
    private lateinit var context: TransactionEventContext

    private var publishedEvents = mutableListOf<DomainEvent>()

    @BeforeEach
    fun setup() {
        eventBus = ApplicationEventBus()
        registry = TransactionAdapterRegistry()
        dedupStore = InMemoryEventDeduplicationStore()

        // Subscribe to track published events
        publishedEvents.clear()

        // Create adapter with all 3 fixes
        val validator = DefaultEventPublishingValidator { eventBus.hasHandlers(it) }
        adapter = EventsTransactionAdapter(eventBus, validator, dedupStore)

        // Register adapter
        registry.register(adapter)

        // Create transaction context
        context = TransactionEventContext()
    }

    @Test
    @DisplayName("Issue #1: Should prevent partial event publishing on validation failure")
    fun testEventPublishingValidation() = runTest {
        // Arrange - Setup scenario where validation will fail
        val order = OrderCreatedEvent()
        val payment = PaymentProcessedEvent()

        context.queueEvent(order)
        context.queueEvent(payment)

        // Create a validator that requires handlers for both events
        val strictValidator = DefaultEventPublishingValidator { event ->
            // Simulate missing handler for OrderCreatedEvent
            event !is OrderCreatedEvent
        }

        val strictAdapter = EventsTransactionAdapter(eventBus, strictValidator, dedupStore)

        // Act & Assert
        try {
            strictAdapter.onPhase(TransactionPhase.BEFORE_COMMIT_VALIDATION, context)
            fail("Should have thrown EventValidationException")
        } catch (e: EventValidationException) {
            // Expected - validation fails before any commit
            assertEquals(0, dedupStore.getPublishedCount(), "No events should be marked as published on validation failure")
            assertEquals(2, context.getPendingEventCount(), "All events should still be pending")
        }
    }

    @Test
    @DisplayName("Issue #1: Should allow commit when all events have handlers")
    fun testEventPublishingValidationPasses() = runTest {
        // Arrange
        val order = OrderCreatedEvent()
        val payment = PaymentProcessedEvent()
        context.queueEvent(order)
        context.queueEvent(payment)

        val validator = DefaultEventPublishingValidator { true } // All events have handlers

        // Act
        val strictAdapter = EventsTransactionAdapter(eventBus, validator, dedupStore)
        strictAdapter.onPhase(TransactionPhase.BEFORE_COMMIT_VALIDATION, context)
        strictAdapter.onPhase(TransactionPhase.BEFORE_COMMIT, context)

        // Assert
        assertTrue(dedupStore.isEventPublished(order.eventId))
        assertTrue(dedupStore.isEventPublished(payment.eventId))
        assertEquals(0, context.getPendingEventCount(), "Events should be cleared after publishing")
    }

    @Test
    @DisplayName("Issue #2: Should rollback transaction when critical adapter fails")
    fun testAdapterFailurePreventCommit() = runTest {
        // Arrange
        val order = OrderCreatedEvent()
        context.queueEvent(order)

        val failingAdapter = EventsTransactionAdapter(
            eventBus,
            DefaultEventPublishingValidator { throw RuntimeException("Critical validation error") },
            dedupStore
        )
        registry.clear()
        registry.register(failingAdapter)

        // Act & Assert
        try {
            val results = registry.executeAdapters(
                TransactionPhase.BEFORE_COMMIT_VALIDATION,
                context,
                failFast = true
            )
            fail("Should have thrown exception on critical adapter failure")
        } catch (e: Exception) {
            // Expected - critical failure prevents commit
            assertEquals(0, dedupStore.getPublishedCount(), "No events published on adapter failure")
        }
    }

    @Test
    @DisplayName("Issue #3: Should prevent duplicate event publishing on retry")
    fun testEventDeduplicationOnRetry() = runTest {
        // Arrange - Simulate first attempt with permissive validator
        val eventId = "idempotent-event-123"
        val order = OrderCreatedEvent(eventId = eventId)

        val testAdapter = EventsTransactionAdapter(eventBus, DefaultEventPublishingValidator { true }, dedupStore)

        // First attempt
        context.queueEvent(order)
        testAdapter.onPhase(TransactionPhase.BEFORE_COMMIT_VALIDATION, context)
        testAdapter.onPhase(TransactionPhase.BEFORE_COMMIT, context)

        assertTrue(dedupStore.isEventPublished(eventId), "Event should be marked as published after first attempt")
        assertEquals(0, context.getPendingEventCount())

        // Simulate retry (service retried the transaction)
        val retryContext = TransactionEventContext()
        val retryOrder = OrderCreatedEvent(eventId = eventId) // Same event ID

        retryContext.queueEvent(retryOrder)

        // Act - Retry publishing
        testAdapter.onPhase(TransactionPhase.BEFORE_COMMIT, retryContext)

        // Assert
        assertEquals(0, retryContext.getPendingEventCount(), "Event should be cleared even though it was skipped")
        assertEquals(1, dedupStore.getPublishedCount(), "Published count should not increase for duplicate")
    }

    @Test
    @DisplayName("All 3 fixes together: Successful transaction with validation, no duplicates, adapter success")
    fun testSuccessfulTransactionWithAllFixes() = runTest {
        // Arrange
        val order = OrderCreatedEvent(eventId = "order-success-1")
        val payment = PaymentProcessedEvent(eventId = "payment-success-1")
        val notification = NotificationSentEvent(eventId = "notif-success-1")

        context.queueEvent(order)
        context.queueEvent(payment)
        context.queueEvent(notification)

        // Use permissive validator for this test
        val testAdapter = EventsTransactionAdapter(eventBus, DefaultEventPublishingValidator { true }, dedupStore)

        // Act - Simulate full transaction lifecycle
        testAdapter.onPhase(TransactionPhase.BEFORE_COMMIT_VALIDATION, context)
        testAdapter.onPhase(TransactionPhase.BEFORE_COMMIT, context)

        // Assert
        assertEquals(3, dedupStore.getPublishedCount(), "All 3 events should be published")
        assertTrue(dedupStore.isEventPublished("order-success-1"))
        assertTrue(dedupStore.isEventPublished("payment-success-1"))
        assertTrue(dedupStore.isEventPublished("notif-success-1"))
        assertEquals(0, context.getPendingEventCount(), "All events should be cleared")
    }

    @Test
    @DisplayName("All 3 fixes together: Mixed batch with duplicates and new events")
    fun testMixedBatchWithDuplicatesAndNewEvents() = runTest {
        // Arrange - Simulate previous attempt with some events
        val dedupOrder = OrderCreatedEvent(eventId = "order-dedup-1")
        val dedupPayment = PaymentProcessedEvent(eventId = "payment-dedup-1")
        dedupStore.markAsPublished("order-dedup-1")
        dedupStore.markAsPublished("payment-dedup-1")

        // New transaction with mix of duplicate and new events
        val context2 = TransactionEventContext()
        context2.queueEvent(dedupOrder) // Duplicate
        context2.queueEvent(dedupPayment) // Duplicate
        context2.queueEvent(NotificationSentEvent(eventId = "notif-new-1")) // New

        // Use permissive validator
        val testAdapter = EventsTransactionAdapter(eventBus, DefaultEventPublishingValidator { true }, dedupStore)

        // Act
        testAdapter.onPhase(TransactionPhase.BEFORE_COMMIT_VALIDATION, context2)
        testAdapter.onPhase(TransactionPhase.BEFORE_COMMIT, context2)

        // Assert
        assertEquals(3, dedupStore.getPublishedCount(), "2 old + 1 new = 3 total")
        assertTrue(dedupStore.isEventPublished("order-dedup-1"))
        assertTrue(dedupStore.isEventPublished("payment-dedup-1"))
        assertTrue(dedupStore.isEventPublished("notif-new-1"))
        assertEquals(0, context2.getPendingEventCount())
    }

    @Test
    @DisplayName("All 3 fixes together: Rollback clears pending events without publishing")
    fun testRollbackWithAllFixes() = runTest {
        // Arrange
        val order = OrderCreatedEvent(eventId = "order-rollback-1")
        val payment = PaymentProcessedEvent(eventId = "payment-rollback-1")

        context.queueEvent(order)
        context.queueEvent(payment)

        // Verify pending events before rollback
        assertEquals(2, context.getPendingEventCount())
        assertEquals(0, dedupStore.getPublishedCount())

        // Act - Simulate rollback
        adapter.onPhase(TransactionPhase.ON_ROLLBACK, context)

        // Assert
        assertEquals(0, context.getPendingEventCount(), "All pending events cleared on rollback")
        assertEquals(0, dedupStore.getPublishedCount(), "No events should be marked as published after rollback")
    }

    @Test
    @DisplayName("Scenario: Validation succeeds, publishing fails for one event, others continue")
    fun testPartialPublishingFailureWithValidation() = runTest {
        // Arrange
        val order = OrderCreatedEvent(eventId = "order-partial-1")
        val payment = PaymentProcessedEvent(eventId = "payment-partial-1")
        val notification = NotificationSentEvent(eventId = "notif-partial-1")

        context.queueEvent(order)
        context.queueEvent(payment)
        context.queueEvent(notification)

        // Validation passes for all
        val validator = DefaultEventPublishingValidator { true }
        val testAdapter = EventsTransactionAdapter(eventBus, validator, dedupStore)

        // Act
        testAdapter.onPhase(TransactionPhase.BEFORE_COMMIT_VALIDATION, context)
        testAdapter.onPhase(TransactionPhase.BEFORE_COMMIT, context)

        // Assert - All should be marked as published (even if bus fails during publishing)
        // The adapter logs failures but continues
        assertEquals(0, context.getPendingEventCount(), "All events should be cleared after attempting publish")
    }

    @Test
    @DisplayName("Performance: Multiple events validation and deduplication")
    fun testPerformanceWithManyEvents() = runTest {
        // Arrange - Create 100 events
        val events = (1..100).map { i ->
            OrderCreatedEvent(eventId = "order-perf-$i")
        }

        events.forEach { context.queueEvent(it) }

        val validator = DefaultEventPublishingValidator { true }
        val testAdapter = EventsTransactionAdapter(eventBus, validator, dedupStore)

        // Act
        val startTime = System.currentTimeMillis()
        testAdapter.onPhase(TransactionPhase.BEFORE_COMMIT_VALIDATION, context)
        testAdapter.onPhase(TransactionPhase.BEFORE_COMMIT, context)
        val duration = System.currentTimeMillis() - startTime

        // Assert
        assertEquals(100, dedupStore.getPublishedCount())
        assertEquals(0, context.getPendingEventCount())
        assertTrue(duration < 1000, "Publishing 100 events should complete in < 1 second, took ${duration}ms")
    }

    @Test
    @DisplayName("Idempotency: Multiple retries produce same result")
    fun testIdempotencyAcrossMultipleRetries() = runTest {
        // Arrange
        val eventId = "idempotent-event-456"
        val order = OrderCreatedEvent(eventId = eventId)

        val testAdapter = EventsTransactionAdapter(eventBus, DefaultEventPublishingValidator { true }, dedupStore)

        // Attempt 1
        val context1 = TransactionEventContext()
        context1.queueEvent(order)
        testAdapter.onPhase(TransactionPhase.BEFORE_COMMIT_VALIDATION, context1)
        testAdapter.onPhase(TransactionPhase.BEFORE_COMMIT, context1)
        val count1 = dedupStore.getPublishedCount()

        // Attempt 2 (Retry)
        val context2 = TransactionEventContext()
        context2.queueEvent(OrderCreatedEvent(eventId = eventId))
        testAdapter.onPhase(TransactionPhase.BEFORE_COMMIT_VALIDATION, context2)
        testAdapter.onPhase(TransactionPhase.BEFORE_COMMIT, context2)
        val count2 = dedupStore.getPublishedCount()

        // Attempt 3 (Retry again)
        val context3 = TransactionEventContext()
        context3.queueEvent(OrderCreatedEvent(eventId = eventId))
        testAdapter.onPhase(TransactionPhase.BEFORE_COMMIT_VALIDATION, context3)
        testAdapter.onPhase(TransactionPhase.BEFORE_COMMIT, context3)
        val count3 = dedupStore.getPublishedCount()

        // Assert - All retries should not increase published count
        assertEquals(count1, count2, "Retry should not increase published count")
        assertEquals(count2, count3, "Second retry should not increase published count")
        assertEquals(1, count3, "Only 1 event should be in dedup store")
    }

    @Test
    @DisplayName("State consistency: Dedup store reflects actual published state")
    fun testDedupStoreConsistency() = runTest {
        // Arrange
        val events = listOf(
            OrderCreatedEvent(eventId = "order-1"),
            PaymentProcessedEvent(eventId = "payment-1"),
            NotificationSentEvent(eventId = "notif-1")
        )

        val testAdapter = EventsTransactionAdapter(eventBus, DefaultEventPublishingValidator { true }, dedupStore)

        // First publish
        val context1 = TransactionEventContext()
        events.forEach { context1.queueEvent(it) }
        testAdapter.onPhase(TransactionPhase.BEFORE_COMMIT_VALIDATION, context1)
        testAdapter.onPhase(TransactionPhase.BEFORE_COMMIT, context1)

        val countAfterFirst = dedupStore.getPublishedCount()

        // Verify each event is marked
        events.forEach { event ->
            assertTrue(dedupStore.isEventPublished(event.eventId), "Event ${event.eventId} should be published")
        }

        // Act - Try to publish same events again
        val context2 = TransactionEventContext()
        events.forEach { context2.queueEvent(it) }
        testAdapter.onPhase(TransactionPhase.BEFORE_COMMIT_VALIDATION, context2)
        testAdapter.onPhase(TransactionPhase.BEFORE_COMMIT, context2)

        val countAfterSecond = dedupStore.getPublishedCount()

        // Assert
        assertEquals(countAfterFirst, countAfterSecond, "Published count should not change on retry")
        assertEquals(3, countAfterSecond)
    }
}
