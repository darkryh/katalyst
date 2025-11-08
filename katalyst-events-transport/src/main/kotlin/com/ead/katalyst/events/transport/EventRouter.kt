package com.ead.katalyst.events.transport

import com.ead.katalyst.events.DomainEvent
import com.ead.katalyst.messaging.Destination
import com.ead.katalyst.messaging.DestinationType

/**
 * Determines the destination for an event.
 *
 * Routes events to appropriate message broker destinations based on
 * event type, content, or other criteria.
 *
 * **Responsibilities:**
 * - Map event types to destination names
 * - Support different destination types (QUEUE, TOPIC, STREAM)
 * - Optionally return routing configuration
 *
 * **Implementations:**
 * - Prefixed: All events → "events.eventtype"
 * - PackageBased: Group by package name
 * - Custom: Application-specific routing logic
 *
 * **Usage:**
 *
 * ```kotlin
 * val router = RoutingStrategies.prefixed("events")
 *
 * val event = UserCreatedEvent(...)
 * val destination = router.resolve(event)
 * // Returns: Destination("events.user.created", TOPIC)
 * ```
 */
interface EventRouter {
    /**
     * Resolve the destination for an event.
     *
     * @param event The event to route
     * @return Destination where event should be published
     * @throws EventRoutingException if routing fails
     */
    fun resolve(event: DomainEvent): Destination

    /**
     * Get optional routing configuration for the event.
     *
     * **Default:** null (use default routing)
     *
     * Can specify:
     * - Routing key (for DIRECT/TOPIC routing)
     * - Priority
     * - TTL
     *
     * @param event The event being routed
     * @return RoutingConfig or null to use defaults
     */
    fun getRouting(event: DomainEvent): com.ead.katalyst.messaging.RoutingConfig? = null
}
