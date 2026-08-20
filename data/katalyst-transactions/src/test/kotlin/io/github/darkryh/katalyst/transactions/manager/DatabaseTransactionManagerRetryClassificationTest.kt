package io.github.darkryh.katalyst.transactions.manager

import io.github.darkryh.katalyst.transactions.config.RetryPolicy
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import java.sql.SQLException

class DatabaseTransactionManagerRetryClassificationTest {

    @Test
    fun `wrapped sqlstate 08003 is retryable`() {
        val wrapped = RuntimeException("wrapped", SQLException("Connection is closed", "08003"))

        val shouldRetry = shouldRetryException(
            exception = wrapped as Exception,
            retryPolicy = RetryPolicy(),
            attempt = 0,
            maxAttempts = 4
        )

        assertTrue(shouldRetry)
    }

    @Test
    fun `subclass of configured retryable class is retryable`() {
        class CustomSqlException : SQLException("custom", "08006")

        val shouldRetry = shouldRetryException(
            exception = CustomSqlException(),
            retryPolicy = RetryPolicy(retryableExceptions = setOf(SQLException::class)),
            attempt = 0,
            maxAttempts = 3
        )

        assertTrue(shouldRetry)
    }

    @Test
    fun `non retryable rule takes precedence over transient nested cause`() {
        val exception = IllegalArgumentException(
            "validation failed",
            SQLException("Connection is closed", "08003")
        )

        val shouldRetry = shouldRetryException(
            exception = exception,
            retryPolicy = RetryPolicy(
                retryableExceptions = setOf(SQLException::class),
                nonRetryableExceptions = setOf(IllegalArgumentException::class)
            ),
            attempt = 0,
            maxAttempts = 5
        )

        assertFalse(shouldRetry)
    }

    @Test
    fun `does not retry when attempts are exhausted`() {
        val shouldRetry = shouldRetryException(
            exception = SQLException("temporary", "08006"),
            retryPolicy = RetryPolicy(),
            attempt = 2,
            maxAttempts = 3
        )

        assertFalse(shouldRetry)
    }
}

