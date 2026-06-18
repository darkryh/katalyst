package io.github.darkryh.katalyst.events.bus.deduplication

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EventDeduplicationRetentionTest {
    @Test
    fun `store evicts oldest entries at configured capacity`() = runTest {
        val store = InMemoryEventDeduplicationStore(maxEntries = 3, retentionMillis = Long.MAX_VALUE)

        repeat(5) { index -> store.markAsPublished("event-$index", index.toLong()) }

        assertEquals(3, store.getPublishedCount())
        assertFalse(store.isEventPublished("event-0"))
        assertTrue(store.isEventPublished("event-4"))
    }
}
