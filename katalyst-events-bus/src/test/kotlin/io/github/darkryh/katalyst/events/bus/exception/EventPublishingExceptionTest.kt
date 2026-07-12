package io.github.darkryh.katalyst.events.bus.exception

import io.github.darkryh.katalyst.events.DomainEvent
import io.github.darkryh.katalyst.events.EventMetadata
import io.github.darkryh.katalyst.events.bus.HandlerFailure
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Tests for [EventPublishingException].
 *
 * Verifies that the exception retains the failed event and produces an
 * informative, non-empty message referencing the event, and that the
 * original handler failure is preserved as the cause.
 */
class EventPublishingExceptionTest {

    private data class TestEvent(
        val eventMetadata: EventMetadata = EventMetadata(eventId = "evt-123", eventType = "test.event")
    ) : DomainEvent {
        override fun getMetadata() = eventMetadata
    }

    @Test
    fun `message is non-empty and references the event`() {
        val event = TestEvent()
        val exception = EventPublishingException(event, emptyList())

        assertTrue(exception.message!!.isNotBlank(), "message should not be blank")
        assertTrue(exception.message!!.contains("TestEvent"), "message should reference the event class")
        assertTrue(exception.message!!.contains("evt-123"), "message should reference the event id")
        assertTrue(exception.message!!.contains("test.event"), "message should reference the event type")
    }

    @Test
    fun `retains a reference to the failed event`() {
        val event = TestEvent()
        val exception = EventPublishingException(event, emptyList())

        assertSame(event, exception.event)
    }

    @Test
    fun `message summarizes handler failures and cause is preserved`() {
        val event = TestEvent()
        val handlerException = RuntimeException("handler blew up")
        val failure = HandlerFailure(handlerClass = "com.example.SomeHandler", exception = handlerException)

        val exception = EventPublishingException(event, listOf(failure))

        assertTrue(exception.message!!.contains("SomeHandler"), "message should reference the failing handler")
        assertTrue(exception.message!!.contains("handler blew up"), "message should reference the failure reason")
        assertSame(handlerException, exception.cause, "cause should be preserved from the handler failure")
    }

    @Test
    fun `no failures still produces a non-empty message without a cause`() {
        val event = TestEvent()
        val exception = EventPublishingException(event, emptyList())

        assertFalse(exception.message.isNullOrBlank())
        assertEquals(null, exception.cause)
    }
}
