package io.github.darkryh.katalyst.scanner.core

import io.github.darkryh.katalyst.scanner.core.DiscoveryMetadata

/**
 * Generic interface for discovering types matching specific criteria.
 *
 * This is the core abstraction that enables reusable type discovery across the application.
 *
 * **Type Parameter T:**
 * - `T` is the base type or marker interface that discovered classes must implement/extend
 * - Examples: `Service`, `RepositoryMarker`, `EventListener<*>`, etc.
 *
 * **Usage Examples:**
 * ```kotlin
 * // Discover all Service implementations
 * val scheduledServiceDiscovery: TypeDiscovery<Service> = ...
 * val services = scheduledServiceDiscovery.discover()
 *
 * // Discover all repositories
 * val repositoryDiscovery: TypeDiscovery<RepositoryMarker> = ...
 * val repos = repositoryDiscovery.discover()
 * ```
 *
 * @param T The base type or marker interface that discovered types must be assignable to
 */
interface TypeDiscovery<T> {
    /**
     * Discovers all types that match the discovery criteria.
     *
     * @return Set of Class objects that match the discovery predicate.
     *         Empty set if no types are found or an error occurs.
     */
    fun discover(): Set<Class<out T>>

    /**
     * Discovers all types with additional metadata about each discovery.
     *
     * Metadata can include information about:
     * - Construction requirements
     * - Dependencies
     * - Annotations
     * - Package location
     * - etc.
     *
     * @return Map of discovered classes to their metadata.
     *         Empty map if no types are found.
     */
    fun discoverWithMetadata(): Map<Class<out T>, DiscoveryMetadata>
}
