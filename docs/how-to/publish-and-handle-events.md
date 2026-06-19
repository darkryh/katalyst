# Publish and handle events

Katalyst includes an in-process event bus for decoupling parts of your application. A
service publishes a domain event; one or more handlers react to it asynchronously. When you
publish inside a transaction, delivery is deferred until the transaction commits — so
handlers never react to changes that get rolled back.

## Enable events

```kotlin
features { enableEvents() }
```

Add the `katalyst-events` and `katalyst-events-bus` dependencies.

## Define a domain event

Implement `DomainEvent`. Events are immutable, past-tense facts.

```kotlin
import io.github.darkryh.katalyst.events.DomainEvent

data class UserRegisteredEvent(
    val accountId: Long,
    val email: String,
    val displayName: String
) : DomainEvent
```

`DomainEvent` supplies `eventId`, metadata, and an `eventType()` with sensible defaults, so
a plain data class is enough. To group related events, use a sealed hierarchy — a handler of
the sealed parent automatically receives every subtype.

## Publish an event

Inject the `EventBus` and publish from a service, ideally inside a transaction:

```kotlin
import io.github.darkryh.katalyst.core.component.Service
import io.github.darkryh.katalyst.events.bus.EventBus

class AuthService(
    private val repository: AuthAccountRepository,
    private val eventBus: EventBus
) : Service {
    suspend fun register(email: String, displayName: String): AuthAccount =
        transactionManager.transaction {
            val account = repository.save(/* … */)
            eventBus.publish(UserRegisteredEvent(account.id!!, account.email, displayName))
            account
        }
}
```

Because the publish happens inside `transactionManager.transaction { … }`, the event is held
until commit. If the transaction rolls back, the event is never delivered.

## Handle an event

Implement `EventHandler<T>`. Declare the event type and the `handle` function. Constructor
parameters are injected as usual.

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

Handlers are discovered under your scanned packages and registered automatically. Several
handlers can listen to the same event; they run asynchronously and in parallel. A handler
that throws is logged, not propagated — so one failure does not stop the others. Make
handlers **idempotent**, since the same event may be retried.

## Observe events as a stream

A `Component` can react to events as a Kotlin `Flow` instead of a handler — useful for
projections and monitors:

```kotlin
import io.github.darkryh.katalyst.core.component.Component
import io.github.darkryh.katalyst.events.bus.EventBus
import io.github.darkryh.katalyst.events.bus.eventsOf
import kotlinx.coroutines.*

class RegistrationMonitor(private val eventBus: EventBus) : Component {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    init {
        scope.launch {
            eventBus.eventsOf<UserRegisteredEvent>().collect { event ->
                log.info("Saw registration for ${event.email}")
            }
        }
    }
}
```

## Verify

In an integration test, publish an event through the real bus and assert the handler's
effect — see [Test your application](test-applications.md). The
[sample app](https://github.com/darkryh/katalyst/tree/master/samples/katalyst-example)
includes `UserRegistrationFlowTest` as a worked example.

## Related

- [Events reference](../reference/events.md) — `EventBus`, `EventHandler`, side effects,
  deduplication, and transaction-aware publishing.
- [Transactions](../reference/transactions.md) — how deferred publishing is implemented.

