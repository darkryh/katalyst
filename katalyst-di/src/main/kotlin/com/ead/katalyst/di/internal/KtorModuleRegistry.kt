package com.ead.katalyst.di.internal

import com.ead.katalyst.ktor.KtorModule
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Registry for auto-discovered Ktor modules.
 *
 * This thread-safe registry collects [KtorModule] instances during component scanning
 * and provides them for installation into the Ktor application pipeline after DI bootstrap.
 *
 * **Deduplication Strategy:**
 * - Route function modules (marked with [RouteModuleMarker]) are compared by identity
 * - Regular [KtorModule] implementations are deduplicated by class type
 *
 * **Thread Safety:**
 * Uses [CopyOnWriteArrayList] for concurrent access during registration.
 */
object KtorModuleRegistry {
    private val modules = CopyOnWriteArrayList<KtorModule>()

    /**
     * Registers a Ktor module for later installation.
     *
     * Prevents duplicate registration based on module type:
     * - Route modules: deduplicates by object identity (===)
     * - Regular modules: deduplicates by class type (::class)
     *
     * @param module The Ktor module to register
     */
    fun register(module: KtorModule) {
        val alreadyRegistered = when (module) {
            is RouteModuleMarker -> modules.any { it === module }
            else -> modules.any { it::class == module::class }
        }

        if (!alreadyRegistered) {
            modules += module
        }
    }

    /**
     * Retrieves and clears all registered modules.
     *
     * This method should be called once during application startup to
     * install all discovered modules into the Ktor pipeline.
     *
     * @return Immutable snapshot of all registered modules
     */
    fun consume(): List<KtorModule> {
        val snapshot = modules.toList()
        modules.clear()
        return snapshot
    }
}
