package com.ead.katalyst.client

/**
 * Defines retry behavior for failed publish operations.
 *
 * Determines:
 * - Maximum number of retry attempts
 * - Delay between retries
 * - Which exceptions are retriable
 * - Whether a specific failure should be retried
 *
 * **Predefined Strategies:**
 *
 * ```kotlin
 * // No retries
 * val noRetry = RetryPolicy.noRetry()
 *
 * // Exponential backoff: 100ms, 200ms, 400ms, etc.
 * val exponential = RetryPolicy.exponentialBackoff(
 *     initialDelayMs = 100,
 *     maxDelayMs = 30000,
 *     multiplier = 2.0,
 *     maxAttempts = 5
 * )
 *
 * // Linear backoff: 500ms, 1000ms, 1500ms, etc.
 * val linear = RetryPolicy.linearBackoff(
 *     initialDelayMs = 500,
 *     maxDelayMs = 5000,
 *     maxAttempts = 3
 * )
 *
 * // Custom logic
 * val custom = RetryPolicy.custom { failure, attemptNumber ->
 *     RetryDecision(
 *         shouldRetry = failure.retriable && attemptNumber < 5,
 *         delayMs = (attemptNumber * 100).toLong()
 *     )
 * }
 * ```
 */
interface RetryPolicy {
    /**
     * Determine if a failed publish should be retried.
     *
     * @param failure The failure result from publish attempt
     * @param attemptNumber The attempt number (1-based)
     * @return Decision with retry flag and optional delay
     */
    fun shouldRetry(failure: PublishResult.Failure, attemptNumber: Int): RetryDecision

    /**
     * Get maximum number of retry attempts.
     *
     * @return Max attempts (0 = no retries)
     */
    fun getMaxAttempts(): Int

    /**
     * Decision about whether and how to retry.
     *
     * @param shouldRetry Whether the operation should be retried
     * @param delayMs Milliseconds to wait before retry (default: 0)
     * @param reason Optional explanation for the decision
     */
    data class RetryDecision(
        val shouldRetry: Boolean,
        val delayMs: Long = 0,
        val reason: String? = null
    )

    companion object {
        /**
         * No retries: fail immediately.
         */
        fun noRetry(): RetryPolicy = NoRetryPolicy()

        /**
         * Exponential backoff with jitter.
         *
         * Delays: initialDelay, initialDelay*multiplier, initialDelay*multiplier², etc.
         * Capped at maxDelay.
         *
         * @param initialDelayMs Starting delay in milliseconds
         * @param maxDelayMs Maximum delay in milliseconds
         * @param multiplier Backoff multiplier (default: 2.0)
         * @param maxAttempts Maximum retry attempts (default: 5)
         */
        fun exponentialBackoff(
            initialDelayMs: Long = 100,
            maxDelayMs: Long = 30000,
            multiplier: Double = 2.0,
            maxAttempts: Int = 5
        ): RetryPolicy = ExponentialBackoffRetryPolicy(
            initialDelayMs,
            maxDelayMs,
            multiplier,
            maxAttempts
        )

        /**
         * Linear backoff.
         *
         * Delays: initialDelay, initialDelay+step, initialDelay+2*step, etc.
         * Capped at maxDelay.
         *
         * @param initialDelayMs Starting delay in milliseconds
         * @param stepMs Increment per attempt
         * @param maxDelayMs Maximum delay in milliseconds
         * @param maxAttempts Maximum retry attempts (default: 3)
         */
        fun linearBackoff(
            initialDelayMs: Long = 500,
            stepMs: Long = 500,
            maxDelayMs: Long = 5000,
            maxAttempts: Int = 3
        ): RetryPolicy = LinearBackoffRetryPolicy(
            initialDelayMs,
            stepMs,
            maxDelayMs,
            maxAttempts
        )

        /**
         * Custom retry logic with lambda.
         *
         * @param resolver Lambda that returns RetryDecision for each failure
         * @param maxAttempts Maximum retry attempts (default: 3)
         */
        fun custom(
            maxAttempts: Int = 3,
            resolver: (failure: PublishResult.Failure, attemptNumber: Int) -> RetryDecision
        ): RetryPolicy = CustomRetryPolicy(maxAttempts, resolver)

        /**
         * Instant retry (no delay) for retriable failures.
         *
         * @param maxAttempts Maximum retry attempts (default: 3)
         */
        fun immediate(maxAttempts: Int = 3): RetryPolicy = ImmediateRetryPolicy(maxAttempts)
    }
}

/**
 * No retry strategy: fail immediately.
 */
private class NoRetryPolicy : RetryPolicy {
    override fun shouldRetry(
        failure: PublishResult.Failure,
        attemptNumber: Int
    ): RetryPolicy.RetryDecision = RetryPolicy.RetryDecision(
        shouldRetry = false,
        reason = "No retry policy configured"
    )

    override fun getMaxAttempts(): Int = 0
}

/**
 * Exponential backoff: delay doubles (or multiplies) after each attempt.
 */
private class ExponentialBackoffRetryPolicy(
    private val initialDelayMs: Long,
    private val maxDelayMs: Long,
    private val multiplier: Double,
    private val maxAttempts: Int
) : RetryPolicy {
    override fun shouldRetry(
        failure: PublishResult.Failure,
        attemptNumber: Int
    ): RetryPolicy.RetryDecision {
        if (attemptNumber > maxAttempts) {
            return RetryPolicy.RetryDecision(
                shouldRetry = false,
                reason = "Max attempts ($maxAttempts) exceeded"
            )
        }

        if (!failure.retriable) {
            return RetryPolicy.RetryDecision(
                shouldRetry = false,
                reason = "Failure is not retriable"
            )
        }

        val delayMs = calculateExponentialDelay(attemptNumber)
        return RetryPolicy.RetryDecision(
            shouldRetry = true,
            delayMs = delayMs,
            reason = "Exponential backoff: ${delayMs}ms delay"
        )
    }

    private fun calculateExponentialDelay(attempt: Int): Long {
        val exponentialDelay = (initialDelayMs * Math.pow(multiplier, (attempt - 1).toDouble())).toLong()
        val withJitter = exponentialDelay + (Math.random() * exponentialDelay * 0.1).toLong()
        return Math.min(withJitter, maxDelayMs)
    }

    override fun getMaxAttempts(): Int = maxAttempts
}

/**
 * Linear backoff: delay increases linearly after each attempt.
 */
private class LinearBackoffRetryPolicy(
    private val initialDelayMs: Long,
    private val stepMs: Long,
    private val maxDelayMs: Long,
    private val maxAttempts: Int
) : RetryPolicy {
    override fun shouldRetry(
        failure: PublishResult.Failure,
        attemptNumber: Int
    ): RetryPolicy.RetryDecision {
        if (attemptNumber > maxAttempts) {
            return RetryPolicy.RetryDecision(
                shouldRetry = false,
                reason = "Max attempts ($maxAttempts) exceeded"
            )
        }

        if (!failure.retriable) {
            return RetryPolicy.RetryDecision(
                shouldRetry = false,
                reason = "Failure is not retriable"
            )
        }

        val delayMs = Math.min(initialDelayMs + (stepMs * (attemptNumber - 1)), maxDelayMs)
        return RetryPolicy.RetryDecision(
            shouldRetry = true,
            delayMs = delayMs,
            reason = "Linear backoff: ${delayMs}ms delay"
        )
    }

    override fun getMaxAttempts(): Int = maxAttempts
}

/**
 * Custom retry policy with lambda resolver.
 */
private class CustomRetryPolicy(
    private val maxAttempts: Int,
    private val resolver: (failure: PublishResult.Failure, attemptNumber: Int) -> RetryPolicy.RetryDecision
) : RetryPolicy {
    override fun shouldRetry(
        failure: PublishResult.Failure,
        attemptNumber: Int
    ): RetryPolicy.RetryDecision = resolver(failure, attemptNumber)

    override fun getMaxAttempts(): Int = maxAttempts
}

/**
 * Immediate retry (no delay) for retriable failures.
 */
private class ImmediateRetryPolicy(private val maxAttempts: Int) : RetryPolicy {
    override fun shouldRetry(
        failure: PublishResult.Failure,
        attemptNumber: Int
    ): RetryPolicy.RetryDecision {
        if (attemptNumber > maxAttempts) {
            return RetryPolicy.RetryDecision(
                shouldRetry = false,
                reason = "Max attempts ($maxAttempts) exceeded"
            )
        }

        if (!failure.retriable) {
            return RetryPolicy.RetryDecision(
                shouldRetry = false,
                reason = "Failure is not retriable"
            )
        }

        return RetryPolicy.RetryDecision(
            shouldRetry = true,
            delayMs = 0,
            reason = "Immediate retry (attempt $attemptNumber/$maxAttempts)"
        )
    }

    override fun getMaxAttempts(): Int = maxAttempts
}
