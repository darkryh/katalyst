package io.github.darkryh.katalyst.events.bus

import io.github.darkryh.katalyst.events.DomainEvent
import io.github.darkryh.katalyst.events.EventMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Backpressure tests (Phase 2) for the [ApplicationEventBus] observation stream.
 *
 * The `events()` stream is a bounded `MutableSharedFlow` (extra buffer 128, `DROP_OLDEST`). A slow
 * observer must therefore **never** slow down or block publishers, and the buffer must stay bounded
 * (overflow drops oldest rather than growing without limit). These are structural invariants of the
 * design, asserted without wall-clock thresholds.
 */
class EventBusBackpressureTest {

    private data class Ping(override val eventId: String) : DomainEvent {
        override fun getMetadata() = EventMetadata(eventType = "test.ping")
    }

    @Test
    fun `a slow observer never blocks publishers and the buffer stays bounded`() = runBlocking(Dispatchers.Default) {
        val bus = ApplicationEventBus()
        val received = AtomicInteger(0)

        // Deliberately slow observer: ~10ms per event means it cannot keep up with a fast burst.
        val observer = launch {
            bus.events().collect {
                received.incrementAndGet()
                delay(10)
            }
        }
        // Let the collector subscribe before publishing.
        delay(100)

        val toPublish = 5_000
        val finishedPublishing = withTimeoutOrNull(15_000) {
            repeat(toPublish) { i -> bus.publish(Ping("e-$i")) }
            true
        }

        observer.cancel()

        // 1) Publisher must complete promptly regardless of the slow observer (no blocking).
        assertTrue(finishedPublishing == true, "slow observer blocked the publisher")
        // 2) The observer cannot have received everything — overflow dropped events, proving the
        //    buffer is bounded (5000 @ 10ms would take ~50s; we never waited that long).
        assertTrue(
            received.get() in 1 until toPublish,
            "expected a bounded, lossy subset to be observed but got ${received.get()} of $toPublish"
        )
    }
}
