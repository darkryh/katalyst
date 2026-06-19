# Events

Katalyst's event system is split across two modules: `katalyst-events` defines the contracts
(`DomainEvent`, `EventHandler`, validation), and `katalyst-events-bus` provides the in-process
bus, transaction awareness, side effects, and deduplication. For a walkthrough, see
[Publish and handle events](../how-to/publish-and-handle-events.md).

Enable it with `features { enableEvents() }`.

## DomainEvent

```kotlin
interface DomainEvent {
    val eventId: String                 // default: derived per instance; used for deduplication
    fun getMetadata(): EventMetadata    // default supplied
    fun eventType(): String             // default: metadata.eventType
}
```

Events are immutable, past-tense facts. A plain data class is enough:

```kotlin
data class UserRegisteredEvent(
    val accountId: Long,
    val email: String,
    val displayName: String
) : DomainEvent
```

`eventId` backs deduplication: a retried transaction re-publishing the same event id is not
delivered twice. `EventMetadata` carries the event type, correlation id, causation id,
timestamps, version, and source.

### Sealed hierarchies

Group related events with a sealed class. A handler of the sealed parent automatically
receives every concrete subtype:

```kotlin
sealed class UserEvent : DomainEvent
data class UserCreatedEvent(val id: Long) : UserEvent()
data class UserDeletedEvent(val id: Long) : UserEvent()
```

## EventHandler

```kotlin
interface EventHandler<T : DomainEvent> {
    val eventType: KClass<T>
    suspend fun handle(event: T)
}
```

Implement it and place the class under a scanned package; it is discovered and registered
automatically. Contract:

- Multiple handlers may listen to the same event type.
- Handlers run asynchronously and in parallel.
- A handler that throws is logged, not propagated — other handlers still run.
- Handlers should be idempotent and complete reasonably quickly.

```kotlin
class UserRegistrationHandler(
    private val userProfileService: UserProfileService
) : EventHandler<UserRegisteredEvent> {
    override val eventType = UserRegisteredEvent::class
    override suspend fun handle(event: UserRegisteredEvent) {
        userProfileService.createProfileForAccount(event.accountId, event.displayName)
    }
}
```

## EventBus

```kotlin
interface EventBus {
    suspend fun publish(event: DomainEvent)
    // registration is performed at startup by the framework
}
```

Inject the bus to publish. Publishing finds every handler for the event type, runs them
asynchronously, and returns when they complete; handler exceptions are caught and logged.

```kotlin
class AuthService(private val eventBus: EventBus) : Service {
    suspend fun register(...) = transactionManager.transaction {
        // …
        eventBus.publish(UserRegisteredEvent(id, email, name))
    }
}
```

Handlers are registered during startup auto-discovery — do not call `register` yourself.

### Observe as a Flow

A `Component` can consume events reactively with the `eventsOf<T>()` extension instead of
implementing a handler:

```kotlin
eventBus.eventsOf<UserRegisteredEvent>().collect { event -> /* … */ }
```

`ApplicationEventBus` is the default implementation, exposing handler subscriptions over a
`SharedFlow`.

## Transaction-aware publishing

When you publish inside `transactionManager.transaction { … }`, delivery is deferred until the
transaction commits; a rollback discards the pending events. This is implemented by
`TransactionAwareEventBus`, which registers a transaction adapter so the bus participates in
commit/rollback. The `publishPendingEvents` helper flushes events queued during a transaction.

This is what makes handlers safe: they never react to state that was rolled back.

## Side effects

`EventSideEffect` and `EventSideEffectAdapter` model post-publish side effects coordinated
with the event lifecycle — for example, dispatching to an external system after a successful
publish. They integrate with the same transaction-aware machinery.

## Deduplication

The bus deduplicates by `eventId` through an `EventDeduplicationStore`:

| Implementation | Behavior |
|----------------|----------|
| `InMemoryEventDeduplicationStore` | Tracks seen event ids in memory. |
| `NoOpEventDeduplicationStore` | Disables deduplication. |

## Interception, modes, and topology

| Type | Purpose |
|------|---------|
| `EventBusInterceptor` | Hook `beforePublish` / `afterPublish`; an interceptor can abort a publish (`InterceptResult`). |
| `EventHandlingMode` | Controls how handlers are executed for an event. |
| `EventHandlerRegistry` / `InMemoryEventHandlerRegistry` | Where handlers are registered. |
| `EventTopology` | Wires handlers to the bus at startup. |
| `PublishResult` / `HandlerFailure` | The outcome of a publish and any per-handler failures. |

## Validation

`katalyst-events` includes an event-validation layer: `EventValidator`,
`BaseEventValidator`, `CompositeEventValidator`, and `NoOpEventValidator`. The bus side adds
`EventPublishingValidator` (default `DefaultEventPublishingValidator`) to validate events
before publishing. Validation failures raise `EventValidationException`.

## Exceptions

`katalyst-events`: `EventException` and subtypes (`EventHandlerException`,
`EventPublishException`, `EventValidationException`, `EventSerializationException`,
`EventDeserializationException`, `EventRoutingException`, `EventConfigurationException`,
`EventHandlerRegistrationException`).

`katalyst-events-bus`: `EventPublishingException`, `HandlerException` and subtypes
(`HandlerExecutionException`, `HandlerRegistrationException`, `HandlerDiscoveryException`,
`InvalidHandlerMetadataException`, `WrongEventTypeException`).

## See also

- [Publish and handle events](../how-to/publish-and-handle-events.md)
- [Transactions](transactions.md) — the adapter that defers delivery to commit.

