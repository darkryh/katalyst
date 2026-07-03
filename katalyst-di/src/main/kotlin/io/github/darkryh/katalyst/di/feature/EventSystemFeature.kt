package io.github.darkryh.katalyst.di.feature

import io.github.darkryh.katalyst.di.KatalystFeaturesBuilder
import io.github.darkryh.katalyst.events.EventHandler
import io.github.darkryh.katalyst.events.bus.ApplicationEventBus
import io.github.darkryh.katalyst.events.bus.EventBus
import io.github.darkryh.katalyst.events.bus.EventHandlerRegistry
import io.github.darkryh.katalyst.events.bus.EventTopology
import io.github.darkryh.katalyst.events.bus.GlobalEventHandlerRegistry
import io.github.darkryh.katalyst.events.bus.InMemoryEventHandlerRegistry
import io.github.darkryh.katalyst.events.bus.TransactionAwareEventBus
import io.github.darkryh.katalyst.events.bus.telemetry.TelemetryEventBusInterceptor
import org.slf4j.LoggerFactory

/**
 * Local transactional event system feature.
 *
 * This feature wires only the in-process EventBus module.
 */
class EventSystemFeature : KatalystFeature {
    private val logger = LoggerFactory.getLogger("EventSystemFeature")
    override val id: String = "events"

    override fun provideBeanModules(): List<KatalystBeanModule> {
        logger.info("Loading local event system feature")
        return listOf(
            katalystBeanModule {
                single<ApplicationEventBus> {
                    // Always-on telemetry interceptor: captures the publish firehose (per-type counts,
                    // handler success/failure, dead events) with zero user setup. Observation-only.
                    ApplicationEventBus(interceptors = listOf(TelemetryEventBusInterceptor))
                }
                single<EventBus> { TransactionAwareEventBus(get()) }
                single<EventHandlerRegistry> { InMemoryEventHandlerRegistry() }
                single<EventTopology> { EventTopology(get(), get()) }
            }
        )
    }

    override fun onReady(context: KatalystBeanContext) {
        runCatching {
            val topology = context.get<EventTopology>()
            val registryHandlers = GlobalEventHandlerRegistry.consumeAll()
            val koinHandlers = runCatching { context.getAll<EventHandler<*>>() }
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
fun KatalystFeaturesBuilder.enableEvents(): KatalystFeaturesBuilder =
    feature(EventSystemFeature())

/**
 * Factory helper for direct feature registration via KatalystDIOptions.
 */
fun eventSystemFeature(): EventSystemFeature = EventSystemFeature()
