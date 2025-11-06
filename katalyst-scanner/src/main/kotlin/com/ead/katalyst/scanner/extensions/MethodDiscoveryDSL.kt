package com.ead.katalyst.scanner.extensions

import com.ead.katalyst.scanner.core.DiscoveryConfig
import com.ead.katalyst.scanner.core.DiscoveryMetadata
import com.ead.katalyst.scanner.core.DiscoveryPredicate
import com.ead.xtory.scanner.core.TypeDiscovery
import com.ead.katalyst.scanner.integration.AutoDiscoveryEngine
import com.ead.katalyst.scanner.scanner.KotlinMethodScanner
import com.ead.katalyst.scanner.scanner.ReflectionsTypeScanner
import com.ead.katalyst.scanner.util.MethodMetadata
import org.koin.core.Koin

/**
 * DSL builder for fluent method discovery configuration and execution.
 *
 * Provides a convenient way to configure and run method discovery on discovered classes
 * with a clear, readable syntax.
 *
 * **Type Parameter T:**
 * - The base type or marker interface for the classes to scan
 *
 * **Usage Examples:**
 * ```kotlin
 * // Discover methods annotated with @RouteHandler in all RouteController classes
 * methodDiscovery<RouteController> {
 *     baseType = RouteController::class.java
 *     scanPackages("com.ead.controllers")
 *     filterMethods { metadata ->
 *         metadata.findAnnotation<RouteHandler>() != null
 *     }
 * }.discoverMethods()
 *
 * // Advanced: Extract type parameters and methods
 * methodDiscovery<Repository<*, *>> {
 *     baseType = Repository::class.java
 *     baseTypeForGenericExtraction = Repository::class.java  // Extract <E, D>
 *     scanPackages("com.ead.repositories")
 *     filterMethods { it.hasMethods() }
 * }.discoverEnhancedMetadata()
 *
 * // With instantiation from Koin
 * val instances = methodDiscovery<RouteController> {
 *     baseType = RouteController::class.java
 *     scanPackages("com.ead.controllers")
 * }.discoverAndInstantiate(koin)
 * ```
 *
 * @param T The base type or marker interface
 */
class MethodDiscoveryBuilder<T> {
    lateinit var baseType: Class<T>
    var baseTypeForGenericExtraction: Class<*>? = null
    private var packages: List<String> = emptyList()
    var predicate: DiscoveryPredicate<T>? = null
    private var excludePackages: List<String> = emptyList()
    var onDiscover: (Class<out T>) -> Unit = {}
    var onError: (Exception) -> Unit = {}
    var methodFilter: (MethodMetadata) -> Boolean = { true }
    var metadataFilter: (DiscoveryMetadata) -> Boolean = { true }

    fun scanPackages(vararg packages: String) {
        this.packages = packages.toList()
    }

    fun excludePackages(vararg packages: String) {
        this.excludePackages = packages.toList()
    }

    fun filterMethods(filter: (MethodMetadata) -> Boolean) {
        this.methodFilter = filter
    }

    fun filterMetadata(filter: (DiscoveryMetadata) -> Boolean) {
        this.metadataFilter = filter
    }

    /**
     * Builds the type discovery scanner.
     */
    private fun buildScanner(): TypeDiscovery<T> {
        require(::baseType.isInitialized) { "baseType must be set" }

        val config = DiscoveryConfig(
            scanPackages = packages,
            predicate = predicate,
            excludePackages = excludePackages,
            onDiscover = onDiscover,
            onError = onError
        )

        return ReflectionsTypeScanner(baseType, config)
    }

    /**
     * Discovers all matching types (without methods).
     */
    fun discover(): Set<Class<out T>> {
        return buildScanner().discover()
    }

    /**
     * Discovers methods in all matching types.
     *
     * **Returns:** List of DiscoveryMetadata containing discovered methods
     *
     * @return List of metadata for classes with methods
     */
    fun discoverMethods(): List<DiscoveryMetadata> {
        val scanner = buildScanner() as? ReflectionsTypeScanner<T>
            ?: throw IllegalStateException("Scanner must be ReflectionsTypeScanner")

        val classes = scanner.discover()
        val methodScanner = KotlinMethodScanner<T>()

        return classes.mapNotNull { clazz ->
            val methods = methodScanner.discoverMethodsInClass(clazz)
                .filter { methodFilter(it) }

            if (methods.isNotEmpty()) {
                DiscoveryMetadata.from(
                    clazz = clazz,
                    methods = methods,
                    baseType = baseTypeForGenericExtraction
                )
            } else {
                null
            }
        }
    }

    /**
     * Discovers methods grouped by their declaring class.
     *
     * **Returns:** Map of class to its discovered methods
     *
     * **Example:**
     * ```kotlin
     * val grouped = methodDiscovery<RouteController> {
     *     baseType = RouteController::class.java
     *     scanPackages("com.ead.controllers")
     * }.discoverMethodsGroupedByClass()
     *
     * grouped.forEach { (clazz, metadata) ->
     *     println("${clazz.simpleName}:")
     *     metadata.methods.forEach { method ->
     *         println("  - ${method.name}")
     *     }
     * }
     * ```
     */
    fun discoverMethodsGroupedByClass(): Map<Class<out T>, DiscoveryMetadata> {
        val scanner = buildScanner() as? ReflectionsTypeScanner<T>
            ?: throw IllegalStateException("Scanner must be ReflectionsTypeScanner")

        val classes = scanner.discover()
        val methodScanner = KotlinMethodScanner<T>()

        return classes.mapNotNull { clazz ->
            val methods = methodScanner.discoverMethodsInClass(clazz)
                .filter { methodFilter(it) }

            if (methods.isNotEmpty()) {
                val metadata = DiscoveryMetadata.from(
                    clazz = clazz,
                    methods = methods,
                    baseType = baseTypeForGenericExtraction
                )
                clazz to metadata
            } else {
                null
            }
        }.toMap()
    }

    /**
     * Discovers methods with enhanced metadata including generic type parameters.
     *
     * **Returns:** Map of class to enhanced metadata with type parameters and methods
     *
     * **Example:**
     * ```kotlin
     * val enhanced = methodDiscovery<Repository<*, *>> {
     *     baseType = Repository::class.java
     *     baseTypeForGenericExtraction = Repository::class.java
     *     scanPackages("com.ead.repositories")
     * }.discoverEnhancedMetadata()
     *
     * enhanced.forEach { (clazz, metadata) ->
     *     println("${clazz.simpleName}:")
     *     metadata.getTypeParameters().forEach { type ->
     *         println("  Type Param: ${type.simpleName}")
     *     }
     * }
     * ```
     */
    fun discoverEnhancedMetadata(): Map<Class<out T>, DiscoveryMetadata> {
        val scanner = buildScanner() as? ReflectionsTypeScanner<T>
            ?: throw IllegalStateException("Scanner must be ReflectionsTypeScanner")

        return scanner.discoverWithEnhancedMetadata(
            baseType = baseTypeForGenericExtraction,
            scanMethods = true,
            methodFilter = { metadata -> metadataFilter(metadata) }
        )
    }

    /**
     * Discovers and instantiates all types from Koin.
     *
     * **Note:** Discovered types must be registered in Koin
     *
     * @param koin The Koin instance for dependency resolution
     * @return List of instantiated service instances
     */
    fun discoverAndInstantiate(koin: Koin): List<T> {
        val discovery = buildScanner()
        val engine = AutoDiscoveryEngine(discovery, koin)
        return engine.discoverAndInstantiate()
    }

    /**
     * Discovers methods in classes (for potential instantiation from Koin).
     *
     * **Note:** Classes can be registered in Koin for dependency injection
     *
     * **Returns:** List of metadata for discovered methods
     *
     * @param koin The Koin instance for potential dependency resolution
     * @return List of discovered method metadata
     */
    fun discoverMethodsAndInstantiate(koin: Koin): List<DiscoveryMetadata> {
        // Simply return discovered methods - actual instantiation
        // is handled when methods are called through the framework
        return discoverMethods()
    }
}

/**
 * Fluent DSL entry point for method discovery configuration.
 *
 * **Usage:**
 * ```kotlin
 * val routes = methodDiscovery<RouteController> {
 *     baseType = RouteController::class.java
 *     scanPackages("com.ead.controllers")
 *     filterMethods { metadata ->
 *         metadata.findAnnotation<RouteHandler>() != null
 *     }
 * }.discoverMethods()
 * ```
 *
 * @param T The base type or marker interface
 * @param builder Lambda to configure the discovery
 * @return Configured MethodDiscoveryBuilder instance
 */
fun <T> methodDiscovery(
    builder: MethodDiscoveryBuilder<T>.() -> Unit
): MethodDiscoveryBuilder<T> {
    return MethodDiscoveryBuilder<T>().apply(builder)
}

/**
 * Convenience function to discover methods with annotation filtering.
 *
 * **Usage:**
 * ```kotlin
 * val routeMethods = discoverMethodsWithAnnotation<RouteController, RouteHandler>(
 *     packages = arrayOf("com.ead.controllers"),
 *     baseType = RouteController::class.java
 * )
 * ```
 *
 * @param T The base type or marker interface
 * @param A The annotation type to filter by
 * @param packages Packages to scan
 * @param baseType The base type class
 * @return List of methods with the specified annotation
 */
inline fun <T, reified A : Annotation> discoverMethodsWithAnnotation(
    vararg packages: String,
    baseType: Class<T>
): List<MethodMetadata> {
    return methodDiscovery<T> {
        this.baseType = baseType
        scanPackages(*packages)
        filterMethods { metadata ->
            metadata.findAnnotation<A>() != null
        }
    }.discoverMethods().flatMap { it.methods }
}
