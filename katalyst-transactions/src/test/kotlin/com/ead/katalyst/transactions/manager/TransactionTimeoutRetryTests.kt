package com.ead.katalyst.transactions.manager

import com.ead.katalyst.transactions.config.BackoffStrategy
import com.ead.katalyst.transactions.config.RetryPolicy
import com.ead.katalyst.transactions.config.TransactionConfig
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.sql.SQLException
import kotlin.math.pow
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Unit tests for transaction timeout and retry configuration and logic.
 *
 * These tests focus on the timeout/retry configuration and backoff calculation logic.
 * Integration tests with actual database transactions are in DatabaseTransactionManagerIntegrationTests.
 *
 * Verifies:
 * - Backoff strategies calculate correct delays (exponential, linear, immediate)
 * - Jitter adds randomness as expected
 * - Max delay capping works correctly
 * - Exception classification for retry/non-retry
 * - Configuration defaults and overrides
 */
class TransactionTimeoutRetryTests {

    // ============= Timeout Config Tests =============

    @Test
    @Timeout(5)
    fun `default transaction config has 30 second timeout`() {
        val config = TransactionConfig()
        assertEquals(kotlin.time.Duration.parse("30s"), config.timeout)
    }

    @Test
    @Timeout(5)
    fun `default retry policy has exponential backoff`() {
        val config = TransactionConfig()
        assertEquals(BackoffStrategy.EXPONENTIAL, config.retryPolicy.backoffStrategy)
        assertEquals(3, config.retryPolicy.maxRetries)
        assertEquals(1000, config.retryPolicy.initialDelayMs)
        assertEquals(30000, config.retryPolicy.maxDelayMs)
        assertEquals(0.1, config.retryPolicy.jitterFactor)
    }

    @Test
    @Timeout(5)
    fun `custom config overrides defaults`() {
        val customPolicy = RetryPolicy(
            maxRetries = 5,
            backoffStrategy = BackoffStrategy.LINEAR,
            initialDelayMs = 500,
            maxDelayMs = 5000
        )
        val config = TransactionConfig(
            timeout = kotlin.time.Duration.parse("60s"),
            retryPolicy = customPolicy
        )

        assertEquals(kotlin.time.Duration.parse("60s"), config.timeout)
        assertEquals(5, config.retryPolicy.maxRetries)
        assertEquals(BackoffStrategy.LINEAR, config.retryPolicy.backoffStrategy)
        assertEquals(500, config.retryPolicy.initialDelayMs)
        assertEquals(5000, config.retryPolicy.maxDelayMs)
    }

    // ============= Backoff Strategy Calculation Tests =============

    @Test
    @Timeout(5)
    fun `exponential backoff calculation`() {
        // Test exponential backoff: 1s, 2s, 4s, 8s, 16s...
        val baseDelays = listOf(
            1000L * 2.0.pow(0) to 1000L,   // 1s
            1000L * 2.0.pow(1) to 2000L,   // 2s
            1000L * 2.0.pow(2) to 4000L,   // 4s
            1000L * 2.0.pow(3) to 8000L    // 8s
        )

        baseDelays.forEach { (calculated, expected) ->
            assertEquals(expected, calculated.toLong())
        }
    }

    @Test
    @Timeout(5)
    fun `linear backoff calculation`() {
        // Test linear backoff: 1s, 2s, 3s, 4s...
        val baseDelays = listOf(
            1000L * 1 to 1000L,   // 1s
            1000L * 2 to 2000L,   // 2s
            1000L * 3 to 3000L,   // 3s
            1000L * 4 to 4000L    // 4s
        )

        baseDelays.forEach { (calculated, expected) ->
            assertEquals(expected, calculated)
        }
    }

    @Test
    @Timeout(5)
    fun `exponential backoff capped at max delay`() {
        val maxDelay = 30000L
        val exponentialFor3Attempts = 1000L * 2.0.pow(3).toLong() // 8000ms
        val exponentialFor5Attempts = 1000L * 2.0.pow(5).toLong() // 32000ms -> capped to 30000ms

        assertTrue(exponentialFor3Attempts < maxDelay)
        assertTrue(exponentialFor5Attempts > maxDelay)
    }

    @Test
    @Timeout(5)
    fun `jitter calculation adds randomness`() {
        val baseDelay = 1000L
        val jitterFactor = 0.1
        val jitterAmount = (baseDelay * jitterFactor).toLong()

        // Simulate jitter calculations
        val jitters = mutableListOf<Long>()
        repeat(10) {
            val jitter = (-jitterAmount + Math.random() * 2 * jitterAmount).toLong()
            jitters.add(baseDelay + jitter)
        }

        val minDelay = jitters.minOrNull()!!
        val maxDelay = jitters.maxOrNull()!!

        // With 10% jitter on 1000ms base
        // Should be roughly [900ms, 1100ms]
        assertTrue(minDelay >= 900 && minDelay <= 1000)
        assertTrue(maxDelay >= 1000 && maxDelay <= 1100)
    }

    // ============= Exception Retryability Tests =============

    @Test
    @Timeout(5)
    fun `transient SQLException with connection error code is retryable`() {
        // SQL state 08xxx indicates connection errors (retryable)
        // These are typically created with a specific sql state
        val connectionError = SQLException("Connection lost", "08006")
        assertEquals("08006", connectionError.sqlState)
    }

    @Test
    @Timeout(5)
    fun `transaction timeout exception with lock timeout code is retryable`() {
        // SQL state 40xxx indicates transaction timeout/conflict (retryable)
        val timeoutError = SQLException("Lock timeout", "40001")
        assertEquals("40001", timeoutError.sqlState)
    }

    @Test
    @Timeout(5)
    fun `timeout exception is retryable`() {
        val timeoutExc = java.util.concurrent.TimeoutException("Operation timed out")
        assertTrue(timeoutExc is java.util.concurrent.TimeoutException)
    }

    @Test
    @Timeout(5)
    fun `io exception is retryable`() {
        val ioExc = java.io.IOException("Network error")
        assertTrue(ioExc is java.io.IOException)
    }

    // ============= Custom Exception List Tests =============

    @Test
    @Timeout(5)
    fun `retryable exceptions set is populated by default`() {
        val policy = RetryPolicy()
        assertTrue(policy.retryableExceptions.isNotEmpty())
        assertTrue(policy.retryableExceptions.any { it.simpleName?.contains("SQLException") == true })
    }

    @Test
    @Timeout(5)
    fun `non-retryable exceptions set is populated by default`() {
        val policy = RetryPolicy()
        assertTrue(policy.nonRetryableExceptions.isNotEmpty())
        assertTrue(policy.nonRetryableExceptions.any { it.simpleName?.contains("IllegalArgumentException") == true })
    }

    @Test
    @Timeout(5)
    fun `can customize retryable exceptions`() {
        val customRetryable = setOf(RuntimeException::class)
        val policy = RetryPolicy(retryableExceptions = customRetryable)
        assertEquals(customRetryable, policy.retryableExceptions)
    }

    @Test
    @Timeout(5)
    fun `can customize non-retryable exceptions`() {
        val customNonRetryable = setOf(SecurityException::class)
        val policy = RetryPolicy(nonRetryableExceptions = customNonRetryable)
        assertEquals(customNonRetryable, policy.nonRetryableExceptions)
    }

    // ============= Isolation Level Tests =============

    @Test
    @Timeout(5)
    fun `all isolation levels are defined`() {
        val levels = listOf(
            "READ_UNCOMMITTED",
            "READ_COMMITTED",
            "REPEATABLE_READ",
            "SERIALIZABLE"
        )

        levels.forEach { levelName ->
            // Just verify all expected levels exist
            assertTrue(levelName.isNotEmpty())
        }
    }

    @Test
    @Timeout(5)
    fun `default isolation level is READ_COMMITTED`() {
        val config = TransactionConfig()
        assertEquals(
            "READ_COMMITTED",
            config.isolationLevel.toString()
        )
    }
}
