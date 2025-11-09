package com.ead.katalyst.scheduler.exception

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
 * ├── SchedulerServiceNotAvailableException
 * ├── SchedulerDiscoveryException
 * ├── SchedulerValidationException
 * └── SchedulerInvocationException
 * ```
 *
 * **Usage**:
 * SchedulerInitializer catches these exceptions and wraps them in
 * InitializerFailedException for lifecycle error handling.
 */
sealed class SchedulerException(
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)

/**
 * Thrown when SchedulerService is not available in the DI container.
 *
 * This typically happens when the scheduler module is not properly registered
 * in the Koin DI container, or the SchedulerService bean is not instantiated.
 *
 * **Example scenario**:
 * ```
 * val scheduler = koin.get<SchedulerService>()  // throws NoSuchElementException
 * // Wrapped as SchedulerServiceNotAvailableException
 * ```
 *
 * **Resolution**: Ensure scheduler module is properly included in DI setup.
 */
class SchedulerServiceNotAvailableException(
    message: String = "SchedulerService is not available in the DI container",
    cause: Throwable? = null
) : SchedulerException(message, cause)

/**
 * Thrown when scheduler method discovery fails.
 *
 * This occurs during STEP 1 (reflection-based discovery) or STEP 2 (bytecode validation)
 * when the scheduler cannot discover or validate scheduler methods.
 *
 * **Error scenarios**:
 * 1. ServiceRegistry not populated (component discovery issue)
 * 2. Reflection errors while scanning services
 * 3. Class loading errors during validation
 *
 * **Example scenario**:
 * ```
 * val services = ServiceRegistry.getAll()  // empty or throws exception
 * // Wrapped as SchedulerDiscoveryException
 * ```
 *
 * **Resolution**: Check that services are properly registered in ServiceRegistry
 * during component discovery phase.
 */
class SchedulerDiscoveryException(
    message: String,
    cause: Throwable? = null
) : SchedulerException(message, cause)

/**
 * Thrown when scheduler method bytecode validation fails.
 *
 * During STEP 2, each candidate method is validated via bytecode analysis.
 * This exception is thrown when bytecode inspection encounters errors.
 *
 * **Error scenarios**:
 * 1. ASM library errors while analyzing bytecode
 * 2. Class file not found on classpath
 * 3. Invalid bytecode or corrupted class file
 *
 * **Note**: Methods that don't call scheduler methods are logged as failures
 * but do NOT throw this exception - they're simply filtered out.
 *
 * **Example scenario**:
 * ```
 * val classFile = classLoader.getResourceAsStream(className)  // throws exception
 * val reader = ClassReader(classFile)  // throws IOException
 * // Wrapped as SchedulerValidationException
 * ```
 *
 * **Resolution**: Ensure all service classes are compiled correctly and
 * available on the classpath.
 */
class SchedulerValidationException(
    message: String,
    cause: Throwable? = null
) : SchedulerException(message, cause)

/**
 * Thrown when scheduler method invocation fails.
 *
 * During STEP 3, validated methods are invoked. This exception is thrown when:
 * 1. The method throws an exception during execution
 * 2. The method returns an invalid type (not SchedulerJobHandle)
 * 3. Reflection invocation fails
 *
 * **Error scenarios**:
 * 1. NullPointerException in the scheduler method
 * 2. Method returns null instead of SchedulerJobHandle
 * 3. Method returns wrong type
 * 4. Reflection invocation security exception
 *
 * **Example scenario**:
 * ```
 * method.call(service)  // throws NullPointerException
 * // Wrapped as SchedulerInvocationException
 * ```
 *
 * **Resolution**:
 * 1. Check the scheduler method implementation for null values
 * 2. Ensure return type is exactly SchedulerJobHandle
 * 3. Verify method has no required parameters
 * 4. Check application logs for the underlying error
 */
class SchedulerInvocationException(
    message: String,
    cause: Throwable? = null
) : SchedulerException(message, cause)

/**
 * Thrown when scheduler configuration is invalid.
 *
 * This occurs when scheduler initializer detects configuration issues
 * that prevent proper operation.
 *
 * **Error scenarios**:
 * 1. Scheduler settings invalid (e.g., negative thread count)
 * 2. Scheduler pool exhausted
 * 3. Incompatible configuration changes
 *
 * **Example scenario**:
 * ```
 * val threadCount = schedulerConfig.getThreadCount()  // returns invalid value
 * if (threadCount <= 0) {
 *     throw SchedulerConfigurationException("Invalid thread count: $threadCount")
 * }
 * ```
 *
 * **Resolution**: Review scheduler configuration and ensure all settings are valid.
 */
class SchedulerConfigurationException(
    message: String,
    cause: Throwable? = null
) : SchedulerException(message, cause)
