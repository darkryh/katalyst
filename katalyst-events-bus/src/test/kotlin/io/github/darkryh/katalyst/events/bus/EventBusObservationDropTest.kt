package io.github.darkryh.katalyst.events.bus

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.github.darkryh.katalyst.events.DomainEvent
import io.github.darkryh.katalyst.events.EventMetadata
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The `events()` observation stream is bounded, so a subscriber that cannot keep up loses events.
 * That is the intended trade (publishers must never block on an observer) - but it must be
 * *observable*. The bus previously combined `DROP_OLDEST` with `tryEmit`, which always returns
 * true, so the "buffer full, dropping" branch was unreachable and every loss was silent.
 */
class EventBusObservationDropTest {

    private val busLogger = LoggerFactory.getLogger(ApplicationEventBus::class.java) as Logger
    private val appender = ListAppender<ILoggingEvent>()
    private var previousLevel: Level? = null

    @BeforeTest
    fun setUp() {
        previousLevel = busLogger.level
        busLogger.level = Level.DEBUG
        appender.list.clear()
        appender.start()
        busLogger.addAppender(appender)
    }

    @AfterTest
    fun tearDown() {
        busLogger.detachAppender(appender)
        appender.stop()
        appender.list.clear()
        busLogger.level = previousLevel
    }

    @Test
    fun `an event lost by a stalled observer is reported instead of vanishing`() =
        runBlocking(Dispatchers.Default) {
            val bus = ApplicationEventBus()
            val subscribed = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val received = AtomicInteger(0)

            // A subscriber that takes one event and then stalls forever: the buffer fills and stays
            // full, so every later emission is a genuine, deterministic drop.
            val observer = launch {
                bus.events()
                    .onSubscription { subscribed.complete(Unit) }
                    .collect {
                        received.incrementAndGet()
                        release.await()
                    }
            }
            withTimeout(10_000) { subscribed.await() }

            val published = 1_000
            withTimeout(30_000) { repeat(published) { i -> bus.publish(Ping("e-$i")) } }

            release.complete(Unit)
            observer.cancel()

            // The publisher was never blocked by the stalled observer.
            assertTrue(received.get() < published, "expected the stalled observer to fall behind")

            val warnings = appender.list.filter { it.level == Level.WARN }
            assertTrue(
                warnings.isNotEmpty(),
                "events dropped for a stalled observer produced no WARN. Logged: " +
                    appender.list.takeLast(5).joinToString { "${it.level}: ${it.formattedMessage}" }
            )
            val message = warnings.first().formattedMessage
            assertTrue(
                "drop" in message.lowercase(),
                "the warning must say events were dropped: $message"
            )
            assertTrue(
                Regex("\\d").containsMatchIn(message),
                "the warning must carry a count so the loss can be quantified: $message"
            )
        }

    private data class Ping(val id: String) : DomainEvent {
        override fun getMetadata() = EventMetadata(eventType = "test.ping")
    }
}
