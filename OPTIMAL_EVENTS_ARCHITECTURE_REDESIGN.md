# Optimal Events Architecture: Complete Redesign from Scratch

**Status**: Architecture recommendation for greenfield implementation
**Scope**: 4-module structure optimized for clarity and extensibility
**No Backward Compatibility Required**: Design assumes clean slate

---

## CORE INSIGHT: Current Design Problem

Your current architecture mixes concerns in ways that create confusion:

```
❌ CURRENT PROBLEM
┌─ katalyst-messaging ─────────────────┐
│  (Abstract messaging - OK)            │
└──────────────────────────────────────┘
           ↑
┌─ katalyst-events ──────────────────────────┐
│  (Bus + Handlers + Messaging Logic mixed)   │  ← TOO MANY JOBS
└────────────────────────────────────────────┘
           ↑
┌─ katalyst-event-driven ────────────────┐
│  (Serialization + Routing + Publishing) │
└────────────────────────────────────────┘
```

**Problem**: Application code must use low-level EventBus directly. No single coherent API.

---

## RECOMMENDED SOLUTION: 4-Module Architecture

```
✅ OPTIMAL DESIGN

┌─────────────────────────────────────────────────────────────┐
│  APPLICATION CODE                                           │
│  (User service, order service, etc.)                        │
│  Uses: EventClient (single public API)                      │
└──────────────────┬──────────────────────────────────────────┘
                   │
        ┌──────────▼───────────┐
        │ katalyst-events-client
        │ (Public API Layer)
        │ - EventClient interface
        │ - PublishResult types
        │ - Retry policies
        │ - Interceptors
        └──────┬─────┬──────┬──┘
               │     │      │
          ┌────▼─┐┌──▼──┐┌─▼────────┐
          │ Bus  ││Trans││ Messaging│
          └────┬─┘└──┬──┘└─┬────────┘
               │     │      │
    ┌──────────▼─┐┌──▼────┐▼──────────┐
    │ events-bus ││trans  │messaging  │
    │ (Local)    ││(Ser+  │(Abstract) │
    │ - EventBus ││Rout)  │           │
    │ - Registry ││       │           │
    │ - Handlers ││       │           │
    └────────┬───┘└───┬───┘           │
             │        │               │
    ┌────────▼┐   ┌───▼─────────┐    │
    │ events  │   │ events-trans │    │
    │(Domain) │   │(Serialize +  │    │
    │ - Model │   │ Route Logic) │    │
    │ - Meta  │   └──────────────┘    │
    └─────────┘                       │
                        ┌──────────────▼──────┐
                        │ (Messaging plugs in)
                        │ RabbitMQ/Kafka/etc  │
                        └─────────────────────┘
```

---

## THE 4 MODULES: Detailed Breakdown

### MODULE 1: `katalyst-events` (Event Domain Model)

**Responsibility**: Pure event definitions and contracts - NO logic

**Files**:
```
src/main/kotlin/com/ead/katalyst/events/
├── DomainEvent.kt              # Base interface with metadata
├── EventMetadata.kt            # Correlation, tracing, versioning
├── EventHandler.kt             # Handler interface
├── EventValidator.kt           # Validation contracts
├── EventException.kt           # Domain exceptions
└── conventions/
    ├── EventNamingConvention.kt
    └── EventVersioning.kt
```

**Key Classes**:
```kotlin
interface DomainEvent {
    fun getMetadata(): EventMetadata
}

data class EventMetadata(
    val eventId: String = UUID.randomUUID().toString(),
    val eventType: String,
    val timestamp: Long = System.currentTimeMillis(),
    val version: Int = 1,
    val correlationId: String? = null,
    val causationId: String? = null,
    val source: String? = null
)

interface EventHandler<T : DomainEvent> {
    val eventType: KClass<T>
    suspend fun handle(event: T)
}

class EventException(message: String, cause: Throwable? = null) : Exception(message, cause)
```

**Dependencies**: None (zero dependencies - pure domain)

**Export**: Only interfaces and data classes - NO implementations

---

### MODULE 2: `katalyst-events-bus` (Local In-Memory Bus)

**Responsibility**: In-memory event pub/sub with local handlers

**Files**:
```
src/main/kotlin/com/ead/katalyst/events/bus/
├── EventBus.kt                 # Interface
├── ApplicationEventBus.kt       # In-memory implementation
├── EventHandlerRegistry.kt      # Injectable registry
├── EventTopology.kt            # Wiring handlers to bus
├── EventBusInterceptor.kt      # Extension point
└── errors/
    └── HandlerException.kt
```

**Key Classes**:
```kotlin
interface EventBus {
    suspend fun publish(event: DomainEvent)
    fun register(handler: EventHandler<out DomainEvent>)
}

class ApplicationEventBus(
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val interceptors: List<EventBusInterceptor> = emptyList()
) : EventBus {
    // Implementation
}

interface EventHandlerRegistry {
    fun <T : DomainEvent> register(handler: EventHandler<T>)
    fun getAllHandlers(): List<EventHandler<*>>
}

interface EventBusInterceptor {
    suspend fun beforePublish(event: DomainEvent): InterceptResult
    suspend fun afterPublish(event: DomainEvent, result: PublishResult)
    suspend fun onPublishError(event: DomainEvent, error: Throwable)
}
```

**Dependencies**: katalyst-events only

**Responsibility**: Only local, in-memory event distribution. NO transport knowledge.

---

### MODULE 3: `katalyst-events-transport` (Serialization + Routing)

**Responsibility**: Event format transformation and destination selection

**Files**:
```
src/main/kotlin/com/ead/katalyst/events/transport/
├── serialization/
│   ├── EventSerializer.kt          # Interface
│   ├── JsonEventSerializer.kt      # JSON implementation
│   ├── EventDeserializer.kt        # Interface
│   └── JsonEventDeserializer.kt    # JSON implementation
├── routing/
│   ├── EventRouter.kt              # Interface
│   ├── RoutingStrategies.kt        # Builder patterns
│   ├── PrefixedRouter.kt
│   ├── PackageBasedRouter.kt
│   └── CustomRouter.kt
├── message/
│   ├── EventMessage.kt             # Transport message
│   └── EventMessageBuilder.kt
└── errors/
    └── SerializationException.kt
```

**Key Classes**:
```kotlin
interface EventSerializer {
    suspend fun serialize(event: DomainEvent): EventMessage
    fun getContentType(): String
}

class JsonEventSerializer : EventSerializer {
    override suspend fun serialize(event: DomainEvent): EventMessage {
        val json = jacksonMapper.writeValueAsString(event)
        return EventMessage(
            contentType = "application/json",
            payload = json.toByteArray(),
            headers = mapOf(
                "event-type" to event.getMetadata().eventType,
                "event-id" to event.getMetadata().eventId,
                "correlation-id" to (event.getMetadata().correlationId ?: "")
            )
        )
    }
}

interface EventRouter {
    fun resolve(event: DomainEvent): Destination
}

object RoutingStrategies {
    fun prefixed(prefix: String = "events"): EventRouter = ...
    fun packageBased(prefix: String = "events"): EventRouter = ...
    fun custom(resolver: (DomainEvent) -> Destination): EventRouter = ...
}
```

**Dependencies**:
- katalyst-events (for DomainEvent)
- katalyst-messaging (for Destination)

**Responsibility**: Only transformation logic. NO network, NO async, NO business logic.

**Testing**: Can test serialization in isolation with sample events.

---

### MODULE 4: `katalyst-events-client` (Public API - Main Integration Point)

**Responsibility**: High-level client API combining local bus + remote transport + coordination

**Files**:
```
src/main/kotlin/com/ead/katalyst/events/client/
├── EventClient.kt               # Main public interface
├── EventClientImpl.kt            # Implementation
├── PublishResult.kt             # Success/Failure types
├── EventClientConfiguration.kt  # DSL configuration
├── interceptors/
│   ├── EventClientInterceptor.kt
│   ├── LoggingInterceptor.kt
│   └── MetricsInterceptor.kt
├── retry/
│   ├── RetryPolicy.kt
│   └── RetryPolicies.kt
├── consumer/
│   ├── RemoteEventConsumer.kt   # Consume from messaging
│   └── EventConsumerCallback.kt
├── koin/
│   ├── EventClientModule.kt     # DI configuration
│   └── EventClientBuilder.kt
└── errors/
    └── PublishException.kt
```

**Key Classes** (The Core API):

```kotlin
/**
 * Main public API for event operations.
 *
 * This is what application code uses.
 * Coordinates local bus + remote transport automatically.
 */
interface EventClient {
    /**
     * Publish an event locally and (optionally) remotely.
     *
     * Automatically:
     * - Publishes to local handlers (async)
     * - Serializes and publishes to external systems
     * - Handles retries if configured
     * - Returns status
     */
    suspend fun publish(event: DomainEvent): PublishResult

    /**
     * Subscribe to events from a remote source.
     * Deserializes and publishes to local bus.
     */
    suspend fun subscribe(
        destination: Destination,
        handler: suspend (DomainEvent) -> Unit
    ): RemoteEventConsumer
}

sealed class PublishResult {
    data class LocalPublished(
        val event: DomainEvent,
        val handlersInvoked: Int,
        val handlersSucceeded: Int
    ) : PublishResult()

    data class LocalAndRemotePublished(
        val event: DomainEvent,
        val localResult: LocalPublished,
        val remoteResult: RemotePublished
    ) : PublishResult()

    data class Failure(
        val event: DomainEvent,
        val exception: Throwable,
        val isRetryable: Boolean,
        val stage: PublishStage
    ) : PublishResult()
}

enum class PublishStage {
    LOCAL_PUBLISHING,
    SERIALIZATION,
    REMOTE_PUBLISHING,
    UNKNOWN
}
```

**Internal Coordination**:

```kotlin
class EventClientImpl(
    private val localBus: EventBus,
    private val serializer: EventSerializer,
    private val router: EventRouter,
    private val producer: Producer,
    private val retryPolicy: RetryPolicy,
    private val interceptors: List<EventClientInterceptor>
) : EventClient {

    override suspend fun publish(event: DomainEvent): PublishResult {
        // Step 1: Run before-publish interceptors
        for (interceptor in interceptors) {
            if (!interceptor.beforePublish(event)) return PublishResult.Aborted
        }

        // Step 2: Publish locally (to in-memory handlers)
        val localResult = try {
            localBus.publish(event)
            PublishResult.LocalPublished(event, handlersInvoked, handlersSucceeded)
        } catch (e: Exception) {
            return PublishResult.Failure(event, e, false, PublishStage.LOCAL_PUBLISHING)
        }

        // Step 3: Serialize and publish remotely (with retries)
        var remoteResult: PublishResult.RemotePublished? = null
        var lastError: Throwable? = null

        var attempt = 1
        while (attempt <= 5) {
            try {
                val serialized = serializer.serialize(event)
                val destination = router.resolve(event)

                val sendResult = producer.send(destination, serialized.toMessage())

                if (sendResult is ProducerResult.Success) {
                    remoteResult = PublishResult.RemotePublished(event, sendResult.messageId)
                    break
                }

                lastError = sendResult.exception
                if (!sendResult.isRetryable) break

                if (retryPolicy.shouldRetry(attempt, sendResult.exception)) {
                    delay(retryPolicy.getDelayMs(attempt))
                    attempt++
                } else {
                    break
                }
            } catch (e: Exception) {
                lastError = e
                if (!retryPolicy.shouldRetry(attempt, e)) break
                delay(retryPolicy.getDelayMs(attempt))
                attempt++
            }
        }

        // Step 4: Return appropriate result
        return when {
            remoteResult != null -> PublishResult.LocalAndRemotePublished(
                event, localResult, remoteResult
            )
            lastError != null -> PublishResult.Failure(
                event, lastError, false, PublishStage.REMOTE_PUBLISHING
            )
            else -> PublishResult.Failure(
                event, Exception("Remote publish failed"), false, PublishStage.REMOTE_PUBLISHING
            )
        }
    }
}
```

**Dependencies**:
- katalyst-events (DomainEvent)
- katalyst-events-bus (EventBus, EventBusInterceptor)
- katalyst-events-transport (EventSerializer, EventRouter)
- katalyst-messaging (Producer, Destination)

---

## DEPENDENCY GRAPH

```
                    ┌─────────────────────┐
                    │ katalyst-messaging  │
                    │ (Abstract)          │
                    └──────────┬──────────┘
                               │
        ┌──────────────────────┼──────────────────────┐
        │                      │                      │
┌───────▼────────┐    ┌───────▼─────────┐    ┌──────▼──────────┐
│ events-transport│    │ events-client   │    │ messaging-impls │
│ (Serialization) │    │ (Public API)    │    │ (RabbitMQ/Kafka)│
└────────┬────────┘    └────────┬────────┘    └─────────────────┘
         │                      │
         └──────────┬───────────┘
                    │
         ┌──────────▼──────────┐
         │ events-bus          │
         │ (Local pub/sub)     │
         └──────────┬──────────┘
                    │
         ┌──────────▼──────────┐
         │ events              │
         │ (Domain models)     │
         └─────────────────────┘

NO CYCLES ✅
Clear dependency direction ✅
Can test each module in isolation ✅
```

---

## HOW APPLICATIONS WOULD USE THIS

### Setup (in Application.kt)

```kotlin
fun main(args: Array<String>) = katalystApplication(args) {
    database(DatabaseConfigFactory.config())
    scanPackages("com.example")
    enableScheduler()

    // NEW: Unified events configuration
    enableEvents {
        // Local bus (always enabled)
        bus {
            dispatcher(Dispatchers.IO)
            interceptor(LoggingInterceptor())
        }

        // Optional: Remote messaging integration
        messaging {
            factory(RabbitMQClientFactory(...))
            serializer(JsonEventSerializer())
            router(RoutingStrategies.prefixed("events"))
            retryPolicy(RetryPolicies.exponential())
        }

        // Optional: Remote event consumption
        consumer {
            // Subscribe to external topics and handle them
        }
    }
}
```

### Application Code (Service Layer)

```kotlin
@Component
class UserService(
    private val userRepository: UserRepository,
    private val eventClient: EventClient  // ← Single dependency!
) {
    suspend fun createUser(request: CreateUserRequest): User {
        // Create user
        val user = User(
            id = UUID.randomUUID(),
            email = request.email,
            name = request.name
        )

        userRepository.save(user)

        // Publish event (handles local + remote automatically)
        val result = eventClient.publish(
            UserCreatedEvent(
                userId = user.id,
                email = user.email,
                timestamp = System.currentTimeMillis()
            )
        )

        // Handle result
        when (result) {
            is PublishResult.LocalAndRemotePublished -> {
                logger.info("User created event published to {}", result.remoteResult.messageId)
            }
            is PublishResult.LocalPublished -> {
                logger.warn("User created event published locally only (remote failed)")
            }
            is PublishResult.Failure -> {
                logger.error("Failed to publish user created event: {}", result.exception.message)
                // You can decide to throw, retry, or continue
            }
        }

        return user
    }
}
```

### Event Handlers (Application Layer - in components)

```kotlin
@Component
class UserCreatedNotificationHandler(
    private val notificationService: NotificationService
) : EventHandler<UserCreatedEvent> {

    override val eventType = UserCreatedEvent::class

    override suspend fun handle(event: UserCreatedEvent) {
        // Runs automatically when UserCreatedEvent is published
        notificationService.sendWelcomeEmail(event.email)
    }
}

@Component
class UserCreatedAuditHandler(
    private val auditService: AuditService
) : EventHandler<UserCreatedEvent> {

    override val eventType = UserCreatedEvent::class

    override suspend fun handle(event: UserCreatedEvent) {
        // Multiple handlers for same event!
        auditService.log("User created", mapOf("userId" to event.userId))
    }
}
```

### Event Definition (Domain)

```kotlin
data class UserCreatedEvent(
    val userId: UUID,
    val email: String,
    override val metadata: EventMetadata = EventMetadata(
        eventType = "user.created",
        source = "user-service"
    )
) : DomainEvent {
    override fun getMetadata(): EventMetadata = metadata
}
```

This is BEAUTIFUL!

- Application code uses one API: `EventClient`
- No knowledge of serialization, routing, or messaging details
- Event handlers are discovered automatically
- Remote publishing happens transparently
- Retries happen automatically
- All coordinated in one place

---

## KEY ADVANTAGES OF THIS STRUCTURE

### 1. **Single Public API**
Applications only know about `EventClient`. Everything else is implementation detail.

### 2. **Clear Separation of Concerns**
```
Events (domain)      → Pure models
Bus (local)         → In-memory pub/sub
Transport (remote)  → Serialization + routing
Client (public)     → Coordination + API
```

### 3. **Testability**
```
Test EventBus alone:     No need for EventClient
Test Serializer alone:   No need for Producer
Test EventClient:        Mock Bus + Serializer
Test Handler:            Just call handle()
```

### 4. **Extensibility**
```
New serializer?     → Implement EventSerializer
New routing?        → Implement EventRouter
New interceptor?    → Implement EventClientInterceptor
New handler?        → Implement EventHandler<T>
New consumer?       → Implement RemoteEventConsumer
```

### 5. **No Coupling Between Modules**
- Transport doesn't know about Bus
- Bus doesn't know about Transport
- Client just orchestrates them

### 6. **Backward Compatible Never Needed**
Since library is in development, you can just implement this from scratch.

---

## COMPARISON: Current vs. Proposed

### Current (Problematic)
```
Application
    ↓
EventBus (knows about EventMessagingPublisher) ← TIGHTLY COUPLED
    ├→ Local handlers
    ├→ EventMessagingPublisher (who knows about Producer)
    │   └→ Serialization happens here (mixed concerns)
    │   └→ Routing happens here (mixed concerns)
    └→ Producer
        └→ Messaging
```

**Problems**:
- EventBus has too many responsibilities
- Can't test serialization without EventBus
- Can't test routing without EventBus
- Multiple registration paths for handlers
- Low-level access for applications

### Proposed (Optimal)
```
Application
    ↓
EventClient (high-level API) ← SINGLE COHERENT INTERFACE
    ├→ EventBus (local pub/sub ONLY)
    ├→ EventSerializer (format agnostic)
    ├→ EventRouter (destination agnostic)
    ├→ Producer (messaging agnostic)
    └→ RetryPolicy (resilience)
```

**Advantages**:
- Each component has ONE job
- All testable in isolation
- Clean dependency graph (no cycles)
- Single public API
- Easy to understand
- Easy to extend

---

## MIGRATION FROM CURRENT DESIGN

Since you're building from scratch (no backward compatibility needed):

### Step 1: Delete Current Modules
```
❌ Delete katalyst-events (old implementation)
❌ Delete katalyst-event-driven (old implementation)
✅ Keep katalyst-messaging (it's good)
```

### Step 2: Implement New Modules In Order
```
1️⃣  katalyst-events                (1-2 days)
    └─ Just domain models

2️⃣  katalyst-events-bus            (2-3 days)
    └─ In-memory bus + handler registry

3️⃣  katalyst-events-transport      (1-2 days)
    └─ Serialization + routing strategies

4️⃣  katalyst-events-client         (2-3 days)
    └─ Public API + coordination
    └─ DI module integration
```

### Step 3: Update Integration Points
```
katalyst-di/DIConfiguration.kt
├─ Remove old enableEvents()
├─ Add new enableEvents() with DSL
└─ Wire up EventClient + EventBus

katalyst-example/Application.kt
├─ Remove old event setup
├─ Use new enableEvents()
└─ Update usage to EventClient
```

---

## DO YOU STILL NEED A SEPARATE "CLIENT" LAYER?

**Answer: YES, absolutely.**

Here's why:

1. **Public API Boundary**
   - EventBus is implementation detail
   - EventClient is what applications should use
   - Forces good architecture

2. **Coordination Logic**
   - Local + Remote publishing coordination
   - Retry policies
   - Error handling
   - All in one place

3. **Extensibility**
   - Interceptors for metrics, logging, tracing
   - Can intercept at client level (affects all publishes)

4. **Testing**
   - Mock EventClient in application tests
   - Mock EventBus in client tests
   - Clear boundaries

5. **Future Features**
   - Event transformation
   - Event filtering
   - Event validation (before publish)
   - Saga coordination
   - All naturally fit into client layer

---

## OPTIONAL: 5TH MODULE FOR CONSUMER?

If you want super clean separation, could add:

```
katalyst-events-consumer (optional)
├─ Message consumption from remote systems
├─ Deserialization pipeline
├─ Event validation
├─ Handler dispatch
└─ Automatically used by EventClient.subscribe()
```

But honestly, this can just be part of `katalyst-events-client`. Adding a 5th module might be overkill.

---

## FINAL RECOMMENDATION

### Go with 4 Modules:

```
1. katalyst-events              (Event domain models)
2. katalyst-events-bus          (In-memory event bus)
3. katalyst-events-transport    (Serialization + routing)
4. katalyst-events-client       (Public API + coordination)
```

### Why NOT 3 Modules:
Combining any of these loses clarity. Each has distinct responsibilities.

### Why NOT 5+ Modules:
Gets too granular. 4 is the sweet spot for clarity without over-modularization.

### Expected Effort:
- Design & planning: 1 day
- Implementation: 8-10 days
- Testing: 3-4 days
- Documentation: 1-2 days
- **Total: 2-3 weeks** (much faster than refactoring!)

### Result:
- Clean architecture ✅
- Single public API ✅
- Excellent testability ✅
- Easy extensibility ✅
- Zero coupling ✅

---

## NEXT ACTION

Would you like me to:

1. **Create detailed specifications** for each of the 4 modules?
2. **Create a complete implementation guide** (like the refactor plan, but for new code)?
3. **Create the module build.gradle.kts files**?
4. **Create example code** for each module?
5. **Update the DI configuration** for the new structure?

Let me know which would be most helpful!

