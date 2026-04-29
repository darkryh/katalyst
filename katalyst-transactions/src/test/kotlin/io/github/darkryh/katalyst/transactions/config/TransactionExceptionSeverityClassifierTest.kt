package io.github.darkryh.katalyst.transactions.config

import io.github.darkryh.katalyst.events.exception.EventValidationException
import kotlin.test.Test
import kotlin.test.assertEquals
import java.sql.SQLException

class TransactionExceptionSeverityClassifierTest {

    @Test
    fun `retryable sql exception is classified as error`() {
        val config = TransactionConfig()
        val severity = DefaultTransactionExceptionSeverityClassifier.classify(
            SQLException("connection dropped", "08006"),
            config
        )

        assertEquals(TransactionExceptionSeverity.ERROR, severity)
    }

    @Test
    fun `explicit non retryable exception is classified as warn`() {
        val config = TransactionConfig()
        val severity = DefaultTransactionExceptionSeverityClassifier.classify(
            IllegalArgumentException("invalid input"),
            config
        )

        assertEquals(TransactionExceptionSeverity.WARN, severity)
    }

    @Test
    fun `known katalyst exception is classified as warn`() {
        val config = TransactionConfig()
        val severity = DefaultTransactionExceptionSeverityClassifier.classify(
            EventValidationException("event failed validation"),
            config
        )

        assertEquals(TransactionExceptionSeverity.WARN, severity)
    }

    @Test
    fun `configured expected business exception is classified as warn`() {
        class InvalidCredentialsException(message: String) : RuntimeException(message)

        val config = TransactionConfig(
            expectedBusinessExceptions = setOf(InvalidCredentialsException::class)
        )

        val severity = DefaultTransactionExceptionSeverityClassifier.classify(
            InvalidCredentialsException("credentials are invalid"),
            config
        )

        assertEquals(TransactionExceptionSeverity.WARN, severity)
    }

    @Test
    fun `conventional validation exception is classified as warn`() {
        class UserExampleValidationException(message: String) : RuntimeException(message)

        val config = TransactionConfig()
        val severity = DefaultTransactionExceptionSeverityClassifier.classify(
            UserExampleValidationException("invalid user input"),
            config
        )

        assertEquals(TransactionExceptionSeverity.WARN, severity)
    }

    @Test
    fun `unexpected runtime exception remains error`() {
        val config = TransactionConfig()
        val severity = DefaultTransactionExceptionSeverityClassifier.classify(
            NullPointerException("unexpected null"),
            config
        )

        assertEquals(TransactionExceptionSeverity.ERROR, severity)
    }

    @Test
    fun `custom classifier can override severity`() {
        val config = TransactionConfig(
            exceptionSeverityClassifier = TransactionExceptionSeverityClassifier { _, _ ->
                TransactionExceptionSeverity.INFO
            }
        )

        val severity = config.exceptionSeverityClassifier.classify(
            RuntimeException("custom"),
            config
        )

        assertEquals(TransactionExceptionSeverity.INFO, severity)
    }
}
