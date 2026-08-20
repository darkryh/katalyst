package io.github.darkryh.katalyst.transactions.exception

import java.sql.SQLException
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Regression tests for [TransactionFailedException.isRetryable].
 *
 * The class documents PostgreSQL deadlock (SQLState 40P01) and serialization-failure
 * (SQLState 40001) as retryable conditions, but PostgreSQL's driver reports these via
 * [SQLException.getSQLState], not [SQLException.getErrorCode] (which is MySQL's vendor-code
 * channel). A classifier that only inspects `errorCode` never matches the PostgreSQL cases.
 */
class TransactionExceptionsTest {

    @Test
    fun `postgres deadlock sqlstate 40P01 is retryable`() {
        val cause = SQLException("deadlock detected", "40P01")
        val exception = TransactionFailedException(cause = cause)

        assertTrue(exception.isRetryable, "40P01 (PostgreSQL deadlock_detected) must be retryable")
    }

    @Test
    fun `postgres serialization failure sqlstate 40001 is retryable`() {
        val cause = SQLException("could not serialize access", "40001")
        val exception = TransactionFailedException(cause = cause)

        assertTrue(exception.isRetryable, "40001 (PostgreSQL serialization_failure) must be retryable")
    }

    @Test
    fun `mysql deadlock vendor code 1213 is retryable`() {
        val cause = SQLException("deadlock found", null, 1213)
        val exception = TransactionFailedException(cause = cause)

        assertTrue(exception.isRetryable, "MySQL vendor code 1213 (deadlock) must be retryable")
    }

    @Test
    fun `mysql lock wait timeout vendor code 1205 is retryable`() {
        val cause = SQLException("lock wait timeout exceeded", null, 1205)
        val exception = TransactionFailedException(cause = cause)

        assertTrue(exception.isRetryable, "MySQL vendor code 1205 (lock timeout) must be retryable")
    }

    @Test
    fun `unrelated sqlstate and vendor code is not retryable`() {
        val cause = SQLException("unique constraint violation", "23505", 0)
        val exception = TransactionFailedException(cause = cause)

        assertFalse(exception.isRetryable)
    }

    @Test
    fun `null cause is not retryable`() {
        val exception = TransactionFailedException(cause = null)

        assertFalse(exception.isRetryable)
    }
}
