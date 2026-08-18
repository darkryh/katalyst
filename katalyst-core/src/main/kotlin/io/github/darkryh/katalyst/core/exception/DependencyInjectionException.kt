package io.github.darkryh.katalyst.core.exception

/**
 * Base type for dependency-injection failures.
 *
 * Covers both halves of the lifecycle: configuration and registration problems detected during
 * startup (scanning, DI initialization, route wiring), and resolution problems at runtime —
 * see [BeanNotFoundException] for the latter.
 *
 * Catch this to handle any DI failure uniformly, or a subtype when the distinction matters.
 *
 * @param message Description of what went wrong with dependency injection
 * @param cause The original exception that caused this DI error
 */
open class DependencyInjectionException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)
