package com.ead.katalyst.messaging.amqp

import com.ead.katalyst.events.DomainEvent
import com.ead.katalyst.events.EventMetadata
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse

/**
 * Integration tests for AMQP messaging module.
 *
 * Tests:
 * - Configuration validation
 * - Connection lifecycle management
 * - Publisher/consumer functionality
 * - Dead letter queue handling
 * - Message routing and serialization
 * - Event bridge integration
 *
 * **Note:** These tests require a running RabbitMQ instance.
 * Can be run with testcontainers or against a local RabbitMQ service.
 *
 * **Local RabbitMQ Setup:**
 * ```
 * docker run -d --name rabbitmq -p 5672:5672 -p 15672:15672 rabbitmq:3-management
 * ```
 */
class AmqpIntegrationTest {

    private lateinit var config: AmqpConfiguration
    private lateinit var connection: AmqpConnection
    private lateinit var publisher: AmqpPublisher
    private lateinit var consumer: AmqpConsumer
    private lateinit var dlqHandler: DeadLetterQueueHandler

    @BeforeEach
    fun setup() {
        // Use local configuration for testing
        config = AmqpConfiguration.testContainer(
            host = "localhost",
            port = 5672
        )

        // Skip tests if RabbitMQ is not available
        try {
            connection = AmqpConnection(config)
            connection.connect()
        } catch (e: Exception) {
            println("RabbitMQ not available, skipping AMQP integration tests")
            println("Run: docker run -d --name rabbitmq -p 5672:5672 rabbitmq:latest")
            return
        }

        publisher = AmqpPublisher(config, connection)
        consumer = AmqpConsumer(config, connection)
        dlqHandler = DeadLetterQueueHandler(config, connection)
    }

    @Test
    fun `test configuration validation passes for valid config`() {
        // Given
        val validConfig = AmqpConfiguration(
            host = "localhost",
            port = 5672,
            username = "guest",
            password = "guest"
        )

        // When
        val result = runCatching { validConfig.validate() }

        // Then
        assertTrue(result.isSuccess)
    }

    @Test
    fun `test configuration validation fails for invalid port`() {
        // Given
        val invalidConfig = AmqpConfiguration(
            host = "localhost",
            port = 99999,  // Invalid port
            username = "guest",
            password = "guest"
        )

        // When
        val result = runCatching { invalidConfig.validate() }

        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun `test configuration validation fails for invalid exchange type`() {
        // Given
        val invalidConfig = AmqpConfiguration(
            host = "localhost",
            port = 5672,
            username = "guest",
            password = "guest",
            exchangeType = "invalid-type"
        )

        // When
        val result = runCatching { invalidConfig.validate() }

        // Then
        assertTrue(result.isFailure)
    }

    @Test
    fun `test configuration fluent builder methods`() {
        // Given
        val baseConfig = AmqpConfiguration.local()

        // When
        val modifiedConfig = baseConfig
            .withHost("rabbitmq.example.com")
            .withPort(5672)
            .withCredentials("admin", "secret")
            .withDurable(true)
            .withDeadLetterQueue(true)

        // Then
        assertEquals("rabbitmq.example.com", modifiedConfig.host)
        assertEquals(5672, modifiedConfig.port)
        assertEquals("admin", modifiedConfig.username)
        assertEquals("secret", modifiedConfig.password)
        assertTrue(modifiedConfig.durable)
        assertTrue(modifiedConfig.enableDeadLetterQueue)
    }

    @Test
    fun `test connection establishment and closure`() {
        // Given
        val testConnection = AmqpConnection(config)

        // When
        val connected = testConnection.connect()

        // Then
        assertNotNull(connected)
        assertTrue(testConnection.isConnected())

        // When
        testConnection.close()

        // Then
        assertFalse(testConnection.isConnected())
    }

    @Test
    fun `test publisher declares exchange on initialization`() {
        // Given
        val testConfig = config.withExchangeName("test-exchange-${System.currentTimeMillis()}")
        val testConnection = AmqpConnection(testConfig)
        testConnection.connect()

        // When
        val testPublisher = AmqpPublisher(testConfig, testConnection)

        // Then - Publisher should not throw if exchange was declared
        assertTrue(testConnection.isConnected())

        // Cleanup
        testConnection.close()
    }

    @Test
    fun `test queue declaration with routing key`() {
        // Given
        val queueName = "test-queue-${System.currentTimeMillis()}"
        val routingKey = "test.routing.key"

        // When
        val result = runCatching {
            publisher.declareQueue(queueName, routingKey, enableDlq = false)
        }

        // Then
        assertTrue(result.isSuccess)
    }

    @Test
    fun `test queue declaration with DLQ enabled`() {
        // Given
        val queueName = "test-dlq-queue-${System.currentTimeMillis()}"
        val routingKey = "test.dlq.routing.key"

        // When
        val result = runCatching {
            publisher.declareQueue(queueName, routingKey, enableDlq = true)
        }

        // Then
        assertTrue(result.isSuccess)
    }

    @Test
    fun `test message publication`() {
        // Given
        val routingKey = "test.publish.key"
        val message = """{"userId":"test-123","action":"created"}"""

        // When
        val result = runCatching {
            publisher.publish(routingKey, message)
        }

        // Then
        assertTrue(result.isSuccess)
    }

    @Test
    fun `test message publication with metadata`() {
        // Given
        val routingKey = "test.metadata.key"
        val message = """{"orderId":"order-456","amount":99.99}"""
        val messageId = "msg-${System.currentTimeMillis()}"
        val correlationId = "corr-${System.currentTimeMillis()}"

        // When
        val result = runCatching {
            publisher.publishWithMetadata(
                routingKey = routingKey,
                message = message,
                messageId = messageId,
                correlationId = correlationId,
                contentType = "application/json"
            )
        }

        // Then
        assertTrue(result.isSuccess)
    }

    @Test
    fun `test DLQ statistics retrieval`() {
        // Given
        val testConfig = config.withDlqPrefix("test-dlq-")
        val testConnection = AmqpConnection(testConfig)
        testConnection.connect()
        val testDlqHandler = DeadLetterQueueHandler(testConfig, testConnection)
        val dlqName = "test-dlq-stats-${System.currentTimeMillis()}"

        // When
        val stats = testDlqHandler.getQueueStats(dlqName)

        // Then
        assertNotNull(stats)
        assertEquals(dlqName, stats.queueName)
        assertTrue(stats.messageCount >= 0)
        assertTrue(stats.consumerCount >= 0)

        // Cleanup
        testConnection.close()
    }

    @Test
    fun `test queue purge operation`() {
        // Given
        val queueName = "test-purge-queue-${System.currentTimeMillis()}"
        val routingKey = "test.purge.key"
        publisher.declareQueue(queueName, routingKey, enableDlq = false)

        // Publish some messages
        for (i in 1..5) {
            publisher.publish(routingKey, """{"message":$i}""")
        }

        // When
        val purgedCount = dlqHandler.purgeQueue(queueName)

        // Then
        assertTrue(purgedCount >= 0)
    }

    @Test
    fun `test consumer subscription and unsubscription`() {
        // Given
        val queueName = "test-consumer-queue-${System.currentTimeMillis()}"
        val routingKey = "test.consumer.key"
        publisher.declareQueue(queueName, routingKey, enableDlq = false)

        var messageReceived = false

        // When
        consumer.subscribe(queueName, { message ->
            messageReceived = true
        })

        // Then
        assertTrue(consumer.isSubscribed(queueName))

        // When
        consumer.unsubscribe(queueName)

        // Then
        assertFalse(consumer.isSubscribed(queueName))
    }

    @Test
    fun `test consumer get subscribed queues`() {
        // Given
        val queue1 = "test-queue-1-${System.currentTimeMillis()}"
        val queue2 = "test-queue-2-${System.currentTimeMillis()}"
        publisher.declareQueue(queue1, "key1", enableDlq = false)
        publisher.declareQueue(queue2, "key2", enableDlq = false)

        // When
        consumer.subscribe(queue1, {})
        consumer.subscribe(queue2, {})

        // Then
        val subscribedQueues = consumer.getSubscribedQueues()
        assertTrue(subscribedQueues.contains(queue1))
        assertTrue(subscribedQueues.contains(queue2))

        // Cleanup
        consumer.unsubscribe(queue1)
        consumer.unsubscribe(queue2)
    }

    @Test
    fun `test AMQP configuration presets`() {
        // Test local preset
        val localConfig = AmqpConfiguration.local()
        assertEquals("localhost", localConfig.host)
        assertEquals(5672, localConfig.port)

        // Test testContainer preset
        val testConfig = AmqpConfiguration.testContainer("testhost", 5672)
        assertEquals("testhost", testConfig.host)
        assertEquals(5000, testConfig.connectionTimeoutMs)

        // Test production preset
        val prodConfig = AmqpConfiguration.production("prod.example.com", "admin", "secret")
        assertEquals("prod.example.com", prodConfig.host)
        assertEquals(5671, prodConfig.port)
        assertEquals("admin", prodConfig.username)
        assertEquals("secret", prodConfig.password)
        assertTrue(prodConfig.durable)
        assertFalse(prodConfig.autoDelete)
    }

    @Test
    fun `test exception handling in publisher`() {
        // Given
        val invalidConfig = AmqpConfiguration(
            host = "invalid-host-${System.currentTimeMillis()}.local",
            port = 5672,
            connectionTimeoutMs = 1000
        )
        val invalidConnection = AmqpConnection(invalidConfig)

        // When
        val result = runCatching {
            invalidConnection.connect()
        }

        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is AmqpConnectionException)
    }

    @Test
    fun `test consumer close operation`() {
        // Given
        val queueName = "test-close-queue-${System.currentTimeMillis()}"
        val routingKey = "test.close.key"
        publisher.declareQueue(queueName, routingKey, enableDlq = false)
        consumer.subscribe(queueName, {})

        // When
        consumer.close()

        // Then
        assertFalse(consumer.isSubscribed(queueName))
        assertEquals(0, consumer.getSubscribedQueues().size)
    }

    @Test
    fun `test DLQ handler close operation`() {
        // Given
        val testConfig = config
        val testConnection = AmqpConnection(testConfig)
        testConnection.connect()
        val testDlqHandler = DeadLetterQueueHandler(testConfig, testConnection)

        // When
        val result = runCatching {
            testDlqHandler.close()
        }

        // Then
        assertTrue(result.isSuccess)

        // Cleanup
        testConnection.close()
    }

    /**
     * Test domain event for use in AMQP tests.
     */
    data class TestDomainEvent(
        val eventId: String,
        val eventType: String,
        val payload: String
    ) : DomainEvent {
        override fun getMetadata(): EventMetadata =
            EventMetadata(eventType = eventType)

        override fun eventType(): String = eventType
    }

    companion object {
        /**
         * Helper to check if RabbitMQ is available.
         */
        fun isRabbitMqAvailable(): Boolean {
            return try {
                val config = AmqpConfiguration.testContainer("localhost", 5672)
                val connection = AmqpConnection(config)
                connection.connect()
                connection.close()
                true
            } catch (e: Exception) {
                false
            }
        }
    }
}
