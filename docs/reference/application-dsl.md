# Application DSL

`katalystApplication` is the single entry point that bootstraps and runs a Katalyst
application. It is a top-level function in `katalyst-di` that takes the command-line
arguments and a configuration lambda, configures the container, and starts the server.

```kotlin
fun main(args: Array<String>) = katalystApplication(args) {
    // configuration blocks
}
```

The lambda is a `KatalystApplicationBuilder`. The blocks below are listed in the order they
normally appear.

## engine

```kotlin
engine(NettyServer)
```

**Required.** Selects the Ktor server engine. Pass one of `NettyServer`, `JettyServer`, or
`CioServer` (each from its own engine module). See [Choose an engine](../how-to/choose-an-engine.md).

## beanEngine

```kotlin
beanEngine(KoinBeanEngine)
```

**Required.** Selects the dependency-injection adapter. `KoinBeanEngine` (from
`katalyst-koin-bean`) is the only adapter in this alpha. Startup fails fast if no bean engine
is selected, so a missing adapter is caught immediately rather than at first injection.

## enableYamlConfiguration

```kotlin
enableYamlConfiguration()
```

Installs the YAML configuration source (from `katalyst-config-yaml`). Required for
YAML-backed database, server-deployment, and runtime config. Call it once. To use a custom
source instead, register a configuration feature — see [Configuration](configuration.md#custom-providers).

## database

```kotlin
database {
    fromConfiguration()         // read database.* from the installed config source
    maxPoolSize = 20            // optional code overrides
    minIdleConnections = 4
    connectionTimeout = 30_000L
}
```

**Required.** Configures the database before dependency injection starts. `fromConfiguration()`
reads the `database.*` keys and applies HikariCP defaults for omitted pool values. For fully
programmatic configuration, pass a `DatabaseConfig` directly:

```kotlin
database(DatabaseConfig(
    url = "jdbc:postgresql://localhost:5432/app",
    username = "app",
    password = System.getenv("DB_PASSWORD"),
    driver = "org.postgresql.Driver"
))
```

The `DatabaseConfig` fields are listed in the [persistence reference](persistence.md#databaseconfig).

## scanPackages

```kotlin
scanPackages("com.example", "com.example.billing")
```

**Required.** The package roots Katalyst scans for components, services, repositories, tables,
routes, middleware, event handlers, scheduled jobs, migrations, and config loaders. Everything
discovered must live under one of these roots.

## schema

```kotlin
schema {
    validateOnStartup()   // default when schema { ... } is omitted
    // createMissing()    // create discovered tables that don't exist (local/test)
    // none()             // an external job owns the schema lifecycle
}
```

Sets the schema-management policy. Omitting the block is equivalent to `validateOnStartup()`.

| Policy | Behavior |
|--------|----------|
| `validateOnStartup()` | Verify discovered tables exist and match; do not create them. The default. |
| `createMissing()` | Create any discovered table not present in the database. For local development and tests. |
| `none()` | Do nothing; migrations or operations own the schema. |

## features

```kotlin
features {
    enableServerConfiguration()
    enableEvents()
    enableMigrations { runAtStartup = true }
    enableScheduler()
    enableWebSockets()
}
```

Opts into non-core features. Each toggle comes from its module and is a no-op if you do not
call it.

| Toggle | Module | Effect |
|--------|--------|--------|
| `enableServerConfiguration()` | `katalyst-di` | Load `ktor.deployment.*` from config |
| `enableEvents()` | `katalyst-di` | Enable the in-process transactional `EventBus` |
| `enableMigrations { … }` | `katalyst-migrations` | Run discovered migrations; accepts `MigrationOptions` fields |
| `enableScheduler()` | `katalyst-scheduler` | Register discovered scheduler jobs |
| `enableWebSockets { … }` | `katalyst-websockets` | Install the Ktor WebSockets plugin; accepts option fields |

`enableMigrations` and `enableWebSockets` take optional configuration lambdas; see
[Migrations](migrations.md#migrationoptions) and [Ktor integration](ktor.md#websocket-options).

## Custom features

Register a custom `KatalystFeature` with `feature(...)` to extend bootstrap — for example, a
configuration source backed by a secrets manager:

```kotlin
katalystApplication(args) {
    feature(YamlConfigurationFeature(myCustomProvider))
    // …
}
```

## Application initializers

Run startup work in `ApplicationInitializer` implementations discovered under your packages.
Multiple are allowed and execute in deterministic order (`order` ascending, then class-name
tie-break):

```kotlin
class CacheWarmupInitializer : ApplicationInitializer {
    override val initializerId = "cacheWarmup"
    override val order = 50
    override suspend fun onApplicationReady() { /* … */ }
}
```

## Command-line and force flag

`katalystApplication(args)` forwards `args` to the engine. The `force` / `--force` flag makes
server-deployment configuration load from CLI and defaults only, bypassing the configured
source; other configs (database, services) still load normally.

## See also

- [DI & auto-wiring](di-auto-wiring.md) — what gets discovered and how injection works.
- [Configuration](configuration.md) — the config source and keys.
- [Architecture & bootstrap lifecycle](../explanation/architecture.md) — what each block
  triggers at runtime.

