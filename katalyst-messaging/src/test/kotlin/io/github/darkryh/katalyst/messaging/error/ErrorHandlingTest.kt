package io.github.darkryh.katalyst.messaging.error

import io.github.darkryh.katalyst.messaging.Message
import kotlinx.coroutines.test.runTest
import kotlin.test.*

/**
 * Comprehensive tests for ErrorHandler functional interface.
 *
 * Tests cover:
 * - ErrorHandler interface contract
 * - Custom implementations
 * - Async error handling
 * - Practical error handling scenarios
 */
class ErrorHandlingTest {

    // ========== TEST IMPLEMENTATIONS ==========

    class LoggingErrorHandler : ErrorHandler {
        val errors = mutableListOf<Pair<Message, Throwable>>()

        override suspend fun onError(message: Message, exception: Throwable) {
            errors.add(message to exception)
        }
    }

    class RetryErrorHandler(private val maxRetries: Int) : ErrorHandler {
        var retryCount = 0

        override suspend fun onError(message: Message, exception: Throwable) {
            if (retryCount < maxRetries) {
                retryCount++
                // Simulate retry logic
            } else {
                throw exception
            }
        }
    }

    class DeadLetterQueueHandler : ErrorHandler {
        val deadLetterMessages = mutableListOf<Message>()

        override suspend fun onError(message: Message, exception: Throwable) {
            deadLetterMessages.add(message)
        }
    }

    // ========== INTERFACE CONTRACT TESTS ==========

    @Test
    fun `ErrorHandler should be a functional interface`() = runTest {
        // Given - Lambda implementation
        val handler: ErrorHandler = ErrorHandler { _, _ -> }

        // Then
        assertNotNull(handler)
    }

    @Test
    fun `ErrorHandler should have onError method`() = runTest {
        // Given
        var called = false
        val handler = ErrorHandler { _, _ -> called = true }
        val message = Message(payload = "test".toByteArray())
        val exception = RuntimeException("error")

        // When
        handler.onError(message, exception)

        // Then
        assertTrue(called)
    }

    @Test
    fun `ErrorHandler onError should be suspend function`() = runTest {
        // Given
        val handler = ErrorHandler { _, _ ->
            kotlinx.coroutines.delay(10)
        }
        val message = Message(payload = "test".toByteArray())

        // When/Then - Should compile and run as suspend function
        handler.onError(message, RuntimeException())
    }

    // ========== CUSTOM IMPLEMENTATION TESTS ==========

    @Test
    fun `LoggingErrorHandler should record errors`() = runTest {
        // Given
        val handler = LoggingErrorHandler()
        val message = Message("key", "payload".toByteArray())
        val exception = IllegalStateException("test error")

        // When
        handler.onError(message, exception)

        // Then
        assertEquals(1, handler.errors.size)
        assertEquals(message, handler.errors[0].first)
        assertEquals(exception, handler.errors[0].second)
    }

    @Test
    fun `LoggingErrorHandler should record multiple errors`() = runTest {
        // Given
        val handler = LoggingErrorHandler()
        val message1 = Message(payload = "msg1".toByteArray())
        val message2 = Message(payload = "msg2".toByteArray())
        val error1 = RuntimeException("error1")
        val error2 = RuntimeException("error2")

        // When
        handler.onError(message1, error1)
        handler.onError(message2, error2)

        // Then
        assertEquals(2, handler.errors.size)
    }

    @Test
    fun `RetryErrorHandler should track retry attempts`() = runTest {
        // Given
        val handler = RetryErrorHandler(maxRetries = 3)
        val message = Message(payload = "test".toByteArray())
        val exception = RuntimeException("error")

        // When
        handler.onError(message, exception)
        handler.onError(message, exception)

        // Then
        assertEquals(2, handler.retryCount)
    }

    @Test
    fun `RetryErrorHandler should throw after max retries`() = runTest {
        // Given
        val handler = RetryErrorHandler(maxRetries = 2)
        val message = Message(payload = "test".toByteArray())
        val exception = RuntimeException("error")

        // When
        handler.onError(message, exception)
        handler.onError(message, exception)

        // Then - Should throw on third attempt
        assertFailsWith<RuntimeException> {
            handler.onError(message, exception)
        }
    }

    @Test
    fun `DeadLetterQueueHandler should store failed messages`() = runTest {
        // Given
        val handler = DeadLetterQueueHandler()
        val message = Message("failed", "payload".toByteArray())
        val exception = RuntimeException("processing failed")

        // When
        handler.onError(message, exception)

        // Then
        assertEquals(1, handler.deadLetterMessages.size)
        assertEquals(message, handler.deadLetterMessages[0])
    }

    // ========== LAMBDA IMPLEMENTATION TESTS ==========

    @Test
    fun `ErrorHandler should support lambda implementation`() = runTest {
        // Given
        var errorCount = 0
        val handler = ErrorHandler { _, _ -> errorCount++ }
        val message = Message(payload = "test".toByteArray())

        // When
        handler.onError(message, RuntimeException())
        handler.onError(message, RuntimeException())

        // Then
        assertEquals(2, errorCount)
    }

    @Test
    fun `ErrorHandler lambda should receive message and exception`() = runTest {
        // Given
        var receivedMessage: Message? = null
        var receivedException: Throwable? = null

        val handler = ErrorHandler { msg, ex ->
            receivedMessage = msg
            receivedException = ex
        }

        val message = Message("key", "payload".toByteArray())
        val exception = IllegalArgumentException("test")

        // When
        handler.onError(message, exception)

        // Then
        assertEquals(message, receivedMessage)
        assertEquals(exception, receivedException)
    }

    // ========== ASYNC HANDLING TESTS ==========

    @Test
    fun `ErrorHandler should support async operations`() = runTest {
        // Given
        var completed = false
        val handler = ErrorHandler { _, _ ->
            kotlinx.coroutines.delay(50)
            completed = true
        }
        val message = Message(payload = "test".toByteArray())

        // When
        handler.onError(message, RuntimeException())

        // Then
        assertTrue(completed)
    }

    @Test
    fun `ErrorHandler should propagate exceptions`() = runTest {
        // Given
        val handler = ErrorHandler { _, _ ->
            throw IllegalStateException("Handler error")
        }
        val message = Message(payload = "test".toByteArray())

        // Then
        assertFailsWith<IllegalStateException> {
            handler.onError(message, RuntimeException())
        }
    }

    // ========== PRACTICAL USAGE SCENARIOS ==========

    @Test
    fun `typical error logging scenario`() = runTest {
        // Given - Log errors to console/file
        val logs = mutableListOf<String>()
        val handler = ErrorHandler { message, exception ->
            val log = "Error processing message ${message.key}: ${exception.message}"
            logs.add(log)
        }

        val message = Message("order-123", "data".toByteArray())
        val exception = RuntimeException("Validation failed")

        // When
        handler.onError(message, exception)

        // Then
        assertEquals(1, logs.size)
        assertTrue(logs[0].contains("order-123"))
        assertTrue(logs[0].contains("Validation failed"))
    }

    @Test
    fun `retry with exponential backoff scenario`() = runTest {
        // Given - Retry with increasing delays
        val retryDelays = mutableListOf<Long>()
        var attempt = 0

        val handler = ErrorHandler { _, _ ->
            val delay = (100 * (1 shl attempt)).toLong()  // Exponential: 100, 200, 400...
            retryDelays.add(delay)
            attempt++
            kotlinx.coroutines.delay(delay)
        }

        val message = Message(payload = "test".toByteArray())

        // When
        handler.onError(message, RuntimeException())
        handler.onError(message, RuntimeException())
        handler.onError(message, RuntimeException())

        // Then
        assertEquals(3, retryDelays.size)
        assertEquals(100L, retryDelays[0])
        assertEquals(200L, retryDelays[1])
        assertEquals(400L, retryDelays[2])
    }

    @Test
    fun `dead letter queue scenario`() = runTest {
        // Given - Move failed messages to DLQ
        val handler = DeadLetterQueueHandler()
        val messages = listOf(
            Message("msg1", "payload1".toByteArray()),
            Message("msg2", "payload2".toByteArray()),
            Message("msg3", "payload3".toByteArray())
        )

        // When - All messages fail processing
        messages.forEach { message ->
            handler.onError(message, RuntimeException("Processing failed"))
        }

        // Then - All in dead letter queue
        assertEquals(3, handler.deadLetterMessages.size)
        assertEquals("msg1", handler.deadLetterMessages[0].key)
        assertEquals("msg2", handler.deadLetterMessages[1].key)
        assertEquals("msg3", handler.deadLetterMessages[2].key)
    }

    @Test
    fun `alert on critical errors scenario`() = runTest {
        // Given - Send alerts for critical errors
        val alerts = mutableListOf<String>()
        val handler = ErrorHandler { message, exception ->
            if (exception is IllegalStateException) {
                alerts.add("CRITICAL: ${exception.message} for message ${message.key}")
            }
        }

        // When
        handler.onError(
            Message("order-1", "data".toByteArray()),
            RuntimeException("Normal error")  // Not critical
        )
        handler.onError(
            Message("order-2", "data".toByteArray()),
            IllegalStateException("Database connection lost")  // Critical
        )

        // Then - Only critical error generated alert
        assertEquals(1, alerts.size)
        assertTrue(alerts[0].contains("CRITICAL"))
        assertTrue(alerts[0].contains("Database connection lost"))
    }

    @Test
    fun `composite error handler scenario`() = runTest {
        // Given - Multiple error handlers
        val logger = LoggingErrorHandler()
        val dlq = DeadLetterQueueHandler()

        val compositeHandler = ErrorHandler { message, exception ->
            logger.onError(message, exception)
            dlq.onError(message, exception)
        }

        val message = Message("key", "payload".toByteArray())
        val exception = RuntimeException("error")

        // When
        compositeHandler.onError(message, exception)

        // Then
        assertEquals(1, logger.errors.size)
        assertEquals(1, dlq.deadLetterMessages.size)
    }

    @Test
    fun `error handler with metrics scenario`() = runTest {
        // Given - Track error metrics
        val errorMetrics = mutableMapOf<String, Int>()

        val handler = ErrorHandler { _, exception ->
            val errorType = exception::class.simpleName ?: "Unknown"
            errorMetrics[errorType] = (errorMetrics[errorType] ?: 0) + 1
        }

        // When - Different types of errors occur
        handler.onError(Message(payload = "t".toByteArray()), RuntimeException())
        handler.onError(Message(payload = "t".toByteArray()), RuntimeException())
        handler.onError(Message(payload = "t".toByteArray()), IllegalArgumentException())

        // Then
        assertEquals(2, errorMetrics["RuntimeException"])
        assertEquals(1, errorMetrics["IllegalArgumentException"])
    }
}
