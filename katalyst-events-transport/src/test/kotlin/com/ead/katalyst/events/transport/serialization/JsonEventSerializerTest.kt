package com.ead.katalyst.events.transport.serialization

import com.ead.katalyst.events.DomainEvent
import com.ead.katalyst.events.EventMetadata
import com.ead.katalyst.events.transport.exception.EventSerializationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlin.text.Charsets

class JsonEventSerializerTest {

    private data class SampleEvent(
        val id: String,
        private val metadata: EventMetadata
    ) : DomainEvent {
        override fun getMetadata(): EventMetadata = metadata
    }

    @Test
    fun `serialize includes metadata headers`() = runBlocking {
        val metadata = EventMetadata.of("sample.event", correlationId = "corr-1")
        val event = SampleEvent("42", metadata)
        val serializer = JsonEventSerializer { """{"id":"${event.id}"}""" }

        val message = serializer.serialize(event)

        assertEquals("""{"id":"42"}""", message.payload.decodeToString())
        assertEquals(metadata.eventType, message.headers["event-type"])
        assertEquals(metadata.eventId, message.headers["event-id"])
        assertEquals("corr-1", message.headers["correlation-id"])
        assertEquals(serializer.getContentType(), message.contentType)
    }

    @Test
    fun `serialize wraps mapper exceptions`() = runBlocking {
        val serializer = JsonEventSerializer { error("boom") }
        val event = SampleEvent("bad", EventMetadata.of("bad.event"))

        val exception = kotlin.runCatching {
            serializer.serialize(event)
        }.exceptionOrNull()

        assertTrue(exception is EventSerializationException)
        assertEquals("bad.event", exception.eventType)
    }

    private fun ByteArray.decodeToString(): String = toString(Charsets.UTF_8)
}
