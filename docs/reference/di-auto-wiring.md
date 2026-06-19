# Dependency injection & auto-wiring

Katalyst discovers components by scanning the packages passed to `scanPackages` and matching
classes against a small set of interfaces and function shapes. There are no DI annotations
and no module files: implementing the interface (or returning the right type) is the signal.
Constructor parameters are resolved by type at instantiation.

## Discovery signals

| You write… | Discovered as | Module |
|------------|---------------|--------|
| A class implementing `Component` | A managed component (observers, helpers) | `katalyst-core` |
| A class implementing `Service` | A service with `transactionManager` and transactional helpers | `katalyst-core` |
| A class implementing `CrudRepository<Id, Entity>` | A repository | `katalyst-persistence` |
| An `object`/class implementing `Table<Id, Entity>` | A table | `katalyst-persistence` |
| A class implementing `EventHandler<T>` | An event handler | `katalyst-events` |
| A class implementing `ApplicationInitializer` | A startup initializer | `katalyst-di` |
| A class implementing `KatalystMigration` | A migration | `katalyst-migrations` |
| An object implementing `AutomaticServiceConfigLoader<T>` | A config loader | `katalyst-config-provider` |
| `fun Route.xxx() = katalystRouting { … }` | A route module | `katalyst-ktor` |
| `fun Application.xxx() = katalystMiddleware { … }` | Middleware | `katalyst-ktor` |
| `fun Route.xxx() = katalystWebSockets { … }` | A WebSocket route | `katalyst-ktor` |
| `fun Application.xxx() = katalystExceptionHandler { … }` | Exception handlers | `katalyst-ktor` |
| A function returning `SchedulerJobHandle` | A scheduler registration | `katalyst-scheduler` |

## Component

```kotlin
import io.github.darkryh.katalyst.core.component.Component

class RegistrationMonitor(private val eventBus: EventBus) : Component {
    // implementing Component is the only signal needed
}
```

`Component` is for lightweight collaborators that need DI but are not transactional services.

## Service

```kotlin
import io.github.darkryh.katalyst.core.component.Service

class AuthService(private val repository: AuthAccountRepository) : Service {
    suspend fun load(id: Long) = transactionManager.transaction { repository.findById(id) }
}
```

`Service` extends `Component` and adds transaction helpers: the `transactionManager`
property, plus `transaction { … }`, `workflowTransaction { … }`, and `currentTransaction { … }`.
See [Transactions](transactions.md).

## Parameter injection rules

Katalyst resolves constructor parameters — and the parameters of route, middleware, and
scheduler functions — by these rules:

- **Object and config parameters** are resolved from the container by type.
- **Kotlin default values** are honored when no binding exists for an optional parameter.
- **Nullable parameters** with no available binding resolve to `null`.
- **Required scalar parameters** (`Int`, `Long`, `Boolean`, `String`) must have a default
  value or be supplied as part of a registered config object — Katalyst will not invent a
  scalar.
- **Missing required dependencies** fail fast at startup with diagnostics naming the type.

```kotlin
class NotificationOrchestrator(
    private val gateway: RealtimeGateway,      // resolved by type
    private val auditTrail: AuditTrail?,       // null if no binding
    private val retryLimit: Int = 3            // default used; scalars need a default
) : Service
```

Deferral wrappers (`Provider<T>`, `Lazy<T>`, `() -> T`) are **not** part of Katalyst
auto-wiring.

## Qualifiers

When two implementations of the same type are intentionally registered, disambiguate a
parameter with `@InjectNamed`:

```kotlin
import io.github.darkryh.katalyst.di.injection.InjectNamed

class PaymentService(
    @InjectNamed("stripe") private val gateway: PaymentGateway
) : Service
```

Keep injection type-only and annotation-free everywhere else.

## Resolving inside Ktor handlers

Inside a route, middleware, or WebSocket block, resolve dependencies with `ktInject`:

```kotlin
val service = call.ktInject<AuthService>()    // direct
val settings by ktInject<JwtSettingsService>() // delegate
```

See [Ktor integration](ktor.md).

## Validation and failure modes

During bootstrap Katalyst builds a dependency graph and validates it before instantiating
anything. Failures are reported as typed `ValidationError`s aggregated into a
`ValidationReport`, and fatal problems throw `FatalDependencyValidationException`. Common
errors:

| Error | Cause |
|-------|-------|
| `MissingDependencyError` | A required parameter has no binding |
| `CircularDependencyError` | Two or more components depend on each other |
| `UninstantiableTypeError` | A discovered type cannot be constructed |
| `InstantiationFailureError` | A constructor threw during instantiation |
| `SecondaryTypeBindingError` | A class binds a disallowed secondary interface |

## See also

- [Application DSL](application-dsl.md) — `scanPackages` and the bootstrap.
- [Architecture & bootstrap lifecycle](../explanation/architecture.md) — the phase-by-phase
  bootstrap that performs discovery, validation, ordering, and injection.
- [Design decisions](../explanation/design-decisions.md) — why discovery is interface-driven.

