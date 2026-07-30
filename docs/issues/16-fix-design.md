# Issue #16 — long-term fix design

> **Status: implemented.** Phases 0–2 of this design have shipped. See
> [§7 What shipped](#7-what-shipped) for the delivered change set, the deviations from the
> original plan, and how each fix was proven necessary.


Companion to [`16-migration-feature-never-executes.md`](./16-migration-feature-never-executes.md).
That doc proves the bug. This one answers: *what is the correct fix for a Spring-Boot-style
framework, and how do we make this class of bug impossible to reintroduce?*

---

## 1. The real defect is not migrations

Migrations are the symptom. The architectural defect is that **the knowledge of "what is a
Katalyst extension point" is duplicated across ~15 sites**, with no single source of truth and no
compiler or test forcing them to agree.

For any framework contract type (`Service`, `Component`, `CrudRepository`, `EventHandler`,
`KtorModule`, `KatalystMigration`, `StartupHook`, `ReadyHook`), the runtime must know eight
independent facts. Today each fact lives in a different `when`-block, `setOf(...)`, or `if`:

| Fact | Where it lives today |
| --- | --- |
| Its scan category name + base type | `ComponentRegistrationOrchestrator.kt:138-147` (hardcoded list) |
| Is empty discovery normal? | `AutoBindingRegistrar.kt:129-138` (`when` block) |
| Which base class to register under | `ComponentRegistrationOrchestrator.kt:406-419` (`when` block) |
| Excluded from secondary bindings | `AutoBindingRegistrar.kt:246-254` (`reserved` set) |
| Is it multi-instance? | `AutoBindingRegistrar.kt:659-662` (`multiBindingSecondaryTypes`) |
| Which registry receives it | `AutoBindingRegistrar.kt:631-636` (`is StartupHook` / `is ReadyHook`) |
| Does it join the dependency graph? | `DependencyGraph.kt:140-153`, `ComponentOrderComputer.kt:69`, `DependencyValidator.kt:161-169` |
| Registration ordering | `ComponentRegistrationOrchestrator.kt:244-257` ("migrations last") |
| The "enable this feature" hint | `ValidationError.kt:268` |

`KatalystMigration` was correctly added to the *discovery* and *ordering* sites and correctly
excluded from the *dependency graph* sites — but it was never given a lookup mechanism. It landed
in `reserved` (excluded from container bindings) **without** landing in
`multiBindingSecondaryTypes` (which is what grants a registry). It fell between the two
mechanisms, and nothing detected that.

**That is the bug to fix.** Patching the migration path alone leaves the next extension point to
fall into the same gap.

## 2. Two parallel mechanisms exist, and the wrong one is documented as broken

Katalyst has two ways to find "all instances of a framework type":

- **Container lookup** — `context.getAll<T>()`, backed by Koin's `Scope.getAll`.
- **Static registries** — `StartupHookRegistry`, `ReadyHookRegistry`, `ServiceRegistry`,
  `TableRegistry`, `KtorModuleRegistry`, `GlobalEventHandlerRegistry`, all coordinated by
  `RegistryManager`/`ResettableRegistry`.

The registries are justified in-code by this claim (`StartupHookRegistry.kt`):

> Koin cannot reliably represent multiple unqualified secondary bindings for the same interface key.

**That claim is measurably wrong for `getAll`.** A probe registering three `KatalystMigration`
implementations that all declare `KatalystMigration` as a secondary type:

```
PROBE getAll size = 3
PROBE ids = [20240101_a, 20240102_b, 20240103_c]
PROBE get<KatalystMigration> = 20240103_c        <-- silent last-wins
PROBE get<MigrationA> = 20240101_a
PROBE get<MigrationB> = 20240102_b
PROBE get<MigrationC> = 20240103_c
```

Koin returns **all three** from `getAll`. What is genuinely unreliable is single-resolution
`get<T>()` on a contended key — it silently returns the last registration instead of failing.
The registries were built to work around the wrong half of the problem.

Consequence: the container path is not a dead end. It works, and `TempMigrationGetAllReproTest`
already showed that adding `KatalystMigration` to `secondaryTypes` makes `MigrationFeature` see
its migrations. This makes a container-native fix viable, which is the Spring-shaped answer
(Spring indexes beans by their full type hierarchy; `getBeansOfType`/`ObjectProvider<List<T>>`
just work).

## 3. Recommended architecture: one extension-point catalog

Replace the 15 scattered sites with a single declarative table.

```kotlin
enum class Cardinality { SINGLE, MULTI }

data class ExtensionPoint<T : Any>(
    val id: String,                       // "migrations" — replaces magic category strings
    val type: KClass<T>,                  // KatalystMigration::class
    val cardinality: Cardinality,         // MULTI => many impls, reachable via getAll
    val optionalDiscovery: Boolean,       // empty result logs INFO, not WARN
    val joinsDependencyGraph: Boolean,    // migrations: false (different lifecycle)
    val registrationPhase: Phase,         // COMPONENTS | AFTER_COMPONENTS
    val enableHint: String? = null,       // "features { enableMigrations() }"
)

internal object ExtensionPoints {
    val all: List<ExtensionPoint<*>> = listOf(/* one row per contract type */)
    fun multiTypesFor(instance: Any): List<KClass<*>> =
        all.filter { it.cardinality == Cardinality.MULTI && it.type.isInstance(instance) }
           .map { it.type }
}
```

Every site above becomes a read of this table:

- `discoverAllComponents()` iterates `ExtensionPoints.all` instead of a hardcoded list.
- `emptyDiscoverySeverity` reads `optionalDiscovery`.
- `registerComponentOfType`'s `when` reads the catalog by category id.
- `reserved` is *derived* — it is exactly the catalog's types.
- `multiBindingSecondaryTypes` is *derived* — the `MULTI` rows.
- `DependencyGraph` / `ComponentOrderComputer` / `DependencyValidator` read `joinsDependencyGraph`
  instead of hardcoding `KatalystMigration::class.java.isAssignableFrom(...)` three times.
- `registerComponentsInOrder`'s "migrations last" special case reads `registrationPhase`.
- `ValidationError`'s hint string reads `enableHint`.

**Adding an extension point becomes one row.** Forgetting a site becomes structurally impossible.

### Use `isInstance`, not supertype reflection

The binding must be computed with `type.isInstance(instance)` — a runtime `instanceof` — not by
walking `clazz.supertypes`. This is precisely why the existing `instance is StartupHook` check in
`registerInstance` works while `computeSecondaryTypes` fails: `supertypes` returns only *direct*
supertypes, so `class Foo : SqlMigration()` never yields `KatalystMigration`. `isInstance` is
depth-independent, survives abstract intermediates, and is correct under generic erasure
(`EventHandler<*>`).

### Keep single-binding semantics untouched

Do **not** widen the single-binding path to the full supertype closure. Today
`computeSecondaryTypes` binds only direct interfaces, and `secondaryTypeOwners` throws
`DependencyInjectionException` on collision. Widening that closure would make every pair of
classes sharing any grandparent interface collide, breaking working applications. The catalog
change is additive: `MULTI` types get declared secondary bindings **and** are exempt from the
collision check (as `multiBindingSecondaryTypes` already intends); `SINGLE` behavior is unchanged.

### Make `get<T>()` on a MULTI type fail loudly

The probe showed `get<KatalystMigration>()` silently returns the last-registered instance. Once
`MULTI` types are declared in the container, that footgun becomes reachable. `KatalystContainer.get`
should reject a `MULTI` extension-point type with a message pointing at `getAll`.

## 4. ⚠️ Landmine: fixing `getAll` double-registers every event handler

`EventSystemFeature.onReady` currently does:

```kotlin
val registryHandlers = GlobalEventHandlerRegistry.consumeAll()
val koinHandlers = runCatching { context.getAll<EventHandler<*>>() }.getOrElse { emptyList() }
topology.registerHandlers(registryHandlers + koinHandlers)
```

`EventHandler::class` is in `reserved`, so `koinHandlers` is **always empty today** — the union is
accidentally safe. `EventTopology.registerHandlers` (`EventTopology.kt:56-76`) loops and registers
each element with **no deduplication**.

The moment `getAll<EventHandler<*>>()` starts working, every handler is registered twice and every
domain event is handled twice. This must be fixed in the same change: pick one source of truth and
dedupe by identity. Any fix that "makes `getAll` work for extension points" without touching this
site ships a silent double-delivery bug.

## 4b. Prototype results — what a boot test actually shows

The minimal fix was implemented and boot-tested through the **real** `bootstrapKatalystContainer`
path (a `SqlMigration` under a scanned package, H2, one boot per schema policy), then reverted.
Two edits: add `KatalystMigration` to `multiBindingSecondaryTypes`, and have `registerInstance`
derive multibinding types via `isInstance` and pass them to the bean engine.

**With the fix — every schema policy works:**

```
BOOT[CREATE_MISSING] boot=OK | probe_widgets=0 | history=1
BOOT[VALIDATE]       boot=OK | probe_widgets=0 | history=1
BOOT[NONE]           boot=OK | probe_widgets=0 | history=1
```

**Without the fix (control) — silent no-op, boot still reports success:**

```
BOOT[CREATE_MISSING] boot=OK | probe_widgets=MISSING | history=MISSING
BOOT[VALIDATE]       boot=OK | probe_widgets=MISSING | history=MISSING
BOOT[NONE]           boot=OK | probe_widgets=MISSING | history=MISSING
```

### Boot ordering is already correct

`validateOnStartup()` was the open question, and the answer is good: feature `onReady` hooks run at
`DIConfiguration.kt:209-213`, **before** Phase 3 applies the schema policy at `DIConfiguration.kt:229+`.
Migrations therefore land before validation inspects the schema. No reordering is needed.

### ⚠️ But the fix as written breaks lifecycle hooks

Running the full suite with the prototype applied:

```
LifecycleHookAutoDiscoveryTest > each discovered hook executes exactly once() FAILED
  hooks executed more than once: {early-startup=2, bare-startup=2,
    component-marked-startup:injected=2, injecting-startup:injected=2,
    late-startup=2, bare-ready=2, injecting-ready:injected=2}
```

Every hook ran twice — the §4 landmine, confirmed on the *hook* path rather than the event path.

The mechanism is worse than simple double-counting, and it is a **pre-existing bug that the empty
`getAll` currently masks**:

- `StartupHookRunner`/`ReadyHookRunner` already union registry ∪ container and dedup with
  `distinctByIdentity()` (an `IdentityHashMap`-backed filter — the implementation is correct).
- **`RegistryManager.resetAll()` is never called by production shutdown.** `stopKatalystStandalone()`
  stops the bean engine and resets `KatalystContainerProvider`, but leaves every registry singleton
  fully populated (`DIConfiguration.kt:468-487`).
- So on a second bootstrap in the same JVM, the registry still holds **instances from the previous
  application**, while the container holds the fresh ones. They are genuinely different objects, so
  identity dedup cannot collapse them → every hook runs twice.

A probe confirmed the container side is clean — 5 hooks, 5 distinct identities, 5 distinct classes.
The duplicates come from stale registry state.

This is independently a correctness bug today: stale hook instances bound to a dead container are
re-executed on any subsequent bootstrap in the same JVM. It is currently invisible only because
`getAll` returns nothing, so the *stale* instances are the only ones that ever run.

**Consequence for the fix order: registry lifecycle must be fixed first.** `stopKatalystStandalone()`
must call `RegistryManager.resetAll()`, or registries must become container-scoped rather than
global singletons. Making `getAll` work before that turns a hidden bug into a visible one.

## 4c. Extension-point audit

Eight extension points exist. Seven have a registry; migrations is the only one that does not — and
is the only one broken:

| Extension point | Lookup mechanism | Status |
| --- | --- | --- |
| `Service` | `ServiceRegistry` | works |
| `Component` | concrete type, injected by type | works |
| `CrudRepository` | concrete type | works |
| `Table` | `TableRegistry` (used by Phase 3) | works |
| `KtorModule` | `KtorModuleRegistry` | works |
| `EventHandler` | `GlobalEventHandlerRegistry` | works |
| `StartupHook` / `ReadyHook` | `StartupHookRegistry` / `ReadyHookRegistry` | works (but see stale-state bug above) |
| **`KatalystMigration`** | **none** | **broken** |

This is the cleanest statement of the defect: the registry is the only lookup mechanism that
actually works today, and migrations were never given one.

## 4d. Test-harness gaps found along the way

- **`katalystTestEnvironment` hardcodes `SchemaPolicy.CREATE_MISSING`**
  (`KatalystTestEnvironment.kt:246`) with no builder override. The regression test for this issue
  *must* use `validate`/`none`, so the harness needs a `schema(policy)` method before that test can
  be written with the public DSL. The prototype had to call `bootstrapKatalystContainer` directly.
- **The harness boots `TestKatalystBeanEngine`, not Koin.** Its `getAll` uses exact-type matching and
  its `get` returns `lastOrNull()` — semantics that happen to match Koin's here (verified separately),
  but nothing enforces that. A bean-engine contract test should pin the two together, otherwise
  integration tests can pass against the fake while production fails against Koin.
- **Two `DatabaseFactory` instances per boot.** `coreDIModule` creates one
  (`CoreDIModule.kt:46`); Phase 3 creates a second (`DIConfiguration.kt:252`) and overrides the
  binding. Migrations run against pool #1, schema validation against pool #2, and pool #1 is never
  closed. Harmless for the in-memory tests (`DB_CLOSE_DELAY=-1`) but a real connection leak.

## 5. Phasing

**Phase 0 — registry lifecycle (prerequisite, proven necessary).**
Make `stopKatalystStandalone()` call `RegistryManager.resetAll()`, or scope registries to the
container instead of JVM-global singletons. Without this, Phase 1 makes every lifecycle hook run
twice (§4b). This is a standalone bug fix and is worth shipping on its own merits.

**Phase 1 — catalog + migrations fixed (the shippable bug fix).**
Introduce `ExtensionPoint`/`ExtensionPoints`, rewrite the ~15 sites to read it, compute multibinding
secondary types via `isInstance`, and fix the event-handler union. Migrations start running. No
change to single-binding semantics. Behavior-neutral for every other extension point.
Prototype-verified to fix all three schema policies (§4b).

**Phase 2 — converge the two mechanisms.**
With the container proven authoritative, reduce the per-type registries to a single generic
`MultiBindingRegistry` keyed by `KClass`, populated from the same catalog loop. Keep
`ResettableRegistry`/`RegistryManager` for test isolation. Retire the per-type singletons behind
thin deprecated views.

**Phase 3 — third-party extension points.**
Make the catalog contributable via `ServiceLoader` so a downstream starter can declare its own
extension point. Only worth doing once Phase 1's descriptor has settled.

## 6. Regression strategy

The existing migration tests all drive `MigrationRunner` with an explicit list — the one path the
issue notes is unaffected. No unit test could have caught this; the failure is in the wiring
*between* discovery and lookup. The tests must therefore be integration-shaped.

1. **Boot-level end-to-end test (the one that would have caught this).**
   Boot a real Katalyst app against H2 with a `SqlMigration` under a scanned package, with
   `schema { none() }` so the schema policy cannot mask the result. Assert the table exists and the
   migration-history row was written. `schema { createMissing() }` must **not** be used — it is what
   masked this bug in production.

2. **Table-driven extension-point reachability contract.**
   For every `MULTI` row in the catalog, register a fake implementation in three shapes —
   direct interface impl, via an abstract intermediate (the `SqlMigration` shape that broke), and a
   two-level chain — then assert it is reachable via both `getAll<T>()` and the registry. This is
   the test that fails today for migrations and would fail for any future extension point added to
   the catalog without a working lookup.

3. **Catalog ↔ `KatalystConventions` parity test.**
   `KatalystConventions` is already the static-analysis/IDE source of truth and lists every marker
   FQN. Assert bidirectionally that each constant has a catalog row and vice versa. This is the
   cheap structural guard that makes "added to 6 of 8 places" a build failure. It also keeps the
   runtime, `katalyst-analysis`, and the IntelliJ plugin from drifting — the stated purpose of
   `KatalystConventions`.

4. **No-double-registration test for events.**
   Boot with one `EventHandler`, publish one event, assert `handleCount == 1`. Directly guards the
   Section 4 landmine.

5. **Silent-no-op guard.**
   Assert that "migrations feature enabled, zero migrations discovered" logs at WARN, not INFO.
   The whole reason this shipped is that the failure mode was indistinguishable from a correct
   boot in the logs.

Tests 2 and 3 are the durable ones: they are driven by the catalog, so they automatically extend
to every extension point added later.

---

## 7. What shipped

Full suite green: **1996 tests**, `./gradlew clean build --rerun-tasks` successful.

### Production changes

| Area | Change |
| --- | --- |
| `di/extension/ExtensionPoint.kt` *(new)* | The catalog. `ExtensionPoint` descriptor + `ExtensionPoints` with all eight markers. `internal` — this is machinery, not public API. |
| `AutoBindingRegistrar` | `emptyDiscoverySeverity`, the `reserved` set and the multibinding set now derive from the catalog. `registerInstance` derives multibinding markers via `ExtensionPoints.multiBindingTypesOf(instance)` (runtime `isInstance`) and **registers them as container secondary types** — the actual fix. |
| `ComponentRegistrationOrchestrator` | Discovery loop, base-class resolution and the AFTER_COMPONENTS pass all read the catalog. Added a warning when implementations are found but their feature is off. |
| `DependencyGraph`, `ComponentOrderComputer`, `DependencyValidator` | Three hardcoded `KatalystMigration.isAssignableFrom` checks replaced with `ExtensionPoints.joinsDependencyGraph(type)`. |
| `DIConfiguration` | `RegistryManager.resetAll()` on shutdown **and** at bootstrap entry (a failed boot never reaches shutdown). Phase 3 reuses the container's `DatabaseFactory` instead of opening a second Hikari pool. |
| `EventSystemFeature` | Registry ∪ container deduplicated by identity. |
| `KatalystApplication` | Ktor module union deduplicated across *both* sources, not just within the container half. |
| `MigrationFeature` | "enabled, running at startup, found nothing" is now WARN. |
| `IdentityDistinct.kt` *(new)* | `distinctByIdentity` promoted out of `StartupHookRunner` — now used by four call sites. |
| `KatalystTestEnvironmentBuilder` | `schema(policy)`; the harness no longer hardcodes `CREATE_MISSING`. |

### Deviations from the plan

- **`EventHandler` is `MULTI`, not `SINGLE`.** The plan preserved its historical `reserved`
  treatment. Writing the double-registration test exposed that handlers were reaching the
  topology *only* through the registry, which does not reflect reality — many handlers
  legitimately coexist. Modelling it as `MULTI` makes handlers container-visible; the identity
  dedup added alongside is what keeps that safe.
- **`ValidationError`'s feature-hint `when` block was left alone.** It maps *feature names*
  (`scheduler`, `websockets`, `configProvider`) to enable calls, and most of those are not
  extension points. Routing it through the catalog would have produced a partial, confusing
  overlap. `featureId`/`enableHint` instead drive the new "implementations found but feature
  disabled" warning, which is a real silent-failure gap and had no coverage before.
- **`ComponentRegistrationOrchestrator`'s constructor changed** (added `enabledFeatureIds`).
  Recorded via `apiDump`; the class sits in an `.internal` package and is used only inside
  `katalyst-di`.

### Tests added, and the failure each one was proven to catch

Every fix was verified by reverting it and confirming the corresponding test fails. None of
these tests pass against the broken code.

| Test | Reverting what makes it fail |
| --- | --- |
| `MigrationBootRegressionTest` (6 tests) | The binding fix — all 6 fail. Boots the real container per schema policy (`NONE`, `VALIDATE`, `CREATE_MISSING_AND_VALIDATE` — never `CREATE_MISSING`, which masks the bug), asserts real tables and history rows, plus re-boot idempotence and `runAtStartup=false`. |
| `ExtensionPointCatalogTest` (7 tests) | The binding fix — the reachability test fails. Covers catalog/`KatalystConventions` parity, uniqueness, derived-set consistency, and multibinding reachability across three impl shapes (direct interface, abstract intermediate, two-level chain). |
| `RegistryLifecycleRegressionTest` (3 tests) | The Phase 0 reset — the identity test fails. Counting ids alone cannot detect the leak: a stale hook reports the same id as the fresh instance it displaced, so the test compares instance identities across bootstraps. |
| `EventHandlerSingleRegistrationTest` (2 tests) | The event dedup — both fail. Publishes real events and asserts each is handled exactly once. |
| `BeanEngineContractTest` (3 tests) | Pins `TestKatalystBeanEngine` to `KoinBeanEngine`. Integration tests boot the fake while applications boot Koin; if they diverge, a green suite can hide a broken runtime. |
| `MigrationFeatureSilentNoOpTest` (2 tests) | The WARN change. Asserts the no-op path is visible and that `runAtStartup=false` stays quiet. |

`LifecycleHookFixtures` now records instance identity alongside hook ids, which is what makes
the registry-leak assertion possible.

### Vacuous assertions, caught by the revert check

Two tests initially passed against the broken code, for the same reason: they compared two
values without asserting either was meaningful.

- *"migrations are not re-applied on a second boot"* compared the history-row count before and
  after a second boot. With the bug, no migration ran on either boot, the history table never
  existed, and the comparison was `null == null`. Idempotence is only meaningful once something
  was actually applied, so the test now pins the first boot to a concrete count of 3 before
  asserting the second boot leaves it unchanged.
- *"three consecutive bootstraps never accumulate hook executions"* asserted the three counts
  were equal — which also holds when every count is zero. It now asserts a non-zero count first.

Neither needed a production change; both were under-specified tests. This is the argument for
running the revert check on *every* regression test rather than trusting a green run: a test
that cannot fail is indistinguishable from a test that passes.

### Not done (deferred by design)

**Phase 3 — third-party extension points via `ServiceLoader`.** The catalog is deliberately
`internal` until the descriptor has settled. Opening it up is additive and can wait for a real
downstream need.
