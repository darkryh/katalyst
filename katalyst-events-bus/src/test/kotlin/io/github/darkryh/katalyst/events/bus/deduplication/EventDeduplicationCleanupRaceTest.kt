package io.github.darkryh.katalyst.events.bus.deduplication

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The exactly-once guarantee the dedup store exists to provide, asserted across the
 * `deletePublishedBefore` / `markAsPublished` race.
 *
 * `EventDeduplicationStoreConcurrencyTest.concurrentCleanupIsSafe` cannot see this class of bug:
 * there every id is marked exactly once and never re-marked, so a stale-snapshot delete is
 * indistinguishable from a correct one. Here an id is deliberately **re-marked with a fresh
 * timestamp while a cleanup pass is in flight** - if cleanup removes by key instead of
 * compare-and-removing the value it observed, the fresh mark is destroyed and the event is
 * published a second time.
 */
@DisplayName("InMemoryEventDeduplicationStore - cleanup must never delete a fresh mark")
class EventDeduplicationCleanupRaceTest {

    private companion object {
        /** Big enough that the removal phase far outlives the re-mark burst below. */
        const val OLD_RECORDS = 50_000
        const val REMARKED = 200
        const val TRIALS = 3

        const val OLD_TS = 1_000L
        const val CUTOFF = 2_000L
        const val FRESH_TS = 3_000L
    }

    @Test
    @DisplayName("an event re-marked while cleanup runs is still published afterwards")
    fun freshMarkSurvivesConcurrentCleanup() {
        var trialsInsideWindow = 0
        val lostPerTrial = mutableListOf<Int>()

        repeat(TRIALS) {
            val store = InMemoryEventDeduplicationStore(maxEntries = OLD_RECORDS + 1)
            runBlocking { repeat(OLD_RECORDS) { i -> store.markAsPublished("e-$i", OLD_TS) } }

            val step = OLD_RECORDS / REMARKED
            val remarked = (0 until REMARKED).map { "e-${it * step}" }

            val start = CyclicBarrier(2)
            val countWhenRemarking = AtomicInteger(-1)

            val cleaner = thread(name = "dedup-cleanup") {
                start.await()
                runBlocking { store.deletePublishedBefore(CUTOFF) }
            }
            val remarker = thread(name = "dedup-remark") {
                start.await()
                runBlocking {
                    // Barrier, not a sleep: spin until the cleanup has provably left its scan
                    // phase and entered removal (the visible count dropped below the snapshot
                    // size). Everything re-marked from here on is a mark the cleanup has
                    // already scanned but not yet acted on - exactly the window under test.
                    var seen = store.getPublishedCount()
                    while (seen == OLD_RECORDS) seen = store.getPublishedCount()
                    countWhenRemarking.set(seen)

                    remarked.forEach { id -> store.markAsPublished(id, FRESH_TS) }
                }
            }
            cleaner.join()
            remarker.join()

            // > 0 proves cleanup was still mid-flight when the re-marks landed. 0 would mean the
            // whole cleanup finished first and the trial proves nothing.
            if (countWhenRemarking.get() > 0) trialsInsideWindow++

            lostPerTrial += runBlocking { remarked.count { !store.isEventPublished(it) } }
        }

        assertTrue(
            trialsInsideWindow > 0,
            "the cleanup/re-mark window never opened in $TRIALS trials - inconclusive, not green"
        )
        assertEquals(
            List(TRIALS) { 0 },
            lostPerTrial,
            "cleanup deleted events that had just been re-marked with a fresh timestamp " +
                "(lost per trial, out of $REMARKED: $lostPerTrial). isEventPublished() now " +
                "returns false for them and they will be published twice."
        )
    }

    @Test
    @DisplayName("deletePublishedBefore removes exactly the strictly-older records and reports the real count")
    fun deletePublishedBeforeIsExact() = runBlocking {
        val random = Random(20_260_818)

        repeat(1_000) {
            val size = random.nextInt(1, 40)
            val cutoff = random.nextLong(0, 1_000)
            val store = InMemoryEventDeduplicationStore(maxEntries = 64)

            val stamps = (0 until size).associate { "e-$it" to random.nextLong(0, 1_000) }
            stamps.forEach { (id, ts) -> store.markAsPublished(id, ts) }

            val expectedDeleted = stamps.count { it.value < cutoff }

            assertEquals(
                expectedDeleted,
                store.deletePublishedBefore(cutoff),
                "deletePublishedBefore must report the number of records it actually removed"
            )
            assertEquals(size - expectedDeleted, store.getPublishedCount())
            stamps.forEach { (id, ts) ->
                assertEquals(
                    ts >= cutoff,
                    store.isEventPublished(id),
                    "record $id (ts=$ts) with cutoff $cutoff"
                )
            }
        }
    }
}
