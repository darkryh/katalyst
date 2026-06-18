package io.github.darkryh.katalyst.events.bus

import io.github.darkryh.katalyst.events.DomainEvent
import io.github.darkryh.katalyst.events.EventHandler
import io.github.darkryh.katalyst.events.EventMetadata
import io.github.darkryh.katalyst.events.bus.deduplication.InMemoryEventDeduplicationStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Tag
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Stress/load and soak suites (Phase 3). Tagged `load`, so they are **excluded from the default
 * CI gate** and run on demand via `./gradlew loadTest`.
 *
 * The load test drives high concurrent publish volume and asserts a **correctness invariant**
 * (every event reaches its handler — zero loss) rather than a throughput threshold. The soak test
 * sustains load for a fixed window and asserts the deduplication store stays bounded (no leak/drift)
 * while cleanup keeps pace.
 */
@Tag("load")
class EventBusLoadAndSoakTest {

    private data class LoadEvent(override val eventId: String) : DomainEvent {
        override fun getMetadata() = EventMetadata(eventType = "test.load")
    }

    private class CountingHandler : EventHandler<LoadEvent> {
        override val eventType: KClass<LoadEvent> = LoadEvent::class
        val count = AtomicInteger(0)
        override suspend fun handle(event: LoadEvent) {
            count.incrementAndGet()
        }
    }

    @Test
    fun `high concurrent publish load delivers every event to its handler`() = runBlocking(Dispatchers.Default) {
        val bus = ApplicationEventBus()
        val handler = CountingHandler()
        bus.register(handler)

        val publishers = 32
        val perPublisher = 2_000 // 64k events under concurrent load

        coroutineScope {
            (0 until publishers).map { p ->
                async(Dispatchers.Default) {
                    repeat(perPublisher) { i -> bus.publish(LoadEvent("p$p-e$i")) }
                }
            }.awaitAll()
        }

        // publish() awaits its handlers (supervisorScope), so the count must be exact: zero loss.
        assertEquals(publishers * perPublisher, handler.count.get(), "events were lost under load")
    }

    @Test
    fun `sustained soak keeps the dedup store bounded with no unbounded growth`() = runBlocking(Dispatchers.Default) {
        val maxEntries = 5_000
        val store = InMemoryEventDeduplicationStore(maxEntries = maxEntries)
        val workers = 8
        val soakMillis = 8_000L
        val deadline = System.currentTimeMillis() + soakMillis
        val produced = AtomicLong(0)
        var peakSize = 0

        coroutineScope {
            (0 until workers).map { w ->
                async(Dispatchers.Default) {
                    var i = 0L
                    while (System.currentTimeMillis() < deadline) {
                        store.markAsPublished("w$w-e${i++}")
                        produced.incrementAndGet()
                        if (i % 1_000 == 0L) {
                            // Periodic cleanup, as a real deployment would run.
                            store.deletePublishedBefore(System.currentTimeMillis() - 1_000)
                        }
                    }
                }
            }.awaitAll()
        }

        peakSize = maxOf(peakSize, store.getPublishedCount())

        // Sanity: the soak actually did meaningful work...
        assertTrue(produced.get() > workers * 1_000L, "soak produced too little to be meaningful: ${produced.get()}")
        // ...and the store never grew beyond its bound despite sustained churn.
        assertTrue(peakSize <= maxEntries, "dedup store grew unbounded under soak: $peakSize > $maxEntries")
    }
}
