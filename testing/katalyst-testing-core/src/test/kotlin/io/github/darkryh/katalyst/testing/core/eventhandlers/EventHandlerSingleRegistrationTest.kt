package io.github.darkryh.katalyst.testing.core.eventhandlers

import io.github.darkryh.katalyst.events.DomainEvent
import io.github.darkryh.katalyst.events.EventHandler
import io.github.darkryh.katalyst.events.bus.EventBus
import io.github.darkryh.katalyst.testing.core.KatalystTestEnvironment
import io.github.darkryh.katalyst.testing.core.katalystTestEnvironment
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.reflect.KClass
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking

/**
 * Guards against double-registration of discovered event handlers.
 *
 * A scanned handler is present in BOTH `GlobalEventHandlerRegistry` and the bean container,
 * and `EventTopology.registerHandlers` subscribes every element it is given without
 * deduplicating. Before container lookups for extension points worked, the container half of
 * that union was always empty, so the overlap was invisible — but it was latent, and turning
 * the lookup on (issue #16) makes every handler subscribe twice and every published event get
 * handled twice.
 *
 * Handling an event twice is worse than not handling it: it double-charges, double-sends,
 * double-writes. This test fails loudly if the union is ever left undeduplicated again.
 */
class EventHandlerSingleRegistrationTest {

    private var environment: KatalystTestEnvironment? = null

    @BeforeTest
    fun setUp() {
        HandledEvents.reset()
    }

    @AfterTest
    fun tearDown() {
        environment?.close()
        environment = null
        HandledEvents.reset()
    }

    @Test
    fun `a discovered handler handles each published event exactly once`() {
        val env = katalystTestEnvironment {
            scan("io.github.darkryh.katalyst.testing.core.eventhandlers")
            disableScheduler()
        }.also { environment = it }

        runBlocking {
            env.container.get(EventBus::class).publish(ProbeEvent("first"))
        }

        assertEquals(
            listOf("first"),
            HandledEvents.handled.toList(),
            "handler subscribed more than once - each published event was handled repeatedly"
        )
    }

    @Test
    fun `the handler is registered exactly once regardless of discovery channel`() {
        val env = katalystTestEnvironment {
            scan("io.github.darkryh.katalyst.testing.core.eventhandlers")
            disableScheduler()
        }.also { environment = it }

        val containerHandlers = runCatching { env.container.getAll(EventHandler::class) }
            .getOrElse { emptyList() }
            .filter { it is ProbeEventHandler }

        assertEquals(
            1,
            containerHandlers.distinctBy { System.identityHashCode(it) }.size,
            "the container should hold exactly one instance of the discovered handler"
        )

        runBlocking {
            env.container.get(EventBus::class).publish(ProbeEvent("a"))
            env.container.get(EventBus::class).publish(ProbeEvent("b"))
        }

        assertEquals(
            listOf("a", "b"),
            HandledEvents.handled.toList(),
            "each event must be handled exactly once"
        )
    }
}

object HandledEvents {
    val handled = CopyOnWriteArrayList<String>()
    fun reset() = handled.clear()
}

data class ProbeEvent(val payload: String) : DomainEvent

class ProbeEventHandler : EventHandler<ProbeEvent> {
    override val eventType: KClass<ProbeEvent> = ProbeEvent::class

    override suspend fun handle(event: ProbeEvent) {
        HandledEvents.handled += event.payload
    }
}
