package io.github.darkryh.katalyst.scanner.core

/**
 * Severity used when a discovery scan returns zero implementations.
 */
enum class EmptyDiscoverySeverity {
    DEBUG,
    INFO,
    WARN,
    ERROR,
    NONE
}

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
 * @param includeSubPackages If `true` (default) sub-packages of [scanPackages] are discovered too.
 *   If `false`, only types declared *directly* in one of [scanPackages] are returned. The flag is a
 *   no-op when [scanPackages] is empty (a whole-classpath scan has no package to anchor to).
 * @param excludePackages Packages whose types are dropped from the discovery result. A type is
 *   dropped when its package *is* an excluded package or is nested under one; matching happens on
 *   package boundaries, so excluding `com.acme.internal` does not drop `com.acme.internalapi`.
 *   Blank entries are ignored.
 * @param onDiscover Callback invoked when a type is discovered. Types removed by
 *   [excludePackages]/[includeSubPackages] never reach this callback.
 * @param onError Callback invoked when an error occurs during discovery
 * @param emptyResultSeverity Log severity when no implementations are discovered
 */
data class DiscoveryConfig<T>(
    val scanPackages: List<String> = emptyList(),
    val predicate: DiscoveryPredicate<T>? = null,
    val includeSubPackages: Boolean = true,
    val excludePackages: List<String> = emptyList(),
    val onDiscover: (Class<out T>) -> Unit = {},
    val onError: (Exception) -> Unit = {},
    val emptyResultSeverity: EmptyDiscoverySeverity = EmptyDiscoverySeverity.WARN
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
        private var emptyResultSeverity: EmptyDiscoverySeverity = EmptyDiscoverySeverity.WARN

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

        fun emptyResultSeverity(severity: EmptyDiscoverySeverity) = apply {
            this.emptyResultSeverity = severity
        }

        fun build(): DiscoveryConfig<T> {
            return DiscoveryConfig(
                scanPackages = scanPackages,
                predicate = predicate,
                includeSubPackages = includeSubPackages,
                excludePackages = excludePackages,
                onDiscover = onDiscover,
                onError = onError,
                emptyResultSeverity = emptyResultSeverity
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
