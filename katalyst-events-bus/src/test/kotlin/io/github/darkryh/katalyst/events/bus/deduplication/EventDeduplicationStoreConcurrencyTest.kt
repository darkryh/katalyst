package io.github.darkryh.katalyst.events.bus.deduplication

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Deterministic concurrency-correctness tests for [InMemoryEventDeduplicationStore].
 *
 * Unlike the timing benchmarks in `Phase1PerformanceTests`, these tests assert *invariants*
 * (no lost entries, exactly-once observation, bounded growth) under **real** parallelism on
 * [Dispatchers.Default]. They make no wall-clock assertions, so they are deterministic and
 * safe to run as part of the default CI gate.
 */
@DisplayName("InMemoryEventDeduplicationStore – concurrency correctness")
class EventDeduplicationStoreConcurrencyTest {

    private companion object {
        const val WORKERS = 16
        const val PER_WORKER = 500
    }

    @Test
    @DisplayName("concurrent marks of distinct ids do not lose entries")
    fun concurrentDistinctMarksAreNotLost() = runConcurrent {
        val store = InMemoryEventDeduplicationStore(maxEntries = WORKERS * PER_WORKER + 1)

        parallel(WORKERS) { worker ->
            repeat(PER_WORKER) { i -> store.markAsPublished("w$worker-e$i") }
        }

        // Every distinct id must be retained: no marks lost to a data race.
        assertEquals(WORKERS * PER_WORKER, store.getPublishedCount())
    }

    @Test
    @DisplayName("concurrent marks of the SAME id collapse to a single entry (idempotent)")
    fun concurrentSameIdIsIdempotent() = runConcurrent {
        val store = InMemoryEventDeduplicationStore()

        parallel(WORKERS) {
            repeat(PER_WORKER) { i -> store.markAsPublished("shared-$i") }
        }

        // Each id was marked WORKERS times concurrently but must appear exactly once.
        assertEquals(PER_WORKER, store.getPublishedCount())
    }

    @Test
    @DisplayName("interleaved check + mark never corrupts the store and ids stay published")
    fun concurrentCheckAndMarkIsConsistent() = runConcurrent {
        val store = InMemoryEventDeduplicationStore(maxEntries = WORKERS * PER_WORKER + 1)
        val falseNegativesAfterMark = AtomicInteger(0)

        parallel(WORKERS) { worker ->
            repeat(PER_WORKER) { i ->
                val id = "w$worker-e$i"
                store.isEventPublished(id) // concurrent reads must not throw / corrupt
                store.markAsPublished(id)
                // After this worker marked it, the store must report it as published.
                if (!store.isEventPublished(id)) falseNegativesAfterMark.incrementAndGet()
            }
        }

        assertEquals(0, falseNegativesAfterMark.get(), "a marked id was observed as not-published")
        assertEquals(WORKERS * PER_WORKER, store.getPublishedCount())
    }

    @Test
    @DisplayName("eviction keeps the store bounded under concurrent overflow")
    fun concurrentOverflowStaysBounded() = runConcurrent {
        val maxEntries = 1_000
        val store = InMemoryEventDeduplicationStore(maxEntries = maxEntries)

        // Push far more distinct ids than maxEntries from many workers at once.
        parallel(WORKERS) { worker ->
            repeat(PER_WORKER) { i -> store.markAsPublished("w$worker-e$i") }
        }

        // The store must never grow unbounded; eviction caps it at maxEntries.
        val count = store.getPublishedCount()
        assertTrue(count <= maxEntries, "store grew beyond maxEntries: $count > $maxEntries")
    }

    @Test
    @DisplayName("concurrent cleanup and marks do not corrupt the store")
    fun concurrentCleanupIsSafe() = runConcurrent {
        val store = InMemoryEventDeduplicationStore(maxEntries = WORKERS * PER_WORKER + 1)
        val now = System.currentTimeMillis()

        parallel(WORKERS) { worker ->
            repeat(PER_WORKER) { i ->
                // Half written "old", half "new"; cleanup races with the writers.
                val ts = if (i % 2 == 0) now - 100_000 else now
                store.markAsPublished("w$worker-e$i", ts)
                if (i % 50 == 0) store.deletePublishedBefore(now - 50_000)
            }
        }

        // A final cleanup must leave only the "new" half, proving no lost/duplicated keys.
        store.deletePublishedBefore(now - 50_000)
        assertEquals(WORKERS * (PER_WORKER / 2), store.getPublishedCount())
    }

    /**
     * Runs [block] on [count] coroutines pinned to [Dispatchers.Default] (real threads) and
     * waits for all of them, so assertions observe the fully-settled state.
     */
    private suspend fun parallel(count: Int, block: suspend (worker: Int) -> Unit) =
        coroutineScope {
            (0 until count).map { worker ->
                async(Dispatchers.Default) { block(worker) }
            }.awaitAll()
        }
}

/** Drives a suspend body on a real multi-threaded dispatcher (not the virtual-time test scheduler). */
private fun runConcurrent(body: suspend () -> Unit) {
    kotlinx.coroutines.runBlocking(Dispatchers.Default) { body() }
}
