package io.github.darkryh.katalyst.scheduler.exception

/**
 * Base exception for scheduler-related errors.
 *
 * **Ownership**: Scheduler module - all scheduler-specific errors extend this class.
 *
 * **Purpose**: Provide structured error handling for:
 * - Service discovery failures
 * - Method validation failures
 * - Method invocation errors
 * - Configuration errors
 *
 * **Error Hierarchy**:
 * ```
 * SchedulerException (base)
 * ├── SchedulerServiceNotAvailableException  (reserved, not currently thrown)
 * ├── SchedulerDiscoveryException            (reserved, not currently thrown)
 * ├── SchedulerValidationException           (reserved, not currently thrown)
 * ├── SchedulerInvocationException           (thrown by SchedulerInitializer)
 * └── SchedulerConfigurationException        (reserved, not currently thrown)
 * ```
 *
 * **Usage**: [io.github.darkryh.katalyst.scheduler.lifecycle.SchedulerInitializer.onReady]
 * throws [SchedulerInvocationException] directly when one or more scheduler methods fail
 * to register. The other subtypes are part of the public error-handling API but are not
 * currently thrown anywhere in this module; they are kept for forward compatibility so
 * callers can already catch/match on them.
 */
sealed class SchedulerException(
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)

/**
 * Represents a missing [io.github.darkryh.katalyst.scheduler.service.SchedulerService] in the
 * DI container.
 *
 * **Status**: reserved - not currently thrown by framework code. Today,
 * [io.github.darkryh.katalyst.scheduler.extension.requireScheduler] raises a plain
 * `IllegalStateException` for this scenario instead. Kept as part of the public API for
 * callers that want a dedicated, catchable type.
 */
class SchedulerServiceNotAvailableException(
    message: String = "SchedulerService is not available in the DI container",
    cause: Throwable? = null
) : SchedulerException(message, cause)

/**
 * Represents a failure discovering scheduler methods (reflection-based candidate scanning).
 *
 * **Status**: reserved - not currently thrown by framework code. Discovery errors in
 * [io.github.darkryh.katalyst.scheduler.lifecycle.SchedulerInitializer] currently propagate as
 * whatever exception the underlying reflection call raised, or are aggregated into
 * [SchedulerInvocationException]. Kept as part of the public API for forward compatibility.
 */
class SchedulerDiscoveryException(
    message: String,
    cause: Throwable? = null
) : SchedulerException(message, cause)

/**
 * Represents a failure validating scheduler method bytecode.
 *
 * **Status**: reserved - not currently thrown by framework code.
 * [io.github.darkryh.katalyst.scheduler.lifecycle.SchedulerMethodBytecodeValidator] swallows
 * bytecode-inspection errors internally (logged at debug level, treated as "not a scheduler
 * method") rather than throwing this exception. Kept as part of the public API for forward
 * compatibility.
 */
class SchedulerValidationException(
    message: String,
    cause: Throwable? = null
) : SchedulerException(message, cause)

/**
 * Thrown when one or more scheduler method registrations fail.
 *
 * **Status**: actively thrown, by
 * [io.github.darkryh.katalyst.scheduler.lifecycle.SchedulerInitializer.onReady]. Each candidate
 * scheduler method is invoked in isolation - a failing method (throws, or returns something other
 * than [io.github.darkryh.katalyst.scheduler.job.SchedulerJobHandle]) is logged and counted, and
 * does not stop the remaining candidates from registering. Once all candidates have been
 * attempted, if any failed, a single aggregate `SchedulerInvocationException` is thrown
 * summarizing the failure count; per-method causes are available in the logs, not chained onto
 * this instance.
 *
 * **Resolution**:
 * 1. Check application logs for the specific method(s) that failed and why.
 * 2. Ensure the scheduler method's return type is exactly `SchedulerJobHandle`.
 * 3. Verify the method has no unresolvable required parameters.
 */
class SchedulerInvocationException(
    message: String,
    cause: Throwable? = null
) : SchedulerException(message, cause)

/**
 * Represents an invalid scheduler configuration.
 *
 * **Status**: reserved - not currently thrown by framework code. Kept as part of the public API
 * for forward compatibility, e.g. future validation of scheduler settings.
 */
class SchedulerConfigurationException(
    message: String,
    cause: Throwable? = null
) : SchedulerException(message, cause)
