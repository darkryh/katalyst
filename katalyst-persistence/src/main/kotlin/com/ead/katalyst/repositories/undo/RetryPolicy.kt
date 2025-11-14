package com.ead.katalyst.repositories.undo

import kotlinx.coroutines.delay
import org.slf4j.LoggerFactory
import kotlin.math.min

/**
 * Configurable retry policy with exponential backoff.
 *
 * Supports:
 * - Configurable number of retries
 * - Exponential backoff with jitter to prevent thundering herd
 * - Maximum backoff cap to prevent excessive waits
 * - Custom retry predicates (which exceptions to retry)
 *
 * **Example**:
 * ```kotlin
 * val policy = RetryPolicy(
 *     maxRetries = 3,
 *     initialDelayMs = 100,
 *     maxDelayMs = 5000,
 *     backoffMultiplier = 2.0
 * )
 * policy.execute { riskyOperation() }
 * ```
 */
class RetryPolicy(
    val maxRetries: Int = 3,
    val initialDelayMs: Long = 100,
    val maxDelayMs: Long = 5000,
    val backoffMultiplier: Double = 2.0,
    val retryPredicate: (Exception) -> Boolean = { true }  // Retry on any exception by default
) {
    private val logger = LoggerFactory.getLogger(RetryPolicy::class.java)

    /**
     * Execute an operation with automatic retries on failure.
     *
     * @param operation The operation to execute
     * @return true if operation succeeded, false if all retries exhausted
     */
    suspend fun execute(operation: suspend () -> Boolean): Boolean {
        var currentDelayMs = initialDelayMs

        for (attempt in 0..maxRetries) {
            try {
                val result = operation()
                if (result) {
                    return true
                }

                logger.debug(
                    "Operation returned false, retrying (attempt {} of {})",
                    attempt + 1, maxRetries + 1
                )

                if (attempt < maxRetries) {
                    currentDelayMs = applyBackoffDelay(attempt, currentDelayMs, "returned false")
                    continue
                }
            } catch (e: Exception) {
                // Check if we should retry on this exception
                if (!retryPredicate(e)) {
                    logger.debug("Exception not retryable, giving up: {}", e.message)
                    return false
                }

                if (attempt < maxRetries) {
                    currentDelayMs = applyBackoffDelay(attempt, currentDelayMs, e.message ?: e::class.simpleName ?: "exception")
                    continue
                }

                logger.error("All retry attempts exhausted, operation failed", e)
                return false
            }
        }

        // If we exit the loop, it means operation returned false
        logger.error("Operation returned false after all attempts")
        return false
    }

    private suspend fun applyBackoffDelay(attempt: Int, delayMs: Long, reason: String): Long {
        // Add jitter to prevent thundering herd (random ±20%)
        val jitter = (Math.random() - 0.5) * 0.4 * delayMs
        val delayWithJitter = (delayMs + jitter).toLong().coerceAtLeast(0L)

        logger.warn(
            "Attempt {} failed ({}), retrying in {}ms (attempt {}/{}, backoff={}ms)",
            attempt + 1, reason, delayWithJitter, attempt + 1, maxRetries + 1, delayMs
        )

        delay(delayWithJitter)

        // Calculate next backoff with cap
        return min(
            (delayMs * backoffMultiplier).toLong(),
            maxDelayMs
        )
    }

    /**
     * Retry predicate that retries only on transient errors.
     * Useful for differentiating between temporary failures and permanent errors.
     */
    companion object {
        /**
         * Retry only on transient network or timeout errors.
         */
        fun transientOnly(e: Exception): Boolean {
            return when (e) {
                is java.net.SocketException -> true
                is java.net.SocketTimeoutException -> true
                is java.io.IOException -> true
                is java.util.concurrent.TimeoutException -> true
                else -> e.message?.contains("timeout", ignoreCase = true) == true ||
                       e.message?.contains("connection refused", ignoreCase = true) == true
            }
        }

        /**
         * Create a policy for retry-all (default behavior).
         */
        fun retryAll(): RetryPolicy {
            return RetryPolicy()
        }

        /**
         * Create a policy for retrying only transient errors.
         */
        fun retryTransient(): RetryPolicy {
            return RetryPolicy(retryPredicate = ::transientOnly)
        }

        /**
         * Create an aggressive policy with many retries for critical operations.
         */
        fun aggressive(): RetryPolicy {
            return RetryPolicy(
                maxRetries = 5,
                initialDelayMs = 50,
                maxDelayMs = 10000,
                backoffMultiplier = 2.0
            )
        }

        /**
         * Create a conservative policy with few retries for non-critical operations.
         */
        fun conservative(): RetryPolicy {
            return RetryPolicy(
                maxRetries = 1,
                initialDelayMs = 500,
                maxDelayMs = 1000,
                backoffMultiplier = 1.5
            )
        }
    }
}
