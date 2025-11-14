package com.ead.katalyst.transactions.config

import kotlin.test.*
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.toDuration

/**
 * Comprehensive tests for TransactionConfig and related configuration classes.
 *
 * Tests cover:
 * - TransactionConfig creation and defaults
 * - RetryPolicy configuration
 * - BackoffStrategy enum
 * - TransactionIsolationLevel enum
 * - Data class behavior (copy, equality, etc.)
 * - Edge cases and validation
 */
class TransactionConfigTest {

    // ========== TRANSACTION CONFIG TESTS ==========

    @Test
    fun `TransactionConfig should use default values`() {
        // When
        val config = TransactionConfig()

        // Then
        assertEquals(30.toDuration(DurationUnit.SECONDS), config.timeout)
        assertNotNull(config.retryPolicy)
        assertEquals(TransactionIsolationLevel.READ_COMMITTED, config.isolationLevel)
    }

    @Test
    fun `TransactionConfig should accept custom values`() {
        // Given
        val timeout = 60.toDuration(DurationUnit.SECONDS)
        val retryPolicy = RetryPolicy(maxRetries = 5)
        val isolationLevel = TransactionIsolationLevel.SERIALIZABLE

        // When
        val config = TransactionConfig(
            timeout = timeout,
            retryPolicy = retryPolicy,
            isolationLevel = isolationLevel
        )

        // Then
        assertEquals(timeout, config.timeout)
        assertEquals(retryPolicy, config.retryPolicy)
        assertEquals(isolationLevel, config.isolationLevel)
    }

    @Test
    fun `TransactionConfig should support data class copy`() {
        // Given
        val original = TransactionConfig(
            timeout = 30.toDuration(DurationUnit.SECONDS)
        )

        // When
        val copy = original.copy(
            timeout = 60.toDuration(DurationUnit.SECONDS)
        )

        // Then
        assertEquals(60.toDuration(DurationUnit.SECONDS), copy.timeout)
        assertEquals(30.toDuration(DurationUnit.SECONDS), original.timeout)  // Original unchanged
    }

    @Test
    fun `TransactionConfig should support equality`() {
        // Given
        val config1 = TransactionConfig(timeout = 30.toDuration(DurationUnit.SECONDS))
        val config2 = TransactionConfig(timeout = 30.toDuration(DurationUnit.SECONDS))
        val config3 = TransactionConfig(timeout = 60.toDuration(DurationUnit.SECONDS))

        // Then
        assertEquals(config1, config2)
        assertNotEquals(config1, config3)
    }

    // ========== RETRY POLICY TESTS ==========

    @Test
    fun `RetryPolicy should use default values`() {
        // When
        val policy = RetryPolicy()

        // Then
        assertEquals(3, policy.maxRetries)
        assertEquals(BackoffStrategy.EXPONENTIAL, policy.backoffStrategy)
        assertEquals(1000L, policy.initialDelayMs)
        assertEquals(30000L, policy.maxDelayMs)
        assertEquals(0.1, policy.jitterFactor)
        assertTrue(policy.retryableExceptions.isNotEmpty())
        assertTrue(policy.nonRetryableExceptions.isNotEmpty())
    }

    @Test
    fun `RetryPolicy should accept custom values`() {
        // Given
        val maxRetries = 5
        val backoffStrategy = BackoffStrategy.LINEAR
        val initialDelayMs = 2000L
        val maxDelayMs = 60000L
        val jitterFactor = 0.2

        // When
        val policy = RetryPolicy(
            maxRetries = maxRetries,
            backoffStrategy = backoffStrategy,
            initialDelayMs = initialDelayMs,
            maxDelayMs = maxDelayMs,
            jitterFactor = jitterFactor
        )

        // Then
        assertEquals(maxRetries, policy.maxRetries)
        assertEquals(backoffStrategy, policy.backoffStrategy)
        assertEquals(initialDelayMs, policy.initialDelayMs)
        assertEquals(maxDelayMs, policy.maxDelayMs)
        assertEquals(jitterFactor, policy.jitterFactor)
    }

    @Test
    fun `RetryPolicy should allow zero retries`() {
        // When
        val policy = RetryPolicy(maxRetries = 0)

        // Then
        assertEquals(0, policy.maxRetries)
    }

    @Test
    fun `RetryPolicy should allow high retry counts`() {
        // When
        val policy = RetryPolicy(maxRetries = 100)

        // Then
        assertEquals(100, policy.maxRetries)
    }

    @Test
    fun `RetryPolicy should handle custom retryable exceptions`() {
        // Given
        val customExceptions = setOf(
            RuntimeException::class,
            IllegalStateException::class
        )

        // When
        val policy = RetryPolicy(retryableExceptions = customExceptions)

        // Then
        assertEquals(customExceptions, policy.retryableExceptions)
    }

    @Test
    fun `RetryPolicy should handle custom non-retryable exceptions`() {
        // Given
        val customExceptions = setOf(
            NullPointerException::class,
            IndexOutOfBoundsException::class
        )

        // When
        val policy = RetryPolicy(nonRetryableExceptions = customExceptions)

        // Then
        assertEquals(customExceptions, policy.nonRetryableExceptions)
    }

    @Test
    fun `RetryPolicy should support data class copy`() {
        // Given
        val original = RetryPolicy(maxRetries = 3)

        // When
        val copy = original.copy(maxRetries = 5)

        // Then
        assertEquals(5, copy.maxRetries)
        assertEquals(3, original.maxRetries)  // Original unchanged
    }

    @Test
    fun `RetryPolicy should support equality`() {
        // Given
        val policy1 = RetryPolicy(maxRetries = 3, initialDelayMs = 1000L)
        val policy2 = RetryPolicy(maxRetries = 3, initialDelayMs = 1000L)
        val policy3 = RetryPolicy(maxRetries = 5, initialDelayMs = 1000L)

        // Then
        assertEquals(policy1, policy2)
        assertNotEquals(policy1, policy3)
    }

    // ========== BACKOFF STRATEGY TESTS ==========

    @Test
    fun `BackoffStrategy should have EXPONENTIAL value`() {
        // When
        val strategy = BackoffStrategy.EXPONENTIAL

        // Then
        assertEquals("EXPONENTIAL", strategy.name)
    }

    @Test
    fun `BackoffStrategy should have LINEAR value`() {
        // When
        val strategy = BackoffStrategy.LINEAR

        // Then
        assertEquals("LINEAR", strategy.name)
    }

    @Test
    fun `BackoffStrategy should have IMMEDIATE value`() {
        // When
        val strategy = BackoffStrategy.IMMEDIATE

        // Then
        assertEquals("IMMEDIATE", strategy.name)
    }

    @Test
    fun `BackoffStrategy should support exhaustive when`() {
        // Given
        val strategies = BackoffStrategy.entries

        // When/Then - Should compile (exhaustive)
        strategies.forEach { strategy ->
            val result = when (strategy) {
                BackoffStrategy.EXPONENTIAL -> "exponential"
                BackoffStrategy.LINEAR -> "linear"
                BackoffStrategy.IMMEDIATE -> "immediate"
            }
            assertNotNull(result)
        }
    }

    @Test
    fun `BackoffStrategy valueOf should work`() {
        // When
        val strategy = BackoffStrategy.valueOf("EXPONENTIAL")

        // Then
        assertEquals(BackoffStrategy.EXPONENTIAL, strategy)
    }

    // ========== TRANSACTION ISOLATION LEVEL TESTS ==========

    @Test
    fun `TransactionIsolationLevel should have READ_UNCOMMITTED value`() {
        // When
        val level = TransactionIsolationLevel.READ_UNCOMMITTED

        // Then
        assertEquals("READ_UNCOMMITTED", level.name)
    }

    @Test
    fun `TransactionIsolationLevel should have READ_COMMITTED value`() {
        // When
        val level = TransactionIsolationLevel.READ_COMMITTED

        // Then
        assertEquals("READ_COMMITTED", level.name)
    }

    @Test
    fun `TransactionIsolationLevel should have REPEATABLE_READ value`() {
        // When
        val level = TransactionIsolationLevel.REPEATABLE_READ

        // Then
        assertEquals("REPEATABLE_READ", level.name)
    }

    @Test
    fun `TransactionIsolationLevel should have SERIALIZABLE value`() {
        // When
        val level = TransactionIsolationLevel.SERIALIZABLE

        // Then
        assertEquals("SERIALIZABLE", level.name)
    }

    @Test
    fun `TransactionIsolationLevel should support exhaustive when`() {
        // Given
        val levels = TransactionIsolationLevel.entries

        // When/Then - Should compile (exhaustive)
        levels.forEach { level ->
            val result = when (level) {
                TransactionIsolationLevel.READ_UNCOMMITTED -> "read_uncommitted"
                TransactionIsolationLevel.READ_COMMITTED -> "read_committed"
                TransactionIsolationLevel.REPEATABLE_READ -> "repeatable_read"
                TransactionIsolationLevel.SERIALIZABLE -> "serializable"
            }
            assertNotNull(result)
        }
    }

    @Test
    fun `TransactionIsolationLevel valueOf should work`() {
        // When
        val level = TransactionIsolationLevel.valueOf("READ_COMMITTED")

        // Then
        assertEquals(TransactionIsolationLevel.READ_COMMITTED, level)
    }

    // ========== EDGE CASES ==========

    @Test
    fun `RetryPolicy should handle zero initial delay`() {
        // When
        val policy = RetryPolicy(initialDelayMs = 0L)

        // Then
        assertEquals(0L, policy.initialDelayMs)
    }

    @Test
    fun `RetryPolicy should handle very large delays`() {
        // When
        val policy = RetryPolicy(
            initialDelayMs = Long.MAX_VALUE / 2,
            maxDelayMs = Long.MAX_VALUE
        )

        // Then
        assertEquals(Long.MAX_VALUE / 2, policy.initialDelayMs)
        assertEquals(Long.MAX_VALUE, policy.maxDelayMs)
    }

    @Test
    fun `RetryPolicy should handle zero jitter`() {
        // When
        val policy = RetryPolicy(jitterFactor = 0.0)

        // Then
        assertEquals(0.0, policy.jitterFactor)
    }

    @Test
    fun `RetryPolicy should handle high jitter`() {
        // When
        val policy = RetryPolicy(jitterFactor = 1.0)  // 100% jitter

        // Then
        assertEquals(1.0, policy.jitterFactor)
    }

    @Test
    fun `RetryPolicy should handle empty exception sets`() {
        // When
        val policy = RetryPolicy(
            retryableExceptions = emptySet(),
            nonRetryableExceptions = emptySet()
        )

        // Then
        assertTrue(policy.retryableExceptions.isEmpty())
        assertTrue(policy.nonRetryableExceptions.isEmpty())
    }

    @Test
    fun `TransactionConfig should handle very short timeout`() {
        // When
        val config = TransactionConfig(timeout = 1.toDuration(DurationUnit.MILLISECONDS))

        // Then
        assertEquals(1.toDuration(DurationUnit.MILLISECONDS), config.timeout)
    }

    @Test
    fun `TransactionConfig should handle very long timeout`() {
        // When
        val config = TransactionConfig(timeout = 1.toDuration(DurationUnit.HOURS))

        // Then
        assertEquals(1.toDuration(DurationUnit.HOURS), config.timeout)
    }

    // ========== REALISTIC CONFIGURATION SCENARIOS ==========

    @Test
    fun `configuration for quick lightweight transactions`() {
        // When
        val config = TransactionConfig(
            timeout = 5.toDuration(DurationUnit.SECONDS),
            retryPolicy = RetryPolicy(
                maxRetries = 1,
                backoffStrategy = BackoffStrategy.IMMEDIATE,
                initialDelayMs = 100L
            ),
            isolationLevel = TransactionIsolationLevel.READ_COMMITTED
        )

        // Then
        assertEquals(5.toDuration(DurationUnit.SECONDS), config.timeout)
        assertEquals(1, config.retryPolicy.maxRetries)
        assertEquals(BackoffStrategy.IMMEDIATE, config.retryPolicy.backoffStrategy)
    }

    @Test
    fun `configuration for long-running batch transactions`() {
        // When
        val config = TransactionConfig(
            timeout = 5.toDuration(DurationUnit.MINUTES),
            retryPolicy = RetryPolicy(
                maxRetries = 0  // No retries for batch
            ),
            isolationLevel = TransactionIsolationLevel.READ_UNCOMMITTED
        )

        // Then
        assertEquals(5.toDuration(DurationUnit.MINUTES), config.timeout)
        assertEquals(0, config.retryPolicy.maxRetries)
        assertEquals(TransactionIsolationLevel.READ_UNCOMMITTED, config.isolationLevel)
    }

    @Test
    fun `configuration for critical financial transactions`() {
        // When
        val config = TransactionConfig(
            timeout = 60.toDuration(DurationUnit.SECONDS),
            retryPolicy = RetryPolicy(
                maxRetries = 5,
                backoffStrategy = BackoffStrategy.EXPONENTIAL,
                initialDelayMs = 2000L,
                maxDelayMs = 60000L
            ),
            isolationLevel = TransactionIsolationLevel.SERIALIZABLE
        )

        // Then
        assertEquals(60.toDuration(DurationUnit.SECONDS), config.timeout)
        assertEquals(5, config.retryPolicy.maxRetries)
        assertEquals(BackoffStrategy.EXPONENTIAL, config.retryPolicy.backoffStrategy)
        assertEquals(TransactionIsolationLevel.SERIALIZABLE, config.isolationLevel)
    }

    @Test
    fun `configuration for high-contention scenarios`() {
        // When
        val config = TransactionConfig(
            timeout = 30.toDuration(DurationUnit.SECONDS),
            retryPolicy = RetryPolicy(
                maxRetries = 10,
                backoffStrategy = BackoffStrategy.EXPONENTIAL,
                initialDelayMs = 500L,
                maxDelayMs = 30000L,
                jitterFactor = 0.3  // High jitter to spread out retries
            ),
            isolationLevel = TransactionIsolationLevel.REPEATABLE_READ
        )

        // Then
        assertEquals(10, config.retryPolicy.maxRetries)
        assertEquals(0.3, config.retryPolicy.jitterFactor)
        assertEquals(TransactionIsolationLevel.REPEATABLE_READ, config.isolationLevel)
    }
}
