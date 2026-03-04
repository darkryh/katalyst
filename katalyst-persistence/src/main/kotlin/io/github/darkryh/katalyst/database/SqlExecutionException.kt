package io.github.darkryh.katalyst.database

/**
 * Exception raised when managed SQL execution fails.
 */
class SqlExecutionException(
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)
