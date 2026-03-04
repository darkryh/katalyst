package io.github.darkryh.katalyst.di.feature

import io.github.darkryh.katalyst.di.KatalystApplicationBuilder
import io.github.darkryh.katalyst.events.EventHandler
import io.github.darkryh.katalyst.events.bus.EventTopology
import io.github.darkryh.katalyst.events.bus.GlobalEventHandlerRegistry
import io.github.darkryh.katalyst.events.bus.eventBusModule
import org.koin.core.Koin
import org.koin.core.module.Module
import org.slf4j.LoggerFactory

/**
 * Local transactional event system feature.
 *
 * This feature wires only the in-process EventBus module.
 */
class EventSystemFeature : KatalystFeature {
    private val logger = LoggerFactory.getLogger("EventSystemFeature")
    override val id: String = "events"

    override fun provideModules(): List<Module> {
        logger.info("Loading local event system feature")
        return listOf(eventBusModule())
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
 * Enables local EventBus support.
 */
fun KatalystApplicationBuilder.enableEvents(): KatalystApplicationBuilder =
    feature(EventSystemFeature())

/**
 * Factory helper for direct feature registration via KatalystDIOptions.
 */
fun eventSystemFeature(): EventSystemFeature = EventSystemFeature()
