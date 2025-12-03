# Testing Katalyst Applications

Katalyst’s testing helpers mirror your production wiring so you can run unit, integration, and end-to-end tests without bespoke DI bootstrappers. This guide summarizes the tooling and shows when to use each helper.

## Helper Modules

| Scope | Module | Use it for |
| --- | --- | --- |
| Core DI bootstrap | `katalyst-testing-core` | `katalystTestEnvironment`, `inMemoryDatabaseConfig`, feature overrides, `FakeConfigProvider` |
| Ktor integration | `katalyst-testing-ktor` | `katalystTestApplication`, auto-installation of routes/middleware/websockets |

## Choosing an Approach

| Scenario | Recommended helper |
| --- | --- |
| Unit tests (config, validators) | Inject `FakeConfigProvider`, no DI bootstrap required |
| Service/repository/event/scheduler integration | `katalystTestEnvironment` |
| HTTP/WebSocket end-to-end | `katalystTestApplication` |
| External systems (Postgres, RabbitMQ) | `katalystTestEnvironment` + Testcontainers, override config via builder |

## katalystTestEnvironment

Boots the full DI graph (database, migrations, scheduler, events, components) inside tests.

```kotlin
import io.github.darkryh.katalyst.testing.core.KatalystTestEnvironment
import io.github.darkryh.katalyst.testing.core.katalystTestEnvironment
import io.github.darkryh.katalyst.testing.core.inMemoryDatabaseConfig
import kotlin.test.BeforeTest
import kotlin.test.AfterTest

class AuthenticationServiceIntegrationTest {
    private lateinit var environment: KatalystTestEnvironment

    @BeforeTest
    fun bootstrap() {
        environment = katalystTestEnvironment {
            database(inMemoryDatabaseConfig())
            scan("io.github.darkryh.katalyst.example")
        }
    }

    @AfterTest
    fun teardown() = environment.close()
}
```

### Usage patterns

- Resolve services/repositories via `environment.get<T>()`.
- Access the real `DatabaseTransactionManager`, `EventBus`, `SchedulerService`, etc.
- Override bindings with `overrideModules(module { single { FakeClock() } })`.
- Swap databases (e.g., Testcontainers Postgres) by passing a custom `DatabaseConfig` to `database(...)`.
- Features installed match production: ConfigProvider, events, migrations, scheduler, websockets (when present on classpath) are enabled automatically so service-level calls like `requireScheduler()` behave the same.

## katalystTestApplication

Wraps Ktor's `testApplication` so all auto-discovered modules are installed before requests run.

```kotlin
import io.github.darkryh.katalyst.testing.ktor.katalystTestApplication
import io.github.darkryh.katalyst.testing.core.inMemoryDatabaseConfig
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.http.HttpHeaders
import kotlin.test.Test
import kotlin.test.assertTrue

class AuthenticationServiceHttpTest {
    @Test
    fun `register login over HTTP`() = katalystTestApplication(
        configureEnvironment = {
            database(inMemoryDatabaseConfig())
            scan("io.github.darkryh.katalyst.example")
        }
    ) { env ->
        val registerResponse = client.post("/api/auth/register") { /* body */ }
        val profileResponse = client.get("/api/users/me") {
            header(HttpHeaders.Authorization, "Bearer ${registerResponse.token}")
        }
        assertTrue(env.get<UserProfileService>().listProfiles().any { it.accountId == registerResponse.accountId })
    }
}
```

For WebSockets, install the `WebSockets` client plugin and reuse the provided `client` instance.

## Events & Scheduler Tests

Because the environment loads the real `EventBus`/`SchedulerService`, you can test asynchronous flows deterministically:

- Publish events to ensure handlers/projectors run.
- Schedule tasks via `SchedulerService.schedule(...)` and await completion with `CompletableDeferred` + `withTimeout`.
- WebSockets are exercised the same way as HTTP using `katalystTestApplication` + the Ktor WebSockets client plugin.

## Coverage & CI

- Kover is applied at the root; run `./gradlew :module:koverHtmlReport` for HTML output (`build/reports/kover/html/index.html`).
- Add `./gradlew :module:test` and coverage commands to CI to keep the harness green.

## Messaging / AMQP

Messaging modules are **in development**. Once available, spin up the relevant broker (e.g., RabbitMQ Testcontainer) and use the same testing helpers to override configuration and drive end-to-end messaging flows.

## Reference tests (from `samples/katalyst-example`)

Use these files as concrete patterns when writing your own tests:

- `AuthenticationServiceIntegrationTest` – boots `katalystTestEnvironment` with H2, exercises service + repository + events.
- `AuthenticationServicePostgresTest` – same flow against Testcontainers Postgres; auto-skips when Docker is unavailable.
- `ExampleApiE2ETest` – end-to-end HTTP using `katalystTestApplication` (auto-installs routes/middleware/websockets).
- `NotificationWebSocketRoutesTest` – WebSocket roundtrip with the provided client inside `katalystTestApplication`.
- `SchedulerIntegrationTest` – verifies scheduler registrations run inside the test environment.
- `AuthAccountStatusMigrationTest` – runs a migration directly with in-memory DB to verify schema/data changes.
