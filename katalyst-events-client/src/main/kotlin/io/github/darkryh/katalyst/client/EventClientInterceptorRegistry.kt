package io.github.darkryh.katalyst.client

import java.util.concurrent.CopyOnWriteArrayList

/**
 * Registry that keeps track of [EventClientInterceptor] instances discovered across modules.
 *
 * The registry mirrors the EventBus handler registry model so messaging/broker modules can
 * contribute interceptors without the application wiring them manually.
 */
interface EventClientInterceptorRegistry {
    /**
     * Register an interceptor if it hasn't been added yet.
     */
    fun register(interceptor: EventClientInterceptor)

    /**
     * Register all interceptors in bulk.
     */
    fun registerAll(interceptors: Collection<EventClientInterceptor>) {
        interceptors.forEach(::register)
    }

    /**
     * Snapshot of the currently registered interceptors.
     */
    fun getAll(): List<EventClientInterceptor>

    /**
     * Clear the registry (primarily for tests).
     */
    fun clear()
}

/**
 * Default in-memory implementation backed by [CopyOnWriteArrayList] for thread safety.
 */
class DefaultEventClientInterceptorRegistry : EventClientInterceptorRegistry {
    private val interceptors = CopyOnWriteArrayList<EventClientInterceptor>()

    override fun register(interceptor: EventClientInterceptor) {
        if (interceptors.contains(interceptor)) return
        interceptors.add(interceptor)
    }

    override fun getAll(): List<EventClientInterceptor> = interceptors.toList()

    override fun clear() {
        interceptors.clear()
    }
}

/**
 * Global staging registry used before Koin is fully initialized.
 *
 * Broker modules can drop their interceptors here and the event feature will
 * drain the list once the DI context is ready.
 */
object GlobalEventClientInterceptorRegistry {
    private val interceptors = CopyOnWriteArrayList<EventClientInterceptor>()

    fun register(interceptor: EventClientInterceptor) {
        interceptors.add(interceptor)
    }

    fun registerAll(list: Collection<EventClientInterceptor>) {
        interceptors.addAll(list)
    }

    /**
     * Returns all staged interceptors and clears the registry.
     */
    fun consumeAll(): List<EventClientInterceptor> {
        val snapshot = interceptors.toList()
        interceptors.clear()
        return snapshot
    }
}
