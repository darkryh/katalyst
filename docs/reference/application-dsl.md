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

## features

```kotlin
features {
    enableYamlConfiguration()
    enableServerTuning()
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
| `enableYamlConfiguration()` | `katalyst-config-yaml` | Install the YAML configuration source. Because `database { fromConfiguration() }` reads it synchronously, put this `features { }` block *before* `database { }`. |
| `enableServerTuning()` | `katalyst-di` | Load `ktor.deployment.*` from the installed config source and make `ServerDeploymentConfiguration` available for injection |
| `enableEvents()` | `katalyst-di` | Enable the in-process transactional `EventBus` |
| `enableMigrations { … }` | `katalyst-migrations` | Run discovered migrations; accepts `MigrationOptions` fields |
| `enableScheduler()` | `katalyst-scheduler` | Register discovered scheduler jobs |
| `enableWebSockets { … }` | `katalyst-websockets` | Install the Ktor WebSockets plugin; accepts option fields |

`enableMigrations` and `enableWebSockets` take optional configuration lambdas; see
[Migrations](migrations.md#migrationoptions) and [Ktor integration](ktor.md#websocket-options).

Katalyst does not auto-select a configuration source or engine deployment values from the
classpath — call `enableYamlConfiguration()` (or register a custom source, see
[Configuration](configuration.md#custom-providers)) explicitly.

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
    // createMissing()    // create discovered schemas, tables and columns (local/test)
    // none()             // an external job owns the schema lifecycle
}
```

Sets the schema-management policy. Omitting the block is equivalent to `validateOnStartup()`.

| Policy | Behavior |
|--------|----------|
| `validateOnStartup()` | Verify discovered tables exist and match; do not create them. The default. |
| `createMissing()` | Create every missing schema, table **and column** for the discovered tables. For local development and tests. |
| `createMissingAndValidate()` | The same, then fail startup if anything still does not match. |
| `none()` | Do nothing; migrations or operations own the schema. |

### What "missing" means

`CREATE TABLE IF NOT EXISTS` skips an existing table *whole*, so creating tables alone is not
enough: add a column to a `Table` after the first boot and it never reaches the database, and the
first query that selects it fails at runtime. `createMissing()` therefore also issues
`ALTER TABLE … ADD COLUMN` for columns an existing table lacks, along with the indices those columns
are part of.

It stays strictly additive. A column the database has and your code does not is never dropped, and
a column whose *type* drifted is never altered — both would risk data, so both are left to a real
migration. Use `createMissingAndValidate()` to be told about them instead of carrying on silently.

Turn column creation off with:

```kotlin
schema { createMissing(createMissingColumns = false) }
```

which restores table-only creation. Worth doing when migrations own your columns, or when the
generated `ALTER TABLE … ADD COLUMN … NOT NULL` cannot succeed because the table already has rows
(give the column a default, make it nullable, or add it with a migration).

## Custom features

Register a custom `KatalystFeature` with `feature(...)` to extend bootstrap — for example, a
configuration source backed by a secrets manager:

```kotlin
katalystApplication(args) {
    feature(YamlConfigurationFeature(myCustomProvider))
    // …
}
```

## Application lifecycle hooks

Katalyst has three lifecycle hook interfaces. Implementing one is the only signal needed — a hook
is scanned, dependency-validated and constructor-injected on its own, and does **not** need to
also implement `Component` or `Service`. All three share `id` and `order`, declared on their common
`LifecycleHook` supertype, so one class can implement several without restating either.

- **`StartupHook`** — runs before the server binds, after all components are instantiated and
  the database schema is initialized. A built-in `StartupValidator` (`order = -100`) always
  runs first to verify database connectivity and schema.
- **`ReadyHook`** — runs once the HTTP server is up and accepting traffic. Use it for runtime
  activations such as scheduler registration or background consumers.
- **`ShutdownHook`** — runs while the application is shutting down, before Katalyst tears anything
  down. Stop here whatever a `ReadyHook` started.

Multiple hooks of each kind are allowed and run in deterministic order (`order` ascending,
ties broken by qualified class name). Shutdown walks the same numbers in reverse:

```kotlin
class CacheWarmup(
    private val cache: CacheClient
) : ReadyHook {
    override val id = "cacheWarmup"
    override val order = 50
    override suspend fun onReady() {
        cache.warm()
    }
}
```

```kotlin
class SchemaWarmupCheck : StartupHook {
    override val order = 10
    override suspend fun onStartup() {
        // pre-start validation/setup, runs after StartupValidator
    }
}
```

### Stopping background work

Anything a `ReadyHook` starts has to be stopped again, and `ShutdownHook` is where. It runs while
the framework is still completely usable — the connection pool is open, the container resolves, a
final `transaction { }` works — and Katalyst **awaits** it before closing anything.

That `suspend` matters. Ktor's own `ApplicationStopping` subscribers are ordinary synchronous
functions, so a `job.cancel()` there returns immediately while the coroutine is still parked inside
a blocking JDBC call. Cancelling is not joining:

```kotlin
class OutboxPublisher(private val outbox: OutboxRepository) : Service, ReadyHook, ShutdownHook {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    override val id = "outboxPublisher"
    override val order = 60

    override suspend fun onReady() {
        job = scope.launch {
            while (isActive) {
                outbox.publishPending()
                delay(200)
            }
        }
    }

    override suspend fun onShutdown() {
        job?.cancelAndJoin()   // awaited: the in-flight pass finishes before the pool closes
        scope.cancel()
    }
}
```

Shutdown proceeds in three steps, each finishing before the next begins:

1. Ktor drains in-flight HTTP requests, then raises `ApplicationStopping` — application subscribers
   run here, with the database still open.
2. Katalyst runs every `ShutdownHook` in descending `order`, awaiting each one. A hook that throws is
   logged and the rest still run; a hook that never returns is abandoned after a timeout so it cannot
   hang the process.
3. Katalyst stops its features, waits briefly for the connection pool to go quiet, and tears down.

The wait in step 3 is a safety net for work that was cancelled but not joined, and it is bounded. If
something is still holding a connection when it expires, Katalyst logs a warning naming how many —
that is the signal that some background work needs a `ShutdownHook`.

Hooks take part in the same dependency graph as components, so a hook whose constructor
dependency cannot be resolved fails the bootstrap with a validation error naming the hook,
rather than being skipped.

Implementing `Component`/`Service` *alongside* a hook interface is also supported, and is the
right choice when the class is genuinely both — for example a service that also warms its own
cache once the server is ready.

## Command-line and force flag

`katalystApplication(args)` forwards `args` to the engine. The `force` / `--force` flag makes
server-deployment configuration load from CLI and defaults only, bypassing the configured
source; other configs (database, services) still load normally.

## See also

- [DI & auto-wiring](di-auto-wiring.md) — what gets discovered and how injection works.
- [Configuration](configuration.md) — the config source and keys.
- [Architecture & bootstrap lifecycle](../explanation/architecture.md) — what each block
  triggers at runtime.
