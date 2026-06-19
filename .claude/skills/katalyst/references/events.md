# Events reference

Two modules: `katalyst-events` (contracts) and `katalyst-events-bus` (in-process bus,
transaction awareness, side effects, dedup). Enable with `features { enableEvents() }`.

## DomainEvent

`io.github.darkryh.katalyst.events.DomainEvent`. A plain data class suffices — defaults supply
`eventId`, metadata, and `eventType()`.

```kotlin
import io.github.darkryh.katalyst.events.DomainEvent

data class UserRegisteredEvent(
    val accountId: Long,
    val email: String,
    val displayName: String
) : DomainEvent
```

`eventId` (default per instance) backs deduplication — a retried transaction re-publishing the
same id is not delivered twice. `EventMetadata` carries event type, correlation id, causation id,
timestamps, version, source.

### Sealed hierarchies

A handler of a sealed parent receives every concrete subtype automatically:

```kotlin
sealed class UserEvent : DomainEvent
data class UserCreatedEvent(val id: Long) : UserEvent()
data class UserDeletedEvent(val id: Long) : UserEvent()
// EventHandler<UserEvent> handles both subtypes
```

## EventHandler

`io.github.darkryh.katalyst.events.EventHandler`:

```kotlin
interface EventHandler<T : DomainEvent> {
    val eventType: KClass<T>
    suspend fun handle(event: T)
}
```

```kotlin
import io.github.darkryh.katalyst.events.EventHandler

class UserRegistrationHandler(
    private val userProfileService: UserProfileService
) : EventHandler<UserRegisteredEvent> {
    override val eventType = UserRegisteredEvent::class
    override suspend fun handle(event: UserRegisteredEvent) {
        userProfileService.createProfileForAccount(event.accountId, event.displayName)
    }
}
```

Discovered under scanned packages and registered automatically. Contract: multiple handlers per
type allowed; they run async + in parallel; a throwing handler is logged, not propagated; make
handlers **idempotent** (events may be retried).

## EventBus — publishing

`io.github.darkryh.katalyst.events.bus.EventBus`:

```kotlin
interface EventBus { suspend fun publish(event: DomainEvent) }
```

Inject and publish — ideally inside a transaction:

```kotlin
import io.github.darkryh.katalyst.core.component.Service   // Service/Component live here
import io.github.darkryh.katalyst.events.bus.EventBus

class AuthService(
    private val repo: AuthAccountRepository,
    private val eventBus: EventBus
) : Service {
    suspend fun register(email: String, displayName: String) = transactionManager.transaction {
        val account = repo.save(/* … */)
        eventBus.publish(UserRegisteredEvent(account.id!!, account.email, displayName))
        account
    }
}
```

Do not call `register(...)` on the bus yourself — handlers are wired at startup. To observe
reactively from a `Component`, use the `eventsOf<T>()` extension:

```kotlin
import io.github.darkryh.katalyst.events.bus.eventsOf
eventBus.eventsOf<UserRegisteredEvent>().collect { event -> /* … */ }
```

`ApplicationEventBus` is the default implementation (subscriptions over a `SharedFlow`).

## Transaction-aware publishing

Publishing inside `transactionManager.transaction { }` defers delivery until commit; a rollback
discards the queued events. Implemented by `TransactionAwareEventBus`, which registers a
transaction adapter. `publishPendingEvents` flushes the queued events on commit. This is the
whole reason handlers never react to rolled-back state — so always publish inside the transaction
that produced the change.

## Side effects, dedup, interception

| Type | Purpose |
|------|---------|
| `EventSideEffect`, `EventSideEffectAdapter` | Post-publish side effects coordinated with the tx lifecycle (e.g. dispatch externally after commit). |
| `EventDeduplicationStore` | Dedup by `eventId`. `InMemoryEventDeduplicationStore` (default-style) or `NoOpEventDeduplicationStore`. |
| `EventBusInterceptor` | `beforePublish`/`afterPublish`; can abort a publish (`InterceptResult`). |
| `EventHandlingMode` | How handlers execute for an event. |
| `EventHandlerRegistry` / `InMemoryEventHandlerRegistry` | Handler registry. |
| `EventTopology` | Wires handlers to the bus at startup. |
| `PublishResult`, `HandlerFailure` | Publish outcome and per-handler failures. |

## Validation

`katalyst-events`: `EventValidator`, `BaseEventValidator`, `CompositeEventValidator`,
`NoOpEventValidator`. `katalyst-events-bus`: `EventPublishingValidator` (default
`DefaultEventPublishingValidator`). Validation failures raise `EventValidationException`.

## Exceptions

`katalyst-events`: `EventException` and subtypes (`EventHandlerException`, `EventPublishException`,
`EventValidationException`, `EventSerializationException`, `EventDeserializationException`,
`EventRoutingException`, `EventConfigurationException`, `EventHandlerRegistrationException`).
`katalyst-events-bus`: `EventPublishingException`, `HandlerException` and subtypes
(`HandlerExecutionException`, `HandlerRegistrationException`, `HandlerDiscoveryException`,
`InvalidHandlerMetadataException`, `WrongEventTypeException`).
