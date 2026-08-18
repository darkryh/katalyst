package io.github.darkryh.katalyst.events.bus

import io.github.darkryh.katalyst.events.DomainEvent
import io.github.darkryh.katalyst.events.EventHandler
import io.github.darkryh.katalyst.events.bus.deduplication.InMemoryEventDeduplicationStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import java.util.concurrent.atomic.AtomicInteger
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The two delivery invariants that matter most, running in the **default** test gate.
 *
 * Both were already asserted — and both were unreachable by CI. `build-logic` excludes the
 * `benchmark` and `load` JUnit tags from the `test` task and routes them to dedicated
 * `benchmarkTest`/`loadTest` tasks, which CI does not run. That exclusion is correct for the
 * wall-clock thresholds those suites are mostly made of, but it also quarantined the only proof
 * that the bus loses nothing under concurrency and the only accuracy check on
 * `deletePublishedBefore` at scale. A correctness assertion nobody runs is not coverage.
 *
 * These are scaled-down copies sized to run in well under a second, so they can live in the gate
 * permanently. The originals stay where they are: their timing thresholds are genuinely
 * environment-sensitive and belong off the gate.
 */
class EventBusDeliveryInvariantTest {

    private data class InvariantEvent(val id: String) : DomainEvent

    private class CountingHandler : EventHandler<InvariantEvent> {
        val count = AtomicInteger(0)
        override val eventType: KClass<InvariantEvent> = InvariantEvent::class
        override suspend fun handle(event: InvariantEvent) {
            count.incrementAndGet()
        }
    }

    @Test
    fun `every event published under concurrent load reaches its handler`() =
        runBlocking(Dispatchers.Default) {
            // `publish()` awaits its handlers inside a supervisorScope, so the count is exact and
            // this is a hard equality — not a "roughly all of them" tolerance. 8 x 250 = 2 000
            // events across a real multi-threaded dispatcher: enough to expose a lost-update or a
            // dropped emission, small enough for the default gate.
            val bus = ApplicationEventBus()
            val handler = CountingHandler()
            bus.register(handler)

            val publishers = 8
            val perPublisher = 250

            coroutineScope {
                (0 until publishers).map { p ->
                    async(Dispatchers.Default) {
                        repeat(perPublisher) { i -> bus.publish(InvariantEvent("p$p-e$i")) }
                    }
                }.awaitAll()
            }

            assertEquals(
                publishers * perPublisher,
                handler.count.get(),
                "events were lost under concurrent load",
            )
        }

    @Test
    fun `deletePublishedBefore removes exactly the records older than the cutoff`() = runBlocking {
        // The accuracy half of the dedup contract: the store must delete every stale record and
        // keep every fresh one. Over-deleting causes duplicate publishes; under-deleting grows the
        // store without bound. Both are silent.
        val store = InMemoryEventDeduplicationStore(maxEntries = 4_000)
        val now = System.currentTimeMillis()
        val stale = 1_000
        val fresh = 500

        repeat(stale) { store.markAsPublished("stale-$it", now - 60_000) }
        repeat(fresh) { store.markAsPublished("fresh-$it", now) }

        val deleted = store.deletePublishedBefore(now - 30_000)

        assertEquals(stale, deleted, "cleanup reported the wrong number of deletions")
        assertEquals(fresh, store.getPublishedCount(), "cleanup left the wrong number of records")
        repeat(fresh) {
            assertEquals(
                true,
                store.isEventPublished("fresh-$it"),
                "a fresh record was deleted by a cleanup that should not have touched it",
            )
        }
        repeat(stale) {
            assertEquals(
                false,
                store.isEventPublished("stale-$it"),
                "a stale record survived cleanup",
            )
        }
    }
}
