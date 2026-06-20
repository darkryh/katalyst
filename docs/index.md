# Katalyst

> A convention-driven backend framework for Kotlin and Ktor. Spring Boot's developer
> experience, the Kotlin way.

Katalyst gives a Ktor service the things you would otherwise wire by hand: dependency
injection, YAML configuration, Exposed + HikariCP persistence, transactions, database
migrations, a scheduler, an in-process event bus, WebSockets, and first-class testing
helpers. You declare components by implementing an interface and point Katalyst at your
package — it discovers, validates, orders, and injects everything at startup.

```kotlin
fun main(args: Array<String>) = katalystApplication(args) {
    engine(NettyServer)
    beanEngine(KoinBeanEngine)
    enableYamlConfiguration()
    database { fromConfiguration() }
    scanPackages("com.example")
    schema { validateOnStartup() }
    features {
        enableServerConfiguration()
        enableEvents()
        enableMigrations()
        enableScheduler()
        enableWebSockets()
    }
}
```

That is the entire bootstrap. Services, repositories, routes, event handlers, and
scheduled jobs under `com.example` are found and wired automatically — no annotations,
no module files.

!!! note "Status"
    Current release line: **`1.0.0-alpha`**. Koin is the only supported dependency-injection
    adapter in this alpha, selected explicitly with `beanEngine(KoinBeanEngine)`. The public
    DSL is kept adapter-neutral so a container SPI can land later without changing how your
    code is written.

## Where to go next

Pick the door that matches what you need right now.

<div class="grid cards" markdown>

-   :material-school: **New here?**

    Start with the [getting-started tutorial](getting-started.md). Build and run your
    first Katalyst service end to end.

-   :material-wrench: **Trying to do something specific?**

    The [how-to guides](how-to/index.md) are task-focused recipes: configure YAML, define
    tables, schedule jobs, publish events, test your app.

-   :material-book-open-variant: **Looking up an API?**

    The [reference](reference/index.md) documents every module, the application DSL, the
    discovery interfaces, configuration keys, and each subsystem.

-   :material-lightbulb-on: **Want the "why"?**

    The [explanation](explanation/index.md) covers the bootstrap lifecycle, the
    interface-driven design, and the trade-offs behind Katalyst.

</div>

## What you get

You depend on **starters**: each one bundles the Katalyst modules and external libraries
(Ktor, Exposed, HikariCP, JDBC drivers, …) for a capability, so you never list those
third-party dependencies yourself. A BOM keeps every version aligned.

| Capability | Starter | Reference |
|------------|---------|-----------|
| Application bootstrap DSL | `katalyst-starter-core` | [Application DSL](reference/application-dsl.md) |
| Annotation-free dependency injection | `katalyst-starter-core` | [DI & auto-wiring](reference/di-auto-wiring.md) |
| YAML configuration, profiles, env interpolation | `katalyst-starter-core` | [Configuration](reference/configuration.md) |
| In-process transactional event bus | `katalyst-starter-core` | [Events](reference/events.md) |
| Exposed + HikariCP persistence | `katalyst-starter-persistence` | [Persistence](reference/persistence.md) |
| Transaction management with retry | `katalyst-starter-persistence` | [Transactions](reference/transactions.md) |
| Database migrations | `katalyst-starter-migrations` | [Migrations](reference/migrations.md) |
| Scheduler (cron / fixed delay / fixed rate / one-time) | `katalyst-starter-scheduler` | [Scheduler](reference/scheduler.md) |
| Routing, middleware, exception handlers, Netty engine | `katalyst-starter-web` | [Ktor integration](reference/ktor.md) |
| WebSockets | `katalyst-starter-websockets` | [Ktor integration](reference/ktor.md) |
| Pluggable server engines (Netty / Jetty / CIO) | `katalyst-starter-web` + engine module | [Choose an engine](how-to/choose-an-engine.md) |
| Testing helpers | `katalyst-starter-test` | [Testing](reference/testing.md) |

See the [module map](reference/modules.md) for every starter, the underlying modules each one
bundles, and their coordinates.

