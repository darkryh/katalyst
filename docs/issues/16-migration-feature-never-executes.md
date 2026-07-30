# Issue #16 — MigrationFeature never executes discovered KatalystMigration classes (1.0.0-alpha05)

- **URL:** https://github.com/darkryh/katalyst/issues/16
- **State:** OPEN
- **Author:** darkryh (Xavier Alexander Torres Calderón)
- **Created:** 2026-07-23
- **Labels:** none
- **Verification status:** ✅ **CONFIRMED** (reproduced against source on `master`, `katalystVersion=1.0.0-alpha05`)

---

## Original issue body

### Summary

On `1.0.0-alpha05`, `KatalystMigration` classes that are correctly discovered under `scanPackages(...)` are never executed at startup. `MigrationFeature.onReady` completes without running any migration, regardless of the `runAtStartup` setting.

### Observed behavior

- A `KatalystMigration` (via `SqlMigration`) placed under a scanned package is discovered and registered by the DI container — but only under its own concrete class.
- `KatalystMigration::class` is included in `AutoBindingRegistrar`'s reserved secondary-binding filter, and it is also absent from `multiBindingSecondaryTypes`.
- As a result, `context.getAll<KatalystMigration>()` inside the migration feature always returns an empty list, so no migration is ever looked up or executed. There is no error, no warning — the boot log simply shows no migration activity.

### Reproduction

1. Consumer app on `1.0.0-alpha05` with `features { enableMigrations() }` (and the migrations starter on the classpath).
2. Add any `KatalystMigration`/`SqlMigration` under a `scanPackages(...)` root.
3. Boot against an empty database.
4. The migration never runs and the migration history table records nothing, even though DI discovery logs show the class was found. Verified by tracing `katalyst-di`'s `AutoBindingRegistrar` and the boot log of a real application.

### Impact

- Any consumer relying on `runAtStartup` migrations silently gets no schema management. Apps using `schema { createMissing() }` mask the problem (DDL still appears, created by the schema policy instead), which makes the failure easy to miss.
- Apps using `validateOnStartup()`/`none()` with migration-managed schemas would fail boot or run against a missing schema.
- Direct `MigrationRunner` usage is unaffected (executing the same migration through `MigrationRunner` in a test works and is how this was isolated).

### Environment

- `io.github.darkryh.katalyst:*:1.0.0-alpha05` (Maven Central)
- JDK 21, Kotlin 2.4.x, Ktor 3.5.x, PostgreSQL (also reproducible in tests against H2)

Found while shipping the first `KatalystMigration` in a downstream application; as far as we can tell this affects every discovered migration on this version, not an edge case.

---

## Verification

### Verdict

**The bug is real.** `context.getAll<KatalystMigration>()` returns an empty list for every discovered migration, so `MigrationFeature.onReady` always takes the "no migrations were discovered" branch and never calls `MigrationRunner`. Both the symptom and the impact described in the issue are accurate.

**One correction to the stated root cause:** the issue attributes the failure to `KatalystMigration::class` being in `AutoBindingRegistrar`'s `reserved` set. That entry is *redundant* — removing it changes nothing. The actual causes are two other filter conditions in `computeSecondaryTypes`, either of which independently suffices to drop the binding. A fix that only deletes the `reserved` entry will **not** fix the bug.

### The call chain

1. **Discovery works.** `ComponentRegistrationOrchestrator.discoverAllComponents()` scans for `KatalystMigration` implementations under the `"migrations"` category
   (`katalyst-di/.../internal/ComponentRegistrationOrchestrator.kt:144`), and they are registered after the topologically-sorted components
   (`ComponentRegistrationOrchestrator.kt:244-257`). The issue is correct that discovery itself is fine.

2. **Registration drops the interface binding.** `registerComponentOfType` computes secondary types and registers:

   ```kotlin
   val secondaryTypes = registrar.computeSecondaryTypes(componentType, baseClass)  // baseClass = KatalystMigration::class
   registrar.registerInstance(instance, componentType, secondaryTypes)
   ```

   (`ComponentRegistrationOrchestrator.kt:412`, `:425-426`)

   `computeSecondaryTypes` (`AutoBindingRegistrar.kt:242-264`) filters candidate supertypes with:

   ```kotlin
   candidate != clazz &&
       candidate != baseType &&          // <-- baseType IS KatalystMigration::class
       candidate.java.isInterface &&     // <-- SqlMigration is an abstract class
       candidate !in reserved            // <-- redundant; the two above already exclude it
   ```

   - For `class Foo : KatalystMigration`, the direct supertype *is* `KatalystMigration`, so `candidate != baseType` drops it.
   - For `class Foo : SqlMigration()`, `clazz.supertypes` returns only *direct* supertypes — `SqlMigration`, an abstract class — so `candidate.java.isInterface` drops it. `KatalystMigration` is never even a candidate.

   Result: `secondaryTypes == []`, and the migration is registered under its concrete class only.

3. **`getAll` misses it.** `KoinKatalystContainer.getAll` (`katalyst-koin-bean/.../KoinKatalystContainer.kt:27-28`) delegates to Koin's `Scope.getAll(clazz)`, which matches on the `BeanDefinition`'s `primaryType`/`secondaryTypes`. `KoinBeanEngine.registerInstance` builds that definition with `primaryType = <concrete class>` and `secondaryTypes = []` (`KoinBeanEngine.kt:68-75`), so `KatalystMigration` matches nothing.

4. **Silent no-op.** `MigrationFeature.onReady` (`katalyst-migrations/.../feature/MigrationFeature.kt:38-42`) logs `"Migrations feature enabled but no migrations were discovered."` at INFO and returns. There is no warning and no failure — matching the reported "silent" behavior.

Also confirmed: `multiBindingSecondaryTypes` contains only `StartupHook` and `ReadyHook` (`AutoBindingRegistrar.kt:659-662`), so migrations get no registry-based fallback the way lifecycle hooks do. There is no other registry (no `MigrationRegistry`) — `getAll` is the only lookup path.

### Empirical reproduction

Two temporary tests were written, run, and then deleted.

**Test 1 — `katalyst-koin-bean`**, exercising the real Koin engine with exactly the arguments the orchestrator passes:

```
REPRO getAll<KatalystMigration> size = 0
REPRO get<DemoMigration> = ...koin.TempMigrationGetAllReproTest$DemoMigration@2f4210a8
FIX   getAll<KatalystMigration> size = 1
```

The instance *is* in the container (resolvable by concrete class) but invisible to `getAll<KatalystMigration>()`. Registering with `secondaryTypes = listOf(KatalystMigration::class)` makes it visible — confirming both the failure and the fix direction.

**Test 2 — `katalyst-di`**, on `computeSecondaryTypes` for both migration shapes:

```
REPRO direct migration secondary types = []
REPRO sql-style migration secondary types = []
```

**Test 3 — root-cause isolation.** `KatalystMigration::class` was temporarily deleted from the `reserved` set and Test 2 re-run:

```
REPRO direct migration secondary types = []
REPRO sql-style migration secondary types = []
```

Unchanged — proving the `reserved` entry is not the operative cause. (The edit was reverted.)

### Claim-by-claim

| Claim in issue | Verdict |
| --- | --- |
| Migrations are discovered but registered only under their concrete class | ✅ Confirmed |
| `KatalystMigration::class` is in the `reserved` secondary-binding filter | ✅ Confirmed present — ⚠️ but it is redundant, not the cause |
| `KatalystMigration` is absent from `multiBindingSecondaryTypes` | ✅ Confirmed |
| `context.getAll<KatalystMigration>()` always returns an empty list | ✅ Confirmed by test |
| No migration is executed, regardless of `runAtStartup` | ✅ Confirmed — both branches of `onReady` call `getAll` |
| Fails silently: no error, no warning | ✅ Confirmed — INFO-level log only |
| `schema { createMissing() }` masks the problem | ✅ Consistent with the code — the schema policy creates DDL independently of migrations |
| Direct `MigrationRunner` usage is unaffected | ✅ Confirmed — `MigrationRunner.runMigrations(migrations)` takes an explicit list and never consults the container |

### Fix

See **[`16-fix-design.md`](./16-fix-design.md)** for the full architectural analysis and phased plan.

Summary: the minimum viable fix is to bind migrations to `KatalystMigration` at registration
(passing it as a secondary type — proven to work above). But the underlying defect is that
extension-point knowledge is duplicated across ~15 sites with no source of truth, which is *how*
migrations ended up in the `reserved` set without a corresponding lookup mechanism. The
recommended long-term fix is a single declarative `ExtensionPoint` catalog that all of those sites
read from.

Whichever route: deleting the `reserved` entry alone is **not** a fix — verified above.

Worth adding regardless: turn the "no migrations were discovered" INFO into a WARN when the migrations feature is enabled but the list is empty, so the next silent no-op is visible in the boot log.

### Regression test to add

An end-to-end test that boots a Katalyst app with a `SqlMigration` under a scanned package and asserts the migration ran (history table row present) would have caught this. The existing migration tests all drive `MigrationRunner` directly with an explicit list — exactly the path the issue notes is unaffected — so they cannot catch a container-lookup failure. Crucially it must run with `schema { none() }`: `schema { createMissing() }` is what masked this bug in production.

See `16-fix-design.md` §6 for the full regression strategy, including the catalog-driven tests that extend automatically to future extension points.
