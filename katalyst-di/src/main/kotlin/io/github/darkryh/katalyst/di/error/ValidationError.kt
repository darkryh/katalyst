package io.github.darkryh.katalyst.di.error

import kotlin.reflect.KClass

/**
 * Sealed hierarchy for dependency injection validation errors.
 *
 * Each error type represents a specific validation failure that occurred during
 * the dependency graph analysis phase. Errors are collected and reported together
 * to give developers a complete picture of all DI configuration issues.
 */
sealed class ValidationError {
    /**
     * The component that has the error.
     */
    abstract val component: KClass<*>

    /**
     * Human-readable error message describing what went wrong.
     */
    abstract val message: String

    /**
     * Optional context information about where in the dependency chain the error occurred.
     */
    open val context: String? = null

    /**
     * Optional suggestion on how to fix this error.
     */
    open val suggestion: String? = null
}

/**
 * A required dependency cannot be resolved from Koin or discovered components.
 *
 * This occurs when:
 * - A component's constructor parameter requires a type that:
 *   - Is not in Koin (not registered by any feature module)
 *   - Is not a Service/Component/Repository (not auto-discovered)
 *   - Is not provided as a secondary type binding from another component
 *
 * @param component The class that has the missing dependency
 * @param requiredType The type that cannot be resolved
 * @param parameterName The constructor parameter name that needs this type
 * @param isInKoin Whether the type is in Koin (for diagnostics)
 * @param isDiscoverable Whether the type is discoverable (extends Service/Component/etc)
 * @param suggestion How to fix this error
 */
data class MissingDependencyError(
    override val component: KClass<*>,
    val requiredType: KClass<*>,
    val parameterName: String,
    val isInKoin: Boolean,
    val isDiscoverable: Boolean,
    override val suggestion: String = ""
) : ValidationError() {
    override val message: String
        get() = "Component ${component.simpleName} requires ${requiredType.simpleName} " +
                "for parameter '$parameterName' which is not available"

    override val context: String
        get() {
            val reasons = mutableListOf<String>()
            if (!isInKoin) reasons.add("not registered in any Koin module")
            if (!isDiscoverable) reasons.add("does not extend Service/Component/Repository interface")
            return reasons.joinToString(" and ")
        }
}

/**
 * A circular dependency was detected in the component graph.
 *
 * This occurs when components have circular dependencies like:
 * - A → B → C → A
 * - A → B → A (simple cycle)
 *
 * Circular dependencies make it impossible to instantiate any component in the cycle
 * because each depends on the other to be instantiated first.
 *
 * @param cycle The list of component types forming the cycle (e.g., [A, B, C] for A→B→C→A)
 */
data class CircularDependencyError(
    val cycle: List<KClass<*>>
) : ValidationError() {
    override val component: KClass<*> = cycle.firstOrNull() ?: Any::class

    override val message: String
        get() = "Circular dependency detected: ${cycle.joinToString(" → ") { it.simpleName ?: "Unknown" }}"

    override val suggestion: String
        get() = buildString {
            appendLine("To fix this circular dependency, try one of these approaches:")
            appendLine("1. Make one dependency optional (nullable):")
            appendLine("     class ${cycle.getOrNull(1)?.simpleName}(")
            appendLine("         private val dep: ${cycle.first().simpleName}? = null  // Optional")
            appendLine("     )")
            appendLine("2. Use lazy initialization:")
            appendLine("     class ${cycle.getOrNull(1)?.simpleName}(")
            appendLine("         private val koin: Koin")
            appendLine("     ) {")
            appendLine("         val dep: ${cycle.first().simpleName} by lazy { koin.get() }")
            appendLine("     }")
            appendLine("3. Extract shared logic to break the cycle")
        }
}

/**
 * A component type cannot be instantiated.
 *
 * This occurs when:
 * - The class has a private constructor (cannot access to instantiate)
 * - The class is abstract (cannot instantiate abstract class)
 * - The class is an interface (cannot instantiate interface)
 * - The constructor has unsupported parameter types
 *
 * @param component The class that cannot be instantiated
 * @param reason Description of why it cannot be instantiated
 */
data class UninstantiableTypeError(
    override val component: KClass<*>,
    val reason: String
) : ValidationError() {
    override val message: String = reason

    override val suggestion: String
        get() = buildString {
            when {
                reason.contains("private constructor", ignoreCase = true) -> {
                    appendLine("Make the constructor public:")
                    appendLine("    class ${component.simpleName}(dep: Dependency) {")
                    appendLine("        // Change from 'private constructor(...)' to 'constructor(...)'")
                    appendLine("    }")
                }
                reason.contains("abstract", ignoreCase = true) -> {
                    appendLine("Either:")
                    appendLine("1. Provide a concrete implementation")
                    appendLine("2. Register in Koin module with concrete implementation:")
                    appendLine("     fun customModule() = module {")
                    appendLine("         single<${component.simpleName}> { ConcreteImplementation() }")
                    appendLine("     }")
                }
                else -> {
                    appendLine("Ensure the class:")
                    appendLine("1. Is not abstract or private")
                    appendLine("2. Has a public, accessible constructor")
                    appendLine("3. All constructor parameters can be resolved from Koin")
                }
            }
        }
}

/**
 * A well-known framework property cannot be injected.
 *
 * This occurs when a component declares a mutable property for:
 * - DatabaseTransactionManager
 * - SchedulerService (if scheduler feature enabled)
 *
 * But the type is not available in Koin.
 *
 * @param component The class that uses the well-known property
 * @param propertyType The property type (DatabaseTransactionManager, SchedulerService, etc)
 * @param propertyName The name of the mutable property
 * @param isAvailable Whether the type is available in Koin
 */
data class WellKnownPropertyError(
    override val component: KClass<*>,
    val propertyType: KClass<*>,
    val propertyName: String,
    val isAvailable: Boolean
) : ValidationError() {
    override val message: String
        get() = "Component ${component.simpleName} uses well-known property '$propertyName' " +
                "of type ${propertyType.simpleName} which is not available in Koin"

    override val suggestion: String
        get() = when (propertyType.simpleName) {
            "DatabaseTransactionManager" ->
                "This should come from CoreDIModule. Verify core modules are loaded."
            "SchedulerService" ->
                "Enable scheduler feature: enableScheduler() in katalystApplication { }"
            else -> "Ensure ${propertyType.simpleName} is registered in a Koin module"
        }
}

/**
 * A required secondary type binding is not provided by any discovered component.
 *
 * This occurs when:
 * - A component needs AuthenticationProvider interface
 * - Some component implements AuthenticationProvider as secondary interface
 * - But that component is not discovered or not instantiated
 *
 * @param component The class that needs the secondary type
 * @param requiredType The secondary interface type that cannot be resolved
 * @param parameterName The constructor parameter name
 * @param providingComponents List of components that could provide this type (if any)
 */
data class SecondaryTypeBindingError(
    override val component: KClass<*>,
    val requiredType: KClass<*>,
    val parameterName: String,
    val providingComponents: List<KClass<*>> = emptyList()
) : ValidationError() {
    override val message: String
        get() = "Component ${component.simpleName} requires ${requiredType.simpleName} " +
                "for parameter '$parameterName' which is not provided by any discovered component"

    override val suggestion: String
        get() = buildString {
            if (providingComponents.isNotEmpty()) {
                appendLine("Found these components that could provide this type:")
                providingComponents.forEach { clazz ->
                    appendLine("  - ${clazz.simpleName}")
                }
            } else {
                val typeName = requiredType.simpleName ?: "CustomType"
                appendLine("Create a component that implements $typeName:")
                appendLine("    class ${typeName.replace("Provider", "ServiceImpl")} : Service, $typeName {")
                appendLine("        override fun doSomething() { }")
                appendLine("    }")
            }
        }
}

/**
 * A feature-provided type is not available in Koin.
 *
 * This occurs when:
 * - A component depends on a type provided by a feature (e.g., ConfigProvider, EventBus)
 * - The feature is not enabled or not loaded
 * - The feature module was not registered in Koin
 *
 * @param component The class that depends on the feature-provided type
 * @param requiredType The type that should come from a feature
 * @param featureName The name of the feature that should provide this type
 * @param parameterName The constructor parameter name
 */
data class FeatureProvidedTypeError(
    override val component: KClass<*>,
    val requiredType: KClass<*>,
    val featureName: String,
    val parameterName: String
) : ValidationError() {
    override val message: String
        get() = "Component ${component.simpleName} requires ${requiredType.simpleName} " +
                "which should come from feature '$featureName' but is not available in Koin"

    override val suggestion: String
        get() = buildString {
            appendLine("Enable the feature in katalystApplication { }:")
            append("    ")
            append(when (featureName) {
                "events" -> "enableEvents { withBus(true) }"
                "scheduler" -> "enableScheduler()"
                "migrations" -> "enableMigrations()"
                "websockets" -> "enableWebSockets()"
                "configProvider" -> "enableConfigProvider()"
                else -> "feature($featureName)  // Verify feature name"
            })
        }
}

/**
 * An instantiation failure occurred during the instantiation phase.
 *
 * This indicates a bug in the validation logic - validation passed but instantiation failed.
 * This should never happen if validation is correct.
 *
 * @param component The class that failed to instantiate
 * @param reason Description of the instantiation failure
 * @param cause The underlying exception
 */
data class InstantiationFailureError(
    override val component: KClass<*>,
    val reason: String,
    val cause: Throwable? = null
) : ValidationError() {
    override val message: String = reason

    override val suggestion: String
        get() = buildString {
            appendLine("This indicates a bug in the validation logic.")
            appendLine("The component passed validation but failed during instantiation.")
            appendLine("Please report this issue with the following details:")
            appendLine("  - Component: ${component.qualifiedName}")
            appendLine("  - Error: $reason")
            if (cause != null) {
                appendLine("  - Cause: ${cause.message}")
            }
        }
}
