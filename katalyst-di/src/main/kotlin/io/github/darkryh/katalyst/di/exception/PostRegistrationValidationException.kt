package io.github.darkryh.katalyst.di.exception

/**
 * Validation failed after components were registered in Koin.
 *
 * This exception is thrown in Phase 7 (Post-Registration Validation) if a component
 * that was successfully instantiated and registered is unexpectedly not found in Koin
 * after registration.
 *
 * This indicates an internal bug in the registration logic, not a user configuration issue.
 *
 * @param component Name of the component that could not be found
 * @param message Description of the validation failure
 * @param cause The underlying exception, if any
 */
class PostRegistrationValidationException(
    val component: String,
    message: String,
    cause: Throwable? = null
) : KatalystDIException(message, cause) {
    constructor(message: String) : this("Unknown", message, null)
    constructor(message: String, cause: Throwable) : this("Unknown", message, cause)
}
