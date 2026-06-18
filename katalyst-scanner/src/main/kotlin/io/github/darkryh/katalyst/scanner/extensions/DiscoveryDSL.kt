package io.github.darkryh.katalyst.scanner.extensions

import io.github.darkryh.katalyst.core.di.KatalystContainer
import io.github.darkryh.katalyst.scanner.core.DiscoveryConfig
import io.github.darkryh.katalyst.scanner.core.DiscoveryPredicate
import io.github.darkryh.katalyst.scanner.core.TypeDiscovery
import io.github.darkryh.katalyst.scanner.integration.AutoDiscoveryEngine
import io.github.darkryh.katalyst.scanner.integration.ContainerDiscoveryRegistry
import io.github.darkryh.katalyst.scanner.scanner.ReflectionsTypeScanner

/**
 * DSL for fluent discovery configuration and execution.
 *
 * Provides a convenient way to configure and run type discovery with a clear, readable syntax.
 *
 * **Usage Examples:**
 * ```kotlin
 * // Basic discovery
 * discovery<Service> {
 *     baseType = Service::class.java
 *     scanPackages("com.ead.xtory")
 * }.discover()
 *
 * // With predicate
 * discovery<Service> {
 *     baseType = Service::class.java
 *     scanPackages("com.ead.xtory", "com.external")
 *     predicate = isNotTestClass()
 * }.discoverAndInstantiate(container)
 *
 * // Full configuration
 * discovery<Service> {
 *     baseType = Service::class.java
 *     scanPackages("com.ead.xtory")
 *     predicate = isConcrete().and(isNotSynthetic())
 *     onDiscover { clazz -> println("Found: ${clazz.simpleName}") }
 *     onError { e -> logger.error("Discovery error", e) }
 * }
 * ```
 *
 * @param T The base type or marker interface
 */
class DiscoveryBuilder<T : Any> {
    lateinit var baseType: Class<T>
    private var packages: List<String> = emptyList()
    var predicate: DiscoveryPredicate<T>? = null
    private var excludePackages: List<String> = emptyList()
    var onDiscover: (Class<out T>) -> Unit = {}
    var onError: (Exception) -> Unit = {}

    fun scanPackages(vararg packages: String) {
        this.packages = packages.toList()
    }

    fun excludePackages(vararg packages: String) {
        this.excludePackages = packages.toList()
    }

    fun build(): TypeDiscovery<T> {
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
     * Discovers all matching types.
     */
    fun discover(): Set<Class<out T>> {
        return build().discover()
    }

    /**
     * Discovers and instantiates all types from the supplied container.
     */
    fun discoverAndInstantiate(container: KatalystContainer): List<T> {
        val discovery = build()
        val engine = AutoDiscoveryEngine(discovery, container)
        return engine.discoverAndInstantiate()
    }

    /**
     * Creates a container-backed discovery registry.
     */
    fun createRegistry(container: KatalystContainer): ContainerDiscoveryRegistry<T> {
        require(::baseType.isInitialized) { "baseType must be set" }
        return ContainerDiscoveryRegistry(baseType, container)
    }
}

/**
 * Fluent DSL entry point for discovery configuration.
 *
 * **Usage:**
 * ```kotlin
 * val services = discovery<Service> {
 *     baseType = Service::class.java
 *     scanPackages("com.ead.xtory")
 * }.discover()
 * ```
 *
 * @param T The base type or marker interface
 * @param builder Lambda to configure the discovery
 * @return Configured TypeDiscovery instance
 */
fun <T : Any> discovery(builder: DiscoveryBuilder<T>.() -> Unit): DiscoveryBuilder<T> {
    return DiscoveryBuilder<T>().apply(builder)
}
