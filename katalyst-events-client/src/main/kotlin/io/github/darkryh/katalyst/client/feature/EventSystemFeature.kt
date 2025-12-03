package io.github.darkryh.katalyst.client.feature

import io.github.darkryh.katalyst.client.EventClientInterceptorRegistry
import io.github.darkryh.katalyst.client.GlobalEventClientInterceptorRegistry
import io.github.darkryh.katalyst.client.eventsClientModule
import io.github.darkryh.katalyst.di.KatalystApplicationBuilder
import io.github.darkryh.katalyst.di.feature.KatalystFeature
import io.github.darkryh.katalyst.events.config.EventConfiguration
import io.github.darkryh.katalyst.events.EventHandler
import io.github.darkryh.katalyst.events.bus.EventTopology
import io.github.darkryh.katalyst.events.bus.GlobalEventHandlerRegistry
import io.github.darkryh.katalyst.events.bus.eventBusModule
import io.github.darkryh.katalyst.events.transport.eventTransportModule
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

        runCatching {
            val registry = koin.get<EventClientInterceptorRegistry>()
            val stagedInterceptors = GlobalEventClientInterceptorRegistry.consumeAll()
            if (stagedInterceptors.isNotEmpty()) {
                registry.registerAll(stagedInterceptors)
                logger.info(
                    "Registered {} event client interceptor(s)",
                    stagedInterceptors.size
                )
            } else {
                logger.debug("No staged event client interceptors to register")
            }
        }.onFailure { error ->
            logger.debug("Event client interceptor registry not available: {}", error.message)
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
 * Factory helper for direct feature registration via [io.github.darkryh.katalyst.di.config.KatalystDIOptions].
 */
fun eventSystemFeature(
    configure: EventConfiguration.() -> Unit = {}
): EventSystemFeature = EventSystemFeature(EventConfiguration().apply(configure))
