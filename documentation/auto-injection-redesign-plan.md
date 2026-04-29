# Katalyst Auto-Injection Redesign Plan

This document captures the planned redesign of Katalyst's automatic injection workflow. It is intentionally detailed because the current behavior crosses several modules and startup phases: component discovery, constructor injection, Ktor route discovery, scheduler discovery, config loading, lifecycle initializers, event handlers, database tables, and Koin registration.

The primary goal is to make auto-injection predictable and easy to use without requiring application code to manually choose `Provider<T>`, `Lazy<T>`, or `() -> T`. Katalyst should decide the safest initialization strategy internally, fail early when it cannot do so, and produce diagnostics that explain the exact parameter or binding that caused the problem.

## Current Decision

Remove the public deferred-injection API from the recommended auto-wiring workflow:

- Remove `Provider<T>` from the public documentation and normal API surface.
- Remove `Lazy<T>` as a recommended constructor-injection pattern.
- Remove `() -> T` function-provider injection as a recommended constructor-injection pattern.
- Keep any temporary compatibility only if needed during implementation, but do not design the next stable workflow around user-selected deferred wrappers.

The new workflow should allow normal constructor and function parameters wherever possible:

```kotlin
class AuthenticationService(
    private val repository: AuthAccountRepository,
    private val eventBus: EventBus,
    private val jwtSettings: JwtSettingsService,
) : Service
```

```kotlin
fun Route.authRoutes(
    service: AuthenticationService,
) = katalystRouting {
    post("/login") {
        call.respond(service.login(call.receive()))
    }
}
```

```kotlin
fun scheduleDigest(
    config: DigestScheduleConfig,
) : SchedulerJobHandle = scheduler.scheduleCron(...)
```

Katalyst should infer the right strategy from the binding graph and callable context. If it cannot infer a safe strategy, startup should fail with a precise message before runtime traffic or background jobs begin.

## Coherence Check And Execution Policy

The plan must not replace explicit `Provider<T>` / `Lazy<T>` APIs with hidden, unsafe proxy behavior. Kotlin classes are final by default, and constructor parameters of concrete types require real instances. Therefore the first implementation must follow this execution policy:

- Prefer eager construction and deterministic ordering.
- Use `callBy` to support Kotlin default parameters.
- Use `null` only for nullable parameters with no binding.
- Use `@ConfigValue` or config objects for scalar values.
- Support injectable parameters in framework-invoked functions such as routes and scheduler methods.
- Fail clear and early for cycles that cannot be solved by normal ordering.
- Treat automatic internal deferral as a future extension, not a required first implementation.

This keeps the plan coherent: users do not need deferred wrappers, but Katalyst also does not pretend it can safely defer arbitrary concrete dependencies without proxies or explicit lifecycle boundaries.

## Execution Progress

Implemented:

- Added side-effect-free fatal diagnostic rendering through `FatalDependencyValidationException.renderReport()` and `renderDetailedReport()`.
- Removed direct stdout printing from the fatal dependency report path.
- Moved expected dependency-validation detail logs to `DEBUG` so the final diagnostic owns user-facing output.
- Made `DIConfiguration.kt` the owner that renders fatal dependency validation reports.
- Made `KatalystApplication.kt` avoid a second user-facing error for known `KatalystDIException` startup failures.
- Added `CallableInvoker.callMemberWithDefaults()` as the first shared invocation primitive.
- Added `ParameterResolver` so constructors, route functions, and scheduler methods share parameter rules.
- Moved constructor instantiation through `CallableInvoker.callConstructor()`.
- Updated route function installation to inject service/config parameters and fail fatally on broken route wiring.
- Updated scheduler runtime invocation to use `callBy` through `CallableInvoker`, fixing scheduler methods with Kotlin default parameters such as `fun scheduleDigest(intervalSeconds: Int = 60)`.
- Added scheduler config-object parameter injection and clear failure for required scalar scheduler parameters.
- Added `DiscoverySnapshot`, `BindingPlan`, `BindingPlanBuilder`, `TypeKey`, and `InjectionStrategyResolver` as the planning boundary used by component orchestration.
- Removed the public constructor-deferred API path: `Provider.kt`, `KoinProvider.kt`, parser support for `Provider<T>` / `Lazy<T>` / `Function0<T>`, deferred injection modes, and deferred-injection tests.
- Removed unused DI-side legacy classes: `DiscoverySummaryLogger`, `ErrorFormatter`, `PostRegistrationValidationException`, `ValidationLogicException`, `ScannerDIModule`, and `AutoBindingRegistrar.isAlreadyRegistered`.
- Updated user docs to describe normal parameter injection instead of deferred constructor wrappers.
- Added tests for side-effect-free diagnostic rendering, constructor defaults/nullability/scalar failures, route parameter injection, scheduler default parameters, scheduler config parameters, and scheduler scalar failure.

Remaining notes:

- Scanner integration utilities (`AutoDiscoveryEngine`, `KoinDiscoveryRegistry`, `ScannerModule`) are retained because the public scanner DSL still references them.
- Automatic internal deferral for arbitrary constructor cycles is intentionally not implemented because the coherent policy forbids hidden proxy behavior for final Kotlin classes.

## Existing Workflow Summary

### Bootstrap Entry Point

Current entry point:

- `katalyst-di/src/main/kotlin/io/github/darkryh/katalyst/di/KatalystApplication.kt`
- `katalyst-di/src/main/kotlin/io/github/darkryh/katalyst/di/config/DIConfiguration.kt`

Current flow:

1. `katalystApplication(args) { ... }` collects engine, database config, scan packages, and features.
2. `initializeDI()` resolves database config and server config.
3. `initializeKoinStandalone()` calls `bootstrapKatalystDI()`.
4. `bootstrapKatalystDI()` starts or augments global Koin.
5. `ComponentRegistrationOrchestrator.registerAllWithValidation()` discovers, analyzes, validates, sorts, and registers components.
6. Database tables are discovered and schema initialization runs.
7. Transaction adapters are registered.
8. Pre-start initializers run.
9. Ktor starts and discovered Ktor modules/routes are installed.
10. Runtime-ready initializers run after server readiness.

Problems:

- The workflow validates some things before registration, but not against the exact same model used for runtime invocation.
- Some paths fail fast, some silently skip candidates, and some log warnings while continuing.
- Several contexts perform parameter handling independently.

### Component Discovery

Current files:

- `katalyst-di/src/main/kotlin/io/github/darkryh/katalyst/di/internal/ComponentRegistrationOrchestrator.kt`
- `katalyst-di/src/main/kotlin/io/github/darkryh/katalyst/di/internal/AutoBindingRegistrar.kt`
- `katalyst-scanner/src/main/kotlin/io/github/darkryh/katalyst/scanner/scanner/ReflectionsTypeScanner.kt`

Current signals:

- `CrudRepository`
- `Component`
- `Service`
- `EventHandler`
- `KtorModule`
- `KatalystMigration`
- Exposed tables implementing Katalyst `Table`
- Top-level route/middleware/websocket functions using Katalyst DSL bytecode markers
- `AutomaticServiceConfigLoader`

Problems:

- Discovery is repeated in multiple phases.
- Empty discovery can produce noisy logs for optional categories.
- Discovery and registration do not produce a single durable model of what Katalyst intends to bind.
- Candidate functions with unsupported parameters are usually skipped or fail later instead of becoming structured validation errors.

### Dependency Analysis

Current files:

- `katalyst-di/src/main/kotlin/io/github/darkryh/katalyst/di/analysis/DependencyAnalyzer.kt`
- `katalyst-di/src/main/kotlin/io/github/darkryh/katalyst/di/analysis/DependencyGraph.kt`
- `katalyst-di/src/main/kotlin/io/github/darkryh/katalyst/di/analysis/ComponentOrderComputer.kt`
- `katalyst-di/src/main/kotlin/io/github/darkryh/katalyst/di/analysis/KnownPlatformTypes.kt`
- `katalyst-di/src/main/kotlin/io/github/darkryh/katalyst/di/validation/DependencyValidator.kt`

Current behavior:

- Constructor params are parsed.
- `Provider<T>`, `Lazy<T>`, and `() -> T` are treated as deferred dependencies.
- Deferred dependencies do not contribute hard startup-order edges.
- Known platform types are assumed to be available.
- Secondary interface bindings are collected.
- A topological order is computed for non-migration components.

Problems:

- Nullable dependencies and default-parameter dependencies are not modeled consistently.
- Primitive/value dependencies such as `Int`, `Long`, `Boolean`, `String`, and enums can become confusing missing Koin dependencies.
- Secondary interface dependencies are marked resolvable, but dependency-order edges are not always mapped to the concrete provider that must be registered first.
- Multiple providers for one interface are accepted during analysis but can fail later during registration.
- `KnownPlatformTypes` treats some classpath-visible contracts as available even when the feature has not actually been enabled.
- The validator can pass while actual instantiation still fails.

### Component Registration

Current files:

- `katalyst-di/src/main/kotlin/io/github/darkryh/katalyst/di/internal/AutoBindingRegistrar.kt`
- `katalyst-di/src/main/kotlin/io/github/darkryh/katalyst/di/internal/ServiceRegistry.kt`
- `katalyst-di/src/main/kotlin/io/github/darkryh/katalyst/di/internal/KtorModuleRegistry.kt`
- `katalyst-di/src/main/kotlin/io/github/darkryh/katalyst/di/internal/TableRegistry.kt`

Current behavior:

- Components are instantiated manually by reflection.
- Mutable well-known properties are injected after construction.
- Instances are registered into Koin using Koin internal APIs:
  - `BeanDefinition`
  - `SingleInstanceFactory`
  - `instanceRegistry.saveMapping`
- Secondary interfaces are registered as Koin secondary mappings.
- Some multibinding roles are handled outside Koin through registries.

Problems:

- Koin internals are risky for a library because Koin upgrades can break the registration layer.
- The real binding behavior is not represented as a first-class plan before registration.
- Secondary-type collision detection happens during registration instead of validation.
- Registries and Koin can drift because some things are registered in both and some only in one.

### Route, Middleware, And WebSocket Discovery

Current file:

- `katalyst-di/src/main/kotlin/io/github/darkryh/katalyst/di/internal/AutoBindingRegistrar.kt`

Current behavior:

- Static Kotlin top-level functions ending in `Kt` are scanned.
- First parameter must be `Route` or `Application`.
- Bytecode must call one of:
  - `katalystRouting`
  - `katalystMiddleware`
  - `katalystExceptionHandler`
  - `katalystWebSockets`
- `RouteFunctionModule.install()` invokes the method with only the receiver.

Problems:

- `fun Route.authRoutes(service: AuthenticationService)` can be discovered but cannot be invoked correctly today.
- Extra parameters are not supported by a common resolver.
- Invocation failures are caught and logged inside route module installation, which can hide broken route wiring.
- Users are forced to resolve dependencies inside route bodies through `ktInject<T>()`, even when a startup-scoped route dependency would be clearer.

### Scheduler Discovery

Current files:

- `katalyst-scheduler/src/main/kotlin/io/github/darkryh/katalyst/scheduler/lifecycle/SchedulerInitializer.kt`
- `katalyst-scheduler/src/main/kotlin/io/github/darkryh/katalyst/scheduler/SchedulerModules.kt`
- `katalyst-scheduler/src/main/kotlin/io/github/darkryh/katalyst/scheduler/SchedulerFeature.kt`

Current behavior:

- `SchedulerInitializer` runs as an `ApplicationReadyInitializer`.
- It scans all `ServiceRegistry` services.
- Candidate methods must return `SchedulerJobHandle`.
- Candidate methods may have no required parameters; optional parameters currently pass.
- Bytecode is inspected to ensure the method calls a scheduler method.
- Validated methods are invoked through `method.call(service)`.

Known bug:

```kotlin
fun scheduleDigest(intervalSeconds: Int = 60): SchedulerJobHandle
```

This can pass discovery because the parameter is optional, but `method.call(service)` does not apply Kotlin defaults. It should either call through `callBy` or be rejected earlier. The new design should support default parameters through the shared invocation engine.

Additional problems:

- Required parameters are silently skipped rather than reported as unsupported.
- Parameters such as config objects should be supported.
- Parameters such as raw `Int` should be supported only through default values, config binding, named binding, or explicit Koin binding.
- Scheduler validation is scheduler-specific instead of sharing parameter semantics with routes, constructors, lifecycle hooks, and config loaders.

### Configuration Auto-Wiring

Current files:

- `katalyst-config-provider/src/main/kotlin/io/github/darkryh/katalyst/config/provider/AutomaticServiceConfigLoader.kt`
- `katalyst-config-provider/src/main/kotlin/io/github/darkryh/katalyst/config/provider/AutomaticConfigLoaderDiscovery.kt`
- `katalyst-di/src/main/kotlin/io/github/darkryh/katalyst/di/internal/ComponentRegistrationOrchestrator.kt`

Current behavior:

- Automatic config loaders are discovered by scan package.
- Each loader produces a config object.
- Config objects are registered into Koin before component instantiation.
- Dependency analysis pre-discovers config types so services depending on config objects can validate.

Problems:

- Loader discovery is outside the main binding model.
- Config loader failures and config object registrations should be represented in the same startup plan.
- Primitive function parameters need a config binding story if they should be injectable.

### Lifecycle Initializers

Current files:

- `katalyst-di/src/main/kotlin/io/github/darkryh/katalyst/di/lifecycle/ApplicationInitializer.kt`
- `katalyst-di/src/main/kotlin/io/github/darkryh/katalyst/di/lifecycle/ApplicationReadyInitializer.kt`
- `katalyst-di/src/main/kotlin/io/github/darkryh/katalyst/di/lifecycle/InitializerRegistry.kt`
- `katalyst-di/src/main/kotlin/io/github/darkryh/katalyst/di/lifecycle/RuntimeReadyInitializerRunner.kt`

Current behavior:

- Initializers are discovered as components or Koin definitions.
- Ordering is deterministic by `order` and class name.
- Lifecycle methods themselves are no-arg contract methods.

Problems:

- Constructor injection into initializer implementations follows the same component injection risks.
- Runtime-ready activations such as scheduler are another parameter and invocation context, but not modeled as such.

### Events

Current files:

- `katalyst-di/src/main/kotlin/io/github/darkryh/katalyst/di/feature/EventSystemFeature.kt`
- `katalyst-events-bus/src/main/kotlin/io/github/darkryh/katalyst/events/bus/EventBusModule.kt`
- `katalyst-events-bus/src/main/kotlin/io/github/darkryh/katalyst/events/bus/EventHandlerRegistry.kt`

Current behavior:

- Event handlers are discovered as components.
- They are registered into `GlobalEventHandlerRegistry`.
- `EventSystemFeature.onKoinReady()` consumes handlers and registers them with `EventTopology`.

Problems:

- `EventBus` is treated as a known available platform type by analysis even when feature activation is the real condition.
- Handler constructor dependencies use the same component graph risk areas.
- Global registry movement should be part of a binding/activation plan.

## Design Goals

1. Application code should not need `Provider<T>`, `Lazy<T>`, or `() -> T` to work around DI ordering.
2. Katalyst should prefer eager construction and startup validation.
3. Katalyst may choose an internal deferred strategy only in lifecycle/function-boundary cases that are safe and explicitly modeled; arbitrary concrete constructor cycles fail with diagnostics.
4. All auto-invoked constructors and functions should share one parameter-resolution contract.
5. Unsupported parameters should fail at startup with a precise diagnostic.
6. Optional, nullable, defaulted, config-bound, and named parameters should behave consistently everywhere.
7. Feature-provided contracts should be based on enabled features, not classpath guesses.
8. Route functions and scheduler functions should support useful parameters instead of forcing all injection into function bodies.
9. Logs should be concise at `INFO` and detailed at `DEBUG`.
10. The final implementation should be covered by tests that reproduce the current painful cases.

## Proposed Architecture

### 1. DiscoverySnapshot

Introduce a durable snapshot created once during bootstrap.

Suggested location:

- `katalyst-di/src/main/kotlin/io/github/darkryh/katalyst/di/discovery/DiscoverySnapshot.kt`
- `katalyst-di/src/main/kotlin/io/github/darkryh/katalyst/di/discovery/DiscoverySnapshotBuilder.kt`

Responsibilities:

- Scan packages once.
- Store discovered component classes.
- Store discovered table classes and table instances.
- Store discovered route functions.
- Store discovered scheduler methods or enough service metadata to derive them.
- Store discovered automatic config loaders.
- Store enabled feature metadata.
- Store discovery warnings with structured reason codes.

Sketch:

```kotlin
internal data class DiscoverySnapshot(
    val components: List<ComponentCandidate>,
    val tables: List<TableCandidate>,
    val routeFunctions: List<CallableCandidate>,
    val configLoaders: List<ConfigLoaderCandidate>,
    val enabledFeatures: List<FeatureCandidate>,
    val warnings: List<DiscoveryWarning>,
)
```

Validation rule:

- Discovery should not silently discard unsupported candidates. It can mark them as invalid or skipped, but the binding validator decides whether that is fatal.

### 2. BindingPlan

Introduce a plan that represents what Katalyst intends to register and invoke.

Suggested location:

- `katalyst-di/src/main/kotlin/io/github/darkryh/katalyst/di/planning/BindingPlan.kt`
- `katalyst-di/src/main/kotlin/io/github/darkryh/katalyst/di/planning/BindingPlanBuilder.kt`

Responsibilities:

- Convert discovery candidates into primary bindings.
- Compute secondary interface bindings.
- Compute multibindings.
- Attach qualifiers if supported.
- Attach feature-provided types.
- Attach config-provided types.
- Attach callable invocation plans for routes, scheduler methods, config loader validation, and lifecycle activations.
- Produce dependency edges between actual providers, not only requested types.

Sketch:

```kotlin
internal data class BindingPlan(
    val bindings: List<BindingDefinition>,
    val invocations: List<CallableInvocationPlan>,
    val tables: List<TableBindingDefinition>,
    val featureTypes: Set<TypeKey>,
    val configTypes: Set<TypeKey>,
)
```

```kotlin
internal data class BindingDefinition(
    val primaryType: TypeKey,
    val implementationType: KClass<*>,
    val secondaryTypes: Set<TypeKey>,
    val lifecycle: BindingLifecycle,
    val source: BindingSource,
)
```

The plan is the source of truth for validation, ordering, and registration.

### 3. TypeKey

Introduce one type identity model instead of passing bare `KClass<*>` everywhere.

Suggested location:

- `katalyst-di/src/main/kotlin/io/github/darkryh/katalyst/di/model/TypeKey.kt`

Needs:

- KClass type.
- Optional qualifier.
- Optional multibinding marker.
- Optional source metadata.

Sketch:

```kotlin
internal data class TypeKey(
    val type: KClass<*>,
    val qualifier: String? = null,
)
```

Why:

- `PaymentGateway` and `@InjectNamed("stripe") PaymentGateway` are different requests.
- Ambiguity validation requires knowing qualifier and provider set.
- Interface resolution and ordering must point from a request key to a selected provider key.

### 4. ParameterResolver

Introduce one resolver for all auto-wired parameters.

Suggested location:

- `katalyst-di/src/main/kotlin/io/github/darkryh/katalyst/di/invocation/ParameterResolver.kt`
- `katalyst-di/src/main/kotlin/io/github/darkryh/katalyst/di/invocation/ParameterResolution.kt`

Supported contexts:

- Constructor injection.
- Route function invocation.
- Middleware function invocation.
- WebSocket route function invocation.
- Scheduler method invocation.
- Config loader `loadConfig` and `validate` calls if kept reflective.
- Future lifecycle callable parameters if the contract is expanded.

Parameter resolution states:

```kotlin
internal enum class ParameterResolutionKind {
    RECEIVER,
    EAGER_BINDING,
    INTERNAL_DEFERRED_BINDING,
    DEFAULT_VALUE,
    NULL_VALUE,
    CONFIG_VALUE,
    NAMED_BINDING,
    UNSUPPORTED,
}
```

Rules:

- Receiver parameters are supplied by the invocation context.
- Default parameters are omitted from `callBy`.
- Nullable parameters become `null` only when no binding exists.
- Non-null parameters require a binding, config value, receiver, or default.
- Primitive/value types require a default, config binding, named binding, or explicit Koin binding.
- Multiple providers require disambiguation.
- Missing feature types fail with feature-specific guidance.
- Internal deferred binding is allowed only when the planner explicitly marks it safe.

### 5. CallableInvoker

Introduce one invocation engine around Kotlin reflection.

Suggested location:

- `katalyst-di/src/main/kotlin/io/github/darkryh/katalyst/di/invocation/CallableInvoker.kt`

Responsibilities:

- Invoke constructors and functions through `callBy`.
- Apply default arguments by omitting defaulted params.
- Pass receivers and extension receivers correctly.
- Resolve value parameters through `ParameterResolver`.
- Return structured failures.

Important:

- This fixes the scheduler default parameter bug.
- This allows route functions to accept injectable parameters.
- This prevents hidden runtime exceptions caused by inconsistent reflection calls.

### 6. InjectionStrategyResolver

Introduce internal strategy selection. This replaces user-facing `Provider<T>` / `Lazy<T>` decisions without promising transparent proxying for arbitrary constructor cycles.

Suggested location:

- `katalyst-di/src/main/kotlin/io/github/darkryh/katalyst/di/planning/InjectionStrategyResolver.kt`

Strategies:

```kotlin
internal enum class InjectionStrategy {
    EAGER,
    INTERNAL_DEFERRED,
    OPTIONAL_NULL,
    DEFAULT_ARGUMENT,
    CONFIG_VALUE,
    FAIL,
}
```

Rules:

- Prefer `EAGER`.
- Use `DEFAULT_ARGUMENT` for defaulted parameters.
- Use `OPTIONAL_NULL` for nullable parameters when no binding exists.
- Use `CONFIG_VALUE` for explicit config-bound values.
- Use `INTERNAL_DEFERRED` only when:
  - The dependency is resolved through a framework-controlled invocation boundary, such as route installation, scheduler activation, or another lifecycle call.
  - The selected edge can be deferred safely without creating a fake concrete instance.
  - Runtime resolution will fail fast before the dependent behavior is used where possible.
- Use `FAIL` when:
  - The edge cannot be deferred safely.
  - There are ambiguous providers.
  - A primitive/value parameter has no source.
  - A required feature was not enabled.

Important constraint:

- Kotlin concrete classes are final by default, so transparent proxying is unsafe as a default strategy.
- Auto-deferral is safest for framework invocation boundaries. Interface-based deferral may be considered later only if an explicit proxy strategy is added and tested.
- For concrete constructor cycles that cannot be solved by ordering, fail with a clear message and suggest extracting an interface or restructuring the dependency.

### 7. Feature Capability Model

Extend `KatalystFeature` with provided-type metadata.

Current file:

- `katalyst-di/src/main/kotlin/io/github/darkryh/katalyst/di/feature/KatalystFeature.kt`

Proposed addition:

```kotlin
interface KatalystFeature {
    val id: String
    fun provideModules(): List<Module>
    fun onKoinReady(koin: Koin) {}
    val providedTypes: Set<KClass<*>>
        get() = emptySet()
}
```

Feature examples:

- Scheduler feature provides `SchedulerService`.
- Events feature provides `EventBus`, `ApplicationEventBus`, `EventTopology`, `EventHandlerRegistry`.
- Config provider feature provides `ConfigProvider`.
- Server configuration feature provides `ServerConfiguration` and `ServerDeploymentConfiguration`.

Validation should use enabled feature capabilities, not `KnownPlatformTypes` classpath guesses.

### 8. Config Value Injection

Add explicit value injection for primitives and scalar values.

Suggested annotation:

- `katalyst-di/src/main/kotlin/io/github/darkryh/katalyst/di/injection/ConfigValue.kt`

Sketch:

```kotlin
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
annotation class ConfigValue(
    val key: String,
)
```

Examples:

```kotlin
fun scheduleDigest(
    @ConfigValue("jobs.digest.intervalSeconds")
    intervalSeconds: Int = 60,
): SchedulerJobHandle
```

```kotlin
class RetryPolicy(
    @ConfigValue("http.retry.maxAttempts")
    val maxAttempts: Int = 3,
)
```

Rules:

- If config provider is enabled and key exists, use config value.
- If key is missing and parameter has default, use default.
- If key is missing and nullable, use null.
- If key is missing and required, fail at startup.
- If config provider is not enabled and there is no default, fail with a feature hint.

This solves the `Int` and primitive-parameter pain without guessing arbitrary values.

## Public API Direction

### Keep

- `Service`
- `Component`
- `CrudRepository`
- `Table`
- `KtorModule`
- `EventHandler`
- `ApplicationInitializer`
- `ApplicationReadyInitializer`
- `AutomaticServiceConfigLoader`
- `InjectNamed`, if multiple implementations remain supported through named bindings
- New `ConfigValue`

### Remove From Recommended Use

- `Provider<T>`
- `Lazy<T>` as a Katalyst DI pattern
- `() -> T` as a Katalyst DI pattern

### Compatibility Choice

Because Katalyst is alpha and the user explicitly accepts breaking changes, prefer removal over long migration support.

Recommended breaking-change plan:

1. Delete `Provider.kt`.
2. Delete parser support for `Provider<T>`, `Lazy<T>`, and `Function0<T>` as special Katalyst modes.
3. Remove deferred-injection documentation from `README.md`, `documentation/README.md`, `documentation/bootstrap.md`, and `documentation/auto-wiring.md`.
4. Replace tests that exercise provider/lazy/function injection with tests for automatic internal strategy selection.
5. Keep Kotlin `lazy {}` usage inside application code if users want it manually, but it is no longer a Katalyst injection feature.

## Validation Rules

### Constructor Parameters

Allowed:

```kotlin
class A(val b: B)
class A(val b: B?)
class A(val retries: Int = 3)
class A(@ConfigValue("a.retries") val retries: Int = 3)
class A(@InjectNamed("primary") val gateway: Gateway)
```

Rejected unless explicitly bound:

```kotlin
class A(val retries: Int)
class A(val name: String)
class A(val enabled: Boolean)
```

Reason:

- Scalar constructor values should not be guessed from DI.

### Route Functions

Recommended support:

```kotlin
fun Route.authRoutes(service: AuthenticationService)
fun Application.securityMiddleware(settings: JwtSettingsService)
fun Route.userRoutes(config: UserRoutesConfig)
```

Rejected:

```kotlin
fun Route.authRoutes(retries: Int)
```

unless the parameter has default, config binding, named binding, or explicit Koin binding.

Route invocation failures should be fatal during startup, not logged and swallowed.

### Scheduler Methods

Allowed:

```kotlin
fun scheduleDigest(): SchedulerJobHandle
fun scheduleDigest(config: DigestScheduleConfig): SchedulerJobHandle
fun scheduleDigest(intervalSeconds: Int = 60): SchedulerJobHandle
fun scheduleDigest(@ConfigValue("jobs.digest.intervalSeconds") intervalSeconds: Int = 60): SchedulerJobHandle
```

Rejected:

```kotlin
fun scheduleDigest(intervalSeconds: Int): SchedulerJobHandle
```

unless there is a deliberate binding source.

Scheduler discovery should report invalid scheduler methods with reasons.

### Interfaces And Multiple Implementations

Allowed:

```kotlin
interface PaymentGateway
class StripeGateway : PaymentGateway, Component

class PaymentService(val gateway: PaymentGateway) : Service
```

if exactly one implementation exists.

Rejected:

```kotlin
class StripeGateway : PaymentGateway, Component
class PaypalGateway : PaymentGateway, Component
class PaymentService(val gateway: PaymentGateway) : Service
```

unless there is a qualifier or explicit primary/default selection.

### Cycles

Allowed only if the planner can solve it safely.

Example for future proxy-capable solving through interface-bound internal deferred edge:

```kotlin
interface APort
class A(val b: B) : APort, Service
class B(val a: APort) : Service
```

Rejected in the first implementation:

```kotlin
class A(val b: B) : Service
class B(val a: A) : Service
```

unless a future safe construction strategy is implemented and tested.

The failure should explain:

- The cycle path.
- Which edge cannot be safely deferred.
- Suggested fixes:
  - Extract an interface.
  - Split responsibilities.
  - Move runtime behavior into an initializer or route/scheduler callable.

## Implementation Phases

### Phase 1: Characterization Tests

Add tests that reproduce current failures before changing internals.

Targets:

- `katalyst-di/src/test/kotlin/io/github/darkryh/katalyst/di/...`
- `katalyst-scheduler/src/test/kotlin/io/github/darkryh/katalyst/scheduler/...`
- `katalyst-ktor/src/test/kotlin/io/github/darkryh/katalyst/ktor/...`
- `samples/katalyst-example/src/test/kotlin/...` for end-to-end verification

Cases:

- Scheduler method with default `Int` should not crash.
- Scheduler method with required `Int` should fail with clear validation.
- Scheduler method with config object parameter should work.
- Route function with service parameter should work.
- Route function with required primitive should fail with clear validation.
- Constructor nullable dependency should pass with null when no binding exists.
- Constructor primitive without default should fail with clear validation.
- Interface dependency should order concrete provider before dependent component.
- Two providers for one interface should fail before registration.
- Disabled feature dependency should fail before runtime.
- Concrete constructor cycle should fail with cycle path and fix suggestion.

### Phase 2: Introduce Planning Model In Parallel

Add new planning classes without replacing runtime yet.

Files to add:

- `di/model/TypeKey.kt`
- `di/discovery/DiscoverySnapshot.kt`
- `di/discovery/DiscoverySnapshotBuilder.kt`
- `di/planning/BindingPlan.kt`
- `di/planning/BindingPlanBuilder.kt`
- `di/planning/InjectionStrategyResolver.kt`
- `di/invocation/ParameterResolver.kt`
- `di/invocation/CallableInvoker.kt`

Acceptance:

- Existing tests still pass.
- New unit tests can build plans from artificial candidates without starting Koin.

### Phase 3: Replace DependencyAnalyzer With BindingPlan Validation

Refactor or replace:

- `DependencyAnalyzer`
- `DependencyGraph`
- `DependencyValidator`
- `ComponentOrderComputer`
- `KnownPlatformTypes`

New validation passes:

1. Binding availability validation.
2. Parameter source validation.
3. Ambiguous provider validation.
4. Feature capability validation.
5. Cycle validation.
6. Internal deferred edge validation.
7. Callable invocation validation.
8. Route/scheduler unsupported-parameter validation.

Acceptance:

- Validation errors describe the exact component, method, parameter, requested type, and suggested fix.
- Validator and runtime registration consume the same `BindingPlan`.

### Phase 4: Replace Constructor Instantiation With CallableInvoker

Refactor:

- `AutoBindingRegistrar.instantiate`

New behavior:

- Constructors use shared parameter resolution.
- Default parameters use `callBy`.
- Nullable missing dependencies become null.
- Required scalars without source fail.
- Named bindings work through `TypeKey`.

Acceptance:

- Existing constructor-injection tests pass.
- New scalar/default/nullable tests pass.

### Phase 5: Route Function Injection

Refactor:

- `AutoBindingRegistrar.registerRouteFunctions`
- `RouteFunctionModule.install`

New behavior:

- Route functions support injectable value parameters after receiver.
- Invocation uses `CallableInvoker`.
- Route installation failure is fatal.
- Unsupported route params become validation errors before install.

Acceptance:

- `fun Route.routes(service: MyService)` works.
- Existing no-extra-param routes continue to work.
- Unsupported parameters fail with clear message.

### Phase 6: Scheduler Method Injection

Refactor:

- `SchedulerInitializer.discoverCandidateMethods`
- `SchedulerInitializer.validateCandidatesByBytecode`
- `SchedulerInitializer.onRuntimeReady`

New behavior:

- Discovery returns valid and invalid candidates with reasons.
- Invocation uses `CallableInvoker`.
- Default parameters work.
- Config object parameters work.
- Required primitive parameters fail validation.
- Bytecode validation still ensures the method actually registers a scheduler job.

Acceptance:

- The known default-`Int` scheduler bug is fixed.
- Scheduler startup summary lists registered jobs and invalid skipped methods.
- Invalid methods are fatal if they look intentional and cannot be invoked safely.

### Phase 7: Feature Capabilities

Refactor:

- `KatalystFeature`
- `SchedulerFeature`
- `EventSystemFeature`
- Config provider feature
- Server configuration feature
- Any migrations/websocket feature with provided DI contracts

Remove or shrink:

- `KnownPlatformTypes`

Acceptance:

- Dependency on `SchedulerService` fails if `enableScheduler()` is missing.
- Dependency on `EventBus` fails if `enableEvents()` is missing.
- Dependency on `ConfigProvider` fails if config provider is missing.
- Error messages include the correct `enableX()` suggestion.

### Phase 8: Koin Registration Adapter

Refactor:

- `AutoBindingRegistrar.registerInstanceWithKoin`

Introduce:

- `KoinBindingAdapter`

Preferred behavior:

- Use public Koin module APIs where feasible.
- If internals are still required, isolate them in one file and cover with tests.

Acceptance:

- Koin registration behavior is tested through `TypeKey` primary and secondary bindings.
- Multibinding behavior is explicit.
- Koin upgrade risk is contained.

### Phase 9: Remove Public Deferred API

Remove or deprecate depending on chosen breaking-change timing:

- `katalyst-di/src/main/kotlin/io/github/darkryh/katalyst/di/injection/Provider.kt`
- special parser handling in `DependencyRequestParser.kt`
- `InjectionMode.PROVIDER`
- `InjectionMode.LAZY`
- `InjectionMode.FUNCTION`
- tests named around deferred provider/lazy/function injection

Update:

- `README.md`
- `documentation/README.md`
- `documentation/bootstrap.md`
- `documentation/auto-wiring.md`

Replacement docs:

- "Katalyst chooses eager or internal deferred injection automatically."
- "Use normal constructor parameters."
- "Use `@ConfigValue` for scalar values."
- "Use `@InjectNamed` only for multiple implementations."

### Phase 10: Logging Cleanup

Refactor:

- `BootstrapProgressLogger`
- `DiscoverySummaryLogger`
- `ComponentRegistrationOrchestrator`
- `ReflectionsTypeScanner`
- `SchedulerInitializer`
- `AutoBindingRegistrar`
- `FatalDependencyValidationException`
- `ErrorFormatter`
- bootstrap catch blocks in `DIConfiguration.kt` and `KatalystApplication.kt`

Default `INFO` should include one concise startup summary:

```text
Katalyst DI initialized: components=18, routes=4, schedulerJobs=2, tables=3, features=5, duration=842ms
```

Move to `DEBUG`:

- Per-component registration.
- Topological order.
- Classpath scan details.
- Bytecode validation details.
- Empty optional category messages.
- Lifecycle boxes.

Fatal errors remain prominent and structured, but they must be emitted once.

Current duplicate-output risk:

- `FatalDependencyValidationException.printReport()` logs the report with `logger.error(report)` and also calls `println(report)`.
- `FatalDependencyValidationException.printDetailedReport()` does the same.
- `bootstrapKatalystDI()` catches the same exception, logs another fatal summary, marks bootstrap progress failed, and rethrows.
- `katalystApplication()` catches the rethrown exception and logs "Failed to start Katalyst application" with the full exception again.
- `BootstrapProgress.failLifecycle()` logs another failure line with the same message.
- `DependencyValidator` logs individual errors while building a report that is later logged again.

Target behavior:

- Validation code collects errors; it should not log every expected validation failure as an error while still returning a report.
- The exception should carry a structured report and concise message; it should not print to stdout.
- Exactly one top-level owner should render the final diagnostic.
- Lower layers may log debug context with stack traces, but not user-facing duplicate errors.
- Fatal diagnostic rendering should support a concise default report and an opt-in verbose report.

Proposed diagnostics API:

```kotlin
interface KatalystDiagnosticException {
    val diagnostic: StartupDiagnostic
}

data class StartupDiagnostic(
    val title: String,
    val summary: String,
    val details: List<DiagnosticDetail>,
    val suggestions: List<String>,
)
```

Rendering rules:

- Library internals throw diagnostic exceptions.
- The application/bootstrap boundary logs the diagnostic exactly once.
- `KATALYST_DI_VERBOSE=true` or `-Dkatalyst.di.verbose=true` expands details.
- No diagnostic path uses `println`; logging backend owns output.
- Stack traces are debug-level unless the failure is an unexpected internal bug.
- Validation failures are user/setup errors; internal contradictions are framework bugs.

Specific cleanup:

- Remove `println(report)` from `FatalDependencyValidationException`.
- Replace `printReport()` with `renderReport()` or `diagnosticReport()` so exceptions do not log themselves.
- Make `DependencyValidator` use debug logs for expected validation checks and leave user-facing output to the final diagnostic renderer.
- Make `DIConfiguration.kt` catch blocks avoid restating the same validation message after the report has been rendered.
- Make `KatalystApplication.kt` avoid logging a second full stack trace for known `KatalystDIException` failures.
- Keep full stack traces for non-diagnostic/unexpected exceptions.

### Phase 11: Legacy And Unused Code Cleanup

The redesign should remove or consolidate code that was useful for earlier experiments but is not part of the final auto-injection path.

Initial cleanup candidates to validate:

- `katalyst-scanner/src/main/kotlin/io/github/darkryh/katalyst/scanner/integration/AutoDiscoveryEngine.kt`
  - Appears to be used mainly by scanner DSL helpers, not by the current Katalyst bootstrap path.
  - Logs high-noise discovery output and performs best-effort instantiation from Koin.
  - Decision: either move to test/sample-only API, mark as low-level scanner utility, or remove if no public contract depends on it.
- `katalyst-scanner/src/main/kotlin/io/github/darkryh/katalyst/scanner/integration/KoinDiscoveryRegistry.kt`
  - Wraps `InMemoryDiscoveryRegistry` and Koin lookups.
  - Not part of the current component orchestration path.
  - Decision: keep only if scanner DSL remains public; otherwise remove with `AutoDiscoveryEngine`.
- `katalyst-scanner/src/main/kotlin/io/github/darkryh/katalyst/scanner/integration/ScannerModule.kt`
  - Provides generic scanner Koin modules, while Katalyst DI currently creates scanner utilities directly.
  - Decision: remove from the main runtime story if it is not documented as a supported standalone scanner feature.
- `katalyst-di/src/main/kotlin/io/github/darkryh/katalyst/di/lifecycle/DiscoverySummaryLogger.kt`
  - Has a global summary object, but current orchestration does not appear to use it as the source of discovery output.
  - Decision: replace with the new `DiscoverySnapshot` summary or remove.
- `katalyst-di/src/main/kotlin/io/github/darkryh/katalyst/di/error/ErrorFormatter.kt`
  - Overlaps heavily with `FatalDependencyValidationException.generateDetailedReport()`.
  - Decision: replace both with one diagnostic renderer.
- `katalyst-di/src/main/kotlin/io/github/darkryh/katalyst/di/exception/PostRegistrationValidationException.kt`
  - Search shows no direct runtime throw site in the current DI flow.
  - Decision: remove or replace with a new internal invariant diagnostic if the binding plan makes post-registration validation unnecessary.
- `katalyst-di/src/main/kotlin/io/github/darkryh/katalyst/di/exception/ValidationLogicException.kt`
  - Search suggests it is referenced by transaction logging filters and exists as a conceptual internal-error type, but not clearly thrown.
  - Decision: replace with one framework-bug diagnostic type if still needed.
- `katalyst-di/src/main/kotlin/io/github/darkryh/katalyst/di/module/ScannerDIModule.kt`
  - Currently logs that scanner utilities are direct imports and does not register meaningful runtime objects.
  - Decision: remove from bootstrap if no actual DI binding is required.
- `AutoBindingRegistrar.isAlreadyRegistered`
  - Private helper currently appears unused.
  - Decision: remove during registrar rewrite.
- `Provider.kt`, `KoinProvider.kt`, and deferred parsing in `DependencyRequestParser.kt`
  - Remove as part of the public deferred API removal.
- `InjectionMode.PROVIDER`, `InjectionMode.LAZY`, and `InjectionMode.FUNCTION`
  - Remove or replace with internal-only strategy values that are not exposed to users.

Cleanup validation steps:

1. Confirm each candidate with `rg` before deletion.
2. Check whether public docs mention the candidate.
3. Check whether tests only exercise the candidate because it exists, not because it supports the product path.
4. Remove dead code in small commits grouped by module.
5. Run targeted module tests after each cleanup group.
6. Keep public scanner utilities only if they are intentionally part of Katalyst's supported standalone scanner API.

## Test Matrix

### DI Constructor Tests

- Direct service dependency resolves eagerly.
- Nullable missing dependency injects null.
- Default primitive constructor parameter uses default.
- Required primitive constructor parameter fails.
- `@ConfigValue` primitive constructor parameter resolves from config.
- Missing config key with default uses default.
- Missing config key without default fails.
- Named dependency resolves exact qualified binding.
- Two unqualified providers fail.
- Interface dependency selects single provider and orders it correctly.
- Disabled feature dependency fails with enable-feature hint.

### Cycle Tests

- Simple concrete cycle fails with clear cycle path.
- Interface cycle is solved only if the strategy resolver marks the selected edge safe.
- Ambiguous cycle-breaking choices fail with explicit reason.
- Internal deferred edge still validates target availability.

### Route Function Tests

- `fun Route.routes()` still works.
- `fun Route.routes(service: Service)` works.
- `fun Application.middleware(config: ConfigObject)` works.
- `fun Route.routes(value: Int = 1)` works.
- `fun Route.routes(value: Int)` fails.
- Route bytecode DSL detection still rejects unrelated extension functions.
- Route invocation failures are fatal, not swallowed.

### Scheduler Tests

- No scheduler methods results in clean summary.
- No services results in clean summary.
- Multiple scheduler methods on one service are retained.
- Default-parameter scheduler method works.
- Config-object scheduler method works.
- Required primitive scheduler method fails.
- Method returning `SchedulerJobHandle` but not calling scheduler is rejected by bytecode validation.
- Kotlin-mangled scheduler method names still validate.
- Scheduler invocation errors include service, method, parameter, and cause.

### Config Loader Tests

- Automatic config loader discovery contributes config type to binding plan.
- Missing config provider fails when automatic config loaders exist.
- Config loader validation failure is fatal.
- Config object constructor injection works.
- Config type duplicate providers fail clearly.

### Feature Tests

- `SchedulerService` requires scheduler feature.
- `EventBus` requires events feature.
- `ConfigProvider` requires config provider feature.
- WebSocket route support requires websocket feature where applicable.
- Feature-provided types appear in binding plan only when feature enabled.

### Registration Tests

- Primary binding can be resolved from Koin.
- Secondary binding can be resolved from Koin.
- Multibinding roles allow multiple providers.
- Non-multibinding secondary collisions fail during plan validation.
- Registries and Koin agree on registered services, routes, handlers, and initializers.

### End-To-End Tests

- Sample app boots with routes, events, scheduler, websockets, migrations.
- Sample route function can receive `AuthenticationService` as a parameter.
- Sample scheduler method can receive a config object.
- Broken sample fixture fails before server start with structured diagnostics.

### Diagnostics Tests

- Fatal DI validation emits exactly one user-facing error report.
- Known setup errors do not print to stdout.
- Known setup errors do not produce duplicate stack traces.
- Verbose mode expands the diagnostic report.
- Non-verbose mode shows concise title, summary, top issues, and fixes.
- Unexpected internal exceptions still include stack traces.
- `DependencyValidator` does not log each expected validation error at `ERROR` before the final report.
- Bootstrap failure paths do not restate the same error at every catch boundary.

## Error Message Standard

All validation errors should include:

- Phase.
- Component/function name.
- Parameter name.
- Requested type.
- Resolution sources considered.
- Why resolution failed.
- Concrete fix suggestions.

Example:

```text
Katalyst auto-injection validation failed.

Location:
  Scheduler method: UserProfileService.scheduleProfileDigest(intervalSeconds: Int)

Problem:
  Parameter 'intervalSeconds' is a required scalar value and has no injection source.

Resolution sources checked:
  - default argument: not present
  - @ConfigValue: not present
  - @InjectNamed: not present
  - Koin binding: not found

Fix:
  - Add a default value: intervalSeconds: Int = 60
  - Or bind it to config: @ConfigValue("jobs.profileDigest.intervalSeconds")
  - Or inject a config object: ProfileDigestConfig
```

## Migration Notes

Because Katalyst is still alpha, prefer direct breaking changes:

1. Remove public deferred wrappers from docs and tests.
2. Fail fast if existing code still uses `Provider<T>`.
3. Provide a clear error:

```text
Provider<T> injection is no longer part of Katalyst auto-wiring.
Use a normal parameter type. Katalyst will choose eager or internal deferred resolution when safe.
```

4. If too disruptive during implementation, support wrappers temporarily behind deprecated behavior, but do not keep them in final docs.

## Implementation Order Recommendation

1. Add characterization tests for current bugs.
2. Add `TypeKey`, `ParameterResolver`, and `CallableInvoker`.
3. Fix scheduler invocation with `CallableInvoker`.
4. Add route function parameter injection with `CallableInvoker`.
5. Add `BindingPlan` and migrate dependency validation onto it.
6. Add feature capabilities and remove `KnownPlatformTypes` assumptions.
7. Add `ConfigValue` for scalar/value params.
8. Replace constructor instantiation with shared resolver.
9. Isolate Koin internal registration.
10. Remove public deferred API and update docs.
11. Replace duplicate exception/report rendering with one diagnostic renderer.
12. Reduce startup logging noise.
13. Remove or consolidate legacy scanner/DI code that is not part of the final workflow.
14. Run module tests and sample end-to-end tests.

## Acceptance Criteria

The redesign is complete when:

- Users can write normal constructor parameters for most dependencies.
- Users can write route and scheduler functions with injectable parameters.
- Users do not need `Provider<T>`, `Lazy<T>`, or `() -> T`.
- Required scalar values either have defaults, config bindings, named bindings, or explicit Koin bindings.
- Missing dependencies fail during startup validation.
- Ambiguous dependencies fail during startup validation.
- Disabled feature dependencies fail with the correct feature hint.
- Scheduler default parameters work.
- Route function parameter injection works.
- Runtime logs are concise by default.
- Fatal validation errors are shown once, with no `println` duplication.
- Legacy/unused auto-discovery code is either removed or documented as an intentional public utility.
- The validation test matrix passes.
