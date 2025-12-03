package io.github.darkryh.katalyst.events.bus.deduplication

import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

/**
 * Stores information about published events to prevent duplicate publishing.
 *
 * When a service retries a transaction that publishes events, the event
 * deduplication store ensures the same event is not published twice.
 *
 * **Usage:**
 * 1. Before publishing an event, check if it's already been published
 * 2. If not published, publish it
 * 3. After successful publishing, mark it as published
 *
 * **Cleanup:**
 * Old published event records should be cleaned up periodically to prevent
 * unbounded memory growth. See deletePublishedBefore() for cleanup.
 */
interface EventDeduplicationStore {

    /**
     * Check if an event has already been published.
     *
     * @param eventId The unique event ID
     * @return true if event was previously published, false otherwise
     */
    suspend fun isEventPublished(eventId: String): Boolean

    /**
     * Mark an event as published.
     *
     * Call this after successfully publishing an event.
     *
     * @param eventId The unique event ID
     * @param publishedAtMillis Timestamp when event was published (default: now)
     */
    suspend fun markAsPublished(eventId: String, publishedAtMillis: Long = System.currentTimeMillis())

    /**
     * Delete old published event records for cleanup.
     *
     * Call this periodically to prevent memory growth. For example, delete
     * records older than 7 days to keep dedup records but free memory.
     *
     * @param beforeMillis Delete records published before this timestamp
     * @return Number of records deleted
     */
    suspend fun deletePublishedBefore(beforeMillis: Long): Int

    /**
     * Get count of published events (for monitoring).
     *
     * @return Number of published event records
     */
    suspend fun getPublishedCount(): Int
}

/**
 * In-memory implementation of EventDeduplicationStore.
 *
 * Thread-safe using ConcurrentHashMap. Suitable for single-node systems.
 * For distributed systems, consider using a persistent store (database, Redis).
 */
class InMemoryEventDeduplicationStore : EventDeduplicationStore {

    private val logger = LoggerFactory.getLogger(InMemoryEventDeduplicationStore::class.java)

    // Map of event ID to publication timestamp (millis)
    private val publishedEvents = ConcurrentHashMap<String, Long>()

    override suspend fun isEventPublished(eventId: String): Boolean {
        val isPublished = publishedEvents.containsKey(eventId)
        if (isPublished) {
            logger.debug("Event dedup check: {} - Already published", eventId)
        }
        return isPublished
    }

    override suspend fun markAsPublished(eventId: String, publishedAtMillis: Long) {
        publishedEvents[eventId] = publishedAtMillis
        logger.debug("Event marked as published: {} (timestamp: {})", eventId, publishedAtMillis)
    }

    override suspend fun deletePublishedBefore(beforeMillis: Long): Int {
        val keysToDelete = publishedEvents
            .filter { it.value < beforeMillis }
            .keys

        keysToDelete.forEach { publishedEvents.remove(it) }

        val deletedCount = keysToDelete.size
        if (deletedCount > 0) {
            logger.info("Cleaned up {} old published event records", deletedCount)
        }
        return deletedCount
    }

    override suspend fun getPublishedCount(): Int {
        return publishedEvents.size
    }
}

/**
 * No-op implementation for testing or when deduplication is disabled.
 *
 * Accepts all events as "not published" - useful for testing where
 * you want events to be published multiple times.
 */
class NoOpEventDeduplicationStore : EventDeduplicationStore {

    override suspend fun isEventPublished(eventId: String): Boolean = false

    override suspend fun markAsPublished(eventId: String, publishedAtMillis: Long) {
        // No-op
    }

    override suspend fun deletePublishedBefore(beforeMillis: Long): Int = 0

    override suspend fun getPublishedCount(): Int = 0
}
