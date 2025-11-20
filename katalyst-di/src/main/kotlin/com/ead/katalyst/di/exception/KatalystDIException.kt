package com.ead.katalyst.di.exception

/**
 * Base exception for all Katalyst Dependency Injection system errors.
 *
 * This is the root exception type for DI-related failures. Specific error scenarios
 * throw more specific subclasses that provide additional context and actionable information.
 *
 * @param message Description of the error
 * @param cause The underlying exception that caused this error, if any
 */
open class KatalystDIException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)
