package com.ead.katalyst.events.bus

import com.ead.katalyst.events.DomainEvent
import com.ead.katalyst.events.EventHandler
import com.ead.katalyst.events.bus.exception.EventPublishingException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.Collections
import kotlin.reflect.KClass
import kotlin.reflect.full.safeCast
import kotlin.system.measureTimeMillis

/**
 * In-memory event bus implementation.
 *
 * This is the core local event distribution engine. It:
 * - Manages subscriptions of event handlers
 * - Publishes events to all interested handlers asynchronously
 * - Executes handlers in parallel with proper error handling
 * - Supports sealed event hierarchies with automatic subtype registration
 * - Provides interceptor extension points
 *
 * **Architecture:**
 *
 * ```
 * Event published
 *     ↓
 * Interceptors: beforePublish()
 *     ↓
 * Find handlers for event type
 *     ↓
 * Launch handlers async (parallel)
 *     ↓
 * Wait for all to complete (supervisorScope)
 *     ↓
 * Interceptors: afterPublish()
 *     ↓
 * Return
 * ```
 *
 * **Thread Safety:**
 * - Uses ConcurrentHashMap for listener storage
 * - Uses CopyOnWriteArrayList for handler lists
 * - Safe for concurrent reads and writes
 * - Safe for concurrent event publishing
 *
 * **Error Handling:**
 * - Handler exceptions are caught and logged
 * - Handler failures don't affect other handlers
 * - Interceptor exceptions are caught and logged
 * - Errors don't propagate to callers
 *
 * @param dispatcher CoroutineDispatcher for handler execution (default: Dispatchers.Default)
 * @param interceptors List of interceptors for extending functionality (default: empty)
 */
class ApplicationEventBus(
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val interceptors: List<EventBusInterceptor> = emptyList()
) : EventBus {

    private val logger = LoggerFactory.getLogger(ApplicationEventBus::class.java)

    // Map: Event KClass -> List of handler functions
    // Using suspend (DomainEvent) -> Unit to allow generic handler invocation
    private val listeners = ConcurrentHashMap<KClass<out DomainEvent>, CopyOnWriteArrayList<RegisteredHandler>>()

    // Map: Event type name -> EventHandlerConfig (for handling mode configuration)
    private val handlerConfigs = ConcurrentHashMap<String, EventHandlerConfig>()
    private val eventStream = MutableSharedFlow<DomainEvent>(
        replay = 0,
        extraBufferCapacity = 128,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    /**
     * Register a handler.
     *
     * For sealed event hierarchies, automatically registers for all concrete subtypes.
     *
     * @param handler The handler to register
     */
    override fun register(handler: EventHandler<out DomainEvent>) {
        // Resolve all concrete types (handles sealed hierarchies)
        @Suppress("UNCHECKED_CAST")
        val concreteTypes = (handler.eventType as KClass<out DomainEvent>).resolveConcreteTypes()

        concreteTypes.forEach { type ->
            val handlerList = listeners.getOrPut(type) { CopyOnWriteArrayList() }

            // Wrap handler to suppress type checking
            val wrappedHandler: suspend (DomainEvent) -> Unit = { event ->
                @Suppress("UNCHECKED_CAST")
                (handler as EventHandler<DomainEvent>).handle(event)
            }

            handlerList.add(
                RegisteredHandler(
                    handlerClassName = handler::class.qualifiedName ?: handler::class.simpleName ?: handler::class.java.name,
                    invoker = wrappedHandler
                )
            )

            logger.debug(
                "Registered event handler {} for {}",
                handler::class.qualifiedName,
                type.qualifiedName
            )
        }
    }

    /**
     * Configure the handling mode for a specific event type.
     *
     * Allows per-event-type configuration of when handlers execute:
     * - SYNC_BEFORE_COMMIT: Handlers execute before transaction commits (failures cause rollback)
     * - ASYNC_AFTER_COMMIT: Handlers execute after commit (eventual consistency)
     *
     * Default: SYNC_BEFORE_COMMIT for transactional consistency
     *
     * @param config The handler configuration for an event type
     */
    fun configureHandlers(config: EventHandlerConfig) {
        handlerConfigs[config.eventType] = config
        logger.debug(
            "Configured handlers for {}: mode={}, timeout={}ms, failOnError={}",
            config.eventType,
            config.handlingMode,
            config.timeoutMs,
            config.failOnHandlerError
        )
    }

    /**
     * Get the handler configuration for an event type.
     *
     * @param event The event to get config for
     * @return EventHandlerConfig for the event, or default if not configured
     */
    fun getHandlerConfig(event: DomainEvent): EventHandlerConfig {
        val config = handlerConfigs[event::class.qualifiedName ?: event::class.simpleName]
        return config ?: EventHandlerConfig(
            eventType = event::class.qualifiedName ?: event::class.simpleName ?: "Unknown",
            handlingMode = EventHandlingMode.SYNC_BEFORE_COMMIT  // Default: transactional consistency
        )
    }

    /**
     * Check if handlers are registered for an event type.
     *
     * Used for validation to ensure event can be published before transaction commits.
     *
     * @param event The event to check
     * @return True if at least one handler is registered, false otherwise
     */
    fun hasHandlers(event: DomainEvent): Boolean {
        val handlers = listeners[event::class]
        return !handlers.isNullOrEmpty()
    }

    override fun events(): SharedFlow<DomainEvent> = eventStream.asSharedFlow()

    override fun <T : DomainEvent> eventsOf(eventType: KClass<T>): Flow<T> =
        eventStream.mapNotNull { eventType.safeCast(it) }

    /**
     * Publish an event to all interested handlers.
     *
     * @param event The event to publish
     */
    override suspend fun publish(event: DomainEvent) {
        logger.debug("Publishing event: {}", event.eventType())

        val startTime = System.currentTimeMillis()
        var publishDuration: Long = 0L

        try {
            // Step 1: Call beforePublish interceptors
            for (interceptor in interceptors) {
                try {
                    val result = interceptor.beforePublish(event)
                    if (result is InterceptResult.Abort) {
                        logger.info("Event publishing aborted: {}", result.reason)
                        return
                    }
                } catch (e: Exception) {
                    logger.warn("Interceptor.beforePublish failed: {}", e.message, e)
                    // Continue even if interceptor fails
                }
            }

            // Step 2: Publish to local handlers and measure duration
            val handlerReport: HandlerExecutionReport
            publishDuration = measureTimeMillis {
                handlerReport = publishToHandlers(event)
            }

            // Step 3: Call afterPublish interceptors
            val result = PublishResult(
                event = event,
                handlersInvoked = handlerReport.handlersInvoked,
                handlersSucceeded = handlerReport.handlersInvoked - handlerReport.failures.size,
                handlersFailed = handlerReport.failures.size,
                failures = handlerReport.failures,
                durationMs = publishDuration
            )

            for (interceptor in interceptors) {
                try {
                    interceptor.afterPublish(event, result)
                } catch (e: Exception) {
                    logger.warn("Interceptor.afterPublish failed: {}", e.message, e)
                    // Continue even if interceptor fails
                }
            }

            emitToFlow(event)

            if (result.handlersFailed > 0) {
                throw EventPublishingException(event, result.failures)
            }

        } catch (e: Exception) {
            logger.error("Error during event publishing: {}", e.message, e)

            // Call onPublishError interceptors
            for (interceptor in interceptors) {
                try {
                    interceptor.onPublishError(event, e)
                } catch (ie: Exception) {
                    logger.warn("Interceptor.onPublishError failed: {}", ie.message, ie)
                }
            }
            throw e
        }
    }

    /**
     * Internal: Publish event to all handlers asynchronously.
     *
     * Uses supervisorScope to ensure all handlers execute even if some fail.
     */
    private suspend fun publishToHandlers(event: DomainEvent): HandlerExecutionReport {
        val handlers = listeners[event::class]

        if (handlers.isNullOrEmpty()) {
            logger.debug("No handlers registered for event: {}", event.eventType())
            return HandlerExecutionReport(handlersInvoked = 0, failures = emptyList())
        }

        logger.debug("Publishing event {} to {} handler(s)", event.eventType(), handlers.size)

        val failures = Collections.synchronizedList(mutableListOf<HandlerFailure>())

        // supervisorScope ensures that if one handler fails, others still execute
        supervisorScope {
            handlers.forEach { handler ->
                launch(dispatcher) {
                    try {
                        handler.invoker(event)
                    } catch (e: Exception) {
                        logger.error(
                            "Event handler failed for {}: {}",
                            event.eventType(),
                            e.message,
                            e
                        )
                        failures.add(
                            HandlerFailure(
                                handlerClass = handler.handlerClassName,
                                exception = e
                            )
                        )
                    }
                }
            }
        }

        return HandlerExecutionReport(
            handlersInvoked = handlers.size,
            failures = failures.toList()
        )
    }

    private fun emitToFlow(event: DomainEvent) {
        if (!eventStream.tryEmit(event)) {
            logger.debug("Event flow buffer full, dropping {}", event.eventType())
        }
    }
}

private data class RegisteredHandler(
    val handlerClassName: String,
    val invoker: suspend (DomainEvent) -> Unit
)

private data class HandlerExecutionReport(
    val handlersInvoked: Int,
    val failures: List<HandlerFailure>
)

/**
 * Resolve concrete types for a KClass.
 *
 * If the class is sealed, returns all concrete subclasses.
 * Otherwise, returns the class itself.
 *
 * This enables handlers to listen to sealed event hierarchies
 * and automatically handle all subtypes.
 *
 * **Example:**
 * ```kotlin
 * sealed class UserEvent : DomainEvent
 * data class UserCreatedEvent(...) : UserEvent()
 * data class UserDeletedEvent(...) : UserEvent()
 *
 * val types = UserEvent::class.resolveConcreteTypes()
 * // Returns: [UserCreatedEvent, UserDeletedEvent]
 * ```
 *
 * @return Set of concrete types (either the class itself or all sealed subtypes)
 */
private fun <T : DomainEvent> KClass<out T>.resolveConcreteTypes(): Set<KClass<out T>> {
    // If not sealed, return as-is
    if (!this.isSealed) {
        @Suppress("UNCHECKED_CAST")
        return setOf(this as KClass<out T>)
    }

    // Get direct sealed subclasses
    val directSubclasses = this.sealedSubclasses
    if (directSubclasses.isEmpty()) {
        @Suppress("UNCHECKED_CAST")
        return setOf(this as KClass<out T>)
    }

    // Recursively resolve all concrete types
    return directSubclasses
        .flatMap { child ->
            @Suppress("UNCHECKED_CAST")
            (child as KClass<out T>).resolveConcreteTypes()
        }
        .toSet()
}
