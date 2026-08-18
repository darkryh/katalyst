package io.github.darkryh.katalyst.events.bus

import io.github.darkryh.katalyst.events.DomainEvent
import io.github.darkryh.katalyst.events.EventHandler
import io.github.darkryh.katalyst.events.bus.exception.EventPublishingException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CancellationException
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
import java.util.concurrent.atomic.AtomicLong
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
 * - Delivers an event to every handler whose registered type accepts it (exact type, sealed parent,
 *   abstract/open base class, interface, or `DomainEvent` catch-all), exactly once per handler
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

    // Dispatch table: concrete published class -> every handler whose registered type accepts it.
    // Computed once per published class and stamped with the registration generation it was
    // computed under, so a later register() transparently invalidates it. Keeps dispatch a single
    // map lookup while allowing registered types to be supertypes of the published event.
    private val dispatchCache = ConcurrentHashMap<KClass<out DomainEvent>, ResolvedHandlers>()
    private val registrationGeneration = AtomicLong(0)

    // Map: Event type name -> EventHandlerConfig (for handling mode configuration)
    private val handlerConfigs = ConcurrentHashMap<String, EventHandlerConfig>()
    // Observation stream. SUSPEND (not DROP_OLDEST) is deliberate: publishing uses tryEmit, which
    // never suspends, so a full buffer surfaces as `false` and the loss can be counted and reported.
    // With DROP_OLDEST tryEmit always succeeded and a slow subscriber lost events invisibly.
    private val eventStream = MutableSharedFlow<DomainEvent>(
        replay = 0,
        extraBufferCapacity = EVENT_STREAM_BUFFER,
        onBufferOverflow = BufferOverflow.SUSPEND
    )
    private val droppedObservations = AtomicLong(0)

    /**
     * Register a handler.
     *
     * The handler receives every published event its declared `eventType` accepts: the exact type,
     * any subtype of it (abstract/open base classes, interfaces and `DomainEvent` catch-alls all
     * work), and - for sealed hierarchies - the concrete subtypes the parent is expanded to. A
     * handler is invoked at most once per event however many of those routes match.
     *
     * @param handler The handler to register
     */
    override fun register(handler: EventHandler<out DomainEvent>) {
        // Resolve all concrete types (handles sealed hierarchies)
        @Suppress("UNCHECKED_CAST")
        val declaredType = handler.eventType as KClass<out DomainEvent>
        val concreteTypes = declaredType.resolveConcreteTypes()
        val handlerName = handler::class.qualifiedName
            ?: handler::class.simpleName
            ?: handler::class.java.name

        // Wrap handler to suppress type checking
        val wrappedHandler: suspend (DomainEvent) -> Unit = { event ->
            @Suppress("UNCHECKED_CAST")
            (handler as EventHandler<DomainEvent>).handle(event)
        }

        // ONE registration shared by every key this handler is filed under: an event that matches
        // several of those keys (e.g. a class implementing two leaves of a sealed interface) then
        // still invokes the handler exactly once, because resolution de-duplicates by identity.
        val registered = RegisteredHandler(handlerClassName = handlerName, invoker = wrappedHandler)

        concreteTypes.forEach { type ->
            listeners.getOrPut(type) { CopyOnWriteArrayList() }.add(registered)

            logger.debug(
                "Registered event handler {} for {}",
                handlerName,
                type.qualifiedName
            )
        }

        // Publish the new listener state first, then invalidate. A resolution computed against the
        // old state can only carry an older generation stamp, so it is recomputed on next use.
        registrationGeneration.incrementAndGet()

        warnIfUnreachable(handlerName, declaredType, concreteTypes)
    }

    /**
     * Report - loudly - a registration that can never deliver anything.
     *
     * Every ordinary shape (concrete class, abstract class, interface, sealed parent) is reachable
     * through assignability. A sealed type with no subclasses is not: it cannot be instantiated and
     * nothing can ever be an instance of it, so the handler is dead on arrival. That used to be
     * indistinguishable from "no such event has happened yet".
     */
    private fun warnIfUnreachable(
        handlerName: String,
        declaredType: KClass<out DomainEvent>,
        concreteTypes: Set<KClass<out DomainEvent>>
    ) {
        val reachable = concreteTypes.any { !(it.isSealed && it.sealedSubclasses.isEmpty()) }
        if (reachable) return

        logger.warn(
            "Event handler {} is registered for {}, a sealed event type with no subclasses: " +
                "no event instance can ever match it, so this handler will never be invoked.",
            handlerName,
            declaredType.qualifiedName ?: declaredType.simpleName ?: declaredType.java.name
        )
    }

    /**
     * All handlers that accept [eventClass], resolved once per published class.
     *
     * A registered type matches when it is assignable from the published class, so a handler
     * registered for a base type (abstract class, interface, sealed parent, `DomainEvent`) receives
     * the concrete events published under it.
     */
    private fun handlersFor(eventClass: KClass<out DomainEvent>): List<RegisteredHandler> {
        val generation = registrationGeneration.get()
        dispatchCache[eventClass]?.let { cached ->
            if (cached.generation == generation) return cached.handlers
        }

        val resolved = resolveHandlers(eventClass)
        dispatchCache[eventClass] = ResolvedHandlers(generation, resolved)
        return resolved
    }

    private fun resolveHandlers(eventClass: KClass<out DomainEvent>): List<RegisteredHandler> {
        val resolved = LinkedHashSet<RegisteredHandler>()
        listeners[eventClass]?.let(resolved::addAll)

        val publishedClass = eventClass.java
        for ((registeredType, handlers) in listeners) {
            if (registeredType == eventClass) continue
            if (registeredType.java.isAssignableFrom(publishedClass)) resolved.addAll(handlers)
        }

        return if (resolved.isEmpty()) emptyList() else resolved.toList()
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
    fun hasHandlers(event: DomainEvent): Boolean = handlersFor(event::class).isNotEmpty()

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
                } catch (e: CancellationException) {
                    throw e
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
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logger.warn("Interceptor.afterPublish failed: {}", e.message, e)
                    // Continue even if interceptor fails
                }
            }

            emitToFlow(event)

            if (result.handlersFailed > 0) {
                throw EventPublishingException(event, result.failures)
            }

        } catch (e: CancellationException) {
            throw e
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
        val handlers = handlersFor(event::class)

        if (handlers.isEmpty()) {
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
                    } catch (e: CancellationException) {
                        throw e
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

    /**
     * Offer the event to the observation stream, never blocking the publisher.
     *
     * `tryEmit` cannot suspend, so with a `SUSPEND` overflow policy it returns false exactly when a
     * slow [events] subscriber has filled the buffer - which is the only way this bus can observe
     * that an observation was lost. Handlers are unaffected: they have already run by this point.
     */
    private fun emitToFlow(event: DomainEvent) {
        if (eventStream.tryEmit(event)) return

        val dropped = droppedObservations.incrementAndGet()
        if (dropped == 1L || dropped % DROPPED_LOG_INTERVAL == 0L) {
            logger.warn(
                "events() observation buffer full ({} slots): dropped {} ({} dropped so far). " +
                    "A slow events() subscriber is not keeping up; event handlers are unaffected.",
                EVENT_STREAM_BUFFER,
                event.eventType(),
                dropped
            )
        }
    }
}

/** Extra buffer slots for the `events()` observation stream. */
private const val EVENT_STREAM_BUFFER = 128

/** After the first drop, report again every N drops so a flood cannot spam the log. */
private const val DROPPED_LOG_INTERVAL = 1_000L

private data class RegisteredHandler(
    val handlerClassName: String,
    val invoker: suspend (DomainEvent) -> Unit
)

/** A dispatch resolution together with the registration generation it was computed under. */
private data class ResolvedHandlers(
    val generation: Long,
    val handlers: List<RegisteredHandler>
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
