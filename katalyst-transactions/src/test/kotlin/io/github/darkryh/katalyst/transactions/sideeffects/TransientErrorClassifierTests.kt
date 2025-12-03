package io.github.darkryh.katalyst.transactions.sideeffects

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import kotlin.test.assertTrue
import kotlin.test.assertFalse

/**
 * Unit tests for transient error classification.
 *
 * Tests cover:
 * - Timeout exceptions (transient)
 * - Connection exceptions (transient)
 * - IO exceptions (transient)
 * - Null pointer (permanent)
 * - Illegal state (permanent)
 * - Custom classifier
 * - Regex pattern matching
 * - Exception cause chain inspection
 */
class TransientErrorClassifierTests {

    private lateinit var classifier: DefaultTransientErrorClassifier

    @BeforeEach
    fun setUp() {
        classifier = DefaultTransientErrorClassifier()
    }

    // ============= Transient Errors (Should Retry) =============

    /**
     * Test: TimeoutException is transient
     */
    @Test
    @Timeout(5)
    fun testTimeoutExceptionIsTransient() {
        val exception = java.util.concurrent.TimeoutException("Request timeout")
        assertTrue(classifier.isTransient(exception))
    }

    /**
     * Test: ConnectException is transient
     */
    @Test
    @Timeout(5)
    fun testConnectExceptionIsTransient() {
        val exception = java.net.ConnectException("Connection refused")
        assertTrue(classifier.isTransient(exception))
    }

    /**
     * Test: SocketTimeoutException is transient
     */
    @Test
    @Timeout(5)
    fun testSocketTimeoutExceptionIsTransient() {
        val exception = java.net.SocketTimeoutException("Socket timeout")
        assertTrue(classifier.isTransient(exception))
    }

    /**
     * Test: InterruptedException is transient
     */
    @Test
    @Timeout(5)
    fun testInterruptedExceptionIsTransient() {
        val exception = InterruptedException("Thread interrupted")
        assertTrue(classifier.isTransient(exception))
    }

    /**
     * Test: Exception message containing "timeout" is transient
     */
    @Test
    @Timeout(5)
    fun testExceptionWithTimeoutMessageIsTransient() {
        val exception = RuntimeException("The request timed out after 5 seconds")
        assertTrue(classifier.isTransient(exception))
    }

    /**
     * Test: Exception message containing "connection" is transient
     */
    @Test
    @Timeout(5)
    fun testExceptionWithConnectionMessageIsTransient() {
        val exception = RuntimeException("Failed to establish connection")
        assertTrue(classifier.isTransient(exception))
    }

    /**
     * Test: Exception message containing "unavailable" is transient
     */
    @Test
    @Timeout(5)
    fun testExceptionWithUnavailableMessageIsTransient() {
        val exception = RuntimeException("Service temporarily unavailable")
        assertTrue(classifier.isTransient(exception))
    }

    /**
     * Test: Exception cause chain - transient cause makes parent transient
     */
    @Test
    @Timeout(5)
    fun testExceptionCauseChainTransient() {
        val cause = java.util.concurrent.TimeoutException("Timeout in chain")
        val wrapper = RuntimeException("Wrapped error", cause)
        assertTrue(classifier.isTransient(wrapper))
    }

    // ============= Permanent Errors (Should Fail Fast) =============

    /**
     * Test: NullPointerException is permanent
     */
    @Test
    @Timeout(5)
    fun testNullPointerExceptionIsPermanent() {
        val exception = NullPointerException("Null value")
        assertFalse(classifier.isTransient(exception))
    }

    /**
     * Test: IllegalStateException is permanent
     */
    @Test
    @Timeout(5)
    fun testIllegalStateExceptionIsPermanent() {
        val exception = IllegalStateException("Invalid state")
        assertFalse(classifier.isTransient(exception))
    }

    /**
     * Test: IllegalArgumentException is permanent
     */
    @Test
    @Timeout(5)
    fun testIllegalArgumentExceptionIsPermanent() {
        val exception = IllegalArgumentException("Invalid argument")
        assertFalse(classifier.isTransient(exception))
    }

    /**
     * Test: ClassCastException is permanent
     */
    @Test
    @Timeout(5)
    fun testClassCastExceptionIsPermanent() {
        val exception = ClassCastException("Cannot cast to type")
        assertFalse(classifier.isTransient(exception))
    }

    /**
     * Test: IndexOutOfBoundsException is permanent
     */
    @Test
    @Timeout(5)
    fun testIndexOutOfBoundsExceptionIsPermanent() {
        val exception = IndexOutOfBoundsException("Index out of bounds")
        assertFalse(classifier.isTransient(exception))
    }

    /**
     * Test: Generic RuntimeException without pattern is permanent
     */
    @Test
    @Timeout(5)
    fun testGenericRuntimeExceptionIsPermanent() {
        val exception = RuntimeException("Something went wrong")
        assertFalse(classifier.isTransient(exception))
    }

    // ============= Pattern Matching =============

    /**
     * Test: Case-insensitive pattern matching
     */
    @Test
    @Timeout(5)
    fun testPatternMatchingCaseInsensitive() {
        val exception1 = RuntimeException("TIMEOUT occurred")
        val exception2 = RuntimeException("Timeout occurred")
        val exception3 = RuntimeException("timeout occurred")

        assertTrue(classifier.isTransient(exception1))
        assertTrue(classifier.isTransient(exception2))
        assertTrue(classifier.isTransient(exception3))
    }

    /**
     * Test: Multiple patterns in message
     */
    @Test
    @Timeout(5)
    fun testMultiplePatternsInMessage() {
        val exception = RuntimeException("Connection timeout: server unavailable")
        assertTrue(classifier.isTransient(exception))
    }

    /**
     * Test: Partial pattern match
     */
    @Test
    @Timeout(5)
    fun testPartialPatternMatch() {
        val exception = RuntimeException("Read connection reset by peer")
        assertTrue(classifier.isTransient(exception))  // Contains "connection"
    }

    // ============= Exception Cause Chain =============

    /**
     * Test: Deep cause chain inspection
     */
    @Test
    @Timeout(5)
    fun testDeepCauseChainInspection() {
        val deepCause = java.net.ConnectException("Connection refused")
        val midLevel = RuntimeException("Processing failed", deepCause)
        val topLevel = RuntimeException("Operation failed", midLevel)

        assertTrue(classifier.isTransient(topLevel))
    }

    /**
     * Test: Mixed cause chain - exception type takes precedence
     * Permanent exceptions should fail fast regardless of transient cause
     */
    @Test
    @Timeout(5)
    fun testMixedCauseChain() {
        val transientCause = java.util.concurrent.TimeoutException("Timeout")
        val permanentWrapper = NullPointerException("NPE while handling")
        permanentWrapper.initCause(transientCause)

        // Permanent exception type takes precedence - fail fast, no retry
        assertFalse(classifier.isTransient(permanentWrapper))
    }

    // ============= Custom Configuration =============

    /**
     * Test: Custom classifier with different patterns
     */
    @Test
    @Timeout(5)
    fun testCustomClassifierWithCustomPatterns() {
        val customClassifier = DefaultTransientErrorClassifier(
            transientPatterns = listOf("custom-error", "retry-me")
        )

        val customError = RuntimeException("This is a custom-error")
        val standardTimeout = RuntimeException("Timeout occurred")

        assertTrue(customClassifier.isTransient(customError))
        assertFalse(customClassifier.isTransient(standardTimeout))  // Not in custom patterns
    }

    /**
     * Test: Empty message handling
     */
    @Test
    @Timeout(5)
    fun testExceptionWithNullMessage() {
        val exception = RuntimeException("")  // Empty message
        assertFalse(classifier.isTransient(exception))  // Should default to permanent
    }

    /**
     * Test: Exception type precedence over message
     */
    @Test
    @Timeout(5)
    fun testExceptionTypePrecedenceOverMessage() {
        // NullPointerException is always permanent, even with "timeout" in message
        val exception = NullPointerException("Timeout during processing")
        assertFalse(classifier.isTransient(exception))
    }

    /**
     * Test: Known transient types bypass message check
     */
    @Test
    @Timeout(5)
    fun testKnownTransientTypeBypassesMessageCheck() {
        val exception = java.net.SocketTimeoutException("Something completely different")
        assertTrue(classifier.isTransient(exception))  // Still transient by type
    }

    /**
     * Test: Pattern matching with special characters
     */
    @Test
    @Timeout(5)
    fun testPatternMatchingWithSpecialMessages() {
        val exception1 = RuntimeException("java.net.ConnectException: Connection refused")
        val exception2 = RuntimeException("[TIMEOUT] Operation failed")
        val exception3 = RuntimeException("Error: Connection reset by peer!")

        assertTrue(classifier.isTransient(exception1))
        assertTrue(classifier.isTransient(exception2))
        assertTrue(classifier.isTransient(exception3))
    }
}
