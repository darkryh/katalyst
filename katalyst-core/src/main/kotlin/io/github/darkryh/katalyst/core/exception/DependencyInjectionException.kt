package io.github.darkryh.katalyst.core.exception

/**
 * Exception thrown during automatic dependency injection setup.
 *
 * This exception indicates a configuration or registration problem detected
 * by the framework during startup (scanning, DI initialization, etc.).
 *
 * Developers should use this to identify missing dependencies, misconfigured
 * services, invalid validators, or other DI-related issues.
 *
 * @param message Description of what went wrong with dependency injection
 * @param cause The original exception that caused this DI error
 */
class DependencyInjectionException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)
