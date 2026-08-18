package io.github.darkryh.katalyst.di.internal

import io.github.darkryh.katalyst.core.component.Component
import io.github.darkryh.katalyst.core.component.Service
import io.github.darkryh.katalyst.core.di.KatalystContainer
import io.github.darkryh.katalyst.core.exception.DependencyInjectionException
import io.github.darkryh.katalyst.core.persistence.Table
import io.github.darkryh.katalyst.conventions.KatalystConventions
import io.github.darkryh.katalyst.transactions.manager.DatabaseTransactionManager
import io.github.darkryh.katalyst.di.analysis.KnownPlatformTypes
import io.github.darkryh.katalyst.di.extension.ExtensionPoints
import io.github.darkryh.katalyst.di.feature.KatalystBeanEngine
import io.github.darkryh.katalyst.events.EventHandler
import io.github.darkryh.katalyst.ktor.KtorModule
import io.github.darkryh.katalyst.di.lifecycle.StartupHook
import io.github.darkryh.katalyst.di.lifecycle.StartupHookRegistry
import io.github.darkryh.katalyst.di.lifecycle.ReadyHook
import io.github.darkryh.katalyst.di.lifecycle.ReadyHookRegistry
import io.github.darkryh.katalyst.di.invocation.CallableInvoker
import io.github.darkryh.katalyst.di.invocation.ParameterResolver
import io.github.darkryh.katalyst.di.invocation.getFromContainerOrNull
import io.github.darkryh.katalyst.migrations.KatalystMigration
import io.github.darkryh.katalyst.repositories.CrudRepository
import io.github.darkryh.katalyst.scanner.core.DiscoveryConfig
import io.github.darkryh.katalyst.scanner.core.DiscoveryPredicate
import io.github.darkryh.katalyst.scanner.core.EmptyDiscoverySeverity
import io.github.darkryh.katalyst.scanner.scanner.ReflectionsTypeScanner
import io.ktor.server.application.*
import io.ktor.server.routing.*
import org.objectweb.asm.*
import org.reflections.Reflections
import org.reflections.scanners.Scanners
import org.reflections.util.ClasspathHelper
import org.reflections.util.ConfigurationBuilder
import org.reflections.util.FilterBuilder
import org.slf4j.LoggerFactory
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.KParameter
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.isAccessible
import kotlin.reflect.jvm.kotlinFunction
import kotlin.reflect.jvm.jvmErasure

private val logger = LoggerFactory.getLogger("AutoBindingRegistrar")

/**
 * Auto-discovery and registration engine for Katalyst components.
 *
 * This class orchestrates automatic component discovery and dependency injection registration
 * during application bootstrap. It scans specified packages for framework components and
 * registers them in the active Katalyst bean container.
 *
 * **Discovered Component Types:**
 * - [CrudRepository] implementations (data access layer)
 * - [Component] implementations (general framework components)
 * - [Table] implementations (database schema definitions)
 * - [KtorModule] implementations (HTTP routing and middleware)
 * - [EventHandler] implementations (event-driven logic)
 * - Route extension functions (Ktor routing DSL functions)
 *
 * **Registration Strategy:**
 * - Components are instantiated with constructor dependency injection
 * - Dependencies are resolved from the active container
 * - Well-known properties (DatabaseTransactionManager, SchedulerService) are auto-injected
 * - Unresolvable constructor cycles fail during validation with explicit diagnostics
 *
 * **Thread Safety:**
 * Not thread-safe. Should only be called once during application startup.
 *
 * @param container The active Katalyst container to resolve components from
 * @param beanEngine The installed bean engine that owns native registration
 * @param scanPackages Array of package names to scan for components
 */
class AutoBindingRegistrar(
    private val container: KatalystContainer,
    private val beanEngine: KatalystBeanEngine,
    scanPackages: Array<String>
) {

    private val packages: List<String> = scanPackages.filter { it.isNotBlank() }

    companion object {
        /**
         * Resets the secondary type ownership tracking.
         *
         * Bootstrap and shutdown reset [SecondaryTypeOwnerRegistry] through
         * [io.github.darkryh.katalyst.di.registry.RegistryManager]; this remains for tests that
         * register instances directly without going through a bootstrap.
         */
        fun resetSecondaryTypeTracking() {
            SecondaryTypeOwnerRegistry.reset()
            logger.debug("Secondary type ownership tracking reset")
        }
    }

    /**
     * Scans configured packages for concrete implementations of a base type.
     *
     * Filters out abstract classes and interfaces, returning only instantiable types.
     *
     * @param T The base type to search for
     * @param baseType The Java class of the base type
     * @return Set of concrete implementation classes
     */
    internal fun <T : Any> discoverConcreteTypes(baseType: Class<T>): Set<Class<out T>> {
        val predicate: DiscoveryPredicate<T> = DiscoveryPredicate { candidate ->
            !Modifier.isAbstract(candidate.modifiers) && !candidate.isInterface
        }

        val config = DiscoveryConfig(
            scanPackages = packages.ifEmpty { emptyList() },
            predicate = predicate,
            emptyResultSeverity = emptyDiscoverySeverity(baseType)
        )

        val scanner = ReflectionsTypeScanner(baseType, config)
        return scanner.discover()
    }

    private fun <T : Any> emptyDiscoverySeverity(baseType: Class<T>): EmptyDiscoverySeverity {
        val optional = ExtensionPoints.forType(baseType.kotlin)?.optionalDiscovery ?: false
        return if (optional) EmptyDiscoverySeverity.INFO else EmptyDiscoverySeverity.WARN
    }

    /**
     * Creates an instance of a component using constructor injection.
     *
     * Selects the primary constructor or the constructor with the fewest
     * required (non-optional) parameters. Resolves all constructor dependencies
     * from the Koin container.
     *
     * @param T The component type to instantiate
     * @param target The Kotlin class to instantiate
     * @return A fully-constructed instance with all dependencies injected
     * @throws IllegalStateException if no suitable constructor is found
     * @throws DependencyInjectionException if dependencies cannot be resolved
     */
    internal fun <T : Any> instantiate(target: KClass<T>): T {
        val constructor = target.primaryConstructor ?: target.constructors
            .minByOrNull { ctor -> ctor.parameters.count { !it.isOptional && it.kind == KParameter.Kind.VALUE } }
            ?: throw IllegalStateException("No usable constructor found for ${target.qualifiedName}")

        return CallableInvoker.callConstructor(
            constructor = constructor,
            resolver = ParameterResolver(container),
            ownerDescription = target.qualifiedName ?: target.simpleName ?: "component"
        )
    }

    /**
     * Performs property injection for well-known framework services.
     *
     * Auto-injects these framework services into mutable properties:
     * - [DatabaseTransactionManager] for transaction management
     * - SchedulerService (when that module is present) for task scheduling
     *
     * Only injects if:
     * 1. The property is mutable (var)
     * 2. The property is not already initialized
     * 3. The service is available in the active container
     *
     * @param instance The component instance to inject properties into
     */
    internal fun injectWellKnownProperties(instance: Any) {
        @Suppress("UNCHECKED_CAST")
        val mutableProperties = instance::class.memberProperties
            .filterIsInstance<KMutableProperty1<Any, Any?>>()

        mutableProperties.forEach { property ->
            val classifier = property.returnType.classifier as? KClass<*> ?: return@forEach

            val value = when {
                classifier == DatabaseTransactionManager::class ->
                    container.getFromContainerOrNull(
                        kClass = DatabaseTransactionManager::class,
                        ownerDescription = "property '${property.name}' of ${instance::class.qualifiedName}",
                    )
                schedulerServiceKClass != null && classifier == schedulerServiceKClass ->
                    container.getFromContainerOrNull(
                        kClass = schedulerServiceKClass,
                        ownerDescription = "property '${property.name}' of ${instance::class.qualifiedName}",
                    )
                else -> null
            } ?: return@forEach

            if (propertyAlreadyInitialised(property, instance)) return@forEach
            runCatching {
                property.isAccessible = true
                property.setter.call(instance, value)
            }.onFailure { error ->
                logger.warn(
                    "Failed to assign {} on {}: {}",
                    classifier.simpleName,
                    instance::class.qualifiedName,
                    error.message
                )
            }
        }
    }

    /**
     * Checks if a property has already been initialized.
     *
     * @param property The property to check
     * @param instance The object instance containing the property
     * @return true if initialized with a non-null value, false if uninitialized
     */
    private fun propertyAlreadyInitialised(property: KMutableProperty1<Any, Any?>, instance: Any): Boolean =
        try {
            property.isAccessible = true
            property.get(instance) != null
        } catch (e: Exception) {
            // Reading a `lateinit` property before it is assigned throws - which is precisely the
            // state this method exists to detect - but the getter runs through reflection, so what
            // arrives here is `InvocationTargetException` wrapping the
            // `UninitializedPropertyAccessException`. Catching that type alone therefore misses it
            // and the exception escapes registration, aborting the whole boot; the cause chain has
            // to be inspected instead. Any other read failure means the current value cannot be
            // observed either, and "not initialised" is the answer that lets injection proceed.
            if (!e.isUninitialisedLateinit()) {
                logger.debug(
                    "Could not read {} on {} before well-known injection: {}",
                    property.name,
                    instance::class.qualifiedName,
                    e.message ?: e.toString()
                )
            }
            false
        }

    /**
     * Computes additional interface types for multi-type binding.
     *
     * Extracts domain-specific interfaces implemented by the component,
     * excluding reserved framework base types to avoid registration conflicts.
     *
     * **Reserved types (excluded):** [Any], [Table], and every framework marker in
     * [ExtensionPoints.reservedTypes] (Component, Service, CrudRepository, EventHandler,
     * KtorModule, KatalystMigration, StartupHook, ReadyHook). Framework markers are bound
     * separately — multibinding markers by [ExtensionPoints.multiBindingTypesOf] in
     * [registerInstance], which uses a runtime `isInstance` check so that markers reached
     * through an abstract intermediate are not missed the way this direct-supertype walk
     * misses them.
     *
     * @param clazz The component class to analyze
     * @param baseType The primary base type being registered
     * @return List of additional interfaces for secondary bindings
     */
    internal fun computeSecondaryTypes(
        clazz: KClass<*>,
        baseType: KClass<*>
    ): List<KClass<*>> {
        val reserved = ExtensionPoints.reservedTypes + setOf(Any::class, Table::class)

        return clazz.supertypes
            .map { it.jvmErasure }
            .filter { candidate ->
                candidate != clazz &&
                    candidate != baseType &&
                    candidate.java.isInterface &&
                    candidate !in reserved
            }
    }

    /**
     * Discovers and registers Ktor route extension functions.
     *
     * Scans for static functions with Route or Application as the first parameter
     * that use Katalyst routing DSL (katalystRouting, katalystExceptionHandler, etc.).
     *
     * Functions are wrapped in [RouteFunctionModule] and registered in [KtorModuleRegistry]
     * for installation during Ktor application startup.
     */
    internal fun registerRouteFunctions() {
        val methods = discoverRouteFunctions()
            .filter { method ->
                if (method.usesKatalystDsl()) {
                    true
                } else {
                    logger.debug(
                        "Skipping route function {}.{} – no katalyst DSL usage detected",
                        method.declaringClass.name,
                        method.name
                    )
                    false
                }
            }
        if (methods.isEmpty()) {
            logger.debug("No route extension functions discovered in {}", packages.joinToString())
            return
        }

        methods.forEach { method ->
            val module = RouteFunctionModule(method, container)
            KtorModuleRegistry.register(module)
            logger.info(
                "Registered route function {}.{} as Ktor module",
                method.declaringClass.name,
                method.name
            )
        }
    }

    /**
     * Discovers and registers Exposed database table definitions.
     *
     * Scans for classes that extend both:
     * - [org.jetbrains.exposed.v1.core.Table] (Exposed framework)
     * - [Table] (Katalyst marker interface)
     *
     * Tables are registered in the active container and later used to initialize the database schema.
     */
    internal fun registerTables() {
        if (packages.isEmpty()) {
            logger.debug("No packages configured for scanning, skipping table discovery")
            return
        }

        val tables = discoverExposedTables()

        if (tables.isEmpty()) {
            logger.debug("No Exposed Table implementations discovered")
            return
        }

        tables.forEach { (tableClass, instance) ->
            try {
                // Tables are registered only under their primary type (concrete class)
                // Secondary types (Table, org.jetbrains.exposed.Table) are NOT registered
                // because multiple tables implementing these interfaces is expected
                registerInstance(
                    instance = instance,
                    primaryType = tableClass,
                    secondaryTypes = emptyList()
                )
                // Also register with TableRegistry for reliable access by StartupValidator
                @Suppress("UNCHECKED_CAST")
                val exposedTable = instance as? org.jetbrains.exposed.v1.core.Table
                if (exposedTable != null) {
                    TableRegistry.register(exposedTable)
                }
                logger.info(
                    "Registered Exposed table {} with Table marker interface",
                    tableClass.qualifiedName
                )
            } catch (e: Exception) {
                logger.error(
                    "Failed to register Exposed table {}: {}",
                    tableClass.qualifiedName,
                    e.message
                )
                logger.debug("Full error registering table ${tableClass.qualifiedName}", e)
            }
        }
    }

    /**
     * Scans classpath for Exposed table implementations with Katalyst marker.
     *
     * Uses reflection to find all classes extending [org.jetbrains.exposed.v1.core.Table]
     * that also implement the [Table] marker interface.
     *
     * **Instantiation Strategy:**
     * - Kotlin objects: retrieves the INSTANCE field
     * - Regular classes: calls the no-arg constructor
     *
     * @return Map of table class to instantiated table object
     */
    private fun discoverExposedTables(): Map<KClass<*>, Any> {
        val results = mutableMapOf<KClass<*>, Any>()

        return try {
            val urls = packages.flatMap { pkg -> ClasspathHelper.forPackage(pkg) }
            if (urls.isEmpty()) {
                return results
            }

            val filter = packages.fold(FilterBuilder()) { builder, pkg ->
                builder.includePackage(pkg)
            }

            val config = ConfigurationBuilder()
                .setUrls(urls)
                .filterInputsBy(filter)
                .setScanners(Scanners.SubTypes)

            val reflections = Reflections(config)
            val markerJava = Table::class.java
            val classLoader = Thread.currentThread().contextClassLoader

            val candidates = reflections.getSubTypesOf(org.jetbrains.exposed.v1.core.Table::class.java)
            candidates.forEach { candidate ->
                runCatching {
                    val loadedClass = Class.forName(candidate.name, false, classLoader)
                    if (!markerJava.isAssignableFrom(loadedClass)) return@forEach

                    val kClass = loadedClass.kotlin
                    if (Modifier.isAbstract(loadedClass.modifiers) || loadedClass.isInterface) {
                        // A shared abstract base for several tables is a normal way to declare
                        // them; only concrete tables are ever registered. Routine -> DEBUG.
                        logger.debug(
                            "Skipping abstract table type {}: only concrete tables are registered",
                            candidate.name
                        )
                        return@forEach
                    }

                    val instance = loadedClass.instantiateTable()

                    // Validate that the table's generic Entity type implements Identifiable
                    validateTableEntityIsIdentifiable(kClass, instance)

                    results[kClass] = instance
                }.onFailure { error ->
                    // Previously the constructor failure was swallowed by getOrNull() inside
                    // instantiateIfPossible() and the `?: return@forEach` skipped the table
                    // before this handler could ever run: a Table whose constructor throws was
                    // dropped with no output at any level, and its schema was then never
                    // created. A discovered concrete table that cannot be built is always worth
                    // a warning - the application will run against a missing table.
                    logger.warn(
                        "Table {} was discovered but could not be instantiated; it will not be " +
                            "registered and its schema will never be created. Reason: {}: {}",
                        candidate.name,
                        error::class.simpleName,
                        error.message,
                        error
                    )
                }
            }

            results
        } catch (e: Exception) {
            logger.debug("Error during Exposed table discovery: {}", e.message)
            results
        }
    }

    /**
     * Validates that a table's generic Entity type implements [io.github.darkryh.katalyst.repositories.Identifiable].
     *
     * This is a runtime validation that complements compile-time type checking.
     * The [Table] interface now requires Entity to implement [io.github.darkryh.katalyst.repositories.Identifiable],
     * but this validation provides helpful diagnostic logging if the constraint
     * is somehow violated.
     *
     * @param kClass The Kotlin class of the table
     * @param instance The instantiated table object
     */
    private fun validateTableEntityIsIdentifiable(kClass: KClass<*>, instance: Any) {
        try {
            // Find the Table<Id, Entity> supertype
            val tableType = kClass.supertypes.find { supertype ->
                supertype.jvmErasure.simpleName == "Table"
            } ?: return

            // Extract the Entity type parameter (second type argument)
            val entityTypeArg = tableType.arguments.getOrNull(1)?.type ?: return
            val entityType = entityTypeArg.jvmErasure

            // Check if Entity implements Identifiable
            val identifiableClass = Class.forName("io.github.darkryh.katalyst.repositories.Identifiable")
            if (!identifiableClass.isAssignableFrom(entityType.java)) {
                logger.warn(
                    "⚠️  Table {} declares entity type {} which does NOT implement Identifiable<Id>. " +
                    "Repositories will fail at runtime. " +
                    "Update table declaration to: Table<Id, {}>",
                    kClass.simpleName,
                    entityType.simpleName,
                    entityType.simpleName
                )
            } else {
                logger.debug(
                    "✓ Table {} entity type {} correctly implements Identifiable<Id>",
                    kClass.simpleName,
                    entityType.simpleName
                )
            }
        } catch (e: Exception) {
            // Silently ignore if we can't validate (e.g., class loading issues)
            logger.debug("Could not validate Identifiable constraint for {}: {}", kClass.simpleName, e.message)
        }
    }

    /**
     * Attempts to instantiate a class using appropriate strategy.
     *
     * Handles both Kotlin objects and regular classes:
     * - Objects: accesses the static INSTANCE field
     * - Classes: calls the no-arg constructor
     *
     * Failures are **propagated**, never swallowed: the caller reports them. Returning null here
     * is what made a table with a throwing constructor disappear without a single log line.
     *
     * @return Instance of the class
     * @throws Throwable whatever the object initialiser or the no-arg constructor raised
     */
    private fun Class<*>.instantiateTable(): Any =
        when {
            isKotlinObject() -> getDeclaredField("INSTANCE").apply { isAccessible = true }.get(null)
            else -> try {
                getDeclaredConstructor().apply { isAccessible = true }.newInstance()
            } catch (error: java.lang.reflect.InvocationTargetException) {
                // Report what the constructor actually threw, not the reflection wrapper - an
                // InvocationTargetException carries a null message.
                throw error.targetException ?: error
            }
        }

    /**
     * Checks if a class is a Kotlin object declaration.
     *
     * Kotlin objects have a static INSTANCE field generated by the compiler.
     *
     * @return true if this is a Kotlin object, false otherwise
     */
    private fun Class<*>.isKotlinObject(): Boolean =
        try {
            getDeclaredField("INSTANCE")
            true
        } catch (_: NoSuchFieldException) {
            false
        }

    /**
     * Scans for route extension functions in top-level Kotlin files.
     *
     * Searches for static methods in classes ending with "Kt" (Kotlin file classes)
     * that take [Route] or [Application] as the first parameter.
     *
     * **Discovery Criteria:**
     * - Static method in a Kotlin file class (NameKt)
     * - First parameter is Route or Application
     * - Returns Unit or void
     *
     * @return List of discovered route function methods
     */
    private fun discoverRouteFunctions(): List<Method> {
        if (packages.isEmpty()) return emptyList()

        val urls = packages.flatMap { pkg -> ClasspathHelper.forPackage(pkg) }
        if (urls.isEmpty()) return emptyList()

        val config = ConfigurationBuilder()
            .setUrls(urls)
            .filterInputsBy(
                packages.fold(FilterBuilder()) { builder, pkg ->
                    builder.includePackage(pkg)
                }
            )
            .setScanners(Scanners.SubTypes.filterResultsBy { _: String? -> true })

        val reflections = Reflections(config)

        val classLoader = Thread.currentThread().contextClassLoader
        val methods = mutableSetOf<Method>()

        reflections
            .getAll(Scanners.SubTypes)
            .asSequence()
            .filter { typeName -> typeName.endsWith("Kt") && packages.any { typeName.startsWith(it) } }
            .forEach { typeName ->
                runCatching { Class.forName(typeName, false, classLoader) }
                    .onSuccess { clazz ->
                        clazz.declaredMethods
                            .filterTo(methods) { method ->
                                method.parameterTypes.isNotEmpty() &&
                                    Modifier.isStatic(method.modifiers) &&
                                    (Route::class.java.isAssignableFrom(method.parameterTypes[0]) ||
                                        Application::class.java.isAssignableFrom(method.parameterTypes[0]))
                            }
                    }
                    .onFailure { error ->
                        logger.debug("Failed to examine class {} for route functions: {}", typeName, error.message)
                    }
            }

        val seen = mutableSetOf<String>()
        return methods.filter { method ->
            val signature = "${method.declaringClass.name}#${method.name}(${method.parameterTypes.joinToString { it.name }})"
            if (!seen.add(signature)) return@filter false

            Modifier.isStatic(method.modifiers) &&
                method.declaringClass.name.endsWith("Kt") &&
                (method.returnType == Void.TYPE || method.returnType.name == "kotlin.Unit")
        }
    }

    /**
     * Registers a component instance with multi-type binding.
     *
     * Creates a singleton definition with:
     * - Primary type: the main component type
     * - Secondary types: additional interfaces for polymorphic resolution
     *
     * All types are registered to resolve to the same instance, enabling
     * dependency injection by any of the component's interfaces.
     *
     * **Collision Detection:**
     * By default secondary type bindings are strict single-owner mappings.
     * If a secondary type is already bound to a different primary type, this method
     * throws a [DependencyInjectionException] to fail-fast on ambiguous bindings.
     *
     * Selected lifecycle extension points (such as [StartupHook] and
     * [ReadyHook]) are treated as multibinding types and are
     * tracked via dedicated registries instead of Koin secondary key mappings.
     *
     * @param instance The component instance to register
     * @param primaryType The primary class type for registration
     * @param secondaryTypes Additional interface types for polymorphic binding
     * @throws DependencyInjectionException if a secondary type collision is detected
     */
    internal fun registerInstance(
        instance: Any,
        primaryType: KClass<*>,
        secondaryTypes: List<KClass<*>>
    ) {
        logger.debug(
            "Registering {} with secondary bindings: {}",
            primaryType.qualifiedName,
            secondaryTypes.joinToString { it.qualifiedName ?: it.simpleName.orEmpty() }
        )

        // Multibinding markers are derived from the instance rather than taken from
        // `secondaryTypes`: the caller computed those by walking DIRECT supertypes, which
        // silently misses any marker reached through an abstract intermediate. A migration
        // written as `class AddUsers : SqlMigration()` has exactly one direct supertype —
        // the abstract class — so KatalystMigration never appears there.
        val derivedMultiTypes = ExtensionPoints.multiBindingTypesOf(instance)
        val singleBindingTypes = secondaryTypes.filterNot { ExtensionPoints.isMultiBinding(it) }
        val multiBindingTypes =
            (secondaryTypes.filter { ExtensionPoints.isMultiBinding(it) } + derivedMultiTypes)
                .distinct()

        // Check for secondary type collisions BEFORE registering
        singleBindingTypes.forEach { type ->
            val existingOwner = SecondaryTypeOwnerRegistry.ownerOf(type)
            if (existingOwner != null && existingOwner != primaryType) {
                throw DependencyInjectionException(
                    buildString {
                        appendLine("Secondary type binding collision detected!")
                        appendLine()
                        appendLine("Interface: ${type.simpleName} (${type.qualifiedName})")
                        appendLine("Already provided by: ${existingOwner.simpleName}")
                        appendLine("Conflicting provider: ${primaryType.simpleName}")
                        appendLine()
                        appendLine("Resolution options:")
                        appendLine("  1. Use @Qualifier annotations to distinguish implementations")
                        appendLine("  2. Remove one of the conflicting implementations")
                        appendLine("  3. Inject by concrete type instead of interface")
                        appendLine("  4. Create a wrapper interface specific to each implementation")
                    }
                )
            }
        }

        // Track ownership of secondary types
        singleBindingTypes.forEach { type ->
            SecondaryTypeOwnerRegistry.claim(type, primaryType)
        }

        if (instance is StartupHook) {
            StartupHookRegistry.register(instance)
        }
        if (instance is ReadyHook) {
            ReadyHookRegistry.register(instance)
        }

        // Multibinding markers ARE registered as container secondary types. The container
        // resolves several instances sharing one unqualified secondary type correctly via
        // `getAll`; only single-resolution `get` is ambiguous, and that is what the
        // collision check above guards. Registering them is what makes
        // `getAll<KatalystMigration>()` and friends return anything at all.
        beanEngine.registerInstance(instance, primaryType, singleBindingTypes + multiBindingTypes)

        if (multiBindingTypes.isNotEmpty()) {
            logger.debug(
                "Registered multibinding types on {}: {}",
                primaryType.simpleName,
                multiBindingTypes.joinToString { it.simpleName.orEmpty() }
            )
        }
    }
}

/**
 * The `SchedulerService` contract, when the scheduler module is on the classpath.
 *
 * Resolved through [KnownPlatformTypes] so this side and
 * [io.github.darkryh.katalyst.di.analysis.DependencyAnalyzer] always agree on which class that
 * is: the analyzer reports `var scheduler: SchedulerService` as a *satisfied*
 * well-known property dependency, so if the two disagree the application boots and the property
 * is simply never assigned - a `lateinit var` then throws on first use.
 */
private val schedulerServiceKClass: KClass<*>? = KnownPlatformTypes.schedulerServiceKClassOrNull()

/** True when this failure is (or wraps) a read of an unassigned `lateinit` property. */
private fun Throwable.isUninitialisedLateinit(): Boolean =
    generateSequence(this) { current -> current.cause?.takeIf { it !== current } }
        .any { it is UninitializedPropertyAccessException }

/**
 * Wrapper for route function methods to enable ordered installation.
 *
 * Wraps a static route function method ([Route] or [Application] extension)
 * and provides ordering hints based on detected DSL usage:
 * - Exception handlers (order -100): installed first
 * - Middleware/plugins (order -50): installed second
 * - Regular routes (order 0): installed last
 *
 * This ensures proper installation order for exception handling and middleware.
 *
 * @property method The Java reflection method to invoke
 */
private class RouteFunctionModule(
    private val method: Method,
    private val container: KatalystContainer
) : KtorModule, RouteModuleMarker {

    // Exception handlers and middleware should be installed FIRST (negative order)
    // Regular routes should be installed AFTER (positive order)
    private val dslCalls = method.katalystDslCalls()

    override val order: Int = when {
        "katalystExceptionHandler" in dslCalls -> -100  // Install exception handlers first
        method.name.contains("exception", ignoreCase = true) -> -100
        "katalystMiddleware" in dslCalls -> -50   // Then middleware/plugins
        method.name.contains("middleware", ignoreCase = true) -> -50
        method.name.contains("plugin", ignoreCase = true) -> -50
        else -> 0  // Regular routes last
    }

    /**
     * Installs the route function into the Ktor application.
     *
     * Invokes the wrapped static method with the appropriate receiver:
     * - [Application] parameter: invokes directly with the application
     * - [Route] parameter: wraps in a routing {} block
     *
     * @param application The Ktor application to install routes into
     */
    override fun install(application: Application) {
        try {
            method.isAccessible = true
            val parameterTypes = method.parameterTypes
            if (parameterTypes.isEmpty()) {
                throw DependencyInjectionException("Route function $method has no receiver parameter")
            }

            val firstParam = parameterTypes.first()
            when {
                Application::class.java.isAssignableFrom(firstParam) -> {
                    invokeWithInjectedParameters(application)
                }

                Route::class.java.isAssignableFrom(firstParam) -> {
                    application.routing {
                        invokeWithInjectedParameters(this)
                    }
                }

                else -> throw DependencyInjectionException(
                    "Route function $method has unsupported receiver parameter ${firstParam.name}"
                )
            }
        } catch (e: Exception) {
            logger.debug("Failed to invoke route function {}", method, e)
            throw DependencyInjectionException("Failed to invoke route function $method: ${e.message}", e)
        }
    }

    private fun invokeWithInjectedParameters(receiver: Any) {
        val function = method.kotlinFunction
        if (function == null) {
            method.invoke(null, receiver)
            return
        }

        val receiverParameter = function.parameters.firstOrNull()
            ?: throw DependencyInjectionException("Route function $method has no Kotlin receiver parameter")

        CallableInvoker.callFunction(
            function = function,
            suppliedParameters = mapOf(receiverParameter to receiver),
            resolver = ParameterResolver(container),
            ownerDescription = "route function ${method.declaringClass.name}.${method.name}"
        )
    }

    companion object {
        private val logger = LoggerFactory.getLogger("RouteFunctionModule")
    }
}

/**
 * Internal class names that define Katalyst routing DSL functions.
 *
 * Used for bytecode analysis to detect if a route function uses Katalyst DSL.
 * Sourced from [KatalystConventions] so the runtime, static analysis and the IDE
 * plugin all agree on what counts as a Katalyst route function.
 */
private val katalystDslOwners = KatalystConventions.dslOwnerInternalNames

/**
 * Katalyst routing DSL method names.
 *
 * Route functions must call at least one of these to be registered.
 * Sourced from [KatalystConventions] (single source of truth).
 */
private val katalystDslMethods = KatalystConventions.dslMethodNames

/**
 * Per-declaring-class cache for bytecode analysis results. ClassValue allows
 * disposable application/plugin classloaders to be reclaimed.
 */
private val methodDslCallCache = object : ClassValue<ConcurrentHashMap<String, Set<String>>>() {
    override fun computeValue(type: Class<*>): ConcurrentHashMap<String, Set<String>> = ConcurrentHashMap()
}

/**
 * Checks if a route function uses Katalyst routing DSL.
 *
 * Uses bytecode analysis (cached) to detect calls to Katalyst DSL methods.
 * This ensures only genuine Katalyst routes are registered.
 *
 * @return true if the method calls Katalyst DSL functions, false otherwise
 */
private fun Method.usesKatalystDsl(): Boolean = katalystDslCalls().isNotEmpty()

/**
 * Returns the set of Katalyst DSL methods invoked by this route function.
 */
private fun Method.katalystDslCalls(): Set<String> =
    methodDslCallCache.get(declaringClass).computeIfAbsent(name + Type.getMethodDescriptor(this)) {
        runCatching { scanForKatalystDslCalls() }
            .onFailure { error ->
                LoggerFactory.getLogger("AutoBindingRegistrar")
                    .debug(
                        "Failed to inspect route function {}.{} for katalyst DSL usage: {}",
                        declaringClass.name,
                        name,
                        error.message
                    )
            }
            .getOrDefault(emptySet())
    }

/**
 * Performs bytecode analysis to detect Katalyst DSL usage.
 *
 * Reads the compiled bytecode of the method and searches for method invocations
 * to Katalyst DSL functions. This is more reliable than name-based heuristics.
 *
 * **Analysis Process:**
 * 1. Loads the class bytecode
 * 2. Finds the target method by signature
 * 3. Scans method instructions for DSL calls
 * 4. Returns the set of called Katalyst DSL functions
 *
 * @return names of Katalyst DSL methods used by this function
 */
private fun Method.scanForKatalystDslCalls(): Set<String> {
    val className = declaringClass.name.replace('.', '/') + ".class"
    val stream = declaringClass.classLoader?.getResourceAsStream(className)
        ?: Thread.currentThread().contextClassLoader?.getResourceAsStream(className)
        ?: return emptySet()

    stream.use { input ->
        val reader = ClassReader(input)
        val targetDescriptor = Type.getMethodDescriptor(this)
        val found = mutableSetOf<String>()

        reader.accept(object : ClassVisitor(Opcodes.ASM9) {
            override fun visitMethod(
                access: Int,
                name: String,
                descriptor: String,
                signature: String?,
                exceptions: Array<out String>?
            ): MethodVisitor? {
                val parent = super.visitMethod(access, name, descriptor, signature, exceptions)
                if (name != this@scanForKatalystDslCalls.name || descriptor != targetDescriptor) {
                    return parent
                }
                return object : MethodVisitor(Opcodes.ASM9, parent) {
                    override fun visitMethodInsn(
                        opcode: Int,
                        owner: String,
                        name: String,
                        descriptor: String,
                        isInterface: Boolean
                    ) {
                        if (owner in katalystDslOwners && name in katalystDslMethods) {
                            found += name
                        }
                        super.visitMethodInsn(opcode, owner, name, descriptor, isInterface)
                    }
                }
            }
        }, ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES)

        return found
    }
}
