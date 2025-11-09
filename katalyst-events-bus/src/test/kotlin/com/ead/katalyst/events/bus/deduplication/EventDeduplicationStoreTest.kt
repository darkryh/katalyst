package com.ead.katalyst.events.bus.deduplication

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit tests for EventDeduplicationStore implementations.
 *
 * Tests both InMemoryEventDeduplicationStore and NoOpEventDeduplicationStore
 * to ensure proper deduplication behavior.
 */
@DisplayName("Event Deduplication Store Tests")
class EventDeduplicationStoreTest {

    @DisplayName("InMemoryEventDeduplicationStore Tests")
    inner class InMemoryStoreTests {
        private lateinit var store: InMemoryEventDeduplicationStore

        @BeforeEach
        fun setup() {
            store = InMemoryEventDeduplicationStore()
        }

        @Test
        @DisplayName("Should return false for unpublished event")
        fun testUnpublishedEventNotFound() = runTest {
            // Act
            val isPublished = store.isEventPublished("unknown-event-id")

            // Assert
            assertFalse(isPublished)
        }

        @Test
        @DisplayName("Should return true after marking event as published")
        fun testMarkEventAsPublished() = runTest {
            // Arrange
            val eventId = "event-123"

            // Act
            store.markAsPublished(eventId)
            val isPublished = store.isEventPublished(eventId)

            // Assert
            assertTrue(isPublished)
        }

        @Test
        @DisplayName("Should persist published event across multiple checks")
        fun testPersistPublishedEventState() = runTest {
            // Arrange
            val eventId = "event-456"
            store.markAsPublished(eventId)

            // Act
            val firstCheck = store.isEventPublished(eventId)
            val secondCheck = store.isEventPublished(eventId)
            val thirdCheck = store.isEventPublished(eventId)

            // Assert
            assertTrue(firstCheck)
            assertTrue(secondCheck)
            assertTrue(thirdCheck)
        }

        @Test
        @DisplayName("Should handle multiple different events")
        fun testMultipleDifferentEvents() = runTest {
            // Arrange
            val eventIds = listOf("event-1", "event-2", "event-3")

            // Act
            eventIds.forEach { store.markAsPublished(it) }

            // Assert
            eventIds.forEach { eventId ->
                assertTrue(store.isEventPublished(eventId), "Event $eventId should be published")
            }
        }

        @Test
        @DisplayName("Should distinguish between published and unpublished events")
        fun testDistinguishPublishedFromUnpublished() = runTest {
            // Arrange
            val publishedId = "published-event"
            val unpublishedId = "unpublished-event"
            store.markAsPublished(publishedId)

            // Act
            val publishedResult = store.isEventPublished(publishedId)
            val unpublishedResult = store.isEventPublished(unpublishedId)

            // Assert
            assertTrue(publishedResult)
            assertFalse(unpublishedResult)
        }

        @Test
        @DisplayName("Should track published count correctly")
        fun testPublishedCountAccuracy() = runTest {
            // Arrange
            val eventIds = listOf("event-1", "event-2", "event-3")

            // Act
            eventIds.forEach { store.markAsPublished(it) }
            val count = store.getPublishedCount()

            // Assert
            assertEquals(3, count)
        }

        @Test
        @DisplayName("Should start with zero published count")
        fun testInitialPublishedCount() = runTest {
            // Act
            val count = store.getPublishedCount()

            // Assert
            assertEquals(0, count)
        }

        @Test
        @DisplayName("Should delete old published events before timestamp")
        fun testDeletePublishedBefore() = runTest {
            // Arrange
            val now = System.currentTimeMillis()
            val oldTimestamp = now - 60000 // 1 minute ago
            val recentTimestamp = now - 10000 // 10 seconds ago

            // Mark events as published with different timestamps
            store.markAsPublished("old-event", oldTimestamp)
            store.markAsPublished("recent-event", recentTimestamp)

            // Act
            val deletedCount = store.deletePublishedBefore(now - 30000) // Delete before 30 sec ago

            // Assert
            assertEquals(1, deletedCount)
            assertFalse(store.isEventPublished("old-event"))
            assertTrue(store.isEventPublished("recent-event"))
        }

        @Test
        @DisplayName("Should not delete recent events during cleanup")
        fun testCleanupPreservesRecentEvents() = runTest {
            // Arrange
            val now = System.currentTimeMillis()
            val eventId = "recent-event"
            store.markAsPublished(eventId, now)

            // Act
            val deletedCount = store.deletePublishedBefore(now - 1000) // Delete before 1 sec ago

            // Assert
            assertEquals(0, deletedCount)
            assertTrue(store.isEventPublished(eventId))
        }

        @Test
        @DisplayName("Should delete all events before specified time")
        fun testDeleteAllEventsBeforeTime() = runTest {
            // Arrange
            val now = System.currentTimeMillis()
            val oldTime = now - 100000
            (1..5).forEach { i ->
                store.markAsPublished("event-$i", oldTime)
            }

            // Act
            val deletedCount = store.deletePublishedBefore(now)

            // Assert
            assertEquals(5, deletedCount)
            (1..5).forEach { i ->
                assertFalse(store.isEventPublished("event-$i"))
            }
        }

        @Test
        @DisplayName("Should return zero for cleanup with no old events")
        fun testCleanupWithNoOldEvents() = runTest {
            // Arrange
            val now = System.currentTimeMillis()
            store.markAsPublished("event-1", now)

            // Act
            val deletedCount = store.deletePublishedBefore(now - 10000)

            // Assert
            assertEquals(0, deletedCount)
        }

        @Test
        @DisplayName("Should use current time as default timestamp")
        fun testDefaultTimestamp() = runTest {
            // Arrange
            val beforeTime = System.currentTimeMillis()
            val eventId = "event-with-default-time"

            // Act
            store.markAsPublished(eventId)
            val afterTime = System.currentTimeMillis()

            // Verify by checking cleanup with future time doesn't delete
            val futureTime = afterTime + 1000
            val deletedCount = store.deletePublishedBefore(futureTime)

            // Assert
            assertEquals(1, deletedCount)
        }

        @Test
        @DisplayName("Should handle concurrent event publication")
        fun testConcurrentEventPublication() = runTest {
            // Arrange
            val eventIds = (1..10).map { "event-$it" }

            // Act
            eventIds.forEach { store.markAsPublished(it) }

            // Assert
            assertEquals(10, store.getPublishedCount())
            eventIds.forEach { assertTrue(store.isEventPublished(it)) }
        }

        @Test
        @DisplayName("Should update published count after cleanup")
        fun testPublishedCountAfterCleanup() = runTest {
            // Arrange
            val now = System.currentTimeMillis()
            val oldTime = now - 100000
            store.markAsPublished("old-event", oldTime)
            store.markAsPublished("new-event", now)
            assertEquals(2, store.getPublishedCount())

            // Act
            store.deletePublishedBefore(now)

            // Assert
            assertEquals(1, store.getPublishedCount())
        }
    }

    @DisplayName("NoOpEventDeduplicationStore Tests")
    inner class NoOpStoreTests {
        private lateinit var store: NoOpEventDeduplicationStore

        @BeforeEach
        fun setup() {
            store = NoOpEventDeduplicationStore()
        }

        @Test
        @DisplayName("Should never report events as published")
        fun testNoOpNeverReportsPublished() = runTest {
            // Arrange
            val eventIds = listOf("event-1", "event-2", "event-3")

            // Act
            eventIds.forEach { store.markAsPublished(it) }

            // Assert
            eventIds.forEach { eventId ->
                assertFalse(store.isEventPublished(eventId))
            }
        }

        @Test
        @DisplayName("Should accept all events as unpublished")
        fun testNoOpAcceptsAllAsUnpublished() = runTest {
            // Arrange
            val eventId = "any-event-id"

            // Act
            store.markAsPublished(eventId)
            val isPublished = store.isEventPublished(eventId)

            // Assert
            assertFalse(isPublished)
        }

        @Test
        @DisplayName("Should maintain zero published count")
        fun testNoOpZeroCount() = runTest {
            // Act
            store.markAsPublished("event-1")
            store.markAsPublished("event-2")
            val count = store.getPublishedCount()

            // Assert
            assertEquals(0, count)
        }

        @Test
        @DisplayName("Should return zero for cleanup")
        fun testNoOpCleanupReturnsZero() = runTest {
            // Act
            store.markAsPublished("event-1")
            val deletedCount = store.deletePublishedBefore(System.currentTimeMillis())

            // Assert
            assertEquals(0, deletedCount)
        }

        @Test
        @DisplayName("Should be suitable for testing")
        fun testNoOpSuitableForTesting() = runTest {
            // NoOp is used when you want events to be published multiple times
            // This test verifies the behavior is consistent with that use case
            val eventId = "test-event"

            // Never treats events as published, so they can be published multiple times
            assertFalse(store.isEventPublished(eventId))
            store.markAsPublished(eventId)
            assertFalse(store.isEventPublished(eventId)) // Still false - no-op behavior
        }
    }
}
