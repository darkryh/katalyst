# Katalyst

> A convention-driven backend framework for Kotlin and Ktor — Spring Boot's developer
> experience, the Kotlin way.

[![Maven Central](https://img.shields.io/maven-central/v/io.github.darkryh.katalyst/katalyst-core?label=Maven%20Central)](https://central.sonatype.com/artifact/io.github.darkryh.katalyst/katalyst-core)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.4.0-blue.svg?logo=kotlin)](https://kotlinlang.org)
[![Ktor](https://img.shields.io/badge/Ktor-3.5.0-orange.svg?logo=ktor)](https://ktor.io)

Katalyst gives a Ktor service the things you would otherwise wire by hand: dependency
injection, YAML configuration, Exposed + HikariCP persistence, transactions, database
migrations, a scheduler, an in-process event bus, WebSockets, and testing helpers. You
declare a component by implementing an interface and point Katalyst at your package — it
discovers, validates, orders, and injects everything at startup. No annotations, no module
files.

📖 **Full documentation: [the docs site](https://darkryh.github.io/katalyst/)** (also
browsable as Markdown under [`docs/`](docs/index.md)).

## Why Katalyst

If you have written Spring Boot, this will feel familiar — autowiring, a starter stack,
sensible defaults — but it stays idiomatic Kotlin and runs on Ktor:

- **Interface-driven discovery, not annotations.** Implement `Service`, `Component`,
  `CrudRepository`, `EventHandler`, or return the right type from a function, and Katalyst
  finds it. Constructor parameters are injected by type.
- **Explicit, readable bootstrap.** One `katalystApplication { … }` block declares your
  engine, DI adapter, config source, database, scanned packages, schema policy, and
  feature toggles. Nothing is hidden.
- **Fail-fast at startup.** Missing dependencies, circular graphs, invalid config, and
  checksum drift surface during boot with actionable diagnostics — not at the first request.

## Installation

Requires JDK 21+, Kotlin 2.4.x, and Ktor 3.5.x. Pin the version from the Maven Central
badge above (latest prerelease: `1.0.0-alpha01`).

```kotlin
plugins {
    kotlin("jvm") version "2.4.0"
    id("io.ktor.plugin") version "3.5.0"
    id("org.jetbrains.kotlin.plugin.serialization") version "2.4.0"
}

repositories {
    mavenCentral()
}

dependencies {
    val katalyst = "1.0.0-alpha01"
    implementation(platform("io.github.darkryh.katalyst:katalyst-bom:$katalyst"))

    implementation("io.github.darkryh.katalyst:katalyst-starter-web")
    implementation("io.github.darkryh.katalyst:katalyst-starter-persistence")

    // Add only the optional features the application uses.
    implementation("io.github.darkryh.katalyst:katalyst-starter-migrations")
    implementation("io.github.darkryh.katalyst:katalyst-starter-scheduler")
    implementation("io.github.darkryh.katalyst:katalyst-starter-websockets")

    testImplementation("io.github.darkryh.katalyst:katalyst-starter-test")
}
```

Add feature starters explicitly so scheduler, migrations, and WebSockets are not selected
unless they are needed.

See the [module map](docs/reference/modules.md) for every artifact, including the Jetty
and CIO engines.

## Quick start

```kotlin
import io.github.darkryh.katalyst.di.katalystApplication
import io.github.darkryh.katalyst.di.feature.enableServerConfiguration
import io.github.darkryh.katalyst.config.yaml.enableYamlConfiguration
import io.github.darkryh.katalyst.koin.KoinBeanEngine
import io.github.darkryh.katalyst.ktor.engine.netty.NettyServer

fun main(args: Array<String>) = katalystApplication(args) {
    engine(NettyServer)                          // pick a server engine
    beanEngine(KoinBeanEngine)                    // pick a DI adapter
    enableYamlConfiguration()                     // install the YAML source
    database { fromConfiguration() }              // read database.* before DI
    scanPackages("com.example")                   // discover everything here
    schema { validateOnStartup() }                // schema policy
    features { enableServerConfiguration() }
}
```

Declare a service — implementing `Service` is the only signal needed:

```kotlin
class GreetingService(private val repository: GreetingRepository) : Service {
    suspend fun greet(name: String): String = transactionManager.transaction {
        repository.recordGreeting(name)
        "Hello, $name"
    }
}
```

And a route — `katalystRouting` registers it automatically:

```kotlin
@Suppress("unused")
fun Route.greetingRoutes() = katalystRouting {
    get("/greet/{name}") {
        val service = call.ktInject<GreetingService>()
        call.respond(service.greet(call.parameters["name"]!!))
    }
}
```

Run it:

```bash
./gradlew run
# Katalyst bootstrap → server running on http://0.0.0.0:8080
```

Full walkthrough: the [getting-started tutorial](docs/getting-started.md).

## Documentation

The docs follow the [Diátaxis](https://diataxis.fr) model — four sections, each for a
different need:

- **[Tutorial](docs/getting-started.md)** — learn by building your first service.
- **[How-to guides](docs/how-to/index.md)** — recipes for configuration, persistence,
  migrations, scheduling, events, WebSockets, engines, and testing.
- **[Reference](docs/reference/index.md)** — every module, the application DSL, discovery
  interfaces, config keys, and each subsystem.
- **[Explanation](docs/explanation/index.md)** — the bootstrap lifecycle and design
  rationale.

## Examples

- [`samples/katalyst-example`](samples/katalyst-example) — full stack (auth, persistence,
  events, scheduler, WebSockets, migrations) on the Netty engine with profile-aware YAML.

## Building and testing

```bash
./gradlew build                              # compile all modules + run checks
./gradlew :katalyst-scheduler:test           # test a single module
./gradlew :katalyst-example:koverHtmlReport  # coverage report
```

See the [contributing guidelines](AGENTS.md) for repository conventions.

## License

<!-- TODO: confirm and link the project's LICENSE file. -->
