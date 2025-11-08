# AMQP Refactor Implementation Roadmap

## Executive Summary

This document outlines the detailed implementation roadmap for refactoring katalyst-messaging-amqp from the RabbitMQ Java client to Kourier (pure Kotlin coroutine-native AMQP client).

**Timeline**: 4-6 weeks
**Effort**: ~160 hours (2 engineers, full-time for 4 weeks)
**Risk Level**: Medium (well-mitigated)
**Impact**: High (10-100x better scalability)

---

## Quick Facts

### What's Changing
- **Old**: `com.rabbitmq:amqp-client:5.20.0` (Java, blocking, thread-heavy)
- **New**: `dev.kourier:amqp-client-robust:0.2.8` (Kotlin, async, coroutine-native)

### What Stays the Same
- AMQP protocol compatibility (still 0.9.1)
- RabbitMQ server compatibility
- Core event system integration
- Configuration schema (with extensions)

### Key Differences for Users
```kotlin
// Before
publisher.publish(key, msg)  // ⚠️ Blocking function

// After
publisher.publish(key, msg)  // ✅ Suspend function
consumer.consumeAsFlow(queue)  // ✅ New Flow API
    .map { ... }
    .collect { ... }
```

---

## Detailed Implementation Plan

### PHASE 1: Foundation & Analysis (Week 1)

**Duration**: 3-4 days
**Deliverables**: POC, analysis, decision

#### 1.1 Kourier Deep Dive
```markdown
Tasks:
- [ ] Clone Kourier repo: https://github.com/guimauvedigital/kourier
- [ ] Review architecture documentation
- [ ] Analyze amqp-core module (protocol implementation)
- [ ] Analyze amqp-client module (high-level API)
- [ ] Analyze amqp-client-robust module (auto-recovery)
- [ ] Study BasicProperties and other data classes
- [ ] Review Flow usage patterns
- [ ] Analyze error handling and exception types
- [ ] Check multiplatform configuration

Expected time: 8-12 hours
```

#### 1.2 Proof of Concept
```kotlin
// poc/src/main/kotlin/KourierPOC.kt

suspend fun main() {
    val scope = CoroutineScope(Dispatchers.Default + Job())

    // Test 1: Basic Connection & Channel
    val conn = createRobustAMQPConnection(scope, "amqp://localhost:5672/")
    val channel = conn.openChannel()
    println("✅ Connected")

    // Test 2: Queue Declaration
    channel.queueDeclare("test-queue", durable = true)
    println("✅ Queue declared")

    // Test 3: Message Publishing
    channel.basicPublish(
        exchange = "",
        routingKey = "test-queue",
        body = "Hello, Kourier!".toByteArray(),
        properties = BasicProperties()
    )
    println("✅ Message published")

    // Test 4: Message Consumption
    channel.basicConsume("test-queue", autoAck = true)
        .take(1)
        .collect { delivery ->
            val msg = String(delivery.body)
            println("✅ Received: $msg")
        }

    conn.close()
    scope.cancel()
}
```

**Expected time**: 4-6 hours
**Success Criteria**:
- Successful connection to local RabbitMQ
- Can publish and consume messages
- No exceptions
- Understand error types

#### 1.3 Comparison & Benchmarking
```markdown
Tasks:
- [ ] Benchmark RabbitMQ client with 100 consumers
- [ ] Benchmark Kourier with 100 consumers
- [ ] Measure memory usage difference
- [ ] Measure CPU usage difference
- [ ] Measure latency (p50, p95, p99)
- [ ] Test recovery behavior (kill RabbitMQ instance)
- [ ] Document findings in BENCHMARK_REPORT.md

Key metrics:
- Memory per consumer: RabbitMQ vs Kourier
- Thread count: RabbitMQ vs Kourier
- Message latency: RabbitMQ vs Kourier
- Recovery time: automatic (Kourier) vs manual (RabbitMQ)
```

**Expected time**: 6-8 hours

#### 1.4 Decision & Planning
```markdown
- [ ] Review POC results with team
- [ ] Review benchmark findings
- [ ] Risk assessment workshop
- [ ] Final decision: proceed with refactor
- [ ] Document assumptions and dependencies
- [ ] Create detailed task breakdown
```

**Expected time**: 2-3 hours

---

### PHASE 2: Core Implementation (Week 2)

**Duration**: 5 days
**Deliverables**: KourierConnection, KourierPublisher, KourierConsumer (with tests)

#### 2.1 KourierConnection.kt

```kotlin
// File: katalyst-messaging-amqp/src/main/kotlin/com/ead/katalyst/messaging/amqp/KourierConnection.kt

package com.ead.katalyst.messaging.amqp

import dev.kourier.amqp.client.createRobustAMQPConnection
import dev.kourier.amqp.client.Channel
import dev.kourier.amqp.client.Connection
import kotlinx.coroutines.CoroutineScope
import org.slf4j.LoggerFactory

/**
 * Manages AMQP connection lifecycle using pure Kotlin Kourier client.
 *
 * Key improvements:
 * - All operations are suspend functions (true async)
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
     * Suspend function - must be called from coroutine context.
     */
    suspend fun connect(): Connection {
        if (connection != null) {
            return connection!!
        }

        logger.info("Connecting to AMQP server at {}:{}", config.host, config.port)

        return try {
            val connUrl = "amqp://${config.username}:${config.password}@${config.host}:${config.port}/${config.virtualHost}"

            val conn = createRobustAMQPConnection(scope, connUrl)
            connection = conn
            logger.info("Successfully connected (automatic recovery enabled)")
            conn
        } catch (e: Exception) {
            logger.error("Failed to connect: {}", e.message, e)
            throw AmqpConnectionException("Failed to connect: ${e.message}", e)
        }
    }

    /**
     * Get existing connection.
     * Throws if not connected.
     */
    fun getConnection(): Connection {
        return connection ?: throw IllegalStateException(
            "Connection not established. Call connect() first."
        )
    }

    /**
     * Create a new channel (suspend function).
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
            logger.error("Error closing: {}", e.message, e)
        }
    }
}
```

**Tasks**:
- [ ] Create file
- [ ] Implement methods
- [ ] Add KDoc documentation
- [ ] Write unit tests
- [ ] Test with local RabbitMQ

**Expected time**: 6 hours

#### 2.2 KourierPublisher.kt

```kotlin
// File: katalyst-messaging-amqp/src/main/kotlin/com/ead/katalyst/messaging/amqp/KourierPublisher.kt

package com.ead.katalyst.messaging.amqp

import dev.kourier.amqp.client.BasicProperties
import org.slf4j.LoggerFactory

/**
 * Publishes messages using pure Kotlin suspension.
 * All operations are suspend functions.
 */
class KourierPublisher(
    private val config: AmqpConfiguration,
    private val connection: KourierConnection
) {
    private val logger = LoggerFactory.getLogger(KourierPublisher::class.java)
    private val declaredExchanges = mutableSetOf<String>()

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
            if (!declaredExchanges.contains(config.exchangeName)) {
                channel.exchangeDeclare(
                    config.exchangeName,
                    config.exchangeType,
                    durable = config.durable,
                    autoDelete = config.autoDelete
                )
                declaredExchanges.add(config.exchangeName)
            }

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

            logger.debug("Published to {} with key {}", config.exchangeName, routingKey)
        } catch (e: Exception) {
            logger.error("Publish failed: {}", e.message, e)
            throw AmqpPublishException("Publish failed: ${e.message}", e)
        }
    }

    /**
     * Publish with metadata (suspend function).
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

            logger.debug("Published {} with correlation {}", messageId, correlationId)
        } catch (e: Exception) {
            logger.error("Publish failed: {}", e.message, e)
            throw AmqpPublishException("Publish failed: ${e.message}", e)
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

                channel.queueDeclare(dlqName, durable = config.durable)
                channel.queueBind(dlqName, config.exchangeName, dlqName)
                logger.debug("DLQ declared: {}", dlqName)
            }

            channel.queueDeclare(queueName, durable = config.durable, arguments = arguments)
            channel.queueBind(queueName, config.exchangeName, routingKey)

            logger.debug("Queue declared and bound: {}", queueName)
        } catch (e: Exception) {
            logger.error("Queue declaration failed: {}", e.message, e)
            throw AmqpPublishException("Queue declaration failed: ${e.message}", e)
        }
    }
}
```

**Tasks**:
- [ ] Create file
- [ ] Implement methods
- [ ] Add caching for declared exchanges
- [ ] Write unit tests
- [ ] Test with local RabbitMQ

**Expected time**: 8 hours

#### 2.3 KourierConsumer.kt

```kotlin
// File: katalyst-messaging-amqp/src/main/kotlin/com/ead/katalyst/messaging/amqp/KourierConsumer.kt

package com.ead.katalyst.messaging.amqp

import dev.kourier.amqp.client.DeliveryMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory

/**
 * Consumes messages using Flow (reactive, composable, non-blocking).
 *
 * Key improvements:
 * - Flow-based instead of callbacks
 * - Composable with map, filter, flatMap, etc.
 * - Natural error handling with try/catch
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
     * Subscribe to queue with suspend callback.
     * Callback is now a suspend function - no forced wrapping needed!
     */
    suspend fun subscribe(
        queueName: String,
        callback: suspend (String) -> Unit,
        autoAck: Boolean = false
    ) {
        try {
            val channel = connection.createChannel()

            val job = scope.launch {
                channel.basicConsume(queueName, autoAck)
                    .collect { delivery ->
                        val message = String(delivery.body, Charsets.UTF_8)
                        try {
                            logger.debug("Received from {}: {}", queueName, message.take(100))
                            callback(message)

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
            logger.error("Failed to subscribe: {}", e.message, e)
            throw AmqpConsumerException("Failed to subscribe: ${e.message}", e)
        }
    }

    /**
     * Get messages as Flow for reactive processing.
     *
     * Example:
     * ```kotlin
     * consumer.consumeAsFlow(queueName)
     *     .filter { !it.startsWith("DEBUG") }
     *     .map { parseJson(it) }
     *     .collect { event -> handleEvent(event) }
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
            logger.info("Unsubscribed from: {}", queueName)
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
     * Close all subscriptions.
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

**Tasks**:
- [ ] Create file
- [ ] Implement methods
- [ ] Write unit tests for Flow composition
- [ ] Test with local RabbitMQ
- [ ] Performance test with 1000 concurrent consumers

**Expected time**: 10 hours

#### 2.4 Testing

**Unit Tests**: KourierConnectionTest.kt, KourierPublisherTest.kt, KourierConsumerTest.kt

```kotlin
class KourierConsumerTest {
    @Test
    fun `test consumeAsFlow is composable`() = runTest {
        // Flow operations work naturally
        consumer.consumeAsFlow(queueName)
            .take(5)
            .filter { it.contains("ERROR") }
            .map { it.uppercase() }
            .collect { message ->
                // Assert
            }
    }

    @Test
    fun `test automatic recovery on connection loss`() = runTest {
        // Kill RabbitMQ, publish, recover
        // Kourier should handle reconnection automatically
    }

    @Test
    fun `test 1000 concurrent consumers`() = runTest {
        // Stress test - should use minimal memory
    }
}
```

**Expected time**: 6 hours

---

### PHASE 3: DLQ & Bridge Refactor (Week 2-3)

**Duration**: 3 days
**Deliverables**: KourierDeadLetterQueueHandler, updated AmqpEventBridge

#### 3.1 KourierDeadLetterQueueHandler.kt

```kotlin
suspend fun getQueueStats(dlqName: String): DlqStats {
    val channel = connection.createChannel()
    val declareOk = channel.queueDeclarePassive(dlqName)

    return DlqStats(
        queueName = dlqName,
        messageCount = declareOk.messageCount,
        consumerCount = declareOk.consumerCount
    )
}
```

**Expected time**: 8 hours

#### 3.2 AmqpEventBridge Refactor

Update to work with suspend functions:

```kotlin
override suspend fun beforePublish(
    event: DomainEvent,
    context: EventClientInterceptor.PublishContext
): EventClientInterceptor.InterceptResult {
    return try {
        val eventMessage = serializer.serialize(event)
        val eventJson = String(eventMessage.payload, Charsets.UTF_8)
        val routingKey = generateRoutingKey(context.eventType)

        // Now a suspend function - truly async!
        publisher.publishWithMetadata(
            routingKey = routingKey,
            message = eventJson,
            messageId = context.eventId,
            correlationId = context.getMetadata("x-correlation-id"),
            contentType = eventMessage.contentType
        )

        EventClientInterceptor.InterceptResult.Continue
    } catch (e: Exception) {
        EventClientInterceptor.InterceptResult.Abort(
            reason = "AMQP publish failed: ${e.message}"
        )
    }
}
```

**Expected time**: 4 hours

---

### PHASE 4: Module & Tests (Week 3)

**Duration**: 4 days
**Deliverables**: Updated AmqpModule, integration tests

#### 4.1 Updated AmqpModule.kt

```kotlin
fun amqpModule(
    config: AmqpConfiguration = AmqpConfiguration.local(),
    enableEventBridge: Boolean = false,
    routingKeyPrefix: String = "events"
): Module = module {
    val logger = LoggerFactory.getLogger("AmqpModule")

    config.validate()

    single<AmqpConfiguration> { config }

    // CoroutineScope for connections
    single<CoroutineScope> {
        // Use Koin's scope or create from parent scope
        CoroutineScope(Dispatchers.IO + SupervisorJob())
    }

    // Kourier connection with automatic recovery
    single<KourierConnection> {
        val conn = KourierConnection(get(), get())
        // Connect eagerly on startup
        runBlocking {
            try {
                conn.connect()
            } catch (e: Exception) {
                logger.error("Failed to connect at startup: {}", e.message)
                throw AmqpConnectionException("Failed to initialize connection", e)
            }
        }
        conn
    }

    // Publisher
    single<KourierPublisher> {
        KourierPublisher(get(), get())
    }

    // Consumer factory
    factory<KourierConsumer> {
        KourierConsumer(get(), get(), get())
    }

    // DLQ handler
    single<KourierDeadLetterQueueHandler> {
        KourierDeadLetterQueueHandler(get(), get())
    }

    // Optional: Event bridge
    if (enableEventBridge) {
        single<AmqpEventBridge> {
            AmqpEventBridge(
                publisher = get(),
                serializer = getOrNull<JsonEventSerializer>()
                    ?: throw IllegalStateException(
                        "JsonEventSerializer required for event bridge"
                    ),
                routingKeyPrefix = routingKeyPrefix
            )
        }
    }
}
```

**Expected time**: 4 hours

#### 4.2 Integration Tests

**KourierIntegrationTest.kt**

```kotlin
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class KourierIntegrationTest {

    private val testConfig = AmqpConfiguration.testContainer()
    private val scope = CoroutineScope(Dispatchers.Default + Job())

    @BeforeAll
    fun setup() {
        // Start test RabbitMQ container
    }

    @Test
    fun `test publish and consume message`() = scope.runTest {
        val connection = KourierConnection(testConfig, this)
        connection.connect()

        val publisher = KourierPublisher(testConfig, connection)
        val consumer = KourierConsumer(testConfig, connection, this)

        // Setup queue
        publisher.declareQueue("test-queue", "test.key")

        // Publish
        publisher.publish("test.key", "Hello, Kourier!")

        // Consume
        var received = ""
        consumer.consumeAsFlow("test-queue", autoAck = true)
            .take(1)
            .collect { received = it }

        assertEquals("Hello, Kourier!", received)
    }

    @Test
    fun `test automatic recovery on disconnection`() = scope.runTest {
        // Kill RabbitMQ container
        // Verify Kourier reconnects automatically
        // Verify messages still work after recovery
    }

    @Test
    fun `test 10000 concurrent consumers`() = scope.runTest {
        // Stress test
        // Verify memory usage is reasonable
        // Verify message throughput
    }

    @Test
    fun `test Flow composition with filtering and mapping`() = scope.runTest {
        val results = consumer.consumeAsFlow(queueName)
            .take(10)
            .filter { it.length > 5 }
            .map { it.uppercase() }
            .toList()

        // Assert results
    }

    @AfterAll
    fun cleanup() {
        scope.cancel()
    }
}
```

**Expected time**: 12 hours

---

### PHASE 5: Migration & Rollout (Week 4)

**Duration**: 5 days
**Deliverables**: Migration guide, deprecation warnings, updated docs

#### 5.1 Deprecation Warnings

```kotlin
// AmqpConnection.kt - Old API
@Deprecated(
    "Use KourierConnection instead. " +
    "RabbitMQ Java client will be removed in v2.0. " +
    "See migration guide: https://example.com/amqp-migration",
    replaceWith = ReplaceWith("KourierConnection")
)
class AmqpConnection(...)
```

#### 5.2 Migration Guide

Create `AMQP_MIGRATION_GUIDE.md`

```markdown
# Migration Guide: RabbitMQ Java Client → Kourier

## Summary of Changes

### Connection Management

```kotlin
// Before (Java client - blocking)
val connection = AmqpConnection(config)
// Must call in non-async context

// After (Kourier - suspend)
val connection = KourierConnection(config, scope)
connection.connect()  // Suspend function
```

### Publishing

```kotlin
// Before (blocking)
publisher.publish(key, msg)  // Thread-blocking

// After (async)
publisher.publish(key, msg)  // Suspend function
```

### Consuming

```kotlin
// Before (callback-based)
consumer.subscribe(queue) { msg -> handleMessage(msg) }

// After (Flow-based)
consumer.consumeAsFlow(queue)
    .map { parseMessage(it) }
    .collect { handleMessage(it) }
```

### Error Handling

```kotlin
// Before (callbacks required)
val deliverCallback = DeliverCallback { tag, delivery ->
    try {
        // handle
    } catch (e: Exception) {
        // nested error handling
    }
}

// After (natural try/catch)
try {
    consumer.subscribe(queue) { msg ->
        // handle
    }
} catch (e: Exception) {
    // handle
}
```

## Breaking Changes

1. All methods are now suspend functions
2. Callbacks are now suspend lambdas
3. Flow-based consumption replaces callback-based
4. CoroutineScope is required for connections

## Migration Path

### Step 1: Update Dependencies
```gradle
- implementation("com.rabbitmq:amqp-client:5.20.0")
+ implementation("dev.kourier:amqp-client-robust:0.2.8")
```

### Step 2: Update Connection Code
```kotlin
// Before
val connection = AmqpConnection(config)

// After
val connection = KourierConnection(config, scope)
connection.connect()
```

### Step 3: Update Publishing
```kotlin
// Before
publisher.publish(key, msg)

// After
publisher.publish(key, msg)  // Just add suspend context
```

### Step 4: Update Consuming
```kotlin
// Before
consumer.subscribe(queue) { msg ->
    processMessage(msg)
}

// After
consumer.consumeAsFlow(queue)
    .collect { msg ->
        processMessage(msg)
    }
```

### Step 5: Test
- Run all integration tests
- Stress test with high concurrency
- Verify recovery behavior

### Step 6: Deploy
- Deploy to staging
- Monitor metrics
- Deploy to production
```

**Expected time**: 4 hours

#### 5.3 Documentation Updates

- [ ] Update README.md with Kourier info
- [ ] Update API docs with examples
- [ ] Create troubleshooting guide
- [ ] Document recovery behavior
- [ ] Add performance tuning guide

**Expected time**: 6 hours

---

### PHASE 6: Benchmarking & Cleanup (Week 4-5)

**Duration**: 5 days
**Deliverables**: Performance report, removed old code, final cleanup

#### 6.1 Benchmarking

```
Benchmarks to Run:
- [ ] Message throughput (msgs/sec)
- [ ] Message latency (p50, p95, p99)
- [ ] Memory usage (heap, off-heap)
- [ ] CPU usage (under load)
- [ ] Connection overhead
- [ ] Recovery time (network partition)
- [ ] 10,000 concurrent consumers
- [ ] GC pressure

Comparison:
- RabbitMQ Java client baseline
- Kourier refactored
- Expected improvement: 10-100x for scalability
```

**Expected time**: 8 hours

#### 6.2 Cleanup

- [ ] Remove AmqpConnection.kt (old Java client)
- [ ] Remove AmqpPublisher.kt (old Java client)
- [ ] Remove AmqpConsumer.kt (old Java client)
- [ ] Remove AmqpEventBridge old version (if merged)
- [ ] Remove RabbitMQ Java client dependency
- [ ] Update all imports
- [ ] Run full test suite
- [ ] Final code review

**Expected time**: 4 hours

#### 6.3 Final Documentation

- [ ] Performance report
- [ ] Architecture decision record (ADR)
- [ ] Lesson learned document
- [ ] Upgrade guide for users

**Expected time**: 4 hours

---

## Summary Timeline

```
Week 1: Foundation & Analysis
  - Kourier deep dive (8-12h)
  - POC (4-6h)
  - Benchmarking (6-8h)
  - Decision & planning (2-3h)
  Total: ~25 hours

Week 2: Core Implementation
  - KourierConnection (6h)
  - KourierPublisher (8h)
  - KourierConsumer (10h)
  - Unit tests (6h)
  Total: ~30 hours

Week 3: DLQ & Bridge & Modules
  - KourierDeadLetterQueueHandler (8h)
  - AmqpEventBridge refactor (4h)
  - AmqpModule update (4h)
  - Integration tests (12h)
  Total: ~28 hours

Week 4-5: Migration & Rollout
  - Deprecation warnings (2h)
  - Migration guide (4h)
  - Documentation updates (6h)
  - Benchmarking (8h)
  - Cleanup (4h)
  - Final docs (4h)
  Total: ~28 hours

Grand Total: ~111 hours (~2 weeks, 1 engineer at 40h/week)
```

---

## Success Criteria

### Functional Requirements
- ✅ All existing AMQP functionality works with Kourier
- ✅ Automatic recovery works (tested)
- ✅ All unit tests pass
- ✅ All integration tests pass
- ✅ Event bridge still works with new API

### Non-Functional Requirements
- ✅ Memory usage 10x better with 10,000 consumers
- ✅ No thread pool overhead
- ✅ Message latency p99 <50ms
- ✅ CPU usage <20% at 1000 msg/sec
- ✅ Zero API breaking changes for users (migration path provided)

### Quality Requirements
- ✅ Code coverage >80%
- ✅ All KDoc comments present
- ✅ Zero warnings (lint, deprecation, etc.)
- ✅ Performance benchmarks documented
- ✅ Migration guide complete

---

## Risks & Mitigation

| Risk | Probability | Impact | Mitigation |
|------|------------|--------|-----------|
| Kourier API changes | Low | Medium | Pin version, follow releases |
| Performance regression | Low | High | Extensive benchmarking |
| Hidden bugs in Kourier | Medium | High | Thorough testing, fallback plan |
| User migration issues | Medium | Medium | Clear docs, deprecation warnings |
| Incomplete recovery handling | Low | High | Chaos engineering tests |

---

## Approval & Handoff

**Estimated Effort**: 111 hours (~3 weeks, 1 engineer)
**Risk Level**: Medium-Low
**Expected Benefits**: 10-100x better scalability
**Recommendation**: **PROCEED with refactor**

This refactoring aligns katalyst with Kotlin idioms and enables true high-performance,non-blocking AMQP integration suitable for modern cloud-native architectures.

