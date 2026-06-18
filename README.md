# Katalyst – Ktor Bootstrap Starter Kit
[![Maven Central](https://img.shields.io/maven-central/v/io.github.darkryh.katalyst/katalyst-core?label=Maven%20Central)](https://central.sonatype.com/artifact/io.github.darkryh.katalyst/katalyst-core)

Katalyst gives you a ready-to-use Ktor backend stack: automatic DI, YAML configuration, Exposed/Hikari/JDBC persistence, migrations, scheduler, events, websockets, and first-class testing helpers—so you can focus on business logic instead of wiring.

- **What you get:** DI (Koin), persistence with Exposed + HikariCP, YAML config, scheduler + events, websockets, migrations, and testing utilities—all packaged for Ktor services.
- **Current alpha line:** `1.0.0-alpha`.
- **DI status:** Koin is the only supported DI adapter in this alpha. Katalyst's public DSL is being kept framework-oriented so a small container SPI can be introduced later without changing the reflection-based discovery model.
- **Docs:** see [`documentation/README.md`](documentation/README.md) for the full guide index.
- **Latest version:** see [`maven-metadata.xml`](https://repo1.maven.org/maven2/io/github/darkryh/katalyst/katalyst-core/maven-metadata.xml) for the current release.

## What's New (Latest Alpha)

- Simpler automatic injection in `katalyst-di`:
  - Use normal constructor parameters for services, repositories, components, configs, and framework contracts.
  - Kotlin default parameters are honored during reflective framework invocation.
  - Nullable missing dependencies resolve to `null`; required missing dependencies fail with clear diagnostics.
  - Route and scheduler functions can receive injectable object/config parameters.
  - Optional `@InjectNamed("...")` remains available for qualifier disambiguation.
- `ApplicationInitializer` supports multi-binding with deterministic execution order.
- Managed low-level SQL API in `katalyst-persistence` via `SqlExecutor` (`executeUpdate`, `query`, `queryOne`, `executeBatch`), reusing active transaction connections when present.

## Add Katalyst to your project (Gradle)

Use the published artifacts from Maven Central (see badge above or [`maven-metadata.xml`](https://repo1.maven.org/maven2/io/github/darkryh/katalyst/katalyst-core/maven-metadata.xml) for the latest version).

```kotlin
plugins {
    kotlin("jvm") version "2.2.21"
    id("io.ktor.plugin") version "3.3.3"
    id("org.jetbrains.kotlin.plugin.serialization") version "2.2.21"
}

repositories {
    mavenCentral()
}

dependencies {
    val katalystVersion = "1.0.0-alpha" // or the latest version from Maven Central
    // Pin your app stack as needed
    val ktorVersion = "3.3.3"
    val exposedVersion = "1.3.0"
    val hikariVersion = "5.1.0"
    val postgresVersion = "42.7.8"

    // Core Katalyst modules
    implementation("io.github.darkryh.katalyst:katalyst-core:$katalystVersion")
    implementation("io.github.darkryh.katalyst:katalyst-transactions:$katalystVersion")
    implementation("io.github.darkryh.katalyst:katalyst-persistence:$katalystVersion")
    implementation("io.github.darkryh.katalyst:katalyst-ktor:$katalystVersion")
    implementation("io.github.darkryh.katalyst:katalyst-scanner:$katalystVersion")
    implementation("io.github.darkryh.katalyst:katalyst-di:$katalystVersion")
    implementation("io.github.darkryh.katalyst:katalyst-koin-bean:$katalystVersion")
    implementation("io.github.darkryh.katalyst:katalyst-migrations:$katalystVersion")
    implementation("io.github.darkryh.katalyst:katalyst-scheduler:$katalystVersion")
    implementation("io.github.darkryh.katalyst:katalyst-ktor-engine-netty:$katalystVersion")

    // Optional feature-toggle bridge for enableWebSockets(); route DSL/options live in katalyst-ktor
    implementation("io.github.darkryh.katalyst:katalyst-websockets:$katalystVersion")

    // Config
    implementation("io.github.darkryh.katalyst:katalyst-config-provider:$katalystVersion")
    implementation("io.github.darkryh.katalyst:katalyst-config-yaml:$katalystVersion")

    // Events
    implementation("io.github.darkryh.katalyst:katalyst-events:$katalystVersion")
    implementation("io.github.darkryh.katalyst:katalyst-events-bus:$katalystVersion")

    // Testing helpers
    testImplementation("io.github.darkryh.katalyst:katalyst-testing-core:$katalystVersion")
    testImplementation("io.github.darkryh.katalyst:katalyst-testing-ktor:$katalystVersion")

    // Ktor surface + persistence (pin to your needs; shown for reference)
    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("org.jetbrains.exposed:exposed-core:$exposedVersion")
    implementation("com.zaxxer:HikariCP:$hikariVersion")
    implementation("org.postgresql:postgresql:$postgresVersion")
}
```

To iterate on Katalyst locally without publishing, you can use a composite build override in your consumer’s `settings.gradle.kts`:

```kotlin
includeBuild("../katalyst") // path to local checkout, removes the need to publish during local dev
```

## Minimal Boot Sequence

```kotlin
package io.github.darkryh.katalyst.example // EXAMPLE - BASE PACKAGE
import io.github.darkryh.katalyst.di.katalystApplication
import io.github.darkryh.katalyst.di.feature.enableServerConfiguration
import io.github.darkryh.katalyst.config.yaml.enableYamlConfiguration
import io.github.darkryh.katalyst.koin.KoinBeanEngine
import io.github.darkryh.katalyst.ktor.engine.netty.NettyServer
import io.github.darkryh.katalyst.websockets.enableWebSockets

fun main(args: Array<String>) = katalystApplication(args) {
    engine(NettyServer)
    beanEngine(KoinBeanEngine)
    enableYamlConfiguration()
    database {
        fromConfiguration()
    }
    scanPackages("io.github.darkryh.katalyst.example") // REQUIRED

    schema {
        validateOnStartup() // default when schema { ... } is omitted
        // createMissing()  // local/test compatibility
        // none()           // external migration job owns schema lifecycle
    }

    features {
        enableServerConfiguration()
        enableEvents()
        enableMigrations()
        enableScheduler()
        enableWebSockets()
    }
}
```

Once DI is running, Katalyst discovers everything else automatically (see [`documentation/auto-wiring.md`](documentation/auto-wiring.md) for more examples):

```kotlin
@Suppress("unused") // automatically injected
fun Route.authRoutes() = katalystRouting {
    route("/api/auth") {
        post("/login") {
            val service = call.ktInject<AuthenticationService>()
            call.respond(service.login(call.receive()))
        }
    }
}

@Suppress("unused")
fun Route.notificationWebSocketRoutes() = katalystWebSockets {
    webSocket("/ws/users") {
        send(Frame.Text("""{"type":"welcome"}"""))
        for (frame in incoming) if (frame is Frame.Text && frame.readText() == "ping") {
            send(Frame.Text("""{"type":"pong"}"""))
        }
    }
}
```

Services and components simply declare their dependencies; implementing `Service`/`Component` is what signals auto-discovery:

```kotlin
class AuthenticationService(
    private val repository: AuthAccountRepository,
    private val validator: AuthValidator,
    private val passwordHasher: PasswordHasher,
    private val eventBus: EventBus, // event-bus module needed
    private val jwtSettings: JwtSettingsService
) : Service {
    private val scheduler = requireScheduler()

    suspend fun register(request: RegisterRequest): AuthResponse = transactionManager.transaction {
        validator.validate(request)
        val account = repository.save(/* … */).toDomain()
        eventBus.publish(UserRegisteredEvent(account.id, account.email, request.displayName))
        AuthResponse(account.id, account.email, jwtSettings.generateToken(account.id, account.email))
    }

    fun scheduleAuthDigest() = scheduler.jobs {
        cron("authentication.broadcast", "0 0/1 * * * ?") {
            broadcastAuth()
        }
    }
}

@Suppress("unused")
class UserRegistrationFlowMonitor(
    private val eventBus: EventBus
) : Component { // implementing Component marks this class for auto-discovery
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    init {
        scope.launch {
            eventBus.eventsOf<UserRegisteredEvent>().collect { event ->
                println("Flow spotted registration for ${'$'}{event.email}")
            }
        }
    }
}
```

## Testing & Coverage

Prefer the testing modules over ad-hoc bootstrapping (full details in [`documentation/testing.md`](documentation/testing.md)):

```kotlin
@Test
fun `register login over HTTP`() = katalystTestApplication(
    configureEnvironment = {
        database(inMemoryDatabaseConfig())
        scan("io.github.darkryh.katalyst.example")
    }
) { env ->
    val register = client.post("/api/auth/register") { /* body */ }
    val profile = client.get("/api/users/me") {
        header(HttpHeaders.Authorization, "Bearer ${register.jwt}")
    }
    assertTrue(env.get<UserProfileService>().listProfiles().any { it.accountId == register.accountId })
}
```

Commands:

```bash
./gradlew build                      # full build + tests
./gradlew :katalyst-example:test     # example module tests (H2 + Postgres container)
./gradlew :katalyst-example:koverHtmlReport  # coverage report (build/reports/kover/html/index.html)
```

Need deeper walkthroughs? Check the `documentation/` folder.
