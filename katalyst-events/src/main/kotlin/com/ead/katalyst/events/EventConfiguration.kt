package com.ead.katalyst.events

/**
 * Configuration for the event system.
 *
 * Controls which event modules are loaded and how they're configured.
 *
 * **Usage:**
 *
 * ```kotlin
 * val config = EventConfiguration()
 *     .withBus(enabled = true)
 *     .withTransport(enabled = true)
 *     .withClient(enabled = true)
 *
 * val koin = bootstrapKatalystDI(
 *     databaseConfig = ...,
 *     eventConfiguration = config
 * )
 * ```
 *
 * @property enableEventBus Whether to load the EventBus module
 * @property enableTransport Whether to load the Transport module (serialization, routing)
 * @property enableClient Whether to load the Client module (public API)
 * @property enableInterceptors Whether to enable interceptor support
 */
data class EventConfiguration(
    val enableEventBus: Boolean = true,
    val enableTransport: Boolean = true,
    val enableClient: Boolean = true,
    val enableInterceptors: Boolean = true
) {
    /**
     * Enable or disable the EventBus module.
     */
    fun withBus(enabled: Boolean): EventConfiguration =
        copy(enableEventBus = enabled)

    /**
     * Enable or disable the Transport module.
     */
    fun withTransport(enabled: Boolean): EventConfiguration =
        copy(enableTransport = enabled)

    /**
     * Enable or disable the Client module.
     */
    fun withClient(enabled: Boolean): EventConfiguration =
        copy(enableClient = enabled)

    /**
     * Enable or disable interceptor support.
     */
    fun withInterceptors(enabled: Boolean): EventConfiguration =
        copy(enableInterceptors = enabled)

    /**
     * Create a copy with all modules enabled (default).
     */
    fun fullStack(): EventConfiguration = EventConfiguration(
        enableEventBus = true,
        enableTransport = true,
        enableClient = true,
        enableInterceptors = true
    )

    /**
     * Create a copy with only bus enabled (local events only).
     */
    fun busOnly(): EventConfiguration = EventConfiguration(
        enableEventBus = true,
        enableTransport = false,
        enableClient = false,
        enableInterceptors = true
    )

    /**
     * Create a copy with bus and client (for publishing).
     */
    fun withClientOnly(): EventConfiguration = EventConfiguration(
        enableEventBus = true,
        enableTransport = false,
        enableClient = true,
        enableInterceptors = true
    )

    /**
     * Convert to module options for Koin.
     */
    fun toModuleOptions(): EventModuleOptions = EventModuleOptions(
        enableEventBus = enableEventBus,
        enableTransport = enableTransport,
        enableClient = enableClient,
        enableInterceptors = enableInterceptors
    )
}

/**
 * Options for loading event modules.
 *
 * Internal use - use EventConfiguration instead.
 */
data class EventModuleOptions(
    val enableEventBus: Boolean = true,
    val enableTransport: Boolean = true,
    val enableClient: Boolean = true,
    val enableInterceptors: Boolean = true
)
