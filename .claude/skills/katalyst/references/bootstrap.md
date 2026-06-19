# Bootstrap reference

The entry point is the top-level `katalystApplication` function in `katalyst-di`:

```kotlin
import io.github.darkryh.katalyst.di.katalystApplication

fun main(args: Array<String>) = katalystApplication(args) { /* builder */ }
```

The lambda receiver is `KatalystApplicationBuilder`. The builder methods, with exact
signatures, follow. `engine`, `beanEngine`, `database`, and `scanPackages` are mandatory; the
rest are optional.

## Builder methods

### engine — REQUIRED

```kotlin
fun engine(engine: KatalystServerEngine): KatalystApplicationBuilder
fun engine(engine: EmbeddedServer<ApplicationEngine, ApplicationEngine.Configuration>): KatalystApplicationBuilder
```

Pass one of the engine objects (each in its own module, so you only depend on the one you use):

| Engine | Import | Module |
|--------|--------|--------|
| `NettyServer` | `io.github.darkryh.katalyst.ktor.engine.netty.NettyServer` | `katalyst-ktor-engine-netty` |
| `JettyServer` | `io.github.darkryh.katalyst.ktor.engine.jetty.JettyServer` | `katalyst-ktor-engine-jetty` |
| `CioServer` | `io.github.darkryh.katalyst.ktor.engine.cio.CioServer` | `katalyst-ktor-engine-cio` |

All three implement `KatalystServerEngine`, so switching engines is a one-line change plus the
dependency. Netty is the usual default.

### beanEngine — REQUIRED

```kotlin
fun beanEngine(engine: KatalystBeanEngine): KatalystApplicationBuilder
```

```kotlin
import io.github.darkryh.katalyst.koin.KoinBeanEngine
beanEngine(KoinBeanEngine)
```

`KoinBeanEngine` (module `katalyst-koin-bean`) is the only adapter in this alpha. Startup fails
fast if no bean engine is selected — this is intentional, so a missing adapter is caught at boot
rather than as a later lazy-injection error. Katalyst owns discovery/analysis/validation/ordering;
the bean engine only performs final registration and resolution, which is why your code never
imports Koin types.

### database — REQUIRED

Two overloads:

```kotlin
fun database(config: DatabaseConfig): KatalystApplicationBuilder
fun database(configure: DatabaseConfigurationBuilder.() -> Unit): KatalystApplicationBuilder
```

The DSL form reads YAML and applies code overrides. `DatabaseConfigurationBuilder`:

```kotlin
database {
    fromConfiguration()            // read database.* (default prefix "database")
    // fromConfiguration("primary") // custom prefix
    // overrides (all optional; have defaults):
    // url, driver, username, password
    // maxPoolSize, minIdleConnections
    // connectionTimeout, idleTimeout, maxLifetime
    // autoCommit, transactionIsolation
}
```

`fromConfiguration(prefix = "database")` reads `<prefix>.url`, `<prefix>.driver`,
`<prefix>.username`, optional `<prefix>.password`, and `<prefix>.pool.*`. Anything you set in
the block after `fromConfiguration()` overrides YAML. For fully programmatic config:

```kotlin
import io.github.darkryh.katalyst.config.DatabaseConfig
database(DatabaseConfig(url = "...", driver = "...", username = "...", password = "..."))
```

The database is configured here — not via a config loader — because the container needs it
before it can build anything (bootstrap Phase 0). See `references/configuration.md`.

### scanPackages — REQUIRED

```kotlin
fun scanPackages(vararg packages: String): KatalystApplicationBuilder
```

```kotlin
scanPackages("com.example", "com.example.billing")
```

The package roots scanned for every discoverable type. A class outside these roots is invisible
with no error. Keep all application code under one root where practical.

### enableYamlConfiguration — required for YAML

An extension on the builder from `katalyst-config-yaml`:

```kotlin
import io.github.darkryh.katalyst.config.yaml.enableYamlConfiguration
fun KatalystApplicationBuilder.enableYamlConfiguration(): KatalystApplicationBuilder
```

Installs the YAML source. Katalyst does **not** auto-install any config source from the
classpath; call this (or `configuration(...)`) or config reads return defaults only.

### configuration — custom config source

```kotlin
fun configuration(source: ConfigProvider): KatalystApplicationBuilder
```

Register a custom `ConfigProvider` (e.g., secrets manager) instead of, or before, YAML.

### schema

```kotlin
fun schema(configure: SchemaManagementBuilder.() -> Unit): KatalystApplicationBuilder
```

`SchemaManagementBuilder` exposes three mutually exclusive policies:

```kotlin
schema { validateOnStartup() }   // default when schema { } is omitted
schema { createMissing() }       // create discovered tables not present — local/test only
schema { none() }                // do nothing; migrations or ops own the schema
```

### features

```kotlin
fun features(configure: KatalystFeaturesBuilder.() -> Unit): KatalystApplicationBuilder
```

Opt into non-core features. Each toggle is an extension on `KatalystFeaturesBuilder` from its
module. Exact import paths (agents get these wrong — copy them):

| Toggle | Import | Module |
|--------|--------|--------|
| `enableServerConfiguration()` | `io.github.darkryh.katalyst.di.feature.enableServerConfiguration` | `katalyst-di` |
| `enableEvents()` | `io.github.darkryh.katalyst.di.feature.enableEvents` | `katalyst-di` |
| `enableMigrations { }` | `io.github.darkryh.katalyst.migrations.extensions.enableMigrations` | `katalyst-migrations` |
| `enableScheduler()` | `io.github.darkryh.katalyst.scheduler.enableScheduler` | `katalyst-scheduler` |
| `enableWebSockets { }` | `io.github.darkryh.katalyst.websockets.enableWebSockets` | `katalyst-websockets` |

```kotlin
features {
    enableServerConfiguration()         // load ktor.deployment.* from config
    enableEvents()                      // in-process transactional EventBus
    enableMigrations {                  // accepts MigrationOptions fields
        runAtStartup = true
        includeTags = setOf("prod")
    }
    enableScheduler()                   // register discovered scheduler jobs
    enableWebSockets {                  // accepts WebSocketOptions fields
        // pingPeriod = 30.seconds
        // timeout = 15.seconds
        // maxFrameSize = Long.MAX_VALUE
        // masking = false
    }
}
```

`enableMigrations` options → `references/migrations.md`. `enableWebSockets` options →
`references/ktor.md`.

### feature — custom features

```kotlin
fun feature(feature: KatalystFeature): KatalystApplicationBuilder   // builder level
fun feature(feature: KatalystFeature): KatalystFeaturesBuilder      // inside features { }
```

Register a custom `KatalystFeature` — for example a custom config source:

```kotlin
feature(YamlConfigurationFeature(myProvider))
```

## ApplicationInitializer

Run startup work by implementing `ApplicationInitializer` under a scanned package. Multiple are
allowed; they execute in deterministic order (`order` ascending, then class-name tie-break).

```kotlin
import io.github.darkryh.katalyst.di.lifecycle.ApplicationInitializer

class CacheWarmupInitializer : ApplicationInitializer {
    override val initializerId = "cacheWarmup"
    override val order = 50
    override suspend fun onApplicationReady() { /* … */ }
}
```

`ApplicationReadyInitializer` is a related contract for ready-time hooks. Both are discovered;
do not register them manually.

## Command-line and the force flag

`args` from `main` are forwarded to the engine. The `force` / `--force` flag makes
server-deployment config (`ktor.deployment.*`) load from CLI and defaults only, bypassing the
configured source; other configs (database, service loaders) still load normally. Useful for
overriding deployment settings without editing YAML.

## Bootstrap phases (why ordering matters)

```
Phase 0  Infrastructure   engine, bean engine, config source, database
Phase 1  Discovery        scan packages
Phase 2  Analysis         build dependency graph from constructor params
Phase 3  Validation       resolve every dependency; fail fast
Phase 4  Ordering         topological sort
Phase 5a Config loading   AutomaticServiceConfigLoaders loaded/validated/registered
Phase 5b Registration     instantiate components in order
Phase 6  Schema/tables    apply schema policy; run migrations if enabled
Phase 7  Routes           install routes, middleware, WebSockets, exception handlers
```

This is why infrastructure config (database) goes in the DSL (Phase 0) while service config is
injected (registered Phase 5a, consumed Phase 5b). See `references/configuration.md` and
`references/di-and-discovery.md`.
