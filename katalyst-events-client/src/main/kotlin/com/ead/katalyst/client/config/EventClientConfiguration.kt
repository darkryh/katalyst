package com.ead.katalyst.client.config

import com.ead.katalyst.client.EventClientInterceptor
import com.ead.katalyst.client.RetryPolicy

/**
 * Configuration settings for EventClient.
 *
 * Holds all configuration options that can be applied during EventClient creation.
 * Useful for external configuration (YAML, environment variables, etc.).
 *
 * **Usage with Builder:**
 *
 * ```kotlin
 * val config = EventClientConfiguration(
 *     retryPolicy = RetryPolicy.exponentialBackoff(maxAttempts = 3),
 *     publishToLocalBus = true,
 *     publishToExternal = true,
 *     correlationId = "trace-123",
 *     interceptors = listOf(
 *         LoggingInterceptor(),
 *         MetricsInterceptor()
 *     ),
 *     batchSize = 100,
 *     batchFlushIntervalMs = 1000
 * )
 *
 * val client = EventClient.builder()
 *     .applyConfiguration(config)
 *     .build()
 * ```
 *
 * **Creating from External Config:**
 *
 * ```kotlin
 * val config = EventClientConfiguration.fromProperties(properties)
 * ```
 *
 * @param retryPolicy Retry strategy for failed publishes
 * @param publishToLocalBus Whether to publish to local EventBus
 * @param publishToExternal Whether to publish to external messaging
 * @param correlationId Optional correlation ID for tracing
 * @param interceptors List of interceptors to attach
 * @param batchSize Maximum events per batch
 * @param batchFlushIntervalMs Interval for flushing batches
 * @param enableMetrics Whether to collect metrics
 * @param enableAuditing Whether to audit all operations
 * @param logLevel Log level for client operations
 * @param metadata Custom metadata for client instance
 */
data class EventClientConfiguration(
    val retryPolicy: RetryPolicy = RetryPolicy.noRetry(),
    val publishToLocalBus: Boolean = true,
    val publishToExternal: Boolean = true,
    val correlationId: String? = null,
    val interceptors: List<EventClientInterceptor> = emptyList(),
    val batchSize: Int = 100,
    val batchFlushIntervalMs: Long = 1000,
    val enableMetrics: Boolean = false,
    val enableAuditing: Boolean = false,
    val logLevel: String = "INFO",
    val metadata: Map<String, String> = emptyMap()
) {
    /**
     * Validate configuration for consistency.
     *
     * @throws IllegalArgumentException if configuration is invalid
     */
    fun validate() {
        require(batchSize > 0) { "batchSize must be > 0" }
        require(batchFlushIntervalMs > 0) { "batchFlushIntervalMs must be > 0" }
        require(publishToLocalBus || publishToExternal) { "At least one of publishToLocalBus or publishToExternal must be true" }

        if (publishToExternal) {
            require(retryPolicy.getMaxAttempts() >= 0) { "retryPolicy maxAttempts must be >= 0" }
        }
    }

    /**
     * Create a copy with updated values.
     *
     * Useful for creating variations of a configuration.
     */
    fun withRetryPolicy(policy: RetryPolicy): EventClientConfiguration =
        copy(retryPolicy = policy)

    fun withPublishToLocalBus(enabled: Boolean): EventClientConfiguration =
        copy(publishToLocalBus = enabled)

    fun withPublishToExternal(enabled: Boolean): EventClientConfiguration =
        copy(publishToExternal = enabled)

    fun withCorrelationId(id: String?): EventClientConfiguration =
        copy(correlationId = id)

    fun withInterceptors(list: List<EventClientInterceptor>): EventClientConfiguration =
        copy(interceptors = list)

    fun addInterceptor(interceptor: EventClientInterceptor): EventClientConfiguration =
        copy(interceptors = interceptors + interceptor)

    fun withBatchSize(size: Int): EventClientConfiguration =
        copy(batchSize = size)

    fun withBatchFlushInterval(intervalMs: Long): EventClientConfiguration =
        copy(batchFlushIntervalMs = intervalMs)

    fun withMetrics(enabled: Boolean): EventClientConfiguration =
        copy(enableMetrics = enabled)

    fun withAuditing(enabled: Boolean): EventClientConfiguration =
        copy(enableAuditing = enabled)

    fun withLogLevel(level: String): EventClientConfiguration =
        copy(logLevel = level)

    fun withMetadata(key: String, value: String): EventClientConfiguration =
        copy(metadata = metadata + (key to value))

    /**
     * Create configuration from property map.
     *
     * Supports properties like:
     * - retry.policy=exponential|linear|none (default: none)
     * - retry.maxAttempts=5
     * - retry.initialDelayMs=100
     * - publish.localBus=true|false (default: true)
     * - publish.external=true|false (default: true)
     * - batch.size=100
     * - batch.flushIntervalMs=1000
     * - metrics.enabled=true|false
     * - auditing.enabled=true|false
     *
     * @param properties Map of property names to values
     * @return EventClientConfiguration
     */
    companion object {
        fun fromProperties(properties: Map<String, String>): EventClientConfiguration {
            val retryPolicy = when (properties["retry.policy"] ?: "none") {
                "exponential" -> RetryPolicy.Companion.exponentialBackoff(
                    initialDelayMs = properties["retry.initialDelayMs"]?.toLongOrNull() ?: 100,
                    maxDelayMs = properties["retry.maxDelayMs"]?.toLongOrNull() ?: 30000,
                    multiplier = properties["retry.multiplier"]?.toDoubleOrNull() ?: 2.0,
                    maxAttempts = properties["retry.maxAttempts"]?.toIntOrNull() ?: 5
                )
                "linear" -> RetryPolicy.Companion.linearBackoff(
                    initialDelayMs = properties["retry.initialDelayMs"]?.toLongOrNull() ?: 500,
                    stepMs = properties["retry.stepMs"]?.toLongOrNull() ?: 500,
                    maxDelayMs = properties["retry.maxDelayMs"]?.toLongOrNull() ?: 5000,
                    maxAttempts = properties["retry.maxAttempts"]?.toIntOrNull() ?: 3
                )
                "immediate" -> RetryPolicy.Companion.immediate(
                    maxAttempts = properties["retry.maxAttempts"]?.toIntOrNull() ?: 3
                )
                else -> RetryPolicy.Companion.noRetry()
            }

            return EventClientConfiguration(
                retryPolicy = retryPolicy,
                publishToLocalBus = properties["publish.localBus"]?.toBoolean() ?: true,
                publishToExternal = properties["publish.external"]?.toBoolean() ?: true,
                correlationId = properties["correlation.id"],
                batchSize = properties["batch.size"]?.toIntOrNull() ?: 100,
                batchFlushIntervalMs = properties["batch.flushIntervalMs"]?.toLongOrNull() ?: 1000,
                enableMetrics = properties["metrics.enabled"]?.toBoolean() ?: false,
                enableAuditing = properties["auditing.enabled"]?.toBoolean() ?: false,
                logLevel = properties["log.level"] ?: "INFO"
            )
        }

        /**
         * Create default configuration.
         */
        fun default(): EventClientConfiguration = EventClientConfiguration()

        /**
         * Create configuration optimized for high throughput.
         */
        fun highThroughput(): EventClientConfiguration = EventClientConfiguration(
            retryPolicy = RetryPolicy.Companion.exponentialBackoff(
                initialDelayMs = 50,
                maxDelayMs = 10000,
                maxAttempts = 3
            ),
            batchSize = 500,
            batchFlushIntervalMs = 100
        )

        /**
         * Create configuration optimized for reliability.
         */
        fun highReliability(): EventClientConfiguration = EventClientConfiguration(
            retryPolicy = RetryPolicy.Companion.exponentialBackoff(
                initialDelayMs = 100,
                maxDelayMs = 60000,
                maxAttempts = 10
            ),
            batchSize = 50,
            batchFlushIntervalMs = 5000,
            enableAuditing = true
        )

        /**
         * Create configuration for local-only publishing (no external messaging).
         */
        fun localOnly(): EventClientConfiguration = EventClientConfiguration(
            publishToLocalBus = true,
            publishToExternal = false
        )

        /**
         * Create configuration for external-only publishing (no local bus).
         */
        fun externalOnly(): EventClientConfiguration = EventClientConfiguration(
            publishToLocalBus = false,
            publishToExternal = true
        )
    }
}