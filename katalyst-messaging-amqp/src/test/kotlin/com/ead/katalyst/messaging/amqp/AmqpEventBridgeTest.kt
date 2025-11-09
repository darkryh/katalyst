package com.ead.katalyst.messaging.amqp

import com.ead.katalyst.client.EventClientInterceptor
import com.ead.katalyst.client.PublishResult
import com.ead.katalyst.events.DomainEvent
import com.ead.katalyst.events.EventMetadata
import com.ead.katalyst.events.transport.serialization.JsonEventSerializer
import com.ead.katalyst.messaging.amqp.config.AmqpConfiguration
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIsInstance
import kotlin.test.assertTrue

/**
 * Unit tests for AmqpEventBridge.
 *
 * Tests:
 * - Event interception and forwarding to AMQP
 * - Routing key generation from event types
 * - Metadata preservation and header mapping
 * - Error handling and retry logic
 * - Before/after/error hooks
 *
 * Uses mock implementations to avoid requiring a real RabbitMQ instance.
 */
class AmqpEventBridgeTest {

    private lateinit var mockPublisher: MockAmqpPublisher
    private lateinit var serializer: JsonEventSerializer
    private lateinit var bridge: AmqpEventBridge

    @BeforeEach
    fun setup() {
        mockPublisher = MockAmqpPublisher()
        serializer = JsonEventSerializer()
        bridge = AmqpEventBridge(
            publisher = mockPublisher,
            serializer = serializer,
            routingKeyPrefix = "events"
        )
    }

    @Test
    fun `test beforePublish forwards event to AMQP`() = runBlocking {
        // Given
        val event = TestEvent("user-123", "John Doe")
        val context = EventClientInterceptor.PublishContext(
            eventId = "evt-${System.currentTimeMillis()}",
            eventType = "UserCreated",
            destination = "local-bus"
        )

        // When
        val result = bridge.beforePublish(event, context)

        // Then
        assertIsInstance<EventClientInterceptor.InterceptResult.Continue>(result)
        assertTrue(mockPublisher.publishCalled)
        assertEquals(1, mockPublisher.publishedMessages.size)
    }

    @Test
    fun `test routing key generation from event type`() = runBlocking {
        // Given
        val testCases = listOf(
            "UserCreated" to "events.user.created",
            "OrderPlacedEvent" to "events.order.placed",
            "PaymentProcessedEvent" to "events.payment.processed",
            "EventPublishedEvent" to "events.event.published",
            "A" to "events.a"
        )

        for ((eventType, expectedRoutingKey) in testCases) {
            // Reset mock
            mockPublisher.reset()

            // Given
            val event = TestEvent("test-id", "test data")
            val context = EventClientInterceptor.PublishContext(
                eventId = "evt-${System.currentTimeMillis()}",
                eventType = eventType
            )

            // When
            bridge.beforePublish(event, context)

            // Then
            assertEquals(expectedRoutingKey, mockPublisher.lastRoutingKey)
        }
    }

    @Test
    fun `test metadata headers are included in AMQP publish`() = runBlocking {
        // Given
        val event = TestEvent("user-123", "Test User")
        val eventId = "evt-${System.currentTimeMillis()}"
        val context = EventClientInterceptor.PublishContext(
            eventId = eventId,
            eventType = "UserEvent",
            metadata = mapOf("x-correlation-id" to "corr-123", "x-trace-id" to "trace-456")
        )

        // When
        bridge.beforePublish(event, context)

        // Then
        val publishCall = mockPublisher.publishedMessages.first()
        assertEquals(eventId, publishCall.messageId)
        assertEquals("corr-123", publishCall.correlationId)
    }

    @Test
    fun `test afterPublish logs success`() = runBlocking {
        // Given
        val event = TestEvent("user-123", "Test User")
        val context = EventClientInterceptor.PublishContext(
            eventId = "evt-123",
            eventType = "UserEvent"
        )
        val successResult = PublishResult.Success(
            eventId = "evt-123",
            eventType = "UserEvent",
            destination = "rabbitmq"
        )

        // When
        bridge.afterPublish(event, successResult, context, durationMs = 100)

        // Then - no exception should be thrown
        // (Actual logging verification would require mocking logger)
    }

    @Test
    fun `test onPublishError returns retry for transient errors`() = runBlocking {
        // Given
        val event = TestEvent("user-123", "Test User")
        val context = EventClientInterceptor.PublishContext(
            eventId = "evt-123",
            eventType = "UserEvent"
        )
        val transientException = java.net.ConnectException("Connection refused")

        // When
        val handling = bridge.onPublishError(
            event,
            transientException,
            context,
            attemptNumber = 1
        )

        // Then
        assertIsInstance<EventClientInterceptor.ErrorHandling.Retry>(handling)
    }

    @Test
    fun `test onPublishError returns stop after max attempts`() = runBlocking {
        // Given
        val event = TestEvent("user-123", "Test User")
        val context = EventClientInterceptor.PublishContext(
            eventId = "evt-123",
            eventType = "UserEvent"
        )
        val exception = Exception("Some error")

        // When
        val handling = bridge.onPublishError(
            event,
            exception,
            context,
            attemptNumber = 4  // 4 is > MAX_RETRY_ATTEMPTS (3)
        )

        // Then
        assertIsInstance<EventClientInterceptor.ErrorHandling.Stop>(handling)
    }

    @Test
    fun `test onPublishError uses exponential backoff`() = runBlocking {
        // Given
        val event = TestEvent("user-123", "Test User")
        val context = EventClientInterceptor.PublishContext(
            eventId = "evt-123",
            eventType = "UserEvent"
        )
        val exception = java.net.SocketTimeoutException("Timeout")

        // When - test backoff delays for different attempts
        val attempt1 = bridge.onPublishError(event, exception, context, attemptNumber = 1)
        val attempt2 = bridge.onPublishError(event, exception, context, attemptNumber = 2)

        // Then
        val retry1 = (attempt1 as EventClientInterceptor.ErrorHandling.Retry)
        val retry2 = (attempt2 as EventClientInterceptor.ErrorHandling.Retry)

        // Delay should increase with attempts (exponential backoff)
        assertTrue(retry2.delayMs >= retry1.delayMs)
    }

    @Test
    fun `test beforePublish aborts on serialization failure`() = runBlocking {
        // Given
        val mockFailingSerializer = object : JsonEventSerializer {
            override fun serialize(event: DomainEvent): String =
                throw RuntimeException("Serialization failed")

            override fun <T : DomainEvent> deserialize(json: String, type: Class<T>): T =
                throw NotImplementedError()
        }

        val bridgeWithFailingSerializer = AmqpEventBridge(
            publisher = mockPublisher,
            serializer = mockFailingSerializer,
            routingKeyPrefix = "events"
        )

        val event = TestEvent("user-123", "Test User")
        val context = EventClientInterceptor.PublishContext(
            eventId = "evt-123",
            eventType = "UserEvent"
        )

        // When
        val result = bridgeWithFailingSerializer.beforePublish(event, context)

        // Then
        assertIsInstance<EventClientInterceptor.InterceptResult.Abort>(result)
        val abort = result as EventClientInterceptor.InterceptResult.Abort
        assertTrue(abort.reason.contains("AMQP publish failed"))
    }

    @Test
    fun `test routing key customization via prefix`() = runBlocking {
        // Given
        val customBridge = AmqpEventBridge(
            publisher = mockPublisher,
            serializer = serializer,
            routingKeyPrefix = "prod.domain"
        )

        val event = TestEvent("user-123", "Test User")
        val context = EventClientInterceptor.PublishContext(
            eventId = "evt-123",
            eventType = "UserCreated"
        )

        // When
        customBridge.beforePublish(event, context)

        // Then
        assertEquals("prod.domain.user.created", mockPublisher.lastRoutingKey)
    }

    @Test
    fun `test event without Event suffix in routing key`() = runBlocking {
        // Given
        val event = TestEvent("user-123", "Test User")
        val context = EventClientInterceptor.PublishContext(
            eventId = "evt-123",
            eventType = "OrderPlacedEvent"  // Has "Event" suffix
        )

        // When
        bridge.beforePublish(event, context)

        // Then
        // Should remove "Event" suffix
        assertEquals("events.order.placed", mockPublisher.lastRoutingKey)
    }

    /**
     * Mock implementation of AmqpPublisher for testing.
     */
    private class MockAmqpPublisher : AmqpPublisher(
        AmqpConfiguration.local(),
        object : AmqpConnection(AmqpConfiguration.local()) {
            override fun createChannel() = throw NotImplementedError()
        }
    ) {
        var publishCalled = false
        var lastRoutingKey = ""
        val publishedMessages = mutableListOf<PublishCall>()

        fun reset() {
            publishCalled = false
            lastRoutingKey = ""
            publishedMessages.clear()
        }

        override fun publish(
            routingKey: String,
            message: String,
            contentType: String,
            headers: Map<String, Any>?
        ) {
            publishCalled = true
            lastRoutingKey = routingKey
        }

        override fun publishWithMetadata(
            routingKey: String,
            message: String,
            messageId: String,
            correlationId: String?,
            contentType: String
        ) {
            publishCalled = true
            lastRoutingKey = routingKey
            publishedMessages.add(PublishCall(routingKey, message, messageId, correlationId))
        }

        data class PublishCall(
            val routingKey: String,
            val message: String,
            val messageId: String,
            val correlationId: String?
        )
    }

    /**
     * Test domain event for bridge tests.
     */
    data class TestEvent(
        val userId: String,
        val userName: String
    ) : DomainEvent {
        override fun getMetadata(): EventMetadata =
            EventMetadata(eventType = "UserEvent")

        override fun eventType(): String = "UserEvent"
    }
}
