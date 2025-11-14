package com.ead.katalyst.client

import kotlin.test.*

/**
 * Comprehensive tests for RetryPolicy and related classes.
 *
 * Tests cover:
 * - RetryDecision data class
 * - NoRetry policy
 * - Exponential backoff policy
 * - Linear backoff policy
 * - Custom policy
 * - Immediate retry policy
 * - Factory methods
 * - Retriable vs non-retriable failures
 * - Max attempts enforcement
 */
class RetryPolicyTest {

    // ========== TEST HELPER: CREATE FAILURE ==========

    private fun createRetriableFailure() = PublishResult.Failure(
        eventType = "test.event",
        reason = "Network timeout",
        retriable = true
    )

    private fun createNonRetriableFailure() = PublishResult.Failure(
        eventType = "test.event",
        reason = "Invalid event format",
        retriable = false
    )

    // ========== RETRY DECISION DATA CLASS TESTS ==========

    @Test
    fun `RetryDecision should contain retry flag`() {
        val decision = RetryPolicy.RetryDecision(shouldRetry = true)
        assertTrue(decision.shouldRetry)
    }

    @Test
    fun `RetryDecision should support delay`() {
        val decision = RetryPolicy.RetryDecision(
            shouldRetry = true,
            delayMs = 1000
        )
        assertEquals(1000L, decision.delayMs)
    }

    @Test
    fun `RetryDecision should have zero delay by default`() {
        val decision = RetryPolicy.RetryDecision(shouldRetry = true)
        assertEquals(0L, decision.delayMs)
    }

    @Test
    fun `RetryDecision should support reason`() {
        val decision = RetryPolicy.RetryDecision(
            shouldRetry = false,
            reason = "Max attempts exceeded"
        )
        assertEquals("Max attempts exceeded", decision.reason)
    }

    @Test
    fun `RetryDecision should have null reason by default`() {
        val decision = RetryPolicy.RetryDecision(shouldRetry = true)
        assertNull(decision.reason)
    }

    // ========== NO RETRY POLICY TESTS ==========

    @Test
    fun `noRetry() should never retry`() {
        val policy = RetryPolicy.noRetry()
        val failure = createRetriableFailure()

        val decision = policy.shouldRetry(failure, 1)

        assertFalse(decision.shouldRetry)
        assertNotNull(decision.reason)
    }

    @Test
    fun `noRetry() should have zero max attempts`() {
        val policy = RetryPolicy.noRetry()
        assertEquals(0, policy.getMaxAttempts())
    }

    @Test
    fun `noRetry() should not retry even for retriable failures`() {
        val policy = RetryPolicy.noRetry()
        val failure = createRetriableFailure()

        val decision = policy.shouldRetry(failure, 1)

        assertFalse(decision.shouldRetry)
    }

    // ========== EXPONENTIAL BACKOFF TESTS ==========

    @Test
    fun `exponentialBackoff() should retry retriable failures`() {
        val policy = RetryPolicy.exponentialBackoff(
            initialDelayMs = 100,
            maxAttempts = 3
        )
        val failure = createRetriableFailure()

        val decision = policy.shouldRetry(failure, 1)

        assertTrue(decision.shouldRetry)
        assertTrue(decision.delayMs >= 100)  // At least initial delay (with jitter)
    }

    @Test
    fun `exponentialBackoff() should not retry non-retriable failures`() {
        val policy = RetryPolicy.exponentialBackoff(maxAttempts = 3)
        val failure = createNonRetriableFailure()

        val decision = policy.shouldRetry(failure, 1)

        assertFalse(decision.shouldRetry)
        assertTrue(decision.reason!!.contains("not retriable"))
    }

    @Test
    fun `exponentialBackoff() should enforce max attempts`() {
        val policy = RetryPolicy.exponentialBackoff(maxAttempts = 3)
        val failure = createRetriableFailure()

        val decision = policy.shouldRetry(failure, 4)  // Attempt 4 exceeds max

        assertFalse(decision.shouldRetry)
        assertTrue(decision.reason!!.contains("Max attempts"))
    }

    @Test
    fun `exponentialBackoff() should increase delay exponentially`() {
        val policy = RetryPolicy.exponentialBackoff(
            initialDelayMs = 100,
            multiplier = 2.0,
            maxAttempts = 5
        )
        val failure = createRetriableFailure()

        val decision1 = policy.shouldRetry(failure, 1)
        val decision2 = policy.shouldRetry(failure, 2)
        val decision3 = policy.shouldRetry(failure, 3)

        // Delays should increase (with some jitter tolerance)
        assertTrue(decision1.delayMs < decision2.delayMs)
        assertTrue(decision2.delayMs < decision3.delayMs)
    }

    @Test
    fun `exponentialBackoff() should cap delay at maxDelay`() {
        val policy = RetryPolicy.exponentialBackoff(
            initialDelayMs = 1000,
            maxDelayMs = 5000,
            multiplier = 2.0,
            maxAttempts = 10
        )
        val failure = createRetriableFailure()

        val decision = policy.shouldRetry(failure, 5)

        // Even with exponential growth, delay should not exceed maxDelay
        assertTrue(decision.delayMs <= 5000)
    }

    @Test
    fun `exponentialBackoff() should have correct max attempts`() {
        val policy = RetryPolicy.exponentialBackoff(maxAttempts = 5)
        assertEquals(5, policy.getMaxAttempts())
    }

    @Test
    fun `exponentialBackoff() should use default values`() {
        val policy = RetryPolicy.exponentialBackoff()

        assertEquals(5, policy.getMaxAttempts())
        // Should successfully create with defaults
        assertNotNull(policy)
    }

    // ========== LINEAR BACKOFF TESTS ==========

    @Test
    fun `linearBackoff() should retry retriable failures`() {
        val policy = RetryPolicy.linearBackoff(
            initialDelayMs = 500,
            stepMs = 500,
            maxAttempts = 3
        )
        val failure = createRetriableFailure()

        val decision = policy.shouldRetry(failure, 1)

        assertTrue(decision.shouldRetry)
        assertEquals(500L, decision.delayMs)
    }

    @Test
    fun `linearBackoff() should not retry non-retriable failures`() {
        val policy = RetryPolicy.linearBackoff(maxAttempts = 3)
        val failure = createNonRetriableFailure()

        val decision = policy.shouldRetry(failure, 1)

        assertFalse(decision.shouldRetry)
    }

    @Test
    fun `linearBackoff() should enforce max attempts`() {
        val policy = RetryPolicy.linearBackoff(maxAttempts = 3)
        val failure = createRetriableFailure()

        val decision = policy.shouldRetry(failure, 4)

        assertFalse(decision.shouldRetry)
        assertTrue(decision.reason!!.contains("Max attempts"))
    }

    @Test
    fun `linearBackoff() should increase delay linearly`() {
        val policy = RetryPolicy.linearBackoff(
            initialDelayMs = 500,
            stepMs = 500,
            maxAttempts = 5
        )
        val failure = createRetriableFailure()

        val decision1 = policy.shouldRetry(failure, 1)
        val decision2 = policy.shouldRetry(failure, 2)
        val decision3 = policy.shouldRetry(failure, 3)

        assertEquals(500L, decision1.delayMs)  // 500 + 0*500
        assertEquals(1000L, decision2.delayMs)  // 500 + 1*500
        assertEquals(1500L, decision3.delayMs)  // 500 + 2*500
    }

    @Test
    fun `linearBackoff() should cap delay at maxDelay`() {
        val policy = RetryPolicy.linearBackoff(
            initialDelayMs = 1000,
            stepMs = 2000,
            maxDelayMs = 5000,
            maxAttempts = 10
        )
        val failure = createRetriableFailure()

        val decision = policy.shouldRetry(failure, 5)

        // Would be 1000 + 4*2000 = 9000, but capped at 5000
        assertEquals(5000L, decision.delayMs)
    }

    @Test
    fun `linearBackoff() should have correct max attempts`() {
        val policy = RetryPolicy.linearBackoff(maxAttempts = 3)
        assertEquals(3, policy.getMaxAttempts())
    }

    // ========== CUSTOM POLICY TESTS ==========

    @Test
    fun `custom() should use provided resolver`() {
        var resolverCalled = false
        val policy = RetryPolicy.custom(maxAttempts = 3) { _, _ ->
            resolverCalled = true
            RetryPolicy.RetryDecision(shouldRetry = true, delayMs = 999)
        }

        val failure = createRetriableFailure()
        val decision = policy.shouldRetry(failure, 1)

        assertTrue(resolverCalled)
        assertTrue(decision.shouldRetry)
        assertEquals(999L, decision.delayMs)
    }

    @Test
    fun `custom() should pass failure and attempt to resolver`() {
        var capturedAttempt = 0
        val policy = RetryPolicy.custom(maxAttempts = 5) { failure, attempt ->
            capturedAttempt = attempt
            RetryPolicy.RetryDecision(shouldRetry = failure.retriable)
        }

        val failure = createRetriableFailure()
        policy.shouldRetry(failure, 3)

        assertEquals(3, capturedAttempt)
    }

    @Test
    fun `custom() should have correct max attempts`() {
        val policy = RetryPolicy.custom(maxAttempts = 7) { _, _ ->
            RetryPolicy.RetryDecision(shouldRetry = true)
        }

        assertEquals(7, policy.getMaxAttempts())
    }

    @Test
    fun `custom() should support complex retry logic`() {
        val policy = RetryPolicy.custom(maxAttempts = 5) { failure, attempt ->
            when {
                !failure.retriable -> RetryPolicy.RetryDecision(
                    shouldRetry = false,
                    reason = "Non-retriable"
                )
                attempt > 3 -> RetryPolicy.RetryDecision(
                    shouldRetry = false,
                    reason = "Too many attempts"
                )
                else -> RetryPolicy.RetryDecision(
                    shouldRetry = true,
                    delayMs = attempt * 1000L,
                    reason = "Retry with ${attempt}s delay"
                )
            }
        }

        val retriableFailure = createRetriableFailure()
        val decision1 = policy.shouldRetry(retriableFailure, 1)
        val decision2 = policy.shouldRetry(retriableFailure, 2)
        val decision3 = policy.shouldRetry(retriableFailure, 4)

        assertTrue(decision1.shouldRetry)
        assertEquals(1000L, decision1.delayMs)

        assertTrue(decision2.shouldRetry)
        assertEquals(2000L, decision2.delayMs)

        assertFalse(decision3.shouldRetry)
        assertEquals("Too many attempts", decision3.reason)
    }

    // ========== IMMEDIATE RETRY TESTS ==========

    @Test
    fun `immediate() should retry with zero delay`() {
        val policy = RetryPolicy.immediate(maxAttempts = 3)
        val failure = createRetriableFailure()

        val decision = policy.shouldRetry(failure, 1)

        assertTrue(decision.shouldRetry)
        assertEquals(0L, decision.delayMs)
    }

    @Test
    fun `immediate() should not retry non-retriable failures`() {
        val policy = RetryPolicy.immediate(maxAttempts = 3)
        val failure = createNonRetriableFailure()

        val decision = policy.shouldRetry(failure, 1)

        assertFalse(decision.shouldRetry)
    }

    @Test
    fun `immediate() should enforce max attempts`() {
        val policy = RetryPolicy.immediate(maxAttempts = 2)
        val failure = createRetriableFailure()

        val decision = policy.shouldRetry(failure, 3)

        assertFalse(decision.shouldRetry)
    }

    @Test
    fun `immediate() should have correct max attempts`() {
        val policy = RetryPolicy.immediate(maxAttempts = 5)
        assertEquals(5, policy.getMaxAttempts())
    }

    // ========== PRACTICAL USAGE SCENARIOS ==========

    @Test
    fun `network timeout with exponential backoff`() {
        val policy = RetryPolicy.exponentialBackoff(
            initialDelayMs = 100,
            maxDelayMs = 10000,
            multiplier = 2.0,
            maxAttempts = 5
        )
        val networkFailure = PublishResult.Failure(
            eventType = "order.placed",
            reason = "Connection timeout",
            retriable = true
        )

        // Attempt 1: Should retry
        val decision1 = policy.shouldRetry(networkFailure, 1)
        assertTrue(decision1.shouldRetry)
        assertTrue(decision1.delayMs >= 100)

        // Attempt 2: Should retry with longer delay
        val decision2 = policy.shouldRetry(networkFailure, 2)
        assertTrue(decision2.shouldRetry)
        assertTrue(decision2.delayMs > decision1.delayMs)

        // Attempt 6: Should not retry (exceeds max)
        val decision6 = policy.shouldRetry(networkFailure, 6)
        assertFalse(decision6.shouldRetry)
    }

    @Test
    fun `validation error with no retry`() {
        val policy = RetryPolicy.noRetry()
        val validationFailure = PublishResult.Failure(
            eventType = "user.created",
            reason = "Invalid email format",
            retriable = false
        )

        val decision = policy.shouldRetry(validationFailure, 1)

        assertFalse(decision.shouldRetry)
    }

    @Test
    fun `rate limiting with linear backoff`() {
        val policy = RetryPolicy.linearBackoff(
            initialDelayMs = 1000,
            stepMs = 1000,
            maxDelayMs = 10000,
            maxAttempts = 5
        )
        val rateLimitFailure = PublishResult.Failure(
            eventType = "notification.sent",
            reason = "Rate limit exceeded",
            retriable = true
        )

        val decision1 = policy.shouldRetry(rateLimitFailure, 1)
        val decision2 = policy.shouldRetry(rateLimitFailure, 2)
        val decision3 = policy.shouldRetry(rateLimitFailure, 3)

        assertEquals(1000L, decision1.delayMs)
        assertEquals(2000L, decision2.delayMs)
        assertEquals(3000L, decision3.delayMs)
    }

    @Test
    fun `custom retry logic based on error type`() {
        val policy = RetryPolicy.custom(maxAttempts = 3) { failure, attempt ->
            when {
                failure.reason.contains("timeout") -> RetryPolicy.RetryDecision(
                    shouldRetry = true,
                    delayMs = 5000,
                    reason = "Retry timeout with 5s delay"
                )
                failure.reason.contains("rate limit") -> RetryPolicy.RetryDecision(
                    shouldRetry = true,
                    delayMs = 60000,
                    reason = "Retry rate limit with 60s delay"
                )
                else -> RetryPolicy.RetryDecision(
                    shouldRetry = false,
                    reason = "Unknown error type"
                )
            }
        }

        val timeoutFailure = PublishResult.Failure(
            eventType = "test",
            reason = "Connection timeout",
            retriable = true
        )
        val rateLimitFailure = PublishResult.Failure(
            eventType = "test",
            reason = "API rate limit exceeded",
            retriable = true
        )

        val timeoutDecision = policy.shouldRetry(timeoutFailure, 1)
        val rateLimitDecision = policy.shouldRetry(rateLimitFailure, 1)

        assertTrue(timeoutDecision.shouldRetry)
        assertEquals(5000L, timeoutDecision.delayMs)

        assertTrue(rateLimitDecision.shouldRetry)
        assertEquals(60000L, rateLimitDecision.delayMs)
    }

    @Test
    fun `immediate retry for transient failures`() {
        val policy = RetryPolicy.immediate(maxAttempts = 3)
        val transientFailure = PublishResult.Failure(
            eventType = "cache.invalidate",
            reason = "Temporary unavailable",
            retriable = true
        )

        val decision1 = policy.shouldRetry(transientFailure, 1)
        val decision2 = policy.shouldRetry(transientFailure, 2)
        val decision3 = policy.shouldRetry(transientFailure, 3)
        val decision4 = policy.shouldRetry(transientFailure, 4)

        assertTrue(decision1.shouldRetry)
        assertEquals(0L, decision1.delayMs)

        assertTrue(decision2.shouldRetry)
        assertEquals(0L, decision2.delayMs)

        assertTrue(decision3.shouldRetry)
        assertEquals(0L, decision3.delayMs)

        assertFalse(decision4.shouldRetry)
    }
}
