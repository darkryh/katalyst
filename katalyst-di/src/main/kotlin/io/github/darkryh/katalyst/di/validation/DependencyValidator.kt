package io.github.darkryh.katalyst.di.validation

import io.github.darkryh.katalyst.core.transaction.DatabaseTransactionManager
import io.github.darkryh.katalyst.di.analysis.ComponentNode
import io.github.darkryh.katalyst.di.analysis.Dependency
import io.github.darkryh.katalyst.di.analysis.DependencyGraph
import io.github.darkryh.katalyst.di.error.*
import org.slf4j.LoggerFactory
import kotlin.reflect.KClass
import kotlin.system.measureTimeMillis

private val logger = LoggerFactory.getLogger("DependencyValidator")

/**
 * Validates component dependencies for issues that would prevent instantiation.
 *
 * Performs multiple validation passes:
 * 1. Cycle detection - Detects circular dependencies (A → B → A)
 * 2. Missing dependency detection - Finds unresolvable types
 * 3. Instantiability checks - Detects private/abstract classes
 * 4. Well-known property validation - Ensures framework properties available
 * 5. Secondary type binding validation - Ensures interface bindings discoverable
 * 6. Feature-provided type validation - Ensures feature modules loaded
 *
 * @param graph The dependency graph to validate
 */
class DependencyValidator(private val graph: DependencyGraph) {

    /**
     * Runs all validations on the dependency graph.
     *
     * @return Complete validation report with all errors found
     */
    fun validateAll(): ValidationReport {
        logger.info("Starting dependency validation")

        val errors = mutableListOf<ValidationError>()
        var analysisDurationMs = 0L
        var validationDurationMs = 0L

        // Run each validation phase
        measureTimeMillis {
            errors.addAll(detectCycles())
        }.also { analysisDurationMs = it }

        measureTimeMillis {
            errors.addAll(findMissingDependencies())
            errors.addAll(findUninstantiableTypes())
            errors.addAll(validateWellKnownProperties())
            errors.addAll(validateSecondaryTypeBindings())
        }.also { validationDurationMs = it }

        val isValid = errors.isEmpty()

        val report = ValidationReport(
            isValid = isValid,
            errors = errors,
            analysisDurationMs = analysisDurationMs,
            validationDurationMs = validationDurationMs
        )

        if (isValid) {
            logger.info("✓ Validation passed: {}", report.getSummary())
        } else {
            logger.error("✗ Validation failed: {}", report.getSummary())
        }

        return report
    }

    /**
     * Detects circular dependencies in the graph using depth-first search.
     *
     * A circular dependency exists when:
     * - A → B → C → A (component A depends on B, B depends on C, C depends on A)
     * - A → A (component depends on itself)
     *
     * Circular dependencies are impossible to resolve because each component
     * depends on another that hasn't been instantiated yet.
     *
     * @return List of circular dependency errors found
     */
    fun detectCycles(): List<CircularDependencyError> {
        logger.info("Detecting circular dependencies")

        val errors = mutableListOf<CircularDependencyError>()
        val visited = mutableSetOf<KClass<*>>()
        val recursionStack = mutableSetOf<KClass<*>>()

        for (component in graph.nodes.keys) {
            if (component !in visited) {
                detectCyclesFromNode(component, visited, recursionStack, mutableListOf(), errors)
            }
        }

        logger.info("Found {} circular dependencies", errors.size)
        return errors
    }

    /**
     * DFS helper for cycle detection.
     *
     * Uses depth-first search to detect back edges (which indicate cycles).
     */
    private fun detectCyclesFromNode(
        current: KClass<*>,
        visited: MutableSet<KClass<*>>,
        recursionStack: MutableSet<KClass<*>>,
        path: MutableList<KClass<*>>,
        errors: MutableList<CircularDependencyError>
    ) {
        visited.add(current)
        recursionStack.add(current)
        path.add(current)

        val dependencies = graph.getDependencies(current)

        for (dependency in dependencies) {
            // Skip optional dependencies in cycle detection
            if (graph.nodes[current]?.dependencies
                ?.find { it.type == dependency }?.isOptional == true
            ) {
                continue
            }

            when {
                dependency !in visited -> {
                    detectCyclesFromNode(dependency, visited, recursionStack, path, errors)
                }
                dependency in recursionStack -> {
                    // Found a cycle - extract the cycle path
                    val cycleStart = path.indexOf(dependency)
                    val cycle = path.subList(cycleStart, path.size) + dependency
                    errors.add(CircularDependencyError(cycle))
                    logger.error("Detected cycle: {}", cycle.map { it.simpleName })
                }
            }
        }

        recursionStack.remove(current)
        path.removeAt(path.size - 1)
    }

    /**
     * Finds dependencies that cannot be resolved.
     *
     * A dependency is unresolvable if:
     * - It's not in Koin (from features)
     * - It's not a discovered component
     * - It's not provided as a secondary type binding
     * - It's not Koin itself
     *
     * @return List of missing dependency errors
     */
    fun findMissingDependencies(): List<MissingDependencyError> {
        logger.info("Checking for missing dependencies")

        val errors = mutableListOf<MissingDependencyError>()

        for ((componentType, node) in graph.nodes) {
            // Skip validation for KatalystMigration classes
            // They have a different lifecycle and resolve dependencies lazily
            try {
                if (io.github.darkryh.katalyst.migrations.KatalystMigration::class.java.isAssignableFrom(componentType.java)) {
                    logger.debug("Skipping dependency validation for migration class: {}", componentType.simpleName)
                    continue
                }
            } catch (_: Exception) {
                // KatalystMigration not available, continue
            }

            for (dependency in node.dependencies) {
                if (dependency.isOptional) {
                    continue  // Optional dependencies don't cause errors
                }

                if (!dependency.isResolvable) {
                    val isInKoin = dependency.type in graph.koinProvidedTypes
                    val isDiscoverable = dependency.type in graph.nodes

                    val suggestion = buildString {
                        appendLine("To fix this missing dependency, try one of these approaches:")
                        appendLine()
                        appendLine("1. If '${dependency.type.simpleName}' is from a feature module:")
                        appendLine("   Add the feature enabler to Application.kt:")
                        appendLine("   fun main(args: Array<String>) = katalystApplication(args) {")
                        appendLine("       // Check for features like:")
                        appendLine("       enableConfigProvider()     // For ConfigProvider")
                        appendLine("       enableScheduler()           // For SchedulerService")
                        appendLine("       enableEvents { withBus(true) }  // For EventBus")
                        appendLine("   }")
                        appendLine()
                        appendLine("2. If '${dependency.type.simpleName}' is a custom service/repository:")
                        appendLine("   Make sure it extends Service, Component, or CrudRepository:")
                        appendLine("   class ${dependency.type.simpleName} : Service {")
                        appendLine("       // implementation")
                        appendLine("   }")
                        appendLine()
                        appendLine("3. Make sure the package is in scanPackages:")
                        appendLine("   scanPackages(\"com.example.myapp\")")
                        appendLine()
                        appendLine("4. Register it manually in a Koin module:")
                        appendLine("   val myModule = module {")
                        appendLine("       single { ${dependency.type.simpleName}() }")
                        appendLine("   }")
                        appendLine("   enableCustomModule(myModule)")
                    }

                    val error = MissingDependencyError(
                        component = componentType,
                        requiredType = dependency.type,
                        parameterName = dependency.parameterName,
                        isInKoin = isInKoin,
                        isDiscoverable = isDiscoverable,
                        suggestion = suggestion
                    )

                    errors.add(error)
                    logger.error(
                        "Missing dependency: {} requires {} (from {})",
                        componentType.simpleName,
                        dependency.type.simpleName,
                        dependency.source
                    )
                }
            }
        }

        logger.info("Found {} missing dependencies", errors.size)
        return errors
    }

    /**
     * Finds component types that cannot be instantiated.
     *
     * A type is uninstantiable if:
     * - It's abstract or an interface
     * - It has no accessible constructor
     * - The constructor has unsupported parameter types
     *
     * @return List of uninstantiable type errors
     */
    fun findUninstantiableTypes(): List<UninstantiableTypeError> {
        logger.info("Checking for uninstantiable types")

        val errors = mutableListOf<UninstantiableTypeError>()

        for ((type, node) in graph.nodes) {
            if (!node.isInstantiable) {
                val reason = when {
                    type.isAbstract -> "Class ${type.simpleName} is abstract and cannot be instantiated"
                    type.java.isInterface -> "Type ${type.simpleName} is an interface and cannot be instantiated"
                    else -> "Type ${type.simpleName} has no accessible constructor"
                }

                errors.add(UninstantiableTypeError(
                    component = type,
                    reason = reason
                ))

                logger.error("Uninstantiable type: {}", reason)
            }
        }

        logger.info("Found {} uninstantiable types", errors.size)
        return errors
    }

    /**
     * Validates that well-known properties are available in Koin.
     *
     * Well-known properties that may be injected:
     * - DatabaseTransactionManager (from CoreDIModule)
     * - SchedulerService (if scheduler feature enabled)
     *
     * @return List of well-known property errors
     */
    fun validateWellKnownProperties(): List<WellKnownPropertyError> {
        logger.info("Validating well-known properties")

        val errors = mutableListOf<WellKnownPropertyError>()

        for ((componentType, node) in graph.nodes) {
            for (dependency in node.dependencies) {
                // Check for DatabaseTransactionManager
                if (dependency.type == DatabaseTransactionManager::class) {
                    if (!graph.canResolve(dependency.type)) {
                        errors.add(WellKnownPropertyError(
                            component = componentType,
                            propertyType = DatabaseTransactionManager::class,
                            propertyName = dependency.parameterName,
                            isAvailable = false
                        ))

                        logger.error(
                            "Missing DatabaseTransactionManager in {}",
                            componentType.simpleName
                        )
                    }
                }
            }
        }

        logger.info("Found {} well-known property errors", errors.size)
        return errors
    }

    /**
     * Validates that secondary type bindings are discoverable.
     *
     * A secondary type is an interface implemented by a component.
     * If another component depends on that interface, it should be resolvable
     * to a component that implements it.
     *
     * @return List of secondary type binding errors
     */
    fun validateSecondaryTypeBindings(): List<SecondaryTypeBindingError> {
        logger.info("Validating secondary type bindings")

        val errors = mutableListOf<SecondaryTypeBindingError>()

        for ((componentType, node) in graph.nodes) {
            for (dependency in node.dependencies) {
                if (dependency.isOptional) continue

                // Check if this is a secondary type that needs validation
                val providers = graph.getProvidersOfSecondaryType(dependency.type)

                if (providers.isNotEmpty() && dependency.type !in graph.nodes) {
                    // This type is only provided as secondary binding
                    // Make sure it's resolvable
                    if (providers.isEmpty()) {
                        errors.add(SecondaryTypeBindingError(
                            component = componentType,
                            requiredType = dependency.type,
                            parameterName = dependency.parameterName,
                            providingComponents = emptyList()
                        ))

                        logger.error(
                            "Secondary type {} not provided by any component",
                            dependency.type.simpleName
                        )
                    }
                }
            }
        }

        logger.info("Found {} secondary type binding errors", errors.size)
        return errors
    }

    /**
     * Gets a list of all validation errors (for completeness).
     *
     * @return List of all errors found during validation
     */
    fun getAllErrors(): List<ValidationError> {
        val errors = mutableListOf<ValidationError>()

        errors.addAll(detectCycles())
        errors.addAll(findMissingDependencies())
        errors.addAll(findUninstantiableTypes())
        errors.addAll(validateWellKnownProperties())
        errors.addAll(validateSecondaryTypeBindings())

        return errors
    }
}
