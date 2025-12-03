package io.github.darkryh.katalyst.di.lifecycle

/**
 * Base exception for all application lifecycle errors.
 *
 * Represents errors that occur during application initialization phases:
 * - Database validation
 * - Service instantiation
 * - Component discovery
 * - Post-initialization hooks
 *
 * This is a fail-fast exception - when thrown, it prevents the application
 * from starting (Spring Boot pattern). The application log will contain
 * detailed information about the failure.
 *
 * @param message Human-readable error description
 * @param cause Root cause exception (if any)
 */
open class LifecycleException(
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause) {
    /**
     * The phase during which this error occurred.
     * Helps identify when in the startup sequence the problem happened.
     */
    open val phase: LifecyclePhase = LifecyclePhase.UNKNOWN
}

/**
 * Lifecycle phases used for error reporting and diagnostics.
 */
enum class LifecyclePhase(val displayName: String) {
    UNKNOWN("Unknown"),
    DATABASE_VALIDATION("Database Validation"),
    COMPONENT_DISCOVERY("Component Discovery"),
    SERVICE_INSTANTIATION("Service Instantiation"),
    SCHEMA_INITIALIZATION("Database Schema"),
    TRANSACTION_ADAPTER_REGISTRATION("Transaction Adapter Registration"),
    PRE_INITIALIZATION("Pre-Initialization"),
    INITIALIZATION("Initialization"),
    POST_INITIALIZATION("Post-Initialization")
}

/**
 * Thrown when database validation fails during startup.
 *
 * This occurs before any initialization, and prevents the application
 * from starting. Common causes:
 * - Database connection failed
 * - Database schema corrupted or incompatible
 * - Insufficient database permissions
 *
 * @param message Description of the validation failure
 * @param cause The underlying exception
 */
class DatabaseValidationException(
    message: String,
    cause: Throwable? = null
) : LifecycleException(message, cause) {
    override val phase: LifecyclePhase = LifecyclePhase.DATABASE_VALIDATION
}

/**
 * Thrown when component discovery fails during DI bootstrap.
 *
 * This occurs when the framework cannot discover or register components.
 * Common causes:
 * - Invalid component annotations
 * - Circular dependencies
 * - Conflicting component registrations
 * - Classpath scanning errors
 *
 * @param message Description of the discovery failure
 * @param cause The underlying exception
 */
class ComponentDiscoveryException(
    message: String,
    cause: Throwable? = null
) : LifecycleException(message, cause) {
    override val phase: LifecyclePhase = LifecyclePhase.COMPONENT_DISCOVERY
}

/**
 * Thrown when service instantiation or initialization fails.
 *
 * This occurs when a service constructor or initialization method fails.
 * Common causes:
 * - Missing required dependencies
 * - Invalid configuration
 * - Service constructor exception
 * - Post-construct method failure
 *
 * @param serviceName Name of the service that failed to initialize
 * @param message Description of the failure
 * @param cause The underlying exception
 */
class ServiceInitializationException(
    val serviceName: String,
    message: String,
    cause: Throwable? = null
) : LifecycleException("Service initialization failed: $serviceName - $message", cause) {
    override val phase: LifecyclePhase = LifecyclePhase.SERVICE_INSTANTIATION
}

/**
 * Thrown when database schema initialization fails.
 *
 * This occurs when creating tables or running migrations.
 * Common causes:
 * - SQL syntax errors in table definitions
 * - Database constraints violated
 * - Migration failure
 * - Incompatible database version
 *
 * @param message Description of the schema error
 * @param cause The underlying exception
 */
class SchemaInitializationException(
    message: String,
    cause: Throwable? = null
) : LifecycleException(message, cause) {
    override val phase: LifecyclePhase = LifecyclePhase.SCHEMA_INITIALIZATION
}

/**
 * Thrown when an initializer hook fails during application startup.
 *
 * This occurs when a registered ApplicationInitializer fails. These hooks
 * run after all components are instantiated and the database is ready.
 * Common causes:
 * - Scheduler configuration errors
 * - Event bus initialization failure
 * - Ktor engine configuration error
 * - Feature initialization failure
 *
 * @param initializerName Name of the initializer that failed
 * @param message Description of the failure
 * @param cause The underlying exception
 */
class InitializerFailedException(
    val initializerName: String,
    message: String,
    cause: Throwable? = null
) : LifecycleException("Initializer failed: $initializerName - $message", cause) {
    override val phase: LifecyclePhase = LifecyclePhase.INITIALIZATION
}

/**
 * Thrown when transaction adapter registration fails.
 *
 * This occurs when setting up transaction adapters for event system integration.
 * Common causes:
 * - Event bus not available
 * - Transaction manager configuration error
 * - Adapter instantiation failure
 *
 * @param adapterName Name of the adapter that failed
 * @param message Description of the failure
 * @param cause The underlying exception
 */
class TransactionAdapterException(
    val adapterName: String,
    message: String,
    cause: Throwable? = null
) : LifecycleException("Transaction adapter registration failed: $adapterName - $message", cause) {
    override val phase: LifecyclePhase = LifecyclePhase.TRANSACTION_ADAPTER_REGISTRATION
}
