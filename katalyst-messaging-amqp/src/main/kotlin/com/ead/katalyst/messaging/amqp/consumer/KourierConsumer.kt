package com.ead.katalyst.messaging.amqp.consumer

import com.ead.katalyst.messaging.amqp.config.AmqpConfiguration
import com.ead.katalyst.messaging.amqp.connection.KourierConnection
import dev.kourier.amqp.AMQPResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory

/**
 * Consumes messages from RabbitMQ using Flow (reactive, composable, non-blocking).
 *
 * Key improvements over RabbitMQ Java Client:
 * - Flow-based instead of DeliverCallback callbacks
 * - Composable with operators: map, filter, flatMap, catch, etc.
 * - Natural error handling with try/catch (not nested callbacks)
 * - Cancellation works naturally via coroutine scope
 * - Suspend functions for all operations
 *
 * **Flow-Based Usage:**
 *
 * ```kotlin
 * val consumer = KourierConsumer(config, connection, scope)
 *
 * // Simple consumption
 * consumer.consumeAsFlow(queueName, autoAck = true)
 *     .collect { message ->
 *         logger.info("Received: {}", message)
 *     }
 *
 * // With Flow operators
 * consumer.consumeAsFlow(queueName)
 *     .filter { !it.contains("DEBUG") }
 *     .map { parseJson(it) }
 *     .collect { event -> handleEvent(event) }
 * ```
 *
 * **Callback-Based Usage (for compatibility):**
 *
 * ```kotlin
 * consumer.subscribe(queueName) { message ->
 *     logger.info("Received: {}", message)
 * }
 * ```
 *
 * @param config AMQP configuration
 * @param connection Kourier connection (must be connected)
 * @param scope CoroutineScope for managing subscriptions
 */
class KourierConsumer(
    private val config: AmqpConfiguration,
    private val connection: KourierConnection,
    private val scope: CoroutineScope
) {
    private val logger = LoggerFactory.getLogger(KourierConsumer::class.java)

    // Track active subscriptions by queue name
    private val subscriptions = mutableMapOf<String, Job>()

    /**
     * Subscribe to a queue with a suspend callback (for compatibility).
     *
     * Launches a coroutine that continuously consumes messages and calls the callback.
     * Callback is now a suspend function - no forced wrapping needed!
     *
     * This is equivalent to callback-based consumption, but using Flow internally.
     *
     * @param queueName Queue to consume from
     * @param callback Suspend function to handle messages (now suspendable!)
     * @param autoAck Whether to auto-acknowledge (true) or manual ack (false)
     * @throws AmqpConsumerException if subscription fails
     */
    suspend fun subscribe(
        queueName: String,
        callback: suspend (String) -> Unit,
        noAck: Boolean = false
    ) {
        try {
            val job = scope.launch {
                try {
                    // Use Flow internally but hide complexity
                    consumeAsFlow(queueName, noAck)
                        .collect { message ->
                            callback(message)
                        }
                } catch (e: Exception) {
                    logger.error("Error in subscription for queue {}: {}", queueName, e.message, e)
                    throw e
                }
            }

            subscriptions[queueName] = job
            logger.info("✅ Subscribed to queue: {}", queueName)
        } catch (e: Exception) {
            logger.error("Failed to subscribe to queue {}: {}", queueName, e.message, e)
            throw AmqpConsumerException("Failed to subscribe: ${e.message}", e)
        }
    }

    /**
     * Consume messages as a Flow (reactive API).
     *
     * Returns a Flow that emits messages from the queue.
     * Flow can be composed with operators: map, filter, flatMap, etc.
     *
     * Messages are converted to strings for convenience.
     * For manual acknowledgment, use basicConsume() directly.
     *
     * **Example with Flow operators:**
     *
     * ```kotlin
     * consumer.consumeAsFlow(queueName)
     *     .filter { msg -> msg.length > 10 }
     *     .map { msg -> msg.uppercase() }
     *     .catch { e -> logger.error("Error", e) }
     *     .collect { msg -> handleMessage(msg) }
     * ```
     *
     * @param queueName Queue to consume from
     * @param noAck Whether to auto-acknowledge messages (true) or manual (false)
     * @return Flow<String> of messages from the queue
     */
    suspend fun consumeAsFlow(
        queueName: String,
        noAck: Boolean = false
    ): Flow<String> {
        val channel = connection.createChannel()

        // Kourier API: basicConsume returns Flow<AMQPResponse.Channel.Message.Delivery>
        return channel.basicConsume(queueName, noAck = noAck)
            .consumeAsFlow()
            .map { delivery ->
                // Kourier delivery structure: delivery.message.body (contains ByteArray)
                val message = String(delivery.message.body, Charsets.UTF_8)

                try {
                    logger.debug("Received from {}: {}", queueName, message.take(100))
                    // API handles ACK/NACK automatically via noAck parameter in basicConsume()
                    message
                } catch (e: Exception) {
                    logger.error("Error processing message from {}: {}", queueName, e.message, e)
                    throw e
                }
            }
    }

    /**
     * Get messages as a Flow with manual acknowledgment control.
     *
     * Similar to consumeAsFlow but returns DeliveryMessage for fine-grained control.
     * Allows manual ack/nack decisions.
     *
     * **Example:**
     *
     * ```kotlin
     * consumer.consumeAsDeliveryFlow(queueName)
     *     .collect { delivery ->
     *         try {
     *             val message = String(delivery.body)
     *             handleMessage(message)
     *             delivery.ack()  // Manually acknowledge
     *         } catch (e: Exception) {
     *             logger.error("Error, nacking with requeue", e)
     *             delivery.nack(requeue = true)  // Nack and requeue
     *         }
     *     }
     * ```
     *
     * @param queueName Queue to consume from
     * @param noAck Whether to auto-acknowledge
     * @return Flow<DeliveryMessage> with full control
     */
    suspend fun consumeAsDeliveryFlow(
        queueName: String,
        noAck: Boolean = false
    ): Flow<AMQPResponse.Channel.Message.Delivery> {
        val channel = connection.createChannel()
        // Kourier returns Flow<AMQPResponse.Channel.Message.Delivery>
        return channel.basicConsume(queueName, noAck = noAck)
            .consumeAsFlow()
    }

    /**
     * Unsubscribe from a queue (suspend function).
     *
     * Cancels the subscription job and removes it from tracking.
     *
     * @param queueName Queue to unsubscribe from
     */
    suspend fun unsubscribe(queueName: String) {
        try {
            subscriptions[queueName]?.cancel()
            subscriptions.remove(queueName)
            logger.info("✅ Unsubscribed from queue: {}", queueName)
        } catch (e: Exception) {
            logger.error("Failed to unsubscribe from queue {}: {}", queueName, e.message, e)
        }
    }

    /**
     * Check if currently subscribed to a queue.
     *
     * @param queueName Queue name
     * @return True if actively subscribed
     */
    fun isSubscribed(queueName: String): Boolean = subscriptions.containsKey(queueName)

    /**
     * Get all subscribed queue names.
     *
     * @return Set of queue names with active subscriptions
     */
    fun getSubscribedQueues(): Set<String> = subscriptions.keys.toSet()

    /**
     * Close all active subscriptions (suspend function).
     *
     * Cancels all subscription jobs and clears tracking.
     * This is typically called during application shutdown.
     */
    suspend fun close() {
        try {
            subscriptions.values.forEach { job ->
                job.cancel()
            }
            subscriptions.clear()
            logger.info("✅ All consumer subscriptions closed")
        } catch (e: Exception) {
            logger.error("Error closing consumers: {}", e.message, e)
        }
    }
}

/**
 * Exception thrown when consumer operations fail.
 *
 * @param message Error message
 * @param cause Underlying exception
 */
class AmqpConsumerException(
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)
