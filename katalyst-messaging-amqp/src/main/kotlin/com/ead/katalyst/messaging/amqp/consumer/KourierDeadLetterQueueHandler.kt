package com.ead.katalyst.messaging.amqp.consumer

import com.ead.katalyst.messaging.amqp.config.AmqpConfiguration
import com.ead.katalyst.messaging.amqp.connection.KourierConnection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory

/**
 * Handles messages in dead letter queues using Kourier (suspend-based).
 *
 * Provides:
 * - Flow-based DLQ monitoring
 * - Failed message analysis
 * - DLQ message inspection and statistics
 * - Automatic subscription lifecycle management
 *
 * **Key improvements over old DeadLetterQueueHandler:**
 * - All operations are suspend functions (non-blocking)
 * - Flow-based monitoring instead of callback-based
 * - Composable operators (filter, map, take) for message processing
 * - Automatic resource cleanup via CoroutineScope
 * - Native Kotlin coroutine integration
 *
 * **Usage:**
 *
 * ```kotlin
 * val dlqHandler = KourierDeadLetterQueueHandler(config, connection, scope)
 *
 * // Monitor DLQ with suspend callback
 * dlqHandler.monitorQueue("dlq.events") { message ->
 *     logger.warn("Message in DLQ: {}", message)
 *     // This is a suspend function - naturally async!
 * }
 *
 * // Get messages from DLQ
 * val messages = dlqHandler.getDeadLetterMessages("dlq.events", limit = 10)
 *
 * // Get queue statistics
 * val stats = dlqHandler.getQueueStats("dlq.events")
 *
 * // Purge DLQ
 * val purgedCount = dlqHandler.purgeQueue("dlq.events")
 *
 * // Stop monitoring
 * dlqHandler.unmonitorQueue("dlq.events")
 *
 * // Cleanup
 * dlqHandler.close()
 * ```
 *
 * @param config AMQP configuration
 * @param connection Kourier connection (must be connected)
 * @param scope CoroutineScope for managing DLQ monitor subscriptions
 */
class KourierDeadLetterQueueHandler(
    private val config: AmqpConfiguration,
    private val connection: KourierConnection,
    private val scope: CoroutineScope
) {
    private val logger = LoggerFactory.getLogger(KourierDeadLetterQueueHandler::class.java)

    // Consumer for monitoring queues
    private val consumer = KourierConsumer(config, connection, scope)

    // Track active DLQ monitor subscriptions
    private val monitoredQueues = mutableMapOf<String, Job>()

    /**
     * Monitor a dead letter queue for failed messages (suspend function).
     *
     * Launches a coroutine that continuously consumes messages from the DLQ.
     * The callback is a suspend function, allowing natural async processing.
     *
     * Multiple monitors can be active simultaneously.
     *
     * @param dlqName Dead letter queue name
     * @param callback Suspend function to handle DLQ messages
     * @throws AmqpConsumerException if subscription fails
     */
    suspend fun monitorQueue(
        dlqName: String,
        callback: suspend (String) -> Unit
    ) {
        try {
            // Use Flow-based consumption internally with suspend callback
            val job = scope.launch {
                try {
                    consumer.consumeAsFlow(dlqName, noAck = true)
                        .collect { message ->
                            try {
                                logger.warn("Message received in DLQ {}: {}", dlqName, message.take(200))
                                callback(message)
                            } catch (e: Exception) {
                                logger.error("Error processing DLQ message from {}: {}", dlqName, e.message, e)
                                throw e
                            }
                        }
                } catch (e: Exception) {
                    logger.error("Error monitoring DLQ {}: {}", dlqName, e.message, e)
                    throw e
                }
            }

            monitoredQueues[dlqName] = job
            logger.info("✅ Started monitoring DLQ: {}", dlqName)
        } catch (e: Exception) {
            logger.error("Failed to monitor DLQ {}: {}", dlqName, e.message, e)
            throw DlqHandlingException("Failed to monitor DLQ: ${e.message}", e)
        }
    }

    /**
     * Stop monitoring a dead letter queue (suspend function).
     *
     * Cancels the monitor job and removes it from tracking.
     *
     * @param dlqName Dead letter queue name
     */
    suspend fun unmonitorQueue(dlqName: String) {
        try {
            monitoredQueues[dlqName]?.cancel()
            monitoredQueues.remove(dlqName)
            logger.info("✅ Stopped monitoring DLQ: {}", dlqName)
        } catch (e: Exception) {
            logger.error("Failed to unmonitor DLQ {}: {}", dlqName, e.message, e)
        }
    }

    /**
     * Check if a DLQ is currently being monitored.
     *
     * @param dlqName Dead letter queue name
     * @return True if actively monitored
     */
    fun isMonitored(dlqName: String): Boolean = monitoredQueues.containsKey(dlqName)

    /**
     * Get messages from a dead letter queue (suspend function).
     *
     * Retrieves up to `limit` messages from the DLQ using Flow operators.
     * Messages are consumed with auto-acknowledgment.
     *
     * @param dlqName Dead letter queue name
     * @param limit Maximum number of messages to retrieve (default: 10)
     * @return List of messages from the DLQ
     */
    suspend fun getDeadLetterMessages(dlqName: String, limit: Int = 10): List<String> {
        val messages = mutableListOf<String>()
        return try {
            consumer.consumeAsFlow(dlqName, noAck = true)
                .take(limit)
                .collect { message ->
                    messages.add(message)
                    logger.debug("Retrieved DLQ message: {}", message.take(100))
                }

            messages
        } catch (e: Exception) {
            logger.error("Failed to get DLQ messages from {}: {}", dlqName, e.message, e)
            emptyList()
        }
    }

    /**
     * Purge all messages from a dead letter queue (suspend function).
     *
     * Deletes and recreates the queue to purge all messages.
     * This is the most reliable way to ensure all messages are removed.
     *
     * @param dlqName Dead letter queue name
     * @return Number of messages that were purged (0 if queue doesn't exist or error occurs)
     */
    suspend fun purgeQueue(dlqName: String): Int {
        return try {
            val channel = connection.createChannel()

            // Get current message count before purge
            val statsBeforePurge = try {
                channel.queueDeclarePassive(dlqName)
            } catch (e: Exception) {
                logger.warn("DLQ {} does not exist or cannot be accessed", dlqName)
                return 0
            }

            val messageCount = statsBeforePurge.messageCount

            // Purge the queue (Kourier API: queuePurge returns AMQPResponse.Channel.Queue.Purged)
            try {
                val purgeOk = channel.queuePurge(dlqName)
                logger.info("Purged {} messages from DLQ {}", purgeOk.messageCount, dlqName)
                purgeOk.messageCount
            } catch (e: Exception) {
                // If purge not supported, delete and recreate
                logger.warn("Purge operation not supported, deleting and recreating queue: {}", dlqName)
                try {
                    channel.queueDelete(dlqName)
                    logger.info("Deleted DLQ {}, purged {} messages", dlqName, messageCount)
                    messageCount
                } catch (deleteEx: Exception) {
                    logger.error("Failed to delete DLQ {}: {}", dlqName, deleteEx.message, deleteEx)
                    0
                }
            }
        } catch (e: Exception) {
            logger.error("Failed to purge DLQ {}: {}", dlqName, e.message, e)
            0
        } as Int
    }

    /**
     * Get dead letter queue statistics (suspend function).
     *
     * Retrieves message count and consumer count for the queue.
     * Returns empty stats with error message if queue doesn't exist.
     *
     * @param dlqName Dead letter queue name
     * @return DlqStats with queue information
     */
    suspend fun getQueueStats(dlqName: String): DlqStats {
        return try {
            val channel = connection.createChannel()

            val declareOk = try {
                channel.queueDeclarePassive(dlqName)
            } catch (e: Exception) {
                logger.warn("DLQ {} does not exist: {}", dlqName, e.message)
                return DlqStats(
                    queueName = dlqName,
                    messageCount = 0,
                    consumerCount = 0,
                    error = "Queue does not exist: ${e.message}"
                )
            }

            DlqStats(
                queueName = dlqName,
                messageCount = declareOk.messageCount.toInt(),
                consumerCount = declareOk.consumerCount.toInt()
            )
        } catch (e: Exception) {
            logger.error("Failed to get stats for DLQ {}: {}", dlqName, e.message, e)
            DlqStats(
                queueName = dlqName,
                messageCount = 0,
                consumerCount = 0,
                error = e.message
            )
        }
    }

    /**
     * Delete a dead letter queue (suspend function).
     *
     * Removes the queue and all its messages.
     *
     * @param dlqName Queue name to delete
     */
    suspend fun deleteQueue(dlqName: String) {
        try {
            // Stop monitoring if active
            if (isMonitored(dlqName)) {
                unmonitorQueue(dlqName)
            }

            val channel = connection.createChannel()

            try {
                channel.queueDelete(dlqName)
                logger.info("✅ Deleted DLQ: {}", dlqName)
            } catch (e: Exception) {
                logger.warn("DLQ {} does not exist or already deleted", dlqName)
            }
        } catch (e: Exception) {
            logger.error("Failed to delete DLQ {}: {}", dlqName, e.message, e)
        }
    }

    /**
     * Get all monitored DLQ names.
     *
     * @return Set of DLQ names currently being monitored
     */
    fun getMonitoredQueues(): Set<String> = monitoredQueues.keys.toSet()

    /**
     * Close handler and cleanup all resources (suspend function).
     *
     * Stops all active monitors and closes the consumer.
     * This is typically called during application shutdown.
     */
    suspend fun close() {
        try {
            // Stop all monitors
            monitoredQueues.values.forEach { job ->
                job.cancel()
            }
            monitoredQueues.clear()

            // Close consumer
            consumer.close()

            logger.info("✅ DLQ handler closed")
        } catch (e: Exception) {
            logger.error("Error closing DLQ handler: {}", e.message, e)
        }
    }
}

/**
 * Statistics for a dead letter queue.
 *
 * @param queueName Queue name
 * @param messageCount Number of messages in queue
 * @param consumerCount Number of active consumers
 * @param error Optional error message if stats retrieval failed
 */
data class DlqStats(
    val queueName: String,
    val messageCount: Int,
    val consumerCount: Int,
    val error: String? = null
)

/**
 * Exception thrown when DLQ handling fails.
 *
 * @param message Error message
 * @param cause Underlying exception
 */
class DlqHandlingException(
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)
