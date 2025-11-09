package com.ead.katalyst.events.transport

import com.ead.katalyst.events.DomainEvent
import com.ead.katalyst.events.transport.exception.EventDeserializationException
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass

/**
 * Resolves event type strings back to KClass objects.
 *
 * Used during deserialization to determine which class to instantiate
 * based on the event type string from message headers.
 *
 * **Typical Type Formats:**
 * - Fully qualified class name: "com.example.UserCreatedEvent"
 * - Simple class name: "UserCreatedEvent"
 * - Custom names: "user.created"
 *
 * **Usage:**
 *
 * ```kotlin
 * val resolver = EventTypeResolver()
 * resolver.register("user.created", UserCreatedEvent::class)
 * resolver.register("user.deleted", UserDeletedEvent::class)
 *
 * val kclass = resolver.resolve("user.created")
 * // Returns: UserCreatedEvent::class
 * ```
 *
 * **Automatic Registration:**
 * Subclasses can override to auto-discover and register types
 * from classpath scanning during application startup.
 */
interface EventTypeResolver {
    /**
     * Resolve an event type string to its KClass.
     *
     * @param eventType Type identifier (e.g., "user.created")
     * @return KClass of the event
     * @throws EventDeserializationException if type is unknown
     */
    fun resolve(eventType: String): KClass<out DomainEvent>

    /**
     * Register a known event type.
     *
     * @param eventType Type identifier
     * @param kclass The event class for this type
     */
    fun register(eventType: String, kclass: KClass<out DomainEvent>)

    /**
     * Check if a type is registered.
     *
     * @param eventType Type identifier
     * @return True if type is known
     */
    fun isKnown(eventType: String): Boolean
}

/**
 * In-memory event type resolver with registration.
 *
 * Thread-safe registry of event type strings to KClass mappings.
 *
 * **Usage:**
 *
 * ```kotlin
 * val resolver = InMemoryEventTypeResolver()
 * resolver.register("user.created", UserCreatedEvent::class)
 * resolver.register("user.deleted", UserDeletedEvent::class)
 *
 * val kclass = resolver.resolve("user.created")
 * ```
 */
class InMemoryEventTypeResolver : EventTypeResolver {
    private val typeMap = ConcurrentHashMap<String, KClass<out DomainEvent>>()

    override fun resolve(eventType: String): KClass<out DomainEvent> {
        return typeMap[eventType]
            ?: throw EventDeserializationException(
                "Unknown event type: $eventType",
                targetType = eventType
            )
    }

    override fun register(eventType: String, kclass: KClass<out DomainEvent>) {
        typeMap[eventType] = kclass
    }

    override fun isKnown(eventType: String): Boolean = typeMap.containsKey(eventType)

    /**
     * Get all registered types.
     *
     * @return Map of event type string to KClass
     */
    fun getAllTypes(): Map<String, KClass<out DomainEvent>> = typeMap.toMap()

    /**
     * Clear all registered types.
     */
    fun clear() {
        typeMap.clear()
    }

    /**
     * Get count of registered types.
     *
     * @return Number of registered types
     */
    fun size(): Int = typeMap.size
}

/**
 * Event type resolver that also tries fully qualified class names.
 *
 * First checks registered types, then attempts to load class by name.
 *
 * **Usage:**
 *
 * ```kotlin
 * val resolver = FallbackEventTypeResolver()
 * resolver.register("user.created", UserCreatedEvent::class)
 *
 * // Resolves registered name
 * val type1 = resolver.resolve("user.created")  // Found in registry
 *
 * // Also resolves by FQCN if not registered
 * val type2 = resolver.resolve("com.example.UserDeletedEvent")  // Loaded by Class.forName
 * ```
 */
class FallbackEventTypeResolver : EventTypeResolver {
    private val registered = InMemoryEventTypeResolver()

    override fun resolve(eventType: String): KClass<out DomainEvent> {
        // First try registered types
        if (registered.isKnown(eventType)) {
            return registered.resolve(eventType)
        }

        // Try to load by fully qualified class name
        return try {
            @Suppress("UNCHECKED_CAST")
            Class.forName(eventType).kotlin as KClass<out DomainEvent>
        } catch (e: ClassNotFoundException) {
            throw EventDeserializationException(
                "Unknown event type: $eventType (not registered and not found by class name)",
                targetType = eventType,
                cause = e
            )
        } catch (e: Exception) {
            throw EventDeserializationException(
                "Failed to resolve event type $eventType: ${e.message}",
                targetType = eventType,
                cause = e
            )
        }
    }

    override fun register(eventType: String, kclass: KClass<out DomainEvent>) {
        registered.register(eventType, kclass)
    }

    override fun isKnown(eventType: String): Boolean {
        // Check if registered
        if (registered.isKnown(eventType)) {
            return true
        }

        // Try to load by class name
        return try {
            Class.forName(eventType)
            true
        } catch (e: Exception) {
            false
        }
    }
}
