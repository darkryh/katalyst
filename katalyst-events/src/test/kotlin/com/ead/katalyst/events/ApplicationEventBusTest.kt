package com.ead.katalyst.events

import com.ead.katalyst.events.EventMessagingPublisher
import kotlinx.coroutines.test.runTest
import java.time.LocalDateTime
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

class ApplicationEventBusTest {

    data class TestEvent(
        val payload: String,
        override val eventId: UUID = UUID.randomUUID(),
        override val occurredAt: LocalDateTime = LocalDateTime.now()
    ) : DomainEvent

    private class RecordingHandler : EventHandler<TestEvent> {
        val events = mutableListOf<TestEvent>()
        override val eventType = TestEvent::class

        override suspend fun handle(event: TestEvent) {
            events += event
        }
    }

    private class RecordingPublisher : EventMessagingPublisher {
        val events = mutableListOf<DomainEvent>()
        override suspend fun publish(event: DomainEvent) {
            events += event
        }
    }

    @Test
    fun `delivers events to registered handlers`() = runTest {
        val bus = ApplicationEventBus()
        val handler = RecordingHandler()
        bus.register(handler)

        val event = TestEvent("hello")
        bus.publish(event)

        assertEquals(listOf(event), handler.events)
    }

    @Test
    fun `publishes events through messaging publisher`() = runTest {
        val publisher = RecordingPublisher()
        val bus = ApplicationEventBus(messagingPublisher = publisher)

        val event = TestEvent("external")
        bus.publish(event)

        assertEquals(listOf(event), publisher.events)
    }

    sealed interface FamilyEvent : DomainEvent {
        data class Created(
            val id: Long,
            override val eventId: UUID = UUID.randomUUID(),
            override val occurredAt: LocalDateTime = LocalDateTime.now()
        ) : FamilyEvent

        data class Deleted(
            val id: Long,
            override val eventId: UUID = UUID.randomUUID(),
            override val occurredAt: LocalDateTime = LocalDateTime.now()
        ) : FamilyEvent
    }

    private class FamilyHandler : EventHandler<FamilyEvent> {
        val events = mutableListOf<FamilyEvent>()
        override val eventType = FamilyEvent::class

        override suspend fun handle(event: FamilyEvent) {
            events += event
        }
    }

    @Test
    fun `handles sealed event families`() = runTest {
        val bus = ApplicationEventBus()
        val handler = FamilyHandler()
        bus.register(handler)

        val created = FamilyEvent.Created(1)
        val deleted = FamilyEvent.Deleted(1)

        bus.publish(created)
        bus.publish(deleted)

        assertEquals(listOf(created, deleted), handler.events)
    }
}
