package com.ead.katalyst.events

import com.ead.katalyst.events.EventMessagingPublisher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.reflect.KClass

/**
 * Primary interface for publishing events.
 */
interface EventBus {
    suspend fun publish(event: DomainEvent)
    fun register(handler: EventHandler<out DomainEvent>)
}

/**
 * In-memory implementation used for both application-level events and as the
 * fan-out point for external bridges.
 */
class ApplicationEventBus(
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val messagingPublisher: EventMessagingPublisher? = null
) : EventBus {

    private val logger = LoggerFactory.getLogger(ApplicationEventBus::class.java)
    private val listeners = ConcurrentHashMap<KClass<out DomainEvent>, CopyOnWriteArrayList<suspend (DomainEvent) -> Unit>>()

    override fun register(handler: EventHandler<out DomainEvent>) {
        val concreteTypes = handler.eventType.resolveConcreteTypes()
        concreteTypes.forEach { type ->
            val group = listeners.getOrPut(type) { CopyOnWriteArrayList() }
            group += { event ->
                @Suppress("UNCHECKED_CAST")
                (handler as EventHandler<DomainEvent>).handle(event)
            }
            logger.debug(
                "Registered event handler {} for {}",
                handler::class.qualifiedName,
                type.qualifiedName
            )
        }
    }

    override suspend fun publish(event: DomainEvent) {
        logger.debug("Publishing event {}", event.eventType())
        messagingPublisher?.let { publisher ->
            runCatching { publisher.publish(event) }
                .onFailure { error ->
                    logger.warn(
                        "Failed to forward event {} to messaging layer: {}",
                        event.eventType(),
                        error.message,
                        error
                    )
                }
        }

        val handlers = listeners[event::class]
        if (handlers.isNullOrEmpty()) {
            logger.debug("No handlers registered for {}", event.eventType())
            return
        }

        supervisorScope {
            handlers.forEach { listener ->
                launch(dispatcher) {
                    runCatching { listener(event) }
                        .onFailure { error ->
                            logger.warn(
                                "Event handler failed for {}: {}",
                                event.eventType(),
                                error.message,
                                error
                            )
                        }
                }
            }
        }
    }
}

private fun <T : DomainEvent> KClass<out T>.resolveConcreteTypes(): Set<KClass<out T>> {
    if (!this.isSealed) return setOf(this)
    val sealedChildren = this.sealedSubclasses
    if (sealedChildren.isEmpty()) return setOf(this)
    return sealedChildren
        .flatMap { child ->
            @Suppress("UNCHECKED_CAST")
            (child as KClass<out T>).resolveConcreteTypes()
        }
        .toSet()
}
