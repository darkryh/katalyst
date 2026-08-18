package io.github.darkryh.katalyst.events.bus

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.github.darkryh.katalyst.events.DomainEvent
import io.github.darkryh.katalyst.events.EventHandler
import io.github.darkryh.katalyst.events.EventMetadata
import org.slf4j.LoggerFactory
import kotlin.reflect.KClass
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [EventTopology] drops a handler that fails to register and carries on - which is the right
 * runtime behaviour, but the summary it logs afterwards must not claim everything worked.
 * A handler silently missing from the bus is an event type that silently stops being handled.
 */
class EventTopologyRegistrationReportTest {

    private val topologyLogger = LoggerFactory.getLogger(EventTopology::class.java) as Logger
    private val appender = ListAppender<ILoggingEvent>()
    private var previousLevel: Level? = null

    @BeforeTest
    fun setUp() {
        previousLevel = topologyLogger.level
        topologyLogger.level = Level.DEBUG
        appender.list.clear()
        appender.start()
        topologyLogger.addAppender(appender)
    }

    @AfterTest
    fun tearDown() {
        topologyLogger.detachAppender(appender)
        appender.stop()
        appender.list.clear()
        topologyLogger.level = previousLevel
    }

    @Test
    fun `a handler that fails to register is reported in the summary, not swallowed`() {
        val topology = EventTopology(ApplicationEventBus(), InMemoryEventHandlerRegistry())

        topology.registerHandlers(listOf(GoodHandler(), BrokenHandler(), GoodHandler()))

        // The bus really did drop it - that part is by design.
        assertEquals(2, topology.getRegistry().size(), "only the healthy handlers should be registered")

        val summary = summaryLine()
        assertTrue(
            summary.level == Level.ERROR || summary.level == Level.WARN,
            "a dropped handler must be summarised above INFO, was ${summary.level}: ${summary.formattedMessage}"
        )
        val message = summary.formattedMessage
        assertTrue("2" in message, "summary must report how many handlers registered: $message")
        assertTrue("1" in message, "summary must report how many handlers failed: $message")
        assertTrue(
            BrokenHandler::class.simpleName!! in message,
            "summary must name the handler that was dropped: $message"
        )
        assertTrue(
            "successfully" !in message.lowercase(),
            "a registration that dropped a handler must not be reported as successful: $message"
        )
    }

    @Test
    fun `a clean registration reports the counts it actually registered`() {
        val topology = EventTopology(ApplicationEventBus(), InMemoryEventHandlerRegistry())

        topology.registerHandlers(listOf(GoodHandler(), GoodHandler()))

        val summary = summaryLine()
        assertEquals(Level.INFO, summary.level, "a clean registration must stay at INFO")
        assertTrue(
            "2" in summary.formattedMessage,
            "summary must report the number of registered handlers: ${summary.formattedMessage}"
        )
    }

    /** The last line EventTopology logs for a registration pass is its summary. */
    private fun summaryLine(): ILoggingEvent =
        appender.list.lastOrNull() ?: error("EventTopology logged no summary at all")

    private data class TestEvent(val id: String = "e") : DomainEvent {
        override fun getMetadata() = EventMetadata(eventType = "test.topology")
    }

    private class GoodHandler : EventHandler<TestEvent> {
        override val eventType: KClass<TestEvent> = TestEvent::class
        override suspend fun handle(event: TestEvent) = Unit
    }

    /** Models a handler whose event type cannot be resolved at bootstrap (proxy, bad generics, ...). */
    private class BrokenHandler : EventHandler<TestEvent> {
        override val eventType: KClass<TestEvent>
            get() = throw IllegalStateException("event type unavailable")

        override suspend fun handle(event: TestEvent) = Unit
    }
}
