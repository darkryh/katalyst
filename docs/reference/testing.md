# Testing

Katalyst ships two testing modules that boot the same dependency-injection graph your
application uses, so tests exercise real wiring. For a walkthrough, see
[Test your application](../how-to/test-applications.md).

| Module | Provides |
|--------|----------|
| `katalyst-testing-core` | `katalystTestEnvironment`, `inMemoryDatabaseConfig`, `FakeConfigProvider` |
| `katalyst-testing-ktor` | `katalystTestApplication` |

## katalystTestEnvironment

Boots the full container against a database and returns a `KatalystTestEnvironment`.

```kotlin
fun katalystTestEnvironment(
    block: KatalystTestEnvironmentBuilder.() -> Unit
): KatalystTestEnvironment
```

The builder accepts:

| Method | Purpose |
|--------|---------|
| `database(config: DatabaseConfig)` | Set the database (use `inMemoryDatabaseConfig()` for H2). |
| `databaseConfig(config)` | Alias for `database`. |
| `scan(vararg packages: String)` | Packages to discover. |
| `scan(packages: Iterable<String>)` | Same, from an iterable. |
| `overrideModules(...)` | Replace bindings with test doubles. |

```kotlin
val env = katalystTestEnvironment {
    database(inMemoryDatabaseConfig())
    scan("com.example")
}
```

`KatalystTestEnvironment`:

| Member | Purpose |
|--------|---------|
| `get<T>()` | Resolve a bean by type. |
| `close()` | Tear down the container (call in `@AfterTest`). |

The environment installs the same optional features as production — `ConfigProvider`, events,
migrations, scheduler, and WebSockets when on the classpath — so `requireScheduler()`,
`EventBus`, and `DatabaseTransactionManager` behave identically.

## katalystTestApplication

Wraps Ktor's `testApplication` and installs every auto-discovered route, middleware, and
WebSocket route before requests run. The trailing lambda is a Ktor test scope (`client` is
available) and receives the environment.

```kotlin
@Test
fun `creates a bookmark`() = katalystTestApplication(
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
```

For WebSocket tests, install the Ktor `WebSockets` client plugin and reuse `client`.

## inMemoryDatabaseConfig

Returns a `DatabaseConfig` for an embedded H2 in-memory database — fast and isolated per test.

```kotlin
fun inMemoryDatabaseConfig(): DatabaseConfig
```

For real-database tests, build a `DatabaseConfig` pointing at a Testcontainers Postgres
instance and pass it to `database(...)` instead.

## FakeConfigProvider

A `ConfigProvider` backed by an in-memory map, for unit tests that need config values without
a container.

```kotlin
val config = FakeConfigProvider(mapOf(
    "jwt.secret" to "test-secret",
    "jwt.issuer" to "test"
))
val settings = JwtSettingsService(config)
```

It implements the full `ConfigProvider` surface (`getString`, `getInt`, `getLong`,
`getBoolean`, `getList`, `getAllKeys`) plus typed `get`/`getOrNull`/`getAll`.

## Coverage

Kover is applied at the root. Generate an HTML report per module:

```bash
./gradlew :module:koverHtmlReport
# build/reports/kover/html/index.html
```

## See also

- [Test your application](../how-to/test-applications.md)
- [DI & auto-wiring](di-auto-wiring.md) — what the test container wires.

