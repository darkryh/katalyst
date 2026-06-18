package io.github.darkryh.katalyst.events.bus.benchmark

import io.github.darkryh.katalyst.events.bus.deduplication.InMemoryEventDeduplicationStore
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Tag
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Properly-measured microbenchmarks (warmup + measured iterations + median) for the hot dedup-store
 * paths, replacing the single-shot wall-clock asserts. Tagged `benchmark`, so excluded from the
 * default CI gate; run with `./gradlew benchmarkTest`. Bounds are deliberately generous — these
 * track regressions, they do not gate PRs. See [MicroBench] and TESTING_STRATEGY.md Phase 4.
 */
@Tag("benchmark")
class DedupStoreBenchmark {

    @Test
    fun `mark throughput`() = runBlocking {
        val batch = 1_000
        val result = MicroBench.measure("dedup.mark x$batch", warmup = 10, iterations = 30) {
            val store = InMemoryEventDeduplicationStore(maxEntries = batch + 1)
            runBlocking { repeat(batch) { i -> store.markAsPublished("e-$i") } }
        }
        // Sanity bound only (cold-CI generous): catches gross >100x regressions, nothing tighter.
        assertTrue(result.medianMs < 500, "dedup mark median unexpectedly high: $result")
    }

    @Test
    fun `check throughput on a populated store`() = runBlocking {
        val size = 10_000
        val store = InMemoryEventDeduplicationStore(maxEntries = size + 1)
        repeat(size) { i -> store.markAsPublished("e-$i") }

        val lookups = 1_000
        val result = MicroBench.measure("dedup.isPublished x$lookups", warmup = 10, iterations = 30) {
            runBlocking { repeat(lookups) { i -> store.isEventPublished("e-$i") } }
        }
        assertTrue(result.medianMs < 200, "dedup check median unexpectedly high: $result")
    }
}
