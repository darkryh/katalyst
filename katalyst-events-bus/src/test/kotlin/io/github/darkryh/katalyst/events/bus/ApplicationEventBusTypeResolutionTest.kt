package io.github.darkryh.katalyst.events.bus

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.github.darkryh.katalyst.events.DomainEvent
import io.github.darkryh.katalyst.events.EventHandler
import kotlinx.coroutines.test.runTest
import org.slf4j.LoggerFactory
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.reflect.KClass
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Handler-to-event-type resolution.
 *
 * `EventHandler.eventType` used to be matched against `event::class` by **exact class**, with
 * sealed parents expanded ahead of time. Any other base type - an abstract class, an interface,
 * a catch-all `EventHandler<DomainEvent>`, or a non-sealed intermediate inside an otherwise
 * sealed hierarchy - registered under a key that no event instance ever carries, so the handler
 * never fired and the only trace was a DEBUG line nobody sees in production.
 *
 * These tests pin the resolution rule that replaced it: a handler receives an event when the
 * registered type is assignable from the event's class, **exactly once** however many registered
 * keys match, and a type that genuinely can never receive anything is reported at WARN.
 */
class ApplicationEventBusTypeResolutionTest {

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
    fun `a handler for an abstract, non-sealed base class receives every subtype`() = runTest {
        val bus = ApplicationEventBus()
        val handler = Recorder(AuditableEvent::class)
        bus.register(handler)

        bus.publish(OrderPlaced("o-1"))
        bus.publish(OrderShipped("o-1"))

        assertEquals(
            listOf("OrderPlaced", "OrderShipped"),
            handler.received.map { it::class.simpleName },
            "a handler registered for an abstract base type received nothing"
        )
    }

    @Test
    fun `a handler for an interface event type receives its implementations`() = runTest {
        val bus = ApplicationEventBus()
        val handler = Recorder(Traceable::class)
        bus.register(handler)

        bus.publish(TracedEvent("t-1"))

        assertEquals(1, handler.received.size, "a handler registered for an event interface received nothing")
    }

    @Test
    fun `a catch-all EventHandler of DomainEvent receives every published event`() = runTest {
        val bus = ApplicationEventBus()
        val catchAll = Recorder(DomainEvent::class)
        bus.register(catchAll)

        bus.publish(OrderPlaced("o-1"))
        bus.publish(TracedEvent("t-1"))
        bus.publish(RestartedAfterCrash())

        assertEquals(
            3,
            catchAll.received.size,
            "the most natural catch-all handler (an audit/logging EventHandler<DomainEvent>) received nothing"
        )
    }

    @Test
    fun `a sealed parent with a non-sealed intermediate still reaches the leaf type`() = runTest {
        val bus = ApplicationEventBus()
        val handler = Recorder(Lifecycle::class)
        bus.register(handler)

        // Resolution of the sealed parent stops at the non-sealed `Started`, so `RestartedAfterCrash`
        // is never a registered key - it can only be reached by supertype assignability.
        bus.publish(RestartedAfterCrash())
        bus.publish(Started())

        assertEquals(
            listOf("RestartedAfterCrash", "Started"),
            handler.received.map { it::class.simpleName },
            "a leaf below a non-sealed intermediate was not delivered"
        )
    }

    @Test
    fun `an event matching several registered types invokes each handler exactly once`() = runTest {
        val bus = ApplicationEventBus()
        // `Signal` resolves to BOTH `Alpha` and `Beta`, and AlphaAndBeta is an instance of both.
        val fanIn = Recorder(Signal::class)
        val alsoExact = Recorder(AlphaAndBeta::class)
        bus.register(fanIn)
        bus.register(alsoExact)

        bus.publish(AlphaAndBeta())

        assertEquals(1, fanIn.received.size, "handler invoked ${fanIn.received.size} times, expected exactly once")
        assertEquals(1, alsoExact.received.size, "exact-type handler must still fire exactly once")
    }

    @Test
    fun `an exact-type handler is unaffected and unrelated events are not delivered`() = runTest {
        val bus = ApplicationEventBus()
        val placed = Recorder(OrderPlaced::class)
        bus.register(placed)

        bus.publish(OrderPlaced("o-1"))
        bus.publish(OrderShipped("o-2"))
        bus.publish(TracedEvent("t-1"))

        assertEquals(1, placed.received.size, "an exact-type handler must receive only its own event type")
    }

    @Test
    fun `hasHandlers agrees with delivery for a base-type registration`() = runTest {
        val bus = ApplicationEventBus()
        bus.register(Recorder(AuditableEvent::class))

        assertTrue(
            bus.hasHandlers(OrderPlaced("o-1")),
            "hasHandlers() said no handler exists for an event that is in fact delivered - " +
                "publishing validation would reject a perfectly handled event"
        )
        assertTrue(!bus.hasHandlers(TracedEvent("t-1")), "hasHandlers() must stay false for unhandled events")
    }

    @Test
    fun `a handler registered after the first publish still receives events`() = runTest {
        val bus = ApplicationEventBus()
        val exact = Recorder(OrderPlaced::class)
        bus.register(exact)
        bus.publish(OrderPlaced("o-1"))

        val late = Recorder(AuditableEvent::class)
        bus.register(late)
        bus.publish(OrderPlaced("o-2"))

        assertEquals(2, exact.received.size)
        assertEquals(1, late.received.size, "a handler registered after the first publish was never resolved")
    }

    @Test
    fun `registering a handler for a type no event can ever match is reported at WARN`() {
        val bus = ApplicationEventBus()

        bus.register(Recorder(NeverEmitted::class))

        val warning = appender.list.firstOrNull { it.level == Level.WARN }
        assertTrue(
            warning != null,
            "registering a handler that can never receive an event produced no WARN, only: " +
                appender.list.joinToString { "${it.level}: ${it.formattedMessage}" }
        )
        assertTrue(
            "Recorder" in warning.formattedMessage,
            "the warning must name the handler: ${warning.formattedMessage}"
        )
        assertTrue(
            "NeverEmitted" in warning.formattedMessage,
            "the warning must name the event type: ${warning.formattedMessage}"
        )
    }

    @Test
    fun `registering a reachable handler produces no warning`() {
        val bus = ApplicationEventBus()

        bus.register(Recorder(OrderPlaced::class))
        bus.register(Recorder(AuditableEvent::class))
        bus.register(Recorder(Lifecycle::class))

        assertEquals(
            emptyList(),
            appender.list.filter { it.level == Level.WARN }.map { it.formattedMessage },
            "a handler that can receive events must not be warned about"
        )
    }

    private class Recorder<T : DomainEvent>(override val eventType: KClass<T>) : EventHandler<T> {
        val received = CopyOnWriteArrayList<T>()
        override suspend fun handle(event: T) {
            received += event
        }
    }
}

// ---- fixtures (top level: sealed hierarchies must live in the same package/module) ----

private abstract class AuditableEvent : DomainEvent

private data class OrderPlaced(val id: String) : AuditableEvent()

private data class OrderShipped(val id: String) : AuditableEvent()

private interface Traceable : DomainEvent

private data class TracedEvent(val id: String) : Traceable

private sealed class Lifecycle : DomainEvent

private open class Started : Lifecycle()

private class RestartedAfterCrash : Started()

private sealed interface Signal : DomainEvent

private interface Alpha : Signal

private interface Beta : Signal

private class AlphaAndBeta : Alpha, Beta

/** A sealed type with no subclasses: nothing can ever be an instance of it. */
private sealed class NeverEmitted : DomainEvent
