# Katalyst – Ktor Bootstrap Starter Kit

Katalyst wraps Ktor with batteries-included tooling so you can ship services faster: automatic DI, YAML-driven config, Exposed/Hikari/JDBC persistence, migrations, scheduler, events, websockets, and first-class testing utilities.

- **Versions:** Kotlin 2.2.20, Ktor 3.3.1, Exposed 1.0.0-rc-3 (see `gradle/libs.versions.toml`).
- **Under the hood:** Koin for DI (`katalyst-di`), Exposed + HikariCP + JDBC for persistence (`katalyst-persistence`), SnakeYAML-based configuration (`katalyst-config-provider`/`yaml`), and coroutine-powered scheduler/events (`katalyst-scheduler`, `katalyst-events`).
- **Modules in a nutshell:** DI (`katalyst-di`), configuration (`katalyst-config-provider`/`yaml`), persistence (`katalyst-persistence`, `katalyst-migrations`), HTTP/WebSockets (`katalyst-ktor`, `katalyst-websockets`), scheduler (`katalyst-scheduler`), events (`katalyst-events-*`), testing (`katalyst-testing-core`/`ktor`). Messaging/AMQP modules are **in development**.
- **Docs:** see [`documentation/README.md`](documentation/README.md) for the full guide index.

## Minimal Boot Sequence

```kotlin
package io.github.darkryh.katalyst.example // EXAMPLE - BASE PACKAGE
import io.github.darkryh.katalyst.di.katalystApplication
import io.github.darkryh.katalyst.di.feature.enableServerConfiguration
import io.github.darkryh.katalyst.config.yaml.enableConfigProvider
import io.github.darkryh.katalyst.ktor.engine.netty.embeddedServer

fun main(args: Array<String>) = katalystApplication(args) {
    engine(embeddedServer())
    database(DbConfigImpl.loadDatabaseConfig())
    scanPackages("io.github.darkryh.katalyst.example") // REQUIRED
    enableServerConfiguration()
    enableConfigProvider()
    enableEvents {
        withBus(true)
    }
    enableMigrations()
    enableScheduler()
    enableWebSockets()
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
    private val scheduler = requireScheduler() // to use this, needs to have implemented scheduler module

    suspend fun register(request: RegisterRequest): AuthResponse = transactionManager.transaction {
        validator.validate(request)
        val account = repository.save(/* … */).toDomain()
        eventBus.publish(UserRegisteredEvent(account.id, account.email, request.displayName))
        AuthResponse(account.id, account.email, jwtSettings.generateToken(account.id, account.email))
    }

    @Suppress("unused") //scheduler functionality based on coroutines
    fun scheduleAuthDigest() = scheduler.scheduleCron(
        config = ScheduleConfig("authentication.broadcast"),
        task = { broadcastAuth() },
        cronExpression = CronExpression("0 0/1 * * * ?")
    )
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

Need deeper walkthroughs? Check the `documentation/` folder. Messaging/AMQP modules will follow the same patterns once they graduate from development.

## Add Katalyst to your project (Gradle)

Use the published artifacts (current: `0.0.21-alpha`). Versions under the hood: Ktor `3.3.1`, Exposed `1.0.0-rc-3`, HikariCP `5.1.0`, Koin `3.5.6`.

```kotlin
plugins {
    kotlin("jvm") version "2.2.20"
    id("io.ktor.plugin") version "3.3.1"
    id("org.jetbrains.kotlin.plugin.serialization") version "2.2.20"
}

repositories {
    mavenCentral()
}

dependencies {
    val katalystVersion = "0.0.21-alpha"
    val ktorVersion = "3.3.1"
    val exposedVersion = "1.0.0-rc-3"
    val hikariVersion = "5.1.0"
    val postgresVersion = "42.7.8"

    // Core Katalyst modules
    implementation("io.github.darkryh.katalyst:katalyst-core:$katalystVersion")
    implementation("io.github.darkryh.katalyst:katalyst-transactions:$katalystVersion")
    implementation("io.github.darkryh.katalyst:katalyst-persistence:$katalystVersion")
    implementation("io.github.darkryh.katalyst:katalyst-ktor:$katalystVersion")
    implementation("io.github.darkryh.katalyst:katalyst-scanner:$katalystVersion")
    implementation("io.github.darkryh.katalyst:katalyst-di:$katalystVersion")
    implementation("io.github.darkryh.katalyst:katalyst-migrations:$katalystVersion")
    implementation("io.github.darkryh.katalyst:katalyst-scheduler:$katalystVersion")
    implementation("io.github.darkryh.katalyst:katalyst-websockets:$katalystVersion")
    implementation("io.github.darkryh.katalyst:katalyst-ktor-engine-netty:$katalystVersion")

    // Config
    implementation("io.github.darkryh.katalyst:katalyst-config-provider:$katalystVersion")
    implementation("io.github.darkryh.katalyst:katalyst-config-yaml:$katalystVersion")

    // Events
    implementation("io.github.darkryh.katalyst:katalyst-events:$katalystVersion")
    implementation("io.github.darkryh.katalyst:katalyst-events-bus:$katalystVersion")
    implementation("io.github.darkryh.katalyst:katalyst-events-transport:$katalystVersion")
    implementation("io.github.darkryh.katalyst:katalyst-events-client:$katalystVersion")

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