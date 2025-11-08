package com.ead.katalyst.events.transport

import org.koin.core.module.Module
import org.koin.dsl.module
import org.koin.core.context.GlobalContext
import org.slf4j.LoggerFactory

/**
 * Koin DI module for katalyst-events-transport.
 *
 * Registers serializers, deserializers, routers, and type resolvers.
 *
 * **Default Configuration:**
 * - JsonEventSerializer for JSON serialization
 * - JsonEventDeserializer for JSON deserialization
 * - FallbackEventTypeResolver for type resolution with Class.forName fallback
 * - PrefixedRouter routing to "events.eventtype" destinations
 *
 * **Usage:**
 *
 * ```kotlin
 * startKoin {
 *     modules(
 *         eventTransportModule()
 *     )
 * }
 *
 * // Later in the app:
 * val serializer: EventSerializer = get()
 * val router: EventRouter = get()
 * ```
 */
fun eventTransportModule(): Module = module {
    val logger = LoggerFactory.getLogger("EventTransportModule")

    logger.info("Configuring Event Transport module")

    // Register event type resolver
    single<EventTypeResolver> {
        logger.debug("Creating FallbackEventTypeResolver singleton")
        FallbackEventTypeResolver()
    }

    // Register JSON event serializer
    single<EventSerializer> {
        logger.debug("Creating JsonEventSerializer singleton")
        JsonEventSerializer()
    }

    // Register JSON event deserializer
    single<EventDeserializer> {
        logger.debug("Creating JsonEventDeserializer singleton")
        JsonEventDeserializer(
            typeResolver = get()
        )
    }

    // Register event router
    single<EventRouter> {
        logger.debug("Creating prefixed EventRouter singleton")
        RoutingStrategies.prefixed("events")
    }

    logger.info("Event Transport module configured successfully")
}
