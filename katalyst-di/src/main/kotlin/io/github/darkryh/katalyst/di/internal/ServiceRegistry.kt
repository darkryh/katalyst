package io.github.darkryh.katalyst.di.internal

import io.github.darkryh.katalyst.core.component.Service
import org.slf4j.LoggerFactory

/**
 * Registry that tracks all discovered Service instances.
 *
 * This registry is populated by AutoBindingRegistrar as services are discovered
 * and registered. It provides a simple way for other components (like SchedulerMethodInvoker)
 * to access all discovered services without relying on Koin's type system.
 *
 * **Why a separate registry?**
 *
 * Services in Katalyst are registered under their concrete type (e.g., AuthenticationService),
 * not under the Service interface. This is intentional to avoid unnecessary indirection.
 * However, components like SchedulerMethodInvoker need to iterate over all services
 * to discover scheduler methods.
 *
 * Rather than accessing Koin's internal registry or trying to work around type registration,
 * we maintain a simple, explicit list of discovered services.
 */
object ServiceRegistry {
    private val logger = LoggerFactory.getLogger("ServiceRegistry")
    private val services = mutableListOf<Service>()

    /**
     * Register a discovered service instance.
     *
     * Called by AutoBindingRegistrar during component discovery.
     *
     * @param service The service instance to register
     */
    fun register(service: Service) {
        services.add(service)
        logger.debug("Registered service: {}", service::class.simpleName)
    }

    /**
     * Get all registered services.
     *
     * @return List of all discovered services
     */
    fun getAll(): List<Service> = services.toList()

    /**
     * Clear all registered services.
     *
     * Useful for testing or reinitialization scenarios.
     */
    fun clear() {
        services.clear()
    }

    /**
     * Get count of registered services.
     */
    fun count(): Int = services.size
}
