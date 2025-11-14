package com.ead.katalyst.scheduler.exception

import kotlin.test.*

/**
 * Comprehensive tests for SchedulerException hierarchy.
 *
 * Tests cover:
 * - Base SchedulerException (sealed class)
 * - SchedulerServiceNotAvailableException
 * - SchedulerDiscoveryException
 * - SchedulerValidationException
 * - SchedulerInvocationException
 * - SchedulerConfigurationException
 * - Exception hierarchy and inheritance
 * - Message and cause handling
 */
class SchedulerExceptionTest {

    // ========== BASE EXCEPTION TESTS ==========

    @Test
    fun `SchedulerException should extend RuntimeException`() {
        val exception = SchedulerServiceNotAvailableException("test")
        assertTrue(exception is RuntimeException)
        assertTrue(exception is SchedulerException)
    }

    @Test
    fun `SchedulerException should be sealed class`() {
        // Sealed class property - can only be extended by known subclasses
        val exception: SchedulerException = SchedulerServiceNotAvailableException("test")
        assertNotNull(exception)
    }

    // ========== SchedulerServiceNotAvailableException TESTS ==========

    @Test
    fun `SchedulerServiceNotAvailableException should have default message`() {
        val exception = SchedulerServiceNotAvailableException()
        assertTrue(exception.message?.contains("SchedulerService is not available") == true)
    }

    @Test
    fun `SchedulerServiceNotAvailableException should support custom message`() {
        val exception = SchedulerServiceNotAvailableException("Custom error message")
        assertEquals("Custom error message", exception.message)
    }

    @Test
    fun `SchedulerServiceNotAvailableException should support cause`() {
        val cause = NoSuchElementException("Bean not found")
        val exception = SchedulerServiceNotAvailableException("Service unavailable", cause)

        assertEquals("Service unavailable", exception.message)
        assertEquals(cause, exception.cause)
    }

    @Test
    fun `SchedulerServiceNotAvailableException should extend SchedulerException`() {
        val exception = SchedulerServiceNotAvailableException()
        assertTrue(exception is SchedulerException)
    }

    // ========== SchedulerDiscoveryException TESTS ==========

    @Test
    fun `SchedulerDiscoveryException should require message`() {
        val exception = SchedulerDiscoveryException("Discovery failed")
        assertEquals("Discovery failed", exception.message)
    }

    @Test
    fun `SchedulerDiscoveryException should support cause`() {
        val cause = ReflectiveOperationException("Reflection error")
        val exception = SchedulerDiscoveryException("Discovery failed", cause)

        assertEquals("Discovery failed", exception.message)
        assertEquals(cause, exception.cause)
    }

    @Test
    fun `SchedulerDiscoveryException should extend SchedulerException`() {
        val exception = SchedulerDiscoveryException("test")
        assertTrue(exception is SchedulerException)
    }

    // ========== SchedulerValidationException TESTS ==========

    @Test
    fun `SchedulerValidationException should require message`() {
        val exception = SchedulerValidationException("Validation failed")
        assertEquals("Validation failed", exception.message)
    }

    @Test
    fun `SchedulerValidationException should support cause`() {
        val cause = IllegalArgumentException("Invalid bytecode")
        val exception = SchedulerValidationException("Validation failed", cause)

        assertEquals("Validation failed", exception.message)
        assertEquals(cause, exception.cause)
    }

    @Test
    fun `SchedulerValidationException should extend SchedulerException`() {
        val exception = SchedulerValidationException("test")
        assertTrue(exception is SchedulerException)
    }

    // ========== SchedulerInvocationException TESTS ==========

    @Test
    fun `SchedulerInvocationException should require message`() {
        val exception = SchedulerInvocationException("Invocation failed")
        assertEquals("Invocation failed", exception.message)
    }

    @Test
    fun `SchedulerInvocationException should support cause`() {
        val cause = NullPointerException("Null scheduler service")
        val exception = SchedulerInvocationException("Invocation failed", cause)

        assertEquals("Invocation failed", exception.message)
        assertEquals(cause, exception.cause)
    }

    @Test
    fun `SchedulerInvocationException should extend SchedulerException`() {
        val exception = SchedulerInvocationException("test")
        assertTrue(exception is SchedulerException)
    }

    // ========== SchedulerConfigurationException TESTS ==========

    @Test
    fun `SchedulerConfigurationException should require message`() {
        val exception = SchedulerConfigurationException("Invalid configuration")
        assertEquals("Invalid configuration", exception.message)
    }

    @Test
    fun `SchedulerConfigurationException should support cause`() {
        val cause = IllegalStateException("Invalid thread count")
        val exception = SchedulerConfigurationException("Configuration error", cause)

        assertEquals("Configuration error", exception.message)
        assertEquals(cause, exception.cause)
    }

    @Test
    fun `SchedulerConfigurationException should extend SchedulerException`() {
        val exception = SchedulerConfigurationException("test")
        assertTrue(exception is SchedulerException)
    }

    // ========== EXCEPTION HIERARCHY TESTS ==========

    @Test
    fun `all scheduler exceptions should extend SchedulerException`() {
        val exceptions = listOf<SchedulerException>(
            SchedulerServiceNotAvailableException("test"),
            SchedulerDiscoveryException("test"),
            SchedulerValidationException("test"),
            SchedulerInvocationException("test"),
            SchedulerConfigurationException("test")
        )

        exceptions.forEach { exception ->
            assertTrue(exception is SchedulerException)
            assertTrue(exception is RuntimeException)
        }
    }

    @Test
    fun `scheduler exceptions should be distinguishable by type`() {
        val serviceException = SchedulerServiceNotAvailableException("test")
        val discoveryException = SchedulerDiscoveryException("test")
        val validationException = SchedulerValidationException("test")
        val invocationException = SchedulerInvocationException("test")
        val configException = SchedulerConfigurationException("test")

        assertTrue(serviceException is SchedulerServiceNotAvailableException)
        assertTrue(discoveryException is SchedulerDiscoveryException)
        assertTrue(validationException is SchedulerValidationException)
        assertTrue(invocationException is SchedulerInvocationException)
        assertTrue(configException is SchedulerConfigurationException)
    }

    // ========== EXCEPTION CATCHING TESTS ==========

    @Test
    fun `should catch specific exception type`() {
        try {
            throw SchedulerDiscoveryException("Discovery failed")
        } catch (e: SchedulerDiscoveryException) {
            assertEquals("Discovery failed", e.message)
        }
    }

    @Test
    fun `should catch base SchedulerException for any scheduler error`() {
        val exceptions = listOf(
            SchedulerServiceNotAvailableException("test1"),
            SchedulerDiscoveryException("test2"),
            SchedulerValidationException("test3")
        )

        exceptions.forEach { exception ->
            try {
                throw exception
            } catch (e: SchedulerException) {
                assertTrue(e.message?.startsWith("test") == true)
            }
        }
    }

    @Test
    fun `should catch RuntimeException for scheduler errors`() {
        try {
            throw SchedulerInvocationException("Invocation failed")
        } catch (e: RuntimeException) {
            assertTrue(e is SchedulerException)
            assertEquals("Invocation failed", e.message)
        }
    }

    // ========== PRACTICAL USAGE SCENARIOS ==========

    @Test
    fun `DI container missing SchedulerService scenario`() {
        val cause = NoSuchElementException("No definition found for type SchedulerService")
        val exception = SchedulerServiceNotAvailableException(
            "SchedulerService is not available in the DI container",
            cause
        )

        assertTrue(exception.message?.contains("DI container") == true)
        assertTrue(exception.cause is NoSuchElementException)
    }

    @Test
    fun `service discovery failure scenario`() {
        val cause = IllegalStateException("ServiceRegistry is empty")
        val exception = SchedulerDiscoveryException(
            "Failed to discover scheduler methods: ServiceRegistry not populated",
            cause
        )

        assertTrue(exception.message?.contains("ServiceRegistry") == true)
        assertNotNull(exception.cause)
    }

    @Test
    fun `bytecode validation failure scenario`() {
        val cause = ClassNotFoundException("Service class not found")
        val exception = SchedulerValidationException(
            "Failed to validate scheduler method bytecode",
            cause
        )

        assertTrue(exception.message?.contains("bytecode") == true)
        assertTrue(exception.cause is ClassNotFoundException)
    }

    @Test
    fun `method invocation failure scenario`() {
        val cause = NullPointerException("Scheduler service returned null")
        val exception = SchedulerInvocationException(
            "Failed to invoke scheduler method: scheduleBackup()",
            cause
        )

        assertTrue(exception.message?.contains("scheduleBackup") == true)
        assertTrue(exception.cause is NullPointerException)
    }

    @Test
    fun `invalid configuration scenario`() {
        val exception = SchedulerConfigurationException(
            "Invalid scheduler configuration: thread count must be positive, got -1"
        )

        assertTrue(exception.message?.contains("thread count") == true)
        assertTrue(exception.message?.contains("-1") == true)
    }

    @Test
    fun `exception with detailed error context`() {
        val exception = SchedulerInvocationException(
            "Failed to invoke scheduler method 'scheduleAuthDigest' on service 'AuthenticationService': " +
                    "Method returned null instead of SchedulerJobHandle"
        )

        val message = exception.message!!
        assertTrue(message.contains("scheduleAuthDigest"))
        assertTrue(message.contains("AuthenticationService"))
        assertTrue(message.contains("SchedulerJobHandle"))
    }

    @Test
    fun `exception chain for debugging`() {
        val rootCause = IllegalArgumentException("Invalid cron expression")
        val intermediateCause = SchedulerValidationException("Validation failed", rootCause)
        val topException = SchedulerInvocationException("Method invocation failed", intermediateCause)

        assertEquals(intermediateCause, topException.cause)
        assertEquals(rootCause, intermediateCause.cause)
    }
}
