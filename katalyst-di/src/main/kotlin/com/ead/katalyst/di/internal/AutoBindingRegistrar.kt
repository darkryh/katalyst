package com.ead.katalyst.di.internal

import com.ead.katalyst.core.component.Component
import com.ead.katalyst.core.component.Service
import com.ead.katalyst.core.exception.DependencyInjectionException
import com.ead.katalyst.core.persistence.Table
import com.ead.katalyst.core.transaction.DatabaseTransactionManager
import com.ead.katalyst.di.lifecycle.DiscoverySummary
import com.ead.katalyst.events.EventHandler
import com.ead.katalyst.events.bus.GlobalEventHandlerRegistry
import com.ead.katalyst.ktor.KtorModule
import com.ead.katalyst.migrations.KatalystMigration
import com.ead.katalyst.repositories.CrudRepository
import com.ead.katalyst.scanner.core.DiscoveryConfig
import com.ead.katalyst.scanner.core.DiscoveryPredicate
import com.ead.katalyst.scanner.scanner.ReflectionsTypeScanner
import io.ktor.server.application.*
import io.ktor.server.routing.*
import org.koin.core.Koin
import org.koin.core.annotation.KoinInternalApi
import org.koin.core.definition.BeanDefinition
import org.koin.core.definition.Kind
import org.koin.core.definition.indexKey
import org.koin.core.error.DefinitionOverrideException
import org.koin.core.instance.SingleInstanceFactory
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
import kotlin.reflect.KType
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.isAccessible
import kotlin.reflect.jvm.jvmErasure

private val logger = LoggerFactory.getLogger("AutoBindingRegistrar")

/**
 * Auto-discovery and registration engine for Katalyst components.
 *
 * This class orchestrates automatic component discovery and dependency injection registration
 * during application bootstrap. It scans specified packages for framework components and
 * registers them in the Koin DI container.
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
 * - Dependencies are resolved from the Koin container
 * - Well-known properties (DatabaseTransactionManager, SchedulerService) are auto-injected
 * - Registration handles circular dependencies through deferred registration
 *
 * **Thread Safety:**
 * Not thread-safe. Should only be called once during application startup.
 *
 * @param koin The active Koin container to register components into
 * @param scanPackages Array of package names to scan for components
 */
@OptIn(KoinInternalApi::class)
class AutoBindingRegistrar(
    private val koin: Koin,
    scanPackages: Array<String>
) {

    private val packages: List<String> = scanPackages.filter { it.isNotBlank() }

    /**
     * Discovers and registers all framework components.
     *
     * This method executes the complete auto-discovery process in a specific order:
     * 1. Repositories (data access layer)
     * 2. Components (general framework components)
     * 3. Services (business logic layer - depends on repositories and components)
     * 4. Tables (database schema definitions)
     * 5. Ktor modules (HTTP routing and middleware)
     * 6. Event handlers (event-driven components)
     * 7. Route functions (standalone routing DSL functions)
     *
     * Components with unresolved dependencies are deferred and retried after
     * their dependencies become available.
     */
    fun registerAll() {
        registerComponents(CrudRepository::class.java, "repositories")
        registerComponents(Component::class.java, "components")
        registerComponents(Service::class.java, "services")

        registerTables()

        registerComponents(KtorModule::class.java, "ktor modules")
        registerComponents(EventHandler::class.java, "event handlers")
        registerComponents(KatalystMigration::class.java, "migrations")
        registerRouteFunctions()

        // Display component discovery summary
        displayComponentDiscoverySummary()
    }

    /**
     * Displays component discovery summary with organized tables.
     *
     * Collects all discovered components from Koin and displays them
     * in a consolidated summary format using DiscoverySummaryLogger.
     */
    private fun displayComponentDiscoverySummary() {
        try {
            DiscoverySummary.clear()

            koin.safeGetAll<CrudRepository<*, *>>().forEach { repo ->
                DiscoverySummary.addRepository(
                    name = repo::class.simpleName ?: "Repository",
                    annotation = "@Repository"
                )
            }

            koin.safeGetAll<Service>().forEach { svc ->
                DiscoverySummary.addService(
                    name = svc::class.simpleName ?: "Service",
                    annotation = "@Service"
                )
            }

            koin.safeGetAll<Component>().forEach { component ->
                DiscoverySummary.addComponent(
                    name = component::class.simpleName ?: "Component",
                    type = component::class.simpleName?.replace("Component", "") ?: "Custom",
                    annotation = "@Component"
                )
            }

            koin.safeGetAll<Table>().forEach { table ->
                val tableName = (table as? org.jetbrains.exposed.sql.Table)?.tableName ?: table::class.simpleName
                tableName?.let { DiscoverySummary.addDatabaseTable(it) }
            }

            koin.safeGetAll<KtorModule>().forEach { module ->
                DiscoverySummary.addKtorModule(
                    name = module::class.simpleName ?: "KtorModule",
                    annotation = "@KtorModule"
                )
            }

            koin.safeGetAll<EventHandler<*>>().forEach { handler ->
                DiscoverySummary.addComponent(
                    name = handler::class.simpleName ?: "EventHandler",
                    type = "EventHandler",
                    annotation = "@EventHandler"
                )
            }

            koin.safeGetAll<KatalystMigration>().forEach { migration ->
                DiscoverySummary.addComponent(
                    name = migration::class.simpleName ?: "Migration",
                    type = "Migration",
                    annotation = "@Migration"
                )
            }

            DiscoverySummary.display()
        } catch (e: Exception) {
            logger.warn("Error displaying discovery summary: {}", e.message)
            logger.debug("Full error during discovery summary display", e)
        }
    }

    private inline fun <reified T : Any> Koin.safeGetAll(): List<T> =
        runCatching { getAll<T>() }.getOrElse { emptyList() }

    private inline fun <reified T : Any> registerComponents(baseType: Class<T>, label: String) {
        val pending = discoverConcreteTypes(baseType).toMutableSet()
        if (pending.isEmpty()) {
            logger.debug("No {} discovered for {}", baseType.simpleName, label)
            return
        }

        val deferred = mutableSetOf<Class<out T>>()

        while (pending.isNotEmpty()) {
            var progressMade = false
            val iterator = pending.iterator()

            while (iterator.hasNext()) {
                val clazz = iterator.next()

                if (isAlreadyRegistered(clazz)) {
                    logger.debug("Skipping {} - already registered", clazz.name)
                    // Still register in ServiceRegistry if this is a Service
                    if (baseType == Service::class.java) {
                        runCatching {
                            val instance = koin.getFromKoinOrNull(clazz.kotlin)
                            if (instance is Service) {
                                ServiceRegistry.register(instance)
                                logger.debug("Registered already-instantiated service {} in ServiceRegistry", clazz.name)
                            }
                        }
                    }
                    iterator.remove()
                    progressMade = true
                    continue
                }

                val result = runCatching {
                    val instance = instantiate(clazz.kotlin)
                    injectWellKnownProperties(instance)

                    val secondaryTypes = computeSecondaryTypes(clazz.kotlin, baseType.kotlin)
                    registerInstanceWithKoin(instance, clazz.kotlin, secondaryTypes)
                    if (baseType == EventHandler::class.java) {
                        GlobalEventHandlerRegistry.register(instance as EventHandler<*>)
                    }
                    if (baseType == KtorModule::class.java) {
                        KtorModuleRegistry.register(instance as KtorModule)
                    }
                    if (baseType == Service::class.java) {
                        ServiceRegistry.register(instance as Service)
                    }
                    logger.info("Registered {} component {}", label, clazz.name)
                    if (koin.getFromKoinOrNull(clazz.kotlin) == null) {
                        logger.warn("Verification failed for {}: instance not retrievable immediately after registration", clazz.name)
                    }
                }

                when {
                    result.isSuccess -> {
                        iterator.remove()
                        deferred.remove(clazz)
                        progressMade = true
                    }

                    result.exceptionOrNull() is DependencyInjectionException -> {
                        if (deferred.add(clazz)) {
                            logger.debug(
                                "Deferring registration of {} until dependencies are available",
                                clazz.name
                            )
                        }
                    }

                    else -> {
                        val error = result.exceptionOrNull()!!
                        logger.error(
                            "Failed to register {} {}: {}",
                            label.dropLastWhile { it == 's' },
                            clazz.name,
                            error.message
                        )
                        logger.debug("Full error while registering ${clazz.name}", error)
                        iterator.remove()
                        progressMade = true
                    }
                }
            }

            if (!progressMade) {
                deferred.forEach { deferredClass ->
                    logger.error(
                        "Unable to resolve dependencies for {} {}. Please ensure all required components are discoverable.",
                        label.dropLastWhile { it == 's' },
                        deferredClass.name
                    )
                }
                break
            }
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
    private fun <T : Any> discoverConcreteTypes(baseType: Class<T>): Set<Class<out T>> {
        val predicate: DiscoveryPredicate<T> = DiscoveryPredicate { candidate ->
            !Modifier.isAbstract(candidate.modifiers) && !candidate.isInterface
        }

        val config = DiscoveryConfig(
            scanPackages = packages.ifEmpty { emptyList() },
            predicate = predicate
        )

        val scanner = ReflectionsTypeScanner(baseType, config)
        return scanner.discover()
    }

    /**
     * Checks if a component class is already registered in Koin.
     *
     * @param clazz The class to check
     * @return true if already registered, false otherwise
     */
    private fun isAlreadyRegistered(clazz: Class<*>): Boolean =
        koin.getFromKoinOrNull(clazz.kotlin) != null

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
    private fun <T : Any> instantiate(target: KClass<T>): T {
        val constructor = target.primaryConstructor ?: target.constructors
            .minByOrNull { ctor -> ctor.parameters.count { !it.isOptional && it.kind == KParameter.Kind.VALUE } }
            ?: throw IllegalStateException("No usable constructor found for ${target.qualifiedName}")

        val args = mutableMapOf<KParameter, Any?>()

        constructor.parameters.forEach { parameter ->
            if (parameter.kind != KParameter.Kind.VALUE) return@forEach
            if (parameter.isOptional) return@forEach

            val dependencyType = parameter.type.classifier as? KClass<*>
                ?: throw IllegalStateException("Unsupported constructor parameter ${parameter.name} in ${target.qualifiedName}")

            val resolved = resolveDependency(dependencyType, parameter.type, target)
            args[parameter] = resolved
        }

        constructor.isAccessible = true
        return if (args.isEmpty()) {
            constructor.call()
        } else {
            constructor.callBy(args)
        }
    }

    /**
     * Resolves a single constructor dependency from the Koin container.
     *
     * Special handling for:
     * - [Koin] instances (returns the active Koin container)
     * - Nullable types (returns null if not found)
     *
     * @param type The Kotlin class of the dependency
     * @param kType The full Kotlin type including nullability
     * @param owner The class that requires this dependency (for error messages)
     * @return The resolved dependency instance, or null for nullable types
     * @throws DependencyInjectionException if a required dependency cannot be resolved
     */
    private fun resolveDependency(type: KClass<*>, kType: KType, owner: KClass<*>): Any? =
        when (type) {
            Koin::class -> koin
            else -> koin.getFromKoinOrNull(type) ?: if (kType.isMarkedNullable) {
                null
            } else {
                throw DependencyInjectionException(
                    "Cannot resolve dependency ${type.qualifiedName} for ${owner.qualifiedName}"
                )
            }
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
     * 3. The service is available in Koin
     *
     * @param instance The component instance to inject properties into
     */
    private fun injectWellKnownProperties(instance: Any) {
        @Suppress("UNCHECKED_CAST")
        val mutableProperties = instance::class.memberProperties
            .filterIsInstance<KMutableProperty1<Any, Any?>>()

        mutableProperties.forEach { property ->
            val classifier = property.returnType.classifier as? KClass<*> ?: return@forEach

            val value = when {
                classifier == DatabaseTransactionManager::class ->
                    koin.getFromKoinOrNull(DatabaseTransactionManager::class)
                schedulerServiceKClass != null && classifier == schedulerServiceKClass ->
                    koin.getFromKoinOrNull(schedulerServiceKClass)
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
            val current = property.get(instance)
            current != null
        } catch (_: UninitializedPropertyAccessException) {
            false
        }

    /**
     * Computes additional interface types for multi-type binding.
     *
     * Extracts domain-specific interfaces implemented by the component,
     * excluding reserved framework base types to avoid registration conflicts.
     *
     * **Reserved types (excluded):**
     * - [Any] (Kotlin/Java base type)
     * - [Component] (framework marker)
     * - [Service] (framework marker)
     * - [CrudRepository] (framework marker)
     * - [EventHandler] (framework marker)
     *
     * @param clazz The component class to analyze
     * @param baseType The primary base type being registered
     * @return List of additional interfaces for secondary bindings
     */
    private fun computeSecondaryTypes(
        clazz: KClass<*>,
        baseType: KClass<*>
    ): List<KClass<*>> {
        val reserved = setOf(
            Any::class,
            Component::class,
            Service::class,
            CrudRepository::class,
            EventHandler::class,
            KatalystMigration::class
        )

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
    private fun registerRouteFunctions() {
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
            val module = RouteFunctionModule(method)
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
     * - [org.jetbrains.exposed.sql.Table] (Exposed framework)
     * - [Table] (Katalyst marker interface)
     *
     * Tables are registered in Koin and later used to initialize the database schema.
     */
    private fun registerTables() {
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
                registerInstanceWithKoin(
                    instance = instance,
                    primaryType = tableClass,
                    secondaryTypes = listOf(Table::class, org.jetbrains.exposed.sql.Table::class)
                )
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
     * Uses reflection to find all classes extending [org.jetbrains.exposed.sql.Table]
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

            val candidates = reflections.getSubTypesOf(org.jetbrains.exposed.sql.Table::class.java)
            candidates.forEach { candidate ->
                runCatching {
                    val loadedClass = Class.forName(candidate.name, false, classLoader)
                    if (!markerJava.isAssignableFrom(loadedClass)) return@forEach

                    val kClass = loadedClass.kotlin
                    val instance = loadedClass.instantiateIfPossible() ?: return@forEach
                    results[kClass] = instance
                }.onFailure { error ->
                    logger.debug(
                        "Could not instantiate table {}: {}",
                        candidate.name,
                        error.message
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
     * Attempts to instantiate a class using appropriate strategy.
     *
     * Handles both Kotlin objects and regular classes:
     * - Objects: accesses the static INSTANCE field
     * - Classes: calls the no-arg constructor
     *
     * @return Instance of the class, or null if instantiation fails
     */
    private fun Class<*>.instantiateIfPossible(): Any? =
        when {
            isKotlinObject() -> {
                runCatching {
                    getDeclaredField("INSTANCE").apply { isAccessible = true }.get(null)
                }.getOrNull()
            }
            else -> runCatching {
                getDeclaredConstructor().apply { isAccessible = true }.newInstance()
            }.getOrNull()
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
     * Registers a component instance in Koin with multi-type binding.
     *
     * Creates a singleton definition with:
     * - Primary type: the main component type
     * - Secondary types: additional interfaces for polymorphic resolution
     *
     * All types are registered to resolve to the same instance, enabling
     * dependency injection by any of the component's interfaces.
     *
     * @param instance The component instance to register
     * @param primaryType The primary class type for registration
     * @param secondaryTypes Additional interface types for polymorphic binding
     */
    private fun registerInstanceWithKoin(
        instance: Any,
        primaryType: KClass<*>,
        secondaryTypes: List<KClass<*>>
    ) {
        logger.debug(
            "Registering {} with secondary bindings: {}",
            primaryType.qualifiedName,
            secondaryTypes.joinToString { it.qualifiedName ?: it.simpleName.orEmpty() }
        )

        val scopeQualifier = koin.scopeRegistry.rootScope.scopeQualifier

        val definition = BeanDefinition(
            scopeQualifier = scopeQualifier,
            primaryType = primaryType,
            qualifier = null,
            definition = { instance },
            kind = Kind.Singleton,
            secondaryTypes = secondaryTypes
        )

        val factory = SingleInstanceFactory(definition)
        val primaryKey = indexKey(primaryType, definition.qualifier, scopeQualifier)

        runCatching {
            koin.instanceRegistry.saveMapping(true, primaryKey, factory, logWarning = false)
            secondaryTypes.forEach { type ->
                val key = indexKey(type, definition.qualifier, scopeQualifier)
                koin.instanceRegistry.saveMapping(true, key, factory, logWarning = false)
            }
            logger.debug("Registered {} as {} in Koin", primaryType.qualifiedName, primaryKey)
            logger.trace(
                "Koin registry keys after {}: {}",
                primaryType.simpleName,
                koin.instanceRegistry.instances.keys.joinToString()
            )
        }.onFailure { error ->
            if (error is DefinitionOverrideException) {
                logger.debug("Skipping duplicate registration for {}", primaryType.qualifiedName)
            } else {
                throw error
            }
        }
    }
}

/**
 * Safely retrieves an instance from Koin, returning null if not found.
 *
 * @param kClass The class type to retrieve
 * @return The instance if found, null otherwise
 */
private fun Koin.getFromKoinOrNull(kClass: KClass<*>): Any? =
    try {
        @Suppress("UNCHECKED_CAST")
        get(kClass as KClass<Any>, qualifier = null) as Any
    } catch (_: Exception) {
        null
    }

private val schedulerServiceKClass: KClass<*>? = runCatching {
    Class.forName("com.ead.katalyst.services.service.SchedulerService").kotlin
}.getOrNull()

/**
 * Wrapper for route function methods to enable ordered installation.
 *
 * Wraps a static route function method ([Route] or [Application] extension)
 * and provides ordering hints based on function naming conventions:
 * - Exception handlers (order -100): installed first
 * - Middleware/plugins (order -50): installed second
 * - Regular routes (order 0): installed last
 *
 * This ensures proper installation order for exception handling and middleware.
 *
 * @property method The Java reflection method to invoke
 */
private class RouteFunctionModule(
    private val method: Method
) : KtorModule, RouteModuleMarker {

    // Exception handlers and middleware should be installed FIRST (negative order)
    // Regular routes should be installed AFTER (positive order)
    override val order: Int = when {
        method.name.contains("exception", ignoreCase = true) -> -100  // Install exception handlers first
        method.name.contains("middleware", ignoreCase = true) -> -50   // Then middleware
        method.name.contains("plugin", ignoreCase = true) -> -50       // Then plugins
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
                logger.warn("Skipping route function {} – no parameters found", method)
                return
            }

            val firstParam = parameterTypes.first()
            when {
                Application::class.java.isAssignableFrom(firstParam) -> {
                    method.invoke(null, application)
                }

                Route::class.java.isAssignableFrom(firstParam) -> {
                    application.routing {
                        method.invoke(null, this)
                    }
                }

                else -> logger.warn(
                    "Skipping route function {} – unsupported parameter type {}",
                    method,
                    firstParam.name
                )
            }
        } catch (e: Exception) {
            logger.error(
                "Failed to invoke route function {}: {}",
                method,
                e.message,
                e
            )
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger("RouteFunctionModule")
    }
}

/**
 * Internal class names that define Katalyst routing DSL functions.
 *
 * Used for bytecode analysis to detect if a route function uses Katalyst DSL.
 */
private val katalystDslOwners = setOf(
    "com/ead/katalyst/ktor/builder/RoutingBuilderKt",
    "com/ead/katalyst/websockets/builder/WebSocketBuilderKt",
    "com/ead/katalyst/ktor/builder/ExceptionHandlerBuilderKt",
    "com/ead/katalyst/ktor/middleware/MiddlewareKt"
)

/**
 * Katalyst routing DSL method names.
 *
 * Route functions must call at least one of these to be registered.
 */
private val katalystDslMethods = setOf(
    "katalystRouting",
    "katalystWebSockets",
    "katalystExceptionHandler",
    "katalystMiddleware"
)

/**
 * Cache for bytecode analysis results to avoid repeated scanning.
 */
private val methodUsageCache = ConcurrentHashMap<Method, Boolean>()

/**
 * Checks if a route function uses Katalyst routing DSL.
 *
 * Uses bytecode analysis (cached) to detect calls to Katalyst DSL methods.
 * This ensures only genuine Katalyst routes are registered.
 *
 * @return true if the method calls Katalyst DSL functions, false otherwise
 */
private fun Method.usesKatalystDsl(): Boolean =
    methodUsageCache.computeIfAbsent(this) { method ->
        runCatching { method.scanForKatalystDsl() }
            .onFailure { error ->
                LoggerFactory.getLogger("AutoBindingRegistrar")
                    .debug(
                        "Failed to inspect route function {}.{} for katalyst DSL usage: {}",
                        method.declaringClass.name,
                        method.name,
                        error.message
                    )
            }
            .getOrDefault(false)
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
 * 4. Returns true if any Katalyst DSL method is called
 *
 * @return true if Katalyst DSL methods are detected, false otherwise
 */
private fun Method.scanForKatalystDsl(): Boolean {
    val className = declaringClass.name.replace('.', '/') + ".class"
    val stream = declaringClass.classLoader?.getResourceAsStream(className)
        ?: Thread.currentThread().contextClassLoader?.getResourceAsStream(className)
        ?: return false

    stream.use { input ->
        val reader = ClassReader(input)
        val targetDescriptor = Type.getMethodDescriptor(this)
        var found = false

        reader.accept(object : ClassVisitor(Opcodes.ASM9) {
            override fun visitMethod(
                access: Int,
                name: String,
                descriptor: String,
                signature: String?,
                exceptions: Array<out String>?
            ): MethodVisitor? {
                val parent = super.visitMethod(access, name, descriptor, signature, exceptions)
                if (name != this@scanForKatalystDsl.name || descriptor != targetDescriptor) {
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
                            found = true
                        }
                        super.visitMethodInsn(opcode, owner, name, descriptor, isInterface)
                    }
                }
            }
        }, ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES)

        return found
    }
}
