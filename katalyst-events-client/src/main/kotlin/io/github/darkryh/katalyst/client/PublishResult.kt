package io.github.darkryh.katalyst.client

/**
 * Result of an event publish operation.
 *
 * Sealed class hierarchy representing different outcomes:
 * - Success: Event published successfully with details
 * - Failure: Event failed to publish with error information
 * - Partial: Batch publish with some successes and some failures
 *
 * **Usage:**
 *
 * ```kotlin
 * val result = eventClient.publish(event)
 * when (result) {
 *     is PublishResult.Success -> {
 *         logger.info("Event published: ${result.eventId}")
 *         println("Destination: ${result.destination}")
 *     }
 *     is PublishResult.Failure -> {
 *         logger.error("Publish failed: ${result.reason}")
 *         // Handle retry, logging, metrics, etc.
 *     }
 *     is PublishResult.Partial -> {
 *         logger.warn("Batch partially published: ${result.successful}/${result.total}")
 *     }
 * }
 * ```
 */
sealed class PublishResult {
    /**
     * Event published successfully.
     *
     * @param eventId The unique event identifier
     * @param eventType The event type string
     * @param destination The destination where event was published
     * @param timestamp When the event was published
     * @param metadata Additional metadata from the publish operation
     */
    data class Success(
        val eventId: String,
        val eventType: String,
        val destination: String,
        val timestamp: Long = System.currentTimeMillis(),
        val metadata: Map<String, String> = emptyMap()
    ) : PublishResult() {
        /**
         * Get a specific metadata value.
         *
         * @param key Metadata key
         * @return Value or null if not present
         */
        fun getMetadata(key: String): String? = metadata[key]
    }

    /**
     * Event publish failed.
     *
     * @param eventId The event being published (if available)
     * @param eventType The event type being published
     * @param reason Human-readable error description
     * @param cause The underlying exception
     * @param retriable Whether this error can be retried
     * @param metadata Additional failure context
     */
    data class Failure(
        val eventId: String? = null,
        val eventType: String,
        val reason: String,
        val cause: Throwable? = null,
        val retriable: Boolean = true,
        val metadata: Map<String, String> = emptyMap()
    ) : PublishResult() {
        /**
         * Get a specific metadata value.
         *
         * @param key Metadata key
         * @return Value or null if not present
         */
        fun getMetadata(key: String): String? = metadata[key]
    }

    /**
     * Batch publish completed with partial success.
     *
     * @param successful Number of successfully published events
     * @param failed Number of failed events
     * @param results Results for each individual event
     * @param timestamp When batch operation completed
     */
    data class Partial(
        val successful: Int,
        val failed: Int,
        val results: List<PublishResult>,
        val timestamp: Long = System.currentTimeMillis()
    ) : PublishResult() {
        /**
         * Check if all events succeeded.
         */
        fun isAllSuccessful(): Boolean = failed == 0

        /**
         * Check if batch is acceptable (has at least one success).
         */
        fun isPartiallySuccessful(): Boolean = successful > 0

        /**
         * Get total events in batch.
         */
        fun total(): Int = successful + failed

        /**
         * Get success rate as percentage.
         */
        fun successRate(): Double = if (total() == 0) 0.0 else (successful.toDouble() / total()) * 100
    }

    /**
     * Check if result indicates success.
     */
    fun isSuccess(): Boolean = this is Success

    /**
     * Check if result indicates failure.
     */
    fun isFailure(): Boolean = this is Failure

    /**
     * Check if result is partial batch.
     */
    fun isPartial(): Boolean = this is Partial

    /**
     * Transform success result or return null.
     */
    fun asSuccess(): Success? = this as? Success

    /**
     * Transform failure result or return null.
     */
    fun asFailure(): Failure? = this as? Failure

    /**
     * Transform partial result or return null.
     */
    fun asPartial(): Partial? = this as? Partial
}
