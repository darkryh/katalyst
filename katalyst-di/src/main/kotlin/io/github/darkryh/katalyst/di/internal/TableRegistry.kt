package io.github.darkryh.katalyst.di.internal

import io.github.darkryh.katalyst.di.registry.RegistryManager
import io.github.darkryh.katalyst.di.registry.ResettableRegistry
import org.jetbrains.exposed.v1.core.Table

/**
 * Thread-safe registry for tracking discovered database tables.
 *
 * Populated during Phase 3 (Component Discovery) by AutoBindingRegistrar
 * when it discovers Exposed Table implementations.
 *
 * Used by StartupValidator during Phase 6 to validate that all discovered
 * tables exist in the database schema before allowing scheduler initialization.
 *
 * **Why this exists:**
 * Koin's `getAll<Any>()` doesn't reliably return singleton instances that
 * were registered during dynamic module loading. This registry provides a
 * reliable way to track tables as they're discovered.
 *
 * **Test Isolation:**
 * Implements [ResettableRegistry] and registers with [RegistryManager] for centralized reset.
 */
internal object TableRegistry : ResettableRegistry {
    init { RegistryManager.register(this) }

    private val tables = mutableListOf<Table>()
    private val lock = Any()

    /**
     * Register a discovered table.
     *
     * Called by AutoBindingRegistrar during Phase 3 component discovery.
     *
     * @param table The discovered Exposed table instance
     */
    fun register(table: Table) {
        synchronized(lock) {
            tables.add(table)
        }
    }

    /**
     * Get all registered tables.
     *
     * @return List of all discovered tables (immutable copy)
     */
    fun getAll(): List<Table> {
        synchronized(lock) {
            return tables.toList()
        }
    }

    /**
     * Clear all registered tables.
     *
     * Used for testing and reinitialization scenarios.
     */
    fun clear() {
        synchronized(lock) {
            tables.clear()
        }
    }

    /**
     * Resets the registry to its initial empty state.
     *
     * Implements [ResettableRegistry.reset] for test isolation.
     */
    override fun reset() {
        synchronized(lock) {
            tables.clear()
        }
    }

    /**
     * Get count of registered tables.
     */
    fun size(): Int {
        synchronized(lock) {
            return tables.size
        }
    }
}
