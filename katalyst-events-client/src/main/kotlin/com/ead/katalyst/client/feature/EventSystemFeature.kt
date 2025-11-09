package com.ead.katalyst.client.feature

import com.ead.katalyst.client.eventsClientModule
import com.ead.katalyst.di.KatalystApplicationBuilder
import com.ead.katalyst.di.feature.KatalystFeature
import com.ead.katalyst.events.config.EventConfiguration
import com.ead.katalyst.events.EventHandler
import com.ead.katalyst.events.bus.EventTopology
import com.ead.katalyst.events.bus.GlobalEventHandlerRegistry
import com.ead.katalyst.events.bus.eventBusModule
import com.ead.katalyst.events.transport.eventTransportModule
import org.koin.core.Koin
import org.koin.core.module.Module
import org.slf4j.LoggerFactory

/**
 * Public feature wrapper that wires the Katalyst event modules according to [EventConfiguration].
 */
class EventSystemFeature(
    private val configuration: EventConfiguration
) : KatalystFeature {
    private val logger = LoggerFactory.getLogger("EventSystemFeature")
    override val id: String = "events"

    override fun provideModules(): List<Module> {
        logger.info(
            "Loading event system feature (bus={}, transport={}, client={})",
            configuration.enableEventBus,
            configuration.enableTransport,
            configuration.enableClient
        )
        val modules = mutableListOf<Module>()

        if (configuration.enableEventBus) {
            modules += eventBusModule()
        }
        if (configuration.enableTransport) {
            modules += eventTransportModule()
        }
        if (configuration.enableClient) {
            modules += eventsClientModule()
        }

        return modules
    }

    override fun onKoinReady(koin: Koin) {
        runCatching {
            val topology = koin.get<EventTopology>()
            val registryHandlers = GlobalEventHandlerRegistry.consumeAll()
            val koinHandlers = runCatching { koin.getAll<EventHandler<*>>() }
                .getOrElse { emptyList() }

            topology.registerHandlers(registryHandlers + koinHandlers)
            logger.info(
                "Registered {} event handler(s) with topology",
                registryHandlers.size + koinHandlers.size
            )
        }.onFailure { error ->
            logger.warn("Unable to register event handlers: {}", error.message)
            logger.debug("Full event handler registration error", error)
        }
    }
}

/**
 * Enables the Katalyst event system (bus, transport, client) using the provided configuration.
 */
fun KatalystApplicationBuilder.enableEvents(
    configure: EventConfiguration.() -> Unit = {}
): KatalystApplicationBuilder {
    val configuration = EventConfiguration().apply(configure)
    return feature(EventSystemFeature(configuration))
}

/**
 * Factory helper for direct feature registration via [com.ead.katalyst.di.config.KatalystDIOptions].
 */
fun eventSystemFeature(
    configure: EventConfiguration.() -> Unit = {}
): EventSystemFeature = EventSystemFeature(EventConfiguration().apply(configure))
