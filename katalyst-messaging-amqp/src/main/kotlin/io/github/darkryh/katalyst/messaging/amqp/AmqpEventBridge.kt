package io.github.darkryh.katalyst.messaging.amqp

import io.github.darkryh.katalyst.client.EventClientInterceptor
import io.github.darkryh.katalyst.client.PublishResult
import io.github.darkryh.katalyst.events.DomainEvent
import io.github.darkryh.katalyst.events.transport.serialization.JsonEventSerializer
import io.github.darkryh.katalyst.messaging.amqp.connection.AmqpConnectionException
import io.github.darkryh.katalyst.messaging.amqp.publisher.AmqpPublishException
import io.github.darkryh.katalyst.messaging.amqp.publisher.KourierPublisher
import org.slf4j.LoggerFactory

/**
 * Bridges EventClient with AMQP/RabbitMQ for event distribution using Kourier.
 *
 * Intercepts event publication and forwards events to RabbitMQ exchange,
 * enabling external services to consume domain events.
 *
 * **Key improvements:**
 * - Uses Kourier (suspend-based, non-blocking) instead of blocking RabbitMQ Java client
 * - All operations are suspend functions (naturally async)
 * - Metadata preservation (messageId, correlationId, timestamps)
 * - Error handling with retryable error detection
 * - Customizable routing key prefix
 * - Exponential backoff retry strategy
 *
 * **Features:**
 * - Automatic routing key generation from event type
 * - Event serialization to JSON
 * - Message deduplication via messageId
 * - Distributed tracing via correlationId
 * - Retry logic with exponential backoff for transient failures
 * - Abort on non-retryable errors (e.g., serialization failures)
 *
 * **Usage:**
 *
 * ```kotlin
 * val amqpBridge = AmqpEventBridge(
 *     publisher = kourierPublisher,
 *     serializer = serializer,
 *     routingKeyPrefix = "events"
 * )
 *
 * val eventClient = EventClient.builder()
 *     .addInterceptor(amqpBridge)
 *     .build()
 *
 * // Events published via client automatically go to RabbitMQ (via Kourier)
 * eventClient.publish(userCreatedEvent)
 * ```
 *
 * @param publisher Kourier publisher for sending messages (suspend-based)
 * @param serializer Event serializer for converting events to JSON
 * @param routingKeyPrefix Prefix for routing keys (e.g., "events.domain.event-type")
 */
class AmqpEventBridge(
    private val publisher: KourierPublisher,
    private val serializer: JsonEventSerializer,
    private val routingKeyPrefix: String = "events"
) : EventClientInterceptor {

    private val logger = LoggerFactory.getLogger(AmqpEventBridge::class.java)

    /**
     * Intercept event publication and forward to AMQP.
     *
     * Serializes the event and publishes it to RabbitMQ with metadata headers.
     *
     * @param event The event being published
     * @param context Publication context
     * @return Continue if publish succeeds, Abort if it fails
     */
    override suspend fun beforePublish(
        event: DomainEvent,
        context: EventClientInterceptor.PublishContext
    ): EventClientInterceptor.InterceptResult {
        return try {
            // Serialize event to EventMessage (contains JSON payload + metadata)
            val eventMessage = serializer.serialize(event)

            // Extract JSON payload as string
            val eventJson = String(eventMessage.payload, Charsets.UTF_8)

            // Generate routing key from event type
            // e.g., "events.user.created" from eventType "UserCreated"
            val routingKey = generateRoutingKey(context.eventType)

            // Build metadata headers combining context metadata and event message headers
            val headers: MutableMap<String, Any> = mutableMapOf(
                "x-event-id" to context.eventId,
                "x-event-type" to context.eventType,
                "x-published-at" to System.currentTimeMillis().toString()
            )

            // Add serialized message headers
            eventMessage.headers.forEach { (k, v) ->
                headers["x-$k"] = v
            }

            context.getMetadata("x-correlation-id")?.let {
                headers["x-correlation-id"] = it
            }

            // Publish to AMQP with metadata
            publisher.publishWithMetadata(
                routingKey = routingKey,
                message = eventJson,
                messageId = context.eventId,
                correlationId = context.getMetadata("x-correlation-id"),
                contentType = eventMessage.contentType
            )

            logger.debug(
                "Event forwarded to AMQP: type={}, routingKey={}, id={}",
                context.eventType,
                routingKey,
                context.eventId
            )

            EventClientInterceptor.InterceptResult.Continue
        } catch (e: Exception) {
            logger.error(
                "Failed to forward event to AMQP: type={}, id={}, error={}",
                context.eventType,
                context.eventId,
                e.message,
                e
            )
            // Abort publication if AMQP fails
            EventClientInterceptor.InterceptResult.Abort(
                reason = "AMQP publish failed: ${e.message}"
            )
        }
    }

    /**
     * Log successful publication to AMQP.
     *
     * @param event The event that was published
     * @param result The publication result
     * @param context Publication context
     * @param durationMs Time spent on publication
     */
    override suspend fun afterPublish(
        event: DomainEvent,
        result: PublishResult,
        context: EventClientInterceptor.PublishContext,
        durationMs: Long
    ) {
        when (result) {
            is PublishResult.Success -> {
                logger.debug(
                    "Event published via AMQP: type={}, destination={}, durationMs={}",
                    context.eventType,
                    result.destination,
                    durationMs
                )
            }
            is PublishResult.Failure -> {
                logger.warn(
                    "Event publication failed: type={}, reason={}, durationMs={}",
                    context.eventType,
                    result.reason,
                    durationMs
                )
            }
            else -> {} // Ignore partial results
        }
    }

    /**
     * Decide on retry behavior for AMQP publish failures.
     *
     * Returns Retry with exponential backoff for transient failures.
     *
     * @param event The event that failed
     * @param exception The exception that occurred
     * @param context Publication context
     * @param attemptNumber Which attempt this was
     * @return Retry with exponential backoff or Stop for max retries
     */
    override suspend fun onPublishError(
        event: DomainEvent,
        exception: Throwable,
        context: EventClientInterceptor.PublishContext,
        attemptNumber: Int
    ): EventClientInterceptor.ErrorHandling {
        logger.warn(
            "AMQP publish error: type={}, attempt={}, error={}",
            context.eventType,
            attemptNumber,
            exception.message
        )

        return if (attemptNumber < MAX_RETRY_ATTEMPTS && isRetryable(exception)) {
            val delayMs = calculateBackoffDelay(attemptNumber)
            logger.info("Scheduling retry for event: type={}, delay={}ms", context.eventType, delayMs)
            EventClientInterceptor.ErrorHandling.Retry(delayMs)
        } else {
            logger.error("Max retries exceeded or non-retryable error for event: type={}", context.eventType)
            EventClientInterceptor.ErrorHandling.Stop
        }
    }

    /**
     * Generate routing key from event type.
     *
     * Converts CamelCase to dot-separated lowercase:
     * "UserCreated" -> "user.created"
     * "OrderPlacedEvent" -> "order.placed"
     *
     * @param eventType Event type string
     * @return Routing key for AMQP
     */
    private fun generateRoutingKey(eventType: String): String {
        val normalized = eventType
            .replace(Regex("Event$"), "") // Remove "Event" suffix if present
            .replace(Regex("([a-z])([A-Z])"), "$1.$2") // Insert dots before capitals
            .lowercase()

        return "$routingKeyPrefix.$normalized"
    }

    /**
     * Determine if an exception is retryable.
     *
     * Transient exceptions like connection timeouts are retryable.
     * Non-transient exceptions like serialization errors are not.
     *
     * @param exception The exception to check
     * @return True if the error should be retried
     */
    private fun isRetryable(exception: Throwable): Boolean {
        return when (exception) {
            is AmqpConnectionException -> true
            is AmqpPublishException -> {
                // Check if it's a transient error
                exception.message?.contains("timeout", ignoreCase = true) ?: false ||
                exception.message?.contains("connection", ignoreCase = true) ?: false
            }
            is java.net.ConnectException -> true
            is java.net.SocketTimeoutException -> true
            is java.io.IOException -> true
            else -> false
        }
    }

    /**
     * Calculate exponential backoff delay for retry attempts.
     *
     * Uses formula: min(baseDelay * 2^(attempt-1), maxDelay)
     *
     * @param attemptNumber The current attempt number (1-based)
     * @return Delay in milliseconds
     */
    private fun calculateBackoffDelay(attemptNumber: Int): Long {
        val baseDelay = 100L // 100ms base delay
        val maxDelay = 32000L // Cap at 32 seconds
        val exponentialDelay = baseDelay * (1 shl (attemptNumber - 1).coerceAtMost(10))
        return exponentialDelay.coerceAtMost(maxDelay)
    }

    companion object {
        private const val MAX_RETRY_ATTEMPTS = 3
    }
}

/**
 * Exception thrown when AMQP event bridge operations fail.
 *
 * @param message Error message
 * @param cause Underlying exception
 */
class AmqpEventBridgeException(
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)
