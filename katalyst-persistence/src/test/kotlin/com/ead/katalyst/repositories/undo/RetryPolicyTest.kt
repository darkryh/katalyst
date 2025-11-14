package com.ead.katalyst.repositories.undo

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import java.io.IOException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeoutException
import kotlin.test.*
import kotlin.time.Duration.Companion.milliseconds

/**
 * Comprehensive tests for RetryPolicy.
 *
 * Tests cover:
 * - Basic retry logic (success on different attempts)
 * - Exponential backoff calculation
 * - Jitter application
 * - Maximum delay capping
 * - Retry predicate filtering
 * - Predefined policy configurations
 * - Edge cases (zero retries, false returns, exceptions)
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RetryPolicyTest {

    // ========== BASIC RETRY LOGIC ==========

    @Test
    fun `execute should succeed on first attempt`() = runTest {
        // Given
        val policy = RetryPolicy()
        var callCount = 0

        // When
        val result = policy.execute {
            callCount++
            true
        }

        // Then
        assertTrue(result)
        assertEquals(1, callCount)
    }

    @Test
    fun `execute should retry on exception and succeed`() = runTest {
        // Given
        val policy = RetryPolicy(maxRetries = 3)
        var callCount = 0

        // When
        val result = policy.execute {
            callCount++
            if (callCount < 3) {
                throw RuntimeException("Simulated failure")
            }
            true
        }

        // Then
        assertTrue(result)
        assertEquals(3, callCount)
    }

    @Test
    fun `execute should return false when operation returns false`() = runTest {
        // Given
        val policy = RetryPolicy(maxRetries = 2)
        var callCount = 0

        // When
        val result = policy.execute {
            callCount++
            false
        }

        // Then
        assertFalse(result)
        assertEquals(3, callCount) // Initial + 2 retries
    }

    @Test
    fun `execute should exhaust retries and return false`() = runTest {
        // Given
        val policy = RetryPolicy(maxRetries = 2)
        var callCount = 0

        // When
        val result = policy.execute {
            callCount++
            throw RuntimeException("Always fails")
        }

        // Then
        assertFalse(result)
        assertEquals(3, callCount) // Initial + 2 retries
    }

    @Test
    fun `execute with maxRetries 0 should not retry`() = runTest {
        // Given
        val policy = RetryPolicy(maxRetries = 0)
        var callCount = 0

        // When
        val result = policy.execute {
            callCount++
            if (callCount < 2) {
                throw RuntimeException("Fails once")
            }
            true
        }

        // Then
        assertFalse(result) // Should fail without retry
        assertEquals(1, callCount)
    }

    @Test
    fun `execute should succeed after multiple failures`() = runTest {
        // Given
        val policy = RetryPolicy(maxRetries = 5)
        var callCount = 0

        // When
        val result = policy.execute {
            callCount++
            if (callCount < 5) {
                throw RuntimeException("Fail ${callCount} times")
            }
            true
        }

        // Then
        assertTrue(result)
        assertEquals(5, callCount)
    }

    // ========== BACKOFF CALCULATION TESTS ==========

    @Test
    fun `execute should apply exponential backoff`() = runTest {
        // Given
        val policy = RetryPolicy(
            maxRetries = 3,
            initialDelayMs = 100,
            backoffMultiplier = 2.0,
            maxDelayMs = 10000
        )
        val attemptTimes = mutableListOf<Long>()
        var callCount = 0

        // When
        policy.execute {
            attemptTimes.add(currentTime)
            callCount++
            if (callCount < 4) {
                throw RuntimeException("Keep retrying")
            }
            true
        }

        // Then
        assertEquals(4, callCount)
        // Verify delays are approximately: 0, ~100ms, ~200ms, ~400ms
        // (with jitter, so we use range checks)
        assertTrue(attemptTimes[1] >= 80) // ~100ms with jitter
        assertTrue(attemptTimes[2] >= 250) // ~100 + 200ms with jitter
        assertTrue(attemptTimes[3] >= 550) // ~100 + 200 + 400ms with jitter
    }

    @Test
    fun `execute should cap delay at maxDelayMs`() = runTest {
        // Given
        val policy = RetryPolicy(
            maxRetries = 10,
            initialDelayMs = 100,
            backoffMultiplier = 2.0,
            maxDelayMs = 500 // Cap at 500ms
        )
        var callCount = 0

        // When
        val startTime = currentTime
        policy.execute {
            callCount++
            if (callCount < 6) {
                throw RuntimeException("Keep retrying")
            }
            true
        }

        // Then
        val totalTime = currentTime - startTime
        assertTrue(totalTime < 2500) // Should be ~1700ms with jitter
    }

    @Test
    fun `execute should handle very small initialDelayMs`() = runTest {
        // Given
        val policy = RetryPolicy(
            maxRetries = 2,
            initialDelayMs = 1,
            backoffMultiplier = 2.0
        )
        var callCount = 0

        // When
        val result = policy.execute {
            callCount++
            if (callCount < 3) {
                throw RuntimeException("Retry")
            }
            true
        }

        // Then
        assertTrue(result)
        assertEquals(3, callCount)
    }

    @Test
    fun `execute should handle linear backoff when multiplier is 1`() = runTest {
        // Given
        val policy = RetryPolicy(
            maxRetries = 3,
            initialDelayMs = 100,
            backoffMultiplier = 1.0 // No exponential growth
        )
        var callCount = 0

        // When
        val result = policy.execute {
            callCount++
            if (callCount < 4) {
                throw RuntimeException("Retry")
            }
            true
        }

        // Then
        assertTrue(result)
        assertEquals(4, callCount)
    }

    // ========== JITTER TESTS ==========

    @Test
    fun `execute should apply jitter to prevent thundering herd`() = runTest {
        // Given
        val policy = RetryPolicy(
            maxRetries = 1,
            initialDelayMs = 1000
        )

        // When - Run multiple times and collect delay variations
        val delays = mutableListOf<Long>()
        repeat(5) {
            var callCount = 0
            var previousAttemptTime = 0L

            policy.execute {
                val now = currentTime
                if (callCount++ == 0) {
                    previousAttemptTime = now
                    throw RuntimeException("First attempt fails")
                }
                val delay = now - previousAttemptTime
                delays.add(delay)
                true
            }
        }

        // Then - Delays should vary due to jitter (±20%)
        // Expected range: 800ms - 1200ms
        assertTrue(delays.all { it >= 700 })  // Min with jitter
        assertTrue(delays.all { it <= 1300 }) // Max with jitter
        assertTrue(delays.toSet().size > 1, "Jitter should produce different delays")
    }

    // ========== RETRY PREDICATE TESTS ==========

    @Test
    fun `execute should stop retrying when predicate returns false`() = runTest {
        // Given
        val policy = RetryPolicy(
            maxRetries = 5,
            retryPredicate = { e -> e.message != "fatal" }
        )
        var callCount = 0

        // When
        val result = policy.execute {
            callCount++
            throw RuntimeException("fatal")
        }

        // Then
        assertFalse(result)
        assertEquals(1, callCount) // Should not retry on "fatal" error
    }

    @Test
    fun `execute should retry when predicate returns true`() = runTest {
        // Given
        val policy = RetryPolicy(
            maxRetries = 3,
            retryPredicate = { e -> e.message == "transient" }
        )
        var callCount = 0

        // When
        val result = policy.execute {
            callCount++
            if (callCount < 3) {
                throw RuntimeException("transient")
            }
            true
        }

        // Then
        assertTrue(result)
        assertEquals(3, callCount)
    }

    @Test
    fun `execute should use default retry predicate when not specified`() = runTest {
        // Given
        val policy = RetryPolicy(maxRetries = 2)
        var callCount = 0

        // When
        val result = policy.execute {
            callCount++
            if (callCount < 3) {
                throw IllegalStateException("Any exception")
            }
            true
        }

        // Then
        assertTrue(result)
        assertEquals(3, callCount) // Default predicate retries all exceptions
    }

    // ========== TRANSIENT ERROR DETECTION TESTS ==========

    @Test
    fun `transientOnly should return true for SocketException`() {
        // Given
        val exception = SocketException("Connection reset")

        // When
        val isTransient = RetryPolicy.transientOnly(exception)

        // Then
        assertTrue(isTransient)
    }

    @Test
    fun `transientOnly should return true for SocketTimeoutException`() {
        // Given
        val exception = SocketTimeoutException("Read timed out")

        // When
        val isTransient = RetryPolicy.transientOnly(exception)

        // Then
        assertTrue(isTransient)
    }

    @Test
    fun `transientOnly should return true for IOException`() {
        // Given
        val exception = IOException("Network unreachable")

        // When
        val isTransient = RetryPolicy.transientOnly(exception)

        // Then
        assertTrue(isTransient)
    }

    @Test
    fun `transientOnly should return true for TimeoutException`() {
        // Given
        val exception = TimeoutException("Operation timed out")

        // When
        val isTransient = RetryPolicy.transientOnly(exception)

        // Then
        assertTrue(isTransient)
    }

    @Test
    fun `transientOnly should return true for timeout in message`() {
        // Given
        val exception = RuntimeException("Request timeout occurred")

        // When
        val isTransient = RetryPolicy.transientOnly(exception)

        // Then
        assertTrue(isTransient)
    }

    @Test
    fun `transientOnly should return true for connection refused in message`() {
        // Given
        val exception = RuntimeException("Connection refused by server")

        // When
        val isTransient = RetryPolicy.transientOnly(exception)

        // Then
        assertTrue(isTransient)
    }

    @Test
    fun `transientOnly should return false for non-transient exception`() {
        // Given
        val exception = IllegalArgumentException("Invalid argument")

        // When
        val isTransient = RetryPolicy.transientOnly(exception)

        // Then
        assertFalse(isTransient)
    }

    @Test
    fun `transientOnly should handle exception with null message`() {
        // Given
        val exception = RuntimeException(null as String?)

        // When
        val isTransient = RetryPolicy.transientOnly(exception)

        // Then
        assertFalse(isTransient)
    }

    // ========== PREDEFINED POLICY TESTS ==========

    @Test
    fun `retryAll should create policy with default settings`() {
        // When
        val policy = RetryPolicy.retryAll()

        // Then
        assertEquals(3, policy.maxRetries)
        assertEquals(100, policy.initialDelayMs)
        assertEquals(5000, policy.maxDelayMs)
        assertEquals(2.0, policy.backoffMultiplier)
    }

    @Test
    fun `retryTransient should use transientOnly predicate`() = runTest {
        // Given
        val policy = RetryPolicy.retryTransient()
        var callCount = 0

        // When
        val result = policy.execute {
            callCount++
            throw IllegalArgumentException("Not transient") // Should not retry
        }

        // Then
        assertFalse(result)
        assertEquals(1, callCount)
    }

    @Test
    fun `retryTransient should retry on transient errors`() = runTest {
        // Given
        val policy = RetryPolicy.retryTransient()
        var callCount = 0

        // When
        val result = policy.execute {
            callCount++
            if (callCount < 3) {
                throw SocketTimeoutException("Transient error")
            }
            true
        }

        // Then
        assertTrue(result)
        assertEquals(3, callCount)
    }

    @Test
    fun `aggressive should have 5 retries`() {
        // When
        val policy = RetryPolicy.aggressive()

        // Then
        assertEquals(5, policy.maxRetries)
        assertEquals(50, policy.initialDelayMs)
        assertEquals(10000, policy.maxDelayMs)
        assertEquals(2.0, policy.backoffMultiplier)
    }

    @Test
    fun `conservative should have 1 retry`() {
        // When
        val policy = RetryPolicy.conservative()

        // Then
        assertEquals(1, policy.maxRetries)
        assertEquals(500, policy.initialDelayMs)
        assertEquals(1000, policy.maxDelayMs)
        assertEquals(1.5, policy.backoffMultiplier)
    }

    @Test
    fun `aggressive policy should retry many times`() = runTest {
        // Given
        val policy = RetryPolicy.aggressive()
        var callCount = 0

        // When
        val result = policy.execute {
            callCount++
            if (callCount < 5) {
                throw RuntimeException("Retry")
            }
            true
        }

        // Then
        assertTrue(result)
        assertEquals(5, callCount)
    }

    @Test
    fun `conservative policy should retry only once`() = runTest {
        // Given
        val policy = RetryPolicy.conservative()
        var callCount = 0

        // When
        val result = policy.execute {
            callCount++
            throw RuntimeException("Always fails")
        }

        // Then
        assertFalse(result)
        assertEquals(2, callCount) // Initial + 1 retry
    }

    // ========== EDGE CASES ==========

    @Test
    fun `execute should handle operation that alternates success and failure`() = runTest {
        // Given
        val policy = RetryPolicy(maxRetries = 5)
        var callCount = 0

        // When
        val result = policy.execute {
            callCount++
            if (callCount % 2 == 0) {
                true
            } else {
                throw RuntimeException("Odd attempts fail")
            }
        }

        // Then
        assertTrue(result)
        assertEquals(2, callCount) // Fails on 1, succeeds on 2
    }

    @Test
    fun `execute should handle very large maxRetries`() = runTest {
        // Given
        val policy = RetryPolicy(maxRetries = 100, initialDelayMs = 1)
        var callCount = 0

        // When
        val result = policy.execute {
            callCount++
            if (callCount < 10) {
                throw RuntimeException("Fail 9 times")
            }
            true
        }

        // Then
        assertTrue(result)
        assertEquals(10, callCount)
    }

    @Test
    fun `execute should respect maxDelayMs even with large multiplier`() = runTest {
        // Given
        val policy = RetryPolicy(
            maxRetries = 5,
            initialDelayMs = 100,
            backoffMultiplier = 10.0, // Very aggressive multiplier
            maxDelayMs = 300 // But capped at 300ms
        )
        var callCount = 0

        // When
        val startTime = currentTime
        policy.execute {
            callCount++
            if (callCount < 5) {
                throw RuntimeException("Keep retrying")
            }
            true
        }

        // Then - Total time should reflect cap
        val totalTime = currentTime - startTime
        // Expected: ~100 + ~300 + ~300 + ~300 + overhead = ~1000ms with jitter
        assertTrue(totalTime < 1500)
    }

    @Test
    fun `execute should handle exception during delay`() = runTest {
        // Given
        val policy = RetryPolicy(maxRetries = 2, initialDelayMs = 100)
        var callCount = 0

        // When/Then - Should complete without issues despite suspending
        val result = policy.execute {
            callCount++
            if (callCount < 3) {
                throw RuntimeException("Retry")
            }
            true
        }

        assertTrue(result)
    }
}
