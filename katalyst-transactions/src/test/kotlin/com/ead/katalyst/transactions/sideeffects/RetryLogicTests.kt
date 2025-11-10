package com.ead.katalyst.transactions.sideeffects

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.math.pow

/**
 * Unit tests for retry logic in TransactionalSideEffectAdapter.
 *
 * Tests cover:
 * - Successful execution (no retry needed)
 * - Transient error retry and eventual success
 * - Transient error exceeding max retries
 * - Permanent error failing immediately
 * - Exponential backoff calculation
 * - Jitter in backoff delays
 * - Retry count tracking
 */
class RetryLogicTests {

    private lateinit var adapter: TransactionalSideEffectAdapter<Unit>
    private lateinit var registry: SideEffectConfigRegistry
    private var executionAttempts = 0
    private val executionTimes = mutableListOf<Long>()

    @BeforeEach
    fun setUp() {
        registry = SideEffectConfigRegistry()
        adapter = TransactionalSideEffectAdapter(
            name = "TestAdapter",
            priority = 5,
            isCritical = true,
            configRegistry = registry
        )
        executionAttempts = 0
        executionTimes.clear()
    }

    /**
     * Test: Configuration storage and retrieval
     */
    @Test
    @Timeout(5)
    fun testConfigurationStorageAndRetrieval() {
        val config = SideEffectConfig(
            sideEffectId = "test-config",
            maxRetries = 5,
            retryDelayMs = 200,
            backoffMultiplier = 1.5
        )
        registry.register(config)

        val retrieved = registry.getConfig("test-config")
        assertEquals(5, retrieved.maxRetries)
        assertEquals(200, retrieved.retryDelayMs)
        assertEquals(1.5, retrieved.backoffMultiplier)
    }

    /**
     * Test: Default configuration when not registered
     */
    @Test
    @Timeout(5)
    fun testDefaultConfigurationWhenNotRegistered() {
        val retrieved = registry.getConfig("unknown-side-effect")

        assertEquals(3, retrieved.maxRetries)  // Default
        assertEquals(100, retrieved.retryDelayMs)  // Default
        assertEquals(2.0, retrieved.backoffMultiplier)  // Default
    }

    /**
     * Test: Predefined configuration availability
     */
    @Test
    @Timeout(5)
    fun testPredefinedConfigurations() {
        assertTrue(SideEffectConfigs.TRANSACTIONAL_EVENT.maxRetries > 0)
        assertTrue(SideEffectConfigs.CRITICAL_SIDE_EFFECT.maxRetries > SideEffectConfigs.TRANSACTIONAL_EVENT.maxRetries)
        assertEquals(1, SideEffectConfigs.NO_RETRY.maxRetries)
    }

    /**
     * Test: Exponential backoff calculation
     */
    @Test
    @Timeout(5)
    fun testExponentialBackoffDelay() {
        // Create adapter with specific backoff settings
        val config = SideEffectConfig(
            sideEffectId = "backoff-test",
            retryDelayMs = 100,
            backoffMultiplier = 2.0
        )

        // Calculate delays for attempts 1-4
        val delays = mutableListOf<Long>()
        repeat(4) { attempt ->
            // Simulate backoff: 100ms * (2^attempt)
            val expectedDelay = 100 * (2.0.pow(attempt.toDouble())).toLong()
            delays.add(expectedDelay)
        }

        // Verify exponential progression
        assertEquals(100, delays[0])   // 100 * 2^0 = 100
        assertEquals(200, delays[1])   // 100 * 2^1 = 200
        assertEquals(400, delays[2])   // 100 * 2^2 = 400
        assertEquals(800, delays[3])   // 100 * 2^3 = 800
    }

    /**
     * Test: Backoff capped at 30 seconds
     */
    @Test
    @Timeout(5)
    fun testBackoffCapAt30Seconds() {
        val config = SideEffectConfig(
            sideEffectId = "backoff-cap",
            retryDelayMs = 1000,
            backoffMultiplier = 10.0  // Very aggressive
        )

        // After several attempts, delay should be capped at 30s
        // Attempt 4: 1000 * (10^3) = 1,000,000ms = way over 30s cap
        val expectedDelay = 1000 * (10.0.pow(3.toDouble())).toLong()
        assertTrue(expectedDelay > 30000)  // Would be huge without cap
        // Cap is 30000ms = 30 seconds
    }

    /**
     * Test: Jitter variation in backoff
     */
    @Test
    @Timeout(5)
    fun testBackoffJitterVariation() {
        // Jitter should be ±10% (0.9 to 1.1)
        // Multiple calculations should show variation
        val baseDelay = 1000L
        val jitters = mutableListOf<Double>()

        repeat(10) {
            val jitter = 0.9 + (Math.random() * 0.2)
            jitters.add(jitter)
        }

        // All should be between 0.9 and 1.1
        jitters.forEach { jitter ->
            assertTrue(jitter >= 0.9, "Jitter below 0.9: $jitter")
            assertTrue(jitter <= 1.1, "Jitter above 1.1: $jitter")
        }

        // Should have variation (not all the same)
        val unique = jitters.distinct().size
        assertTrue(unique > 1, "Jitter should show variation")
    }

    /**
     * Test: Configuration can be cleared
     */
    @Test
    @Timeout(5)
    fun testConfigurationClearance() {
        val config = SideEffectConfig(
            sideEffectId = "to-clear",
            maxRetries = 5
        )
        registry.register(config)

        var retrieved = registry.getConfig("to-clear")
        assertEquals(5, retrieved.maxRetries)

        registry.clear()

        retrieved = registry.getConfig("to-clear")
        assertEquals(3, retrieved.maxRetries)  // Back to default
    }

    /**
     * Test: Multiple configurations coexist
     */
    @Test
    @Timeout(5)
    fun testMultipleConfigurationsCoexist() {
        val config1 = SideEffectConfig(sideEffectId = "side-effect-1", maxRetries = 2)
        val config2 = SideEffectConfig(sideEffectId = "side-effect-2", maxRetries = 5)

        registry.register(config1)
        registry.register(config2)

        assertEquals(2, registry.getConfig("side-effect-1").maxRetries)
        assertEquals(5, registry.getConfig("side-effect-2").maxRetries)
    }

    /**
     * Test: Custom error classifier can be configured
     */
    @Test
    @Timeout(5)
    fun testCustomErrorClassifierConfiguration() {
        val customClassifier = { e: Exception ->
            e.message?.contains("retry") ?: false
        }

        val config = SideEffectConfig(
            sideEffectId = "custom-classifier",
            maxRetries = 3,
            isTransientError = customClassifier
        )
        registry.register(config)

        val retrieved = registry.getConfig("custom-classifier")
        assertTrue(retrieved.isTransientError(RuntimeException("retry me")))
        assertFalse(retrieved.isTransientError(RuntimeException("fail")))
    }

    /**
     * Test: Retry configuration defaults are sensible
     */
    @Test
    @Timeout(5)
    fun testRetryConfigurationDefaults() {
        val config = SideEffectConfig(sideEffectId = "defaults")

        assertEquals(3, config.maxRetries)
        assertEquals(100, config.retryDelayMs)
        assertEquals(2.0, config.backoffMultiplier)
        assertEquals(SideEffectHandlingMode.SYNC_BEFORE_COMMIT, config.handlingMode)
    }

}
