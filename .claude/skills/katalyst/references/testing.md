# Testing reference

The testing helpers boot the real DI graph so tests exercise real wiring. They ship in two
modules — `katalyst-testing-core` (`katalystTestEnvironment`, `inMemoryDatabaseConfig`,
`FakeConfigProvider`) and `katalyst-testing-ktor` (`katalystTestApplication`) — but you don't
depend on those directly. Add the single test starter; it bundles both plus JUnit 5, the Ktor
WebSocket test client, and Testcontainers for PostgreSQL. Its version comes from the BOM, so
omit it (add the BOM to the test config if the module isn't already on it):

```kotlin
testImplementation(platform("io.github.darkryh.katalyst:katalyst-bom:1.0.0-alpha01"))
testImplementation("io.github.darkryh.katalyst:katalyst-starter-test")
```

Helper choice:

| Goal | Helper |
|------|--------|
| Unit-test a config reader/validator | `FakeConfigProvider` (no container) |
| Services/repos/events/scheduler | `katalystTestEnvironment` |
| HTTP/WebSocket end-to-end | `katalystTestApplication` |
| Real Postgres | `katalystTestEnvironment` + Testcontainers `DatabaseConfig` |

## katalystTestEnvironment

```kotlin
import io.github.darkryh.katalyst.testing.core.katalystTestEnvironment
import io.github.darkryh.katalyst.testing.core.inMemoryDatabaseConfig
import io.github.darkryh.katalyst.testing.core.KatalystTestEnvironment

val env = katalystTestEnvironment {
    database(inMemoryDatabaseConfig())   // or databaseConfig(...); or a Testcontainers DatabaseConfig
    scan("com.example")                  // scan(vararg) or scan(Iterable)
    // overrideModules(module { single<Clock> { FixedClock() } })  // swap bindings
}
```

`KatalystTestEnvironment`:

```kotlin
inline fun <reified T : Any> get(): T   // resolve a bean
fun close()                              // tear down (call in @AfterTest)
```

The environment installs the same optional features as production (ConfigProvider, events,
migrations, scheduler, WebSockets when on classpath), so `requireScheduler()`, `EventBus`, and
`DatabaseTransactionManager` behave identically.

```kotlin
class AuthServiceIntegrationTest {
    private lateinit var env: KatalystTestEnvironment
    @BeforeTest fun setUp() { env = katalystTestEnvironment { database(inMemoryDatabaseConfig()); scan("com.example") } }
    @AfterTest fun tearDown() = env.close()

    @Test fun `registers an account`() = runBlocking {
        val account = env.get<AuthService>().register("a@example.com", "Ada")
        assertNotNull(account.id)
    }
}
```

## katalystTestApplication

Wraps Ktor `testApplication`; installs every auto-discovered route/middleware/WebSocket before
requests. The lambda is a Ktor test scope (`client` available) and receives the environment.

```kotlin
import io.github.darkryh.katalyst.testing.ktor.katalystTestApplication
import io.github.darkryh.katalyst.testing.core.inMemoryDatabaseConfig
import io.ktor.client.request.*
import io.ktor.http.*

@Test
fun `creates a bookmark`() = katalystTestApplication(
    configureEnvironment = { database(inMemoryDatabaseConfig()); scan("com.example") }
) { env ->
    val response = client.post("/bookmarks") {
        contentType(ContentType.Application.Json)
        setBody("""{"url":"https://kotlinlang.org"}""")
    }
    assertEquals(HttpStatusCode.Created, response.status)
    assertEquals(1, env.get<BookmarkService>().list().size)
}
```

For WebSocket tests, install the Ktor `WebSockets` client plugin and reuse `client`.

## inMemoryDatabaseConfig

`fun inMemoryDatabaseConfig(): DatabaseConfig` — embedded H2, isolated per test. For real DBs,
build a `DatabaseConfig` against a Testcontainers Postgres and pass it to `database(...)`.

## FakeConfigProvider

A map-backed `ConfigProvider` for unit tests without a container:

```kotlin
import io.github.darkryh.katalyst.testing.core.FakeConfigProvider
val config = FakeConfigProvider(mapOf("jwt.secret" to "test-secret", "jwt.issuer" to "test"))
val settings = JwtSettingsService(config)
```

Implements the full `ConfigProvider` surface plus typed `get`/`getOrNull`/`getAll`.

## Coverage

Kover at root: `./gradlew :module:koverHtmlReport` → `build/reports/kover/html/index.html`.
