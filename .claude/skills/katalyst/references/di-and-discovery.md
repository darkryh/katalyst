# Dependency injection & discovery reference

Discovery is structural: implement an interface (or use a DSL function) under a scanned package
and Katalyst manages the type. There are no DI annotations except the optional `@InjectNamed`
qualifier. Constructor parameters are dependencies, resolved by type.

## Discovery signals (complete)

| Write this | Discovered as | Interface/import |
|------------|---------------|------------------|
| `class X(...) : Component` | Managed component | `io.github.darkryh.katalyst.core.component.Component` |
| `class X(...) : Service` | Transactional service | `io.github.darkryh.katalyst.core.component.Service` |
| `class X : CrudRepository<Id, E>` | Repository | `io.github.darkryh.katalyst.repositories.CrudRepository` |
| `object X : LongIdTable(...), Table<Id, E>` | Table | `io.github.darkryh.katalyst.core.persistence.Table` |
| `class X : EventHandler<T>` | Event handler | `io.github.darkryh.katalyst.events.EventHandler` |
| `class X : ApplicationInitializer` | Startup initializer | `io.github.darkryh.katalyst.di.lifecycle.ApplicationInitializer` |
| `class X : KatalystMigration` | Migration | `io.github.darkryh.katalyst.migrations.KatalystMigration` |
| `object X : AutomaticServiceConfigLoader<T>` | Config loader | `io.github.darkryh.katalyst.config.provider.AutomaticServiceConfigLoader` |
| `fun Route.x() = katalystRouting { }` | Route module | `io.github.darkryh.katalyst.ktor.builder.katalystRouting` |
| `fun Application.x() = katalystMiddleware { }` | Middleware | `io.github.darkryh.katalyst.ktor.middleware.katalystMiddleware` |
| `fun Route.x() = katalystWebSockets { }` | WebSocket route | `io.github.darkryh.katalyst.ktor.websocket.katalystWebSockets` |
| `fun Application.x() = katalystExceptionHandler { }` | Exception handlers | `io.github.darkryh.katalyst.ktor.builder.katalystExceptionHandler` |
| `fun x() = requireScheduler().jobs { }` (returns `SchedulerJobHandle`) | Scheduler registration | `io.github.darkryh.katalyst.scheduler.extension.requireScheduler` |

There is **no annotation-based discovery.** Source KDoc that shows `@Component` is stale and
does not reflect the runtime; implement the interface instead.

## Component vs Service

```kotlin
import io.github.darkryh.katalyst.core.component.Component
import io.github.darkryh.katalyst.core.component.Service
```

- `Component` — lightweight collaborators needing DI (observers, helpers, clients). No
  transaction support.
- `Service` — extends `Component`; adds the transaction surface:
  - `val transactionManager: DatabaseTransactionManager`
  - `suspend fun <T> transaction(workflowId: String? = null, config: TransactionConfig? = null, block: suspend Transaction.() -> T): T`
  - `suspend fun <T> workflowTransaction(workflowId, config, block): T`
  - `suspend fun <T> currentTransaction(block): T`

Use `Service` for anything that touches the database or publishes events; `Component` for the
rest. Details in `references/transactions.md`.

## Parameter injection rules (exact)

When Katalyst instantiates a discovered class (or invokes a route/middleware/scheduler
function), it resolves each parameter:

- **Object / config types** → resolved from the container by type.
- **Kotlin default value present** → used if no binding exists for that parameter.
- **Nullable type, no binding** → resolves to `null`.
- **Required scalar** (`Int`, `Long`, `Boolean`, `String`) → must have a Kotlin default or be a
  field of a registered config object. Katalyst will not fabricate a scalar.
- **Required object, no binding** → fail fast at startup with `MissingDependencyError`.

```kotlin
class NotificationOrchestrator(
    private val gateway: RealtimeGateway,   // resolved by type
    private val auditTrail: AuditTrail?,    // null if no binding
    private val retryLimit: Int = 3         // default used; bare Int with no default would fail
) : Service
```

Deferral wrappers (`Provider<T>`, `Lazy<T>`, `() -> T`) are **not** supported. If you have a
cycle, restructure (often: have one side observe events instead of holding a reference).

## Qualifiers

Only when two implementations of the same type are intentionally registered:

```kotlin
import io.github.darkryh.katalyst.di.injection.InjectNamed

class PaymentService(
    @InjectNamed("stripe") private val gateway: PaymentGateway
) : Service
```

Keep injection type-only and annotation-free everywhere else.

## Resolving in Ktor handlers

Inside `katalystRouting` / `katalystMiddleware` / `katalystWebSockets`, use `ktInject`:

```kotlin
import io.github.darkryh.katalyst.ktor.extension.ktInject

val svc = call.ktInject<BookmarkService>()       // direct, on ApplicationCall
val settings by ktInject<JwtSettingsService>()    // property delegate
```

A route/middleware/scheduler function may also declare injected parameters directly:

```kotlin
fun Route.bookmarkRoutes(service: BookmarkService) = katalystRouting { /* use service */ }
```

## Programmatic container access

```kotlin
// from katalyst-core
interface KatalystContainer {
    fun <T : Any> get(type: KClass<T>, qualifier: String? = null): T
    fun <T : Any> getOrNull(type: KClass<T>, qualifier: String? = null): T?
    fun <T : Any> getAll(type: KClass<T>): List<T>
}
```

From a Ktor `Application`: `katalystContainer()` / `getKatalystContainer()` (in `katalyst-ktor`).
Prefer `ktInject` in handlers and constructor injection in components — direct container access
is for framework-level or advanced code.

## Validation and failure modes

Katalyst validates the whole graph before instantiating anything. Failures are typed
`ValidationError`s aggregated in a `ValidationReport`; fatal problems throw
`FatalDependencyValidationException` before the server binds a port.

| Error type | Meaning | Typical fix |
|------------|---------|-------------|
| `MissingDependencyError` | Required parameter has no binding | Provide it, make nullable, or default |
| `CircularDependencyError` | Mutual dependency | Break the cycle (observe via events) |
| `UninstantiableTypeError` | Type cannot be constructed | Make it concrete with an injectable ctor |
| `InstantiationFailureError` | Constructor threw | Fix the constructor logic |
| `SecondaryTypeBindingError` | Disallowed secondary interface bound | Don't bind non-allowlisted secondary types |
| `FeatureProvidedTypeError` / `WellKnownPropertyError` | Conflicts with a framework-provided type | Rename/remove the conflicting binding |

The internals (`DependencyAnalyzer`, `DependencyGraph`, `ComponentOrderComputer`,
`DependencyValidator`, `BindingPlanBuilder`, `InjectionStrategyResolver`) live in `katalyst-di`
and are not part of application code, but their error types appear in startup diagnostics — map
the message to the table above.
