# Test your application

Katalyst's testing helpers boot the same DI graph your application uses, so tests exercise
real wiring — repositories, transactions, events, and the scheduler — without bespoke
bootstrappers. This guide shows how to choose and use each helper.

## Add the test dependencies

One starter covers all the testing helpers. Its version comes from the BOM you already
declare for the main dependencies, so add the BOM to the test configuration too and omit the
starter's version:

```kotlin
testImplementation(platform("io.github.darkryh.katalyst:katalyst-bom:1.0.0-alpha04"))
testImplementation("io.github.darkryh.katalyst:katalyst-starter-test")
```

`katalyst-starter-test` brings the test host (`katalystTestEnvironment`,
`katalystTestApplication`), JUnit 5, the Ktor WebSocket test client, and Testcontainers for
PostgreSQL transitively — you don't list them yourself.

## Choose a helper

| Goal | Helper |
|------|--------|
| Unit-test a config reader or validator | `FakeConfigProvider` (no DI bootstrap) |
| Integration-test services, repositories, events, scheduler | `katalystTestEnvironment` |
| End-to-end test over HTTP or WebSockets | `katalystTestApplication` |
| Test against real Postgres | `katalystTestEnvironment` + Testcontainers |

## Integration tests with katalystTestEnvironment

`katalystTestEnvironment` boots the full container against a database and resolves beans with
`environment.get<T>()`. Use `inMemoryDatabaseConfig()` for a fast embedded H2 database.

```kotlin
import io.github.darkryh.katalyst.testing.core.KatalystTestEnvironment
import io.github.darkryh.katalyst.testing.core.katalystTestEnvironment
import io.github.darkryh.katalyst.testing.core.inMemoryDatabaseConfig
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertNotNull

class AuthServiceIntegrationTest {
    private lateinit var env: KatalystTestEnvironment

    @BeforeTest
    fun setUp() {
        env = katalystTestEnvironment {
            database(inMemoryDatabaseConfig())
            scan("com.example")
        }
    }

    @AfterTest
    fun tearDown() = env.close()

    @Test
    fun `registers an account`() = runBlocking {
        val service = env.get<AuthService>()
        val account = service.register("a@example.com", "Ada")
        assertNotNull(account.id)
    }
}
```

The environment installs the same optional features as production — `ConfigProvider`, events,
migrations, scheduler, and WebSockets (when on the classpath) — so calls like
`requireScheduler()` behave identically.

### Override a binding

Swap a dependency for a fake with `overrideModules`:

```kotlin
env = katalystTestEnvironment {
    database(inMemoryDatabaseConfig())
    scan("com.example")
    overrideModules(module { single<Clock> { FixedClock() } })
}
```

### Use Postgres via Testcontainers

Pass a `DatabaseConfig` pointing at the container instead of `inMemoryDatabaseConfig()`:

```kotlin
val postgres = PostgreSQLContainer("postgres:16").apply { start() }
env = katalystTestEnvironment {
    database(DatabaseConfig(
        url = postgres.jdbcUrl,
        username = postgres.username,
        password = postgres.password,
        driver = "org.postgresql.Driver"
    ))
    scan("com.example")
}
```

## End-to-end tests with katalystTestApplication

`katalystTestApplication` wraps Ktor's `testApplication` and installs every auto-discovered
route, middleware, and WebSocket route before requests run. The lambda receives the test
environment so you can assert on both HTTP responses and bean state.

```kotlin
import io.github.darkryh.katalyst.testing.ktor.katalystTestApplication
import io.github.darkryh.katalyst.testing.core.inMemoryDatabaseConfig
import io.ktor.client.request.*
import io.ktor.http.*
import kotlin.test.Test
import kotlin.test.assertEquals

class ApiE2ETest {
    @Test
    fun `creates a bookmark over HTTP`() = katalystTestApplication(
        configureEnvironment = {
            database(inMemoryDatabaseConfig())
            scan("com.example")
        }
    ) { env ->
        val response = client.post("/bookmarks") {
            contentType(ContentType.Application.Json)
            setBody("""{"url":"https://kotlinlang.org"}""")
        }
        assertEquals(HttpStatusCode.Created, response.status)
        assertEquals(1, env.get<BookmarkService>().list().size)
    }
}
```

For WebSocket tests, install the Ktor `WebSockets` client plugin and reuse the provided
`client`.

## Unit tests with FakeConfigProvider

For pure logic that only needs config values, construct a `FakeConfigProvider` with the
entries you want — no container needed:

```kotlin
val config = FakeConfigProvider(mapOf("jwt.secret" to "test-secret", "jwt.issuer" to "test"))
val settings = JwtSettingsService(config)
```

## Coverage

Kover is applied at the root. Generate an HTML report per module:

```bash
./gradlew :app:koverHtmlReport
# open build/reports/kover/html/index.html
```

## Related

- [Testing reference](../reference/testing.md) — the helper signatures and builder methods.
- [Publish and handle events](publish-and-handle-events.md) — testing event flows.
- [Schedule background jobs](schedule-jobs.md) — testing scheduled jobs.

