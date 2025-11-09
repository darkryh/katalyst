package com.ead.katalyst.events.transport.routing

import com.ead.katalyst.events.DomainEvent
import com.ead.katalyst.messaging.Destination
import com.ead.katalyst.messaging.DestinationType

/**
 * Pre-built routing strategies for common use cases.
 *
 * Provides factory methods for creating EventRouter implementations
 * without needing to write custom code.
 *
 * **Usage:**
 *
 * ```kotlin
 * // All events to same destination
 * val router1 = RoutingStrategies.single("all-events")
 *
 * // Events to destinations like "events.user.created"
 * val router2 = RoutingStrategies.prefixed("events")
 *
 * // Events grouped by package: "events.user"
 * val router3 = RoutingStrategies.packageBased()
 *
 * // Custom routing logic
 * val router4 = RoutingStrategies.custom { event ->
 *     when (event) {
 *         is UserEvent -> Destination("user-events", TOPIC)
 *         is OrderEvent -> Destination("order-events", TOPIC)
 *         else -> Destination("general-events", TOPIC)
 *     }
 * }
 * ```
 */
object RoutingStrategies {
    /**
     * Single destination: All events to one destination.
     *
     * **Example:**
     * UserCreatedEvent → "all-events"
     * OrderCreatedEvent → "all-events"
     *
     * @param destinationName The destination name
     * @param destinationType Queue, Topic, or Stream (default: TOPIC)
     * @return EventRouter using single destination
     */
    fun single(
        destinationName: String,
        destinationType: DestinationType = DestinationType.TOPIC
    ): EventRouter = SingleDestinationRouter(destinationName, destinationType)

    /**
     * Prefixed: All events as "prefix.eventtype".
     *
     * **Example:**
     * Prefix: "events"
     * UserCreatedEvent → "events.user.created"
     * OrderDeletedEvent → "events.order.deleted"
     *
     * @param prefix Destination prefix (default: "events")
     * @param destinationType Queue, Topic, or Stream (default: TOPIC)
     * @param separator Character between parts (default: ".")
     * @return EventRouter using prefixed naming
     */
    fun prefixed(
        prefix: String = "events",
        destinationType: DestinationType = DestinationType.TOPIC,
        separator: String = "."
    ): EventRouter = PrefixedRouter(prefix, destinationType, separator)

    /**
     * Package-based: Group events by package name.
     *
     * **Example:**
     * com.example.user.UserCreatedEvent → "events.user"
     * com.example.order.OrderDeletedEvent → "events.order"
     *
     * @param prefix Destination prefix (default: "events")
     * @param destinationType Queue, Topic, or Stream (default: TOPIC)
     * @return EventRouter using package-based grouping
     */
    fun packageBased(
        prefix: String = "events",
        destinationType: DestinationType = DestinationType.TOPIC
    ): EventRouter = PackageBasedRouter(prefix, destinationType)

    /**
     * Custom: Use a lambda for routing logic.
     *
     * **Example:**
     * ```kotlin
     * RoutingStrategies.custom { event ->
     *     when (event) {
     *         is UserEvent -> Destination("user.events", TOPIC)
     *         is OrderEvent -> Destination("order.events", TOPIC)
     *         else -> Destination("other.events", TOPIC)
     *     }
     * }
     * ```
     *
     * @param resolver Lambda that returns destination for an event
     * @return EventRouter using custom resolver
     */
    fun custom(
        resolver: (DomainEvent) -> Destination
    ): EventRouter = CustomRouter(resolver)
}

/**
 * All events to a single destination.
 *
 * @param destinationName The destination
 * @param destinationType Queue, Topic, or Stream
 */
private class SingleDestinationRouter(
    private val destinationName: String,
    private val destinationType: DestinationType
) : EventRouter {
    override fun resolve(event: DomainEvent): Destination =
        Destination(destinationName, destinationType)
}

/**
 * Destinations like "prefix.eventtype".
 *
 * @param prefix Destination prefix
 * @param destinationType Queue, Topic, or Stream
 * @param separator Part separator
 */
private class PrefixedRouter(
    private val prefix: String,
    private val destinationType: DestinationType,
    private val separator: String
) : EventRouter {
    override fun resolve(event: DomainEvent): Destination {
        val eventType = event.eventType().lowercase()
        val name = "$prefix$separator$eventType"
        return Destination(name, destinationType)
    }
}

/**
 * Group events by package: "prefix.package".
 *
 * @param prefix Destination prefix
 * @param destinationType Queue, Topic, or Stream
 */
private class PackageBasedRouter(
    private val prefix: String,
    private val destinationType: DestinationType
) : EventRouter {
    override fun resolve(event: DomainEvent): Destination {
        val eventClass = event::class
        val packageName = eventClass.java.packageName
        val lastPackage = packageName.substringAfterLast('.')
        val name = "$prefix.$lastPackage"
        return Destination(name, destinationType)
    }
}

/**
 * Custom routing logic.
 *
 * @param resolver Function that returns destination for event
 */
private class CustomRouter(
    private val resolver: (DomainEvent) -> Destination
) : EventRouter {
    override fun resolve(event: DomainEvent): Destination = resolver(event)
}
