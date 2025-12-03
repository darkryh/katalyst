package io.github.darkryh.katalyst.scanner.core

/**
 * Configuration for type discovery.
 *
 * Encapsulates all settings needed to perform type discovery including:
 * - Which packages to scan
 * - What predicate to use for filtering
 * - Callbacks for discovery events
 *
 * **Usage:**
 * ```kotlin
 * val config = DiscoveryConfig<Service>(
 *     scanPackages = listOf("com.ead.xtory"),
 *     predicate = DiscoveryPredicate<Service> { clazz ->
 *         clazz.interfaces.contains(Service::class.java)
 *     }
 * )
 * ```
 *
 * @param T The base type or marker interface being discovered
 * @param scanPackages Packages to scan. Empty list = scan entire classpath
 * @param predicate Optional predicate for filtering discovered classes
 * @param includeSubPackages If true, also scan sub-packages of specified packages
 * @param excludePackages Packages to exclude from scanning
 * @param onDiscover Callback invoked when a type is discovered
 * @param onError Callback invoked when an error occurs during discovery
 */
data class DiscoveryConfig<T>(
    val scanPackages: List<String> = emptyList(),
    val predicate: DiscoveryPredicate<T>? = null,
    val includeSubPackages: Boolean = true,
    val excludePackages: List<String> = emptyList(),
    val onDiscover: (Class<out T>) -> Unit = {},
    val onError: (Exception) -> Unit = {}
) {
    /**
     * Builder for fluent configuration creation.
     */
    class Builder<T> {
        private var scanPackages: List<String> = emptyList()
        private var predicate: DiscoveryPredicate<T>? = null
        private var includeSubPackages: Boolean = true
        private var excludePackages: List<String> = emptyList()
        private var onDiscover: (Class<out T>) -> Unit = {}
        private var onError: (Exception) -> Unit = {}

        fun scanPackages(vararg packages: String) = apply {
            this.scanPackages = packages.toList()
        }

        fun scanPackages(packages: List<String>) = apply {
            this.scanPackages = packages
        }

        fun predicate(predicate: DiscoveryPredicate<T>) = apply {
            this.predicate = predicate
        }

        fun includeSubPackages(include: Boolean) = apply {
            this.includeSubPackages = include
        }

        fun excludePackages(vararg packages: String) = apply {
            this.excludePackages = packages.toList()
        }

        fun onDiscover(callback: (Class<out T>) -> Unit) = apply {
            this.onDiscover = callback
        }

        fun onError(callback: (Exception) -> Unit) = apply {
            this.onError = callback
        }

        fun build(): DiscoveryConfig<T> {
            return DiscoveryConfig(
                scanPackages = scanPackages,
                predicate = predicate,
                includeSubPackages = includeSubPackages,
                excludePackages = excludePackages,
                onDiscover = onDiscover,
                onError = onError
            )
        }
    }

    companion object {
        /**
         * Creates a builder for fluent configuration.
         */
        fun <T> builder(): Builder<T> = Builder()
    }
}
