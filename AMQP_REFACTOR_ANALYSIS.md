# AMQP Client Library Refactor Analysis

## Executive Summary

The katalyst-messaging-amqp module currently uses the **RabbitMQ Java client** (`com.rabbitmq:amqp-client:5.20.0`), which is a blocking, Java-centric library incompatible with Kotlin's coroutine model. This analysis proposes a refactor to use **Kourier**, a pure Kotlin AMQP client designed specifically for coroutines and Kotlin Multiplatform support.

---

## Current Architecture (Java Client Based)

### Libraries in Use
- **com.rabbitmq:amqp-client:5.20.0** - Official RabbitMQ Java client
  - Blocking I/O model
  - Thread-based concurrency
  - Manual channel management
  - No native coroutine support

### Current Implementation Structure

**AmqpConnection.kt**
```kotlin
class AmqpConnection(private val config: AmqpConfiguration) {
    private var connection: Connection? = null

    fun connect(): Connection {
        val factory = ConnectionFactory().apply { ... }
        connection = factory.newConnection()
        return connection!!
    }
}
```

**AmqpPublisher.kt**
```kotlin
class AmqpPublisher(config: AmqpConfiguration, connection: AmqpConnection) {
    fun publish(routingKey: String, message: String, ...) {
        val channel = connection.createChannel()
        channel.basicPublish(exchangeName, routingKey, properties, payload)
    }
}
```

**AmqpConsumer.kt**
```kotlin
class AmqpConsumer(config: AmqpConfiguration, connection: AmqpConnection) {
    fun subscribe(queueName: String, callback: (String) -> Unit, autoAck: Boolean = false) {
        val channel = connection.createChannel()
        val deliverCallback = DeliverCallback { consumerTag, delivery ->
            val message = String(delivery.body, Charsets.UTF_8)
            if (scope != null) {
                scope.launch { callback(message) }  // Forced wrapping in coroutine
            } else {
                callback(message)  // Blocking in coroutine context
            }
        }
    }
}
```

### Problems with Current Implementation

1. **Blocking I/O Model**: RabbitMQ Java client uses blocking I/O, forcing workarounds for coroutines
2. **Thread Management**: Creates many threads for connections/channels - inefficient in high-concurrency scenarios
3. **Coroutine Incompatibility**: Must manually wrap blocking code with `scope.launch { }`
4. **Callback Hell**: DeliverCallback, CancelCallback require nested callbacks
5. **Manual Resource Management**: Must explicitly create/close channels for each operation
6. **Not Suspendable**: Cannot use `suspend` functions natively - all operations are blocking

### Code Smell Examples

**Problem 1: Blocking in Coroutine Context**
```kotlin
// Current approach - forces threads into async context
scope.launch {
    // This is a THREAD POOL CALL, not a coroutine
    callback(message)
}
```

**Problem 2: Manual Channel Management**
```kotlin
// Must create/close channels manually - no resource scope
val channel = connection.createChannel()
try {
    val delivery = channel.basicGet(dlqName, true)
    // ...
} finally {
    channel.close()
}
```

**Problem 3: Non-Suspendable Operations**
```kotlin
// Cannot be marked as suspend - it's blocking
fun subscribe(queueName: String, callback: (String) -> Unit) {
    // All operations here are blocking
    val consumerTag = channel.basicConsume(queueName, autoAck, callback, cancelCallback)
}
```

---

## Proposed Architecture (Kourier Kotlin Client)

### Libraries to Use
- **dev.kourier:amqp-client-robust:0.2.8** - Pure Kotlin AMQP client
  - Coroutines-native (suspend functions)
  - Non-blocking I/O via Kotlin channels
  - Automatic recovery and reconnection
  - Kotlin Multiplatform ready (JVM + Native)
  - Memory efficient (uses async I/O, not threads)

### Kourier API Overview

#### Key Types

**Connection Management**
```kotlin
// High-level API
suspend fun createRobustAMQPConnection(
    scope: CoroutineScope,
    url: String = "amqp://guest:guest@localhost:5672/"
): Connection

// Low-level API with DSL
suspend fun createAMQPConnection(
    scope: CoroutineScope,
    host: String = "localhost",
    port: Int = 5672,
    username: String = "guest",
    password: String = "guest"
): Connection
```

**Channel Operations**
```kotlin
interface Channel {
    suspend fun basicPublish(
        exchange: String = "",
        routingKey: String,
        body: ByteArray,
        properties: BasicProperties = BasicProperties()
    )

    suspend fun basicConsume(
        queue: String,
        autoAck: Boolean = false,
        exclusive: Boolean = false,
        noWait: Boolean = false,
        consumerTag: String = ""
    ): Channel  // Returns a Flow-like channel

    suspend fun queueDeclare(
        queue: String,
        passive: Boolean = false,
        durable: Boolean = true,
        exclusive: Boolean = false,
        autoDelete: Boolean = false,
        arguments: Map<String, Any?> = emptyMap()
    ): QueueDeclareOk

    suspend fun exchangeDeclare(
        exchange: String,
        type: String = "direct",  // direct, fanout, topic
        passive: Boolean = false,
        durable: Boolean = true,
        autoDelete: Boolean = false
    )

    suspend fun queueBind(
        queue: String,
        exchange: String,
        routingKey: String = ""
    )
}
```

**Message Consumption**
```kotlin
// Kourier uses Kotlin's Flow for consuming messages
suspend fun Channel.basicConsume(queue: String): Flow<DeliveryMessage> {
    // Returns Flow that emits DeliveryMessage objects
}

data class DeliveryMessage(
    val consumerTag: String,
    val envelope: Envelope,
    val properties: BasicProperties,
    val body: ByteArray
)
```

---

## Proposed Refactored Implementation

### 1. KourierConnection.kt (Replaces AmqpConnection.kt)

```kotlin
package com.ead.katalyst.messaging.amqp

import com.ead.katalyst.messaging.amqp.config.AmqpConfiguration
import dev.kourier.amqp.client.createRobustAMQPConnection
import dev.kourier.amqp.client.Channel
import dev.kourier.amqp.client.Connection
import kotlinx.coroutines.CoroutineScope
import org.slf4j.LoggerFactory

/**
 * Manages AMQP connection lifecycle using pure Kotlin Kourier client.
 *
 * Key improvements over Java RabbitMQ client:
 * - All operations are suspend functions (true async/await)
 * - Automatic recovery and reconnection
 * - Resource management via CoroutineScope
 * - Memory efficient (async I/O, not thread pools)
 */
class KourierConnection(
    private val config: AmqpConfiguration,
    private val scope: CoroutineScope
) {
    private val logger = LoggerFactory.getLogger(KourierConnection::class.java)
    private var connection: Connection? = null

    /**
     * Establish connection with automatic recovery.
     *
     * This is a suspend function - use within a coroutine.
     */
    suspend fun connect(): Connection {
        if (connection != null) {
            return connection!!
        }

        logger.info("Connecting to AMQP server at {}:{}", config.host, config.port)

        return try {
            // Kourier's robust client handles automatic recovery
            val conn = createRobustAMQPConnection(
                scope,
                url = "amqp://${config.username}:${config.password}@${config.host}:${config.port}/${config.virtualHost}"
            )
            connection = conn
            logger.info("Successfully connected to AMQP server (with automatic recovery)")
            conn
        } catch (e: Exception) {
            logger.error("Failed to connect to AMQP server: {}", e.message, e)
            throw AmqpConnectionException("Failed to connect: ${e.message}", e)
        }
    }

    /**
     * Get existing connection or create new one.
     * Non-suspendable version - use for emergency fallback only.
     */
    fun getConnection(): Connection {
        return connection ?: throw IllegalStateException("Connection not established. Call connect() first.")
    }

    /**
     * Create a new channel from the connection.
     * Channels are automatically managed by Kourier.
     */
    suspend fun createChannel(): Channel {
        return getConnection().openChannel()
    }

    /**
     * Check if connected.
     */
    fun isConnected(): Boolean = connection != null

    /**
     * Close connection gracefully.
     */
    suspend fun close() {
        try {
            connection?.let {
                logger.info("Closing AMQP connection")
                it.close()
                connection = null
            }
        } catch (e: Exception) {
            logger.error("Error closing AMQP connection: {}", e.message, e)
        }
    }
}
```

### 2. KourierPublisher.kt (Replaces AmqpPublisher.kt)

```kotlin
package com.ead.katalyst.messaging.amqp

import com.ead.katalyst.messaging.amqp.config.AmqpConfiguration
import dev.kourier.amqp.client.Channel
import dev.kourier.amqp.client.BasicProperties
import org.slf4j.LoggerFactory

/**
 * Publishes messages to RabbitMQ using pure Kotlin suspension.
 *
 * All operations are suspend functions - natural async/await pattern.
 */
class KourierPublisher(
    private val config: AmqpConfiguration,
    private val connection: KourierConnection
) {
    private val logger = LoggerFactory.getLogger(KourierPublisher::class.java)

    init {
        // Note: Exchange declaration moved to lazy init in first publish
    }

    /**
     * Publish a message (suspend function).
     */
    suspend fun publish(
        routingKey: String,
        message: String,
        contentType: String = "application/json",
        headers: Map<String, Any>? = null
    ) {
        try {
            val channel = connection.createChannel()

            // Lazy exchange declaration
            channel.exchangeDeclare(
                config.exchangeName,
                config.exchangeType,
                durable = config.durable,
                autoDelete = config.autoDelete
            )

            val properties = BasicProperties(
                contentType = contentType,
                deliveryMode = 2,  // Persistent
                headers = headers?.mapValues { it.value.toString() }
            )

            channel.basicPublish(
                config.exchangeName,
                routingKey,
                message.toByteArray(Charsets.UTF_8),
                properties
            )

            logger.debug("Published message to {} with routing key {}",
                config.exchangeName, routingKey)
        } catch (e: Exception) {
            logger.error("Failed to publish message: {}", e.message, e)
            throw AmqpPublishException("Failed to publish: ${e.message}", e)
        }
    }

    /**
     * Publish with metadata headers (suspend function).
     */
    suspend fun publishWithMetadata(
        routingKey: String,
        message: String,
        messageId: String,
        correlationId: String? = null,
        contentType: String = "application/json"
    ) {
        try {
            val channel = connection.createChannel()

            val properties = BasicProperties(
                contentType = contentType,
                deliveryMode = 2,
                messageId = messageId,
                correlationId = correlationId,
                timestamp = System.currentTimeMillis()
            )

            channel.basicPublish(
                config.exchangeName,
                routingKey,
                message.toByteArray(Charsets.UTF_8),
                properties
            )

            logger.debug("Published message {} with correlation {}", messageId, correlationId)
        } catch (e: Exception) {
            logger.error("Failed to publish message with metadata: {}", e.message, e)
            throw AmqpPublishException("Failed to publish: ${e.message}", e)
        }
    }

    /**
     * Declare queue and bind to exchange (suspend function).
     */
    suspend fun declareQueue(
        queueName: String,
        routingKey: String,
        enableDlq: Boolean = config.enableDeadLetterQueue
    ) {
        try {
            val channel = connection.createChannel()

            val arguments = mutableMapOf<String, Any?>()
            if (enableDlq && config.enableDeadLetterQueue) {
                val dlqName = config.dlqPrefix + queueName
                arguments["x-dead-letter-exchange"] = config.exchangeName
                arguments["x-dead-letter-routing-key"] = dlqName

                // Declare DLQ
                channel.queueDeclare(dlqName, durable = config.durable)
                channel.queueBind(dlqName, config.exchangeName, dlqName)
                logger.debug("DLQ declared: {}", dlqName)
            }

            channel.queueDeclare(queueName, durable = config.durable, arguments = arguments)
            channel.queueBind(queueName, config.exchangeName, routingKey)

            logger.debug("Queue declared and bound: {}", queueName)
        } catch (e: Exception) {
            logger.error("Failed to declare queue: {}", e.message, e)
            throw AmqpPublishException("Failed to declare queue: ${e.message}", e)
        }
    }
}
```

### 3. KourierConsumer.kt (Replaces AmqpConsumer.kt)

```kotlin
package com.ead.katalyst.messaging.amqp

import com.ead.katalyst.messaging.amqp.config.AmqpConfiguration
import dev.kourier.amqp.client.Channel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory

/**
 * Consumes messages from RabbitMQ using Flow (push-based, lazy, non-blocking).
 *
 * Advantages over callback-based approach:
 * - Flow is composable (map, filter, flatMap, etc.)
 * - Natural error handling with try/catch
 * - Can be collected in a structured way
 * - Cancellation works naturally via scope
 */
class KourierConsumer(
    private val config: AmqpConfiguration,
    private val connection: KourierConnection,
    private val scope: CoroutineScope
) {
    private val logger = LoggerFactory.getLogger(KourierConsumer::class.java)
    private val subscriptions = mutableMapOf<String, Job>()

    /**
     * Subscribe to queue with Flow-based consumption (suspend function).
     */
    suspend fun subscribe(
        queueName: String,
        callback: suspend (String) -> Unit,
        autoAck: Boolean = false
    ) {
        try {
            val channel = connection.createChannel()

            val job = scope.launch {
                // basicConsume returns a Flow<DeliveryMessage>
                channel.basicConsume(queueName, autoAck)
                    .collect { delivery ->
                        val message = String(delivery.body, Charsets.UTF_8)
                        try {
                            logger.debug("Received message from {}: {}",
                                queueName, message.take(100))
                            callback(message)

                            // Manual ack if needed
                            if (!autoAck) {
                                delivery.ack()
                            }
                        } catch (e: Exception) {
                            logger.error("Error processing message: {}", e.message, e)
                            if (!autoAck) {
                                delivery.nack(requeue = true)
                            }
                        }
                    }
            }

            subscriptions[queueName] = job
            logger.info("Subscribed to queue: {}", queueName)
        } catch (e: Exception) {
            logger.error("Failed to subscribe to queue {}: {}", queueName, e.message, e)
            throw AmqpConsumerException("Failed to subscribe: ${e.message}", e)
        }
    }

    /**
     * Get messages as a Flow (for reactive processing).
     *
     * Example usage:
     * ```kotlin
     * consumer.consumeAsFlow(queueName)
     *     .map { it.uppercase() }
     *     .filter { it.contains("ERROR") }
     *     .collect { processedMessage ->
     *         logger.warn("Error message: {}", processedMessage)
     *     }
     * ```
     */
    suspend fun consumeAsFlow(
        queueName: String,
        autoAck: Boolean = false
    ): Flow<String> {
        val channel = connection.createChannel()

        return channel.basicConsume(queueName, autoAck)
            .map { delivery ->
                String(delivery.body, Charsets.UTF_8).also {
                    if (!autoAck) {
                        delivery.ack()
                    }
                }
            }
    }

    /**
     * Unsubscribe from queue (suspend function).
     */
    suspend fun unsubscribe(queueName: String) {
        try {
            subscriptions[queueName]?.cancel()
            subscriptions.remove(queueName)
            logger.info("Unsubscribed from queue: {}", queueName)
        } catch (e: Exception) {
            logger.error("Failed to unsubscribe: {}", e.message, e)
        }
    }

    /**
     * Check if subscribed.
     */
    fun isSubscribed(queueName: String): Boolean = subscriptions.containsKey(queueName)

    /**
     * Get all subscribed queues.
     */
    fun getSubscribedQueues(): Set<String> = subscriptions.keys.toSet()

    /**
     * Close all subscriptions (suspend function).
     */
    suspend fun close() {
        try {
            subscriptions.values.forEach { it.cancel() }
            subscriptions.clear()
            logger.info("All consumers closed")
        } catch (e: Exception) {
            logger.error("Error closing consumers: {}", e.message, e)
        }
    }
}
```

---

## Comparative Analysis

### Table: Java Client vs. Kourier

| Aspect | Java RabbitMQ Client | Kourier |
|--------|----------------------|---------|
| **I/O Model** | Blocking (threads) | Non-blocking (async) |
| **Coroutine Support** | None (must wrap) | Native suspend functions |
| **Memory Usage** | High (thread pools) | Low (single event loop) |
| **Scalability** | Limited (thread per connection) | High (thousands of concurrent) |
| **Complexity** | Callbacks + listeners | Suspend functions + Flow |
| **Recovery** | Manual with retries | Automatic (robust client) |
| **Error Handling** | try/catch + callbacks | Natural try/catch |
| **Resource Management** | Manual close() calls | Automatic via scope |
| **Multiplatform** | JVM only | JVM + Native |
| **Timeouts** | Blocked threads | Natural coroutine cancellation |
| **API Style** | Imperative | Functional (Flow) |

### Code Comparison Example

**Java Client (Current)**
```kotlin
// Blocking approach - must wrap in scope
consumer.subscribe(queueName) { message ->
    scope.launch {  // Forced wrapping!
        processMessage(message)
    }
}

// Must manage channels manually
val channel = connection.createChannel()
try {
    val delivery = channel.basicGet(queue, true)
    if (delivery != null) {
        val message = String(delivery.body)
        handleMessage(message)
    }
} finally {
    channel.close()  // Manual cleanup
}
```

**Kourier (Proposed)**
```kotlin
// Natural suspend function - no wrapping needed
consumer.subscribe(queueName) { message ->
    processMessage(message)  // Already in suspend context
}

// Flow-based approach - composable and clean
consumer.consumeAsFlow(queueName)
    .map { String(it.body) }
    .filter { it.contains("ERROR") }
    .collect { errorMessage ->
        handleError(errorMessage)  // Natural flow
    }
```

---

## Implementation Phases

### Phase 1: Dependencies & Skeleton
- [ ] Add Kourier dependency: `dev.kourier:amqp-client-robust:0.2.8`
- [ ] Create `KourierConnection.kt`
- [ ] Create `KourierPublisher.kt`
- [ ] Create `KourierConsumer.kt`
- [ ] Keep old classes for compatibility during transition

### Phase 2: Core Implementation
- [ ] Implement all suspend methods
- [ ] Add Flow-based consumption
- [ ] Implement automatic recovery
- [ ] Add comprehensive tests

### Phase 3: DLQ Handler Refactor
- [ ] Create `KourierDeadLetterQueueHandler.kt`
- [ ] Use suspend functions for all operations
- [ ] Implement Flow for DLQ monitoring

### Phase 4: EventBridge Refactor
- [ ] Update `AmqpEventBridge.kt` to use Kourier
- [ ] Remove blocking calls
- [ ] Simplify retry logic using coroutines

### Phase 5: DI Module Update
- [ ] Update `AmqpModule.kt` to register Kourier components
- [ ] Provide CoroutineScope to components
- [ ] Add preset configurations for Kourier

### Phase 6: Testing
- [ ] Create integration tests using Kourier
- [ ] Test automatic recovery
- [ ] Test Flow-based consumption
- [ ] Stress testing with high concurrency

### Phase 7: Migration & Cleanup
- [ ] Remove Java RabbitMQ client dependency
- [ ] Delete old Java-based classes
- [ ] Update all documentation
- [ ] Performance benchmarking

---

## Benefits of Migration

### 1. **True Async/Await**
```kotlin
// Current: Blocked threads
channel.basicPublish(...)  // BLOCKS

// Proposed: Suspends, doesn't block
suspend fun publish(...) { ... }
```

### 2. **Better Concurrency**
- Handle 1000s of concurrent connections with fewer threads
- CPU-bound work remains responsive

### 3. **Simpler Error Handling**
```kotlin
// Current: Nested callbacks
try {
    channel.basicConsume(queue, DeliverCallback { ... }, CancelCallback { ... })
} catch (e: Exception) { ... }

// Proposed: Natural try/catch in suspend context
try {
    channel.basicConsume(queue, autoAck).collect { delivery ->
        // Handle delivery
    }
} catch (e: Exception) { ... }
```

### 4. **Composable Operations with Flow**
```kotlin
consumer.consumeAsFlow(queueName)
    .filter { !it.startsWith("HEARTBEAT") }
    .map { parseJson(it) }
    .flatMapMerge { event -> processEvent(event) }
    .catch { e -> logger.error("Error processing event", e) }
    .collect { result -> saveResult(result) }
```

### 5. **Automatic Recovery**
- Kourier's `createRobustAMQPConnection` handles reconnection
- No need for manual retry logic

### 6. **Multiplatform Ready**
- Same code works on JVM and Native
- Future flexibility for target platforms

---

## Migration Checklist

- [ ] Review Kourier API documentation
- [ ] Create wrapper classes (Phase 1)
- [ ] Run integration tests with both clients side-by-side
- [ ] Benchmark performance (throughput, latency, memory)
- [ ] Update example application
- [ ] Stress test with high connection count
- [ ] Document new API for users
- [ ] Migrate all dependents
- [ ] Remove old classes
- [ ] Deploy to production

---

## Risks & Mitigation

| Risk | Mitigation |
|------|-----------|
| **Unknown Kourier bugs** | Run extensive testing; monitor GitHub issues |
| **API changes in Kourier** | Pin to specific version; create abstraction layer |
| **Missing features** | Document all Kourier limitations upfront |
| **Breaking change for users** | Maintain compatibility layer; provide migration guide |
| **Performance regression** | Benchmark both implementations thoroughly |

---

## Next Steps

1. **Download Kourier source** - Analyze actual API implementation
2. **Create small proof-of-concept** - Test basic publish/consume
3. **Performance benchmark** - Compare throughput and latency
4. **Plan rollout strategy** - Decide on dual-client period
5. **Document migration path** - For library users

