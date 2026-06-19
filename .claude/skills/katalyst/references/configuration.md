# Configuration reference

Config flows: YAML → `ConfigProvider` → injected values. Modules: `katalyst-config-provider`
(provider + loaders + helpers), `katalyst-config-spi` (format SPI), `katalyst-config-yaml`
(YAML loader, profiles, `${ENV:default}`).

Katalyst installs **no** config source automatically. Call `enableYamlConfiguration()` (or
`configuration(customSource)`) in the bootstrap, or reads fall back to defaults.

## ConfigProvider

`io.github.darkryh.katalyst.core.config.ConfigProvider`. Inject it to read keys directly.

```kotlin
interface ConfigProvider {
    fun getString(key: String, default: String = ""): String
    fun getInt(key: String, default: Int = 0): Int
    fun getLong(key: String, default: Long = 0L): Long
    fun getBoolean(key: String, default: Boolean = false): Boolean
    fun getList(key: String, default: List<String> = emptyList()): List<String>
    fun getAllKeys(): Set<String>
}
```

```kotlin
import io.github.darkryh.katalyst.core.component.Service   // Service/Component live here
import io.github.darkryh.katalyst.core.config.ConfigProvider

class JwtSettingsService(config: ConfigProvider) : Service {
    private val secret = config.getString("jwt.secret")
    init { require(secret.isNotBlank()) { "jwt.secret must not be blank" } }
}
```

Keys are dotted paths into the YAML tree. For more than a couple of ad-hoc reads, use a typed
config object loaded by a loader (below).

## Two loader patterns — pick correctly

| | `ServiceConfigLoader<T>` | `AutomaticServiceConfigLoader<T>` |
|---|---|---|
| Discovery / registration | Manual | Automatic (classpath scan) |
| Timing | Before DI (Phase 0) | During DI (Phase 5a) |
| Injection | Manual call | Constructor parameter |
| Use for | Infrastructure config | Service/component config |

Decision: needs to exist **before DI** (database, ports)? → application DSL `database { }`, or a
`ServiceConfigLoader`. Needs to be **injected into components** (API keys, flags, endpoints)? →
`AutomaticServiceConfigLoader`. The database cannot use `AutomaticServiceConfigLoader` (loads
too late, Phase 5a).

### AutomaticServiceConfigLoader (the common one)

`io.github.darkryh.katalyst.config.provider.AutomaticServiceConfigLoader`:

```kotlin
interface AutomaticServiceConfigLoader<T : Any> {
    val configType: KClass<T>
    fun loadConfig(provider: ConfigProvider): T
    fun validate(config: T) {}   // optional; throw to fail fast at startup
}
```

Three steps:

```kotlin
// 1. Config data class
data class NotificationApiConfig(
    val baseUrl: String,
    val apiKey: String,
    val timeoutSeconds: Int = 30
)

// 2. Loader (object), under a scanned package
import io.github.darkryh.katalyst.config.provider.AutomaticServiceConfigLoader
import io.github.darkryh.katalyst.config.provider.requiredString
import io.github.darkryh.katalyst.config.provider.intOrNull
import io.github.darkryh.katalyst.core.config.ConfigProvider
import kotlin.reflect.KClass

object NotificationApiConfigLoader : AutomaticServiceConfigLoader<NotificationApiConfig> {
    override val configType: KClass<NotificationApiConfig> = NotificationApiConfig::class
    override fun loadConfig(provider: ConfigProvider) = NotificationApiConfig(
        baseUrl = provider.requiredString("notification.baseUrl"),
        apiKey = provider.requiredString("notification.apiKey"),
        timeoutSeconds = provider.intOrNull("notification.timeoutSeconds") ?: 30
    )
    override fun validate(config: NotificationApiConfig) {
        require(config.baseUrl.isNotBlank()) { "notification.baseUrl is required" }
    }
}

// 3. Inject the config type directly
class NotificationClient(private val config: NotificationApiConfig) : Service { /* … */ }
```

The loader is discovered, its output loaded/validated/registered as a singleton in Phase 5a,
then injected by type in Phase 5b. A missing required key or a failing `validate` aborts
startup with a `ConfigException` naming the key.

### ServiceConfigLoader

`io.github.darkryh.katalyst.config.provider.ServiceConfigLoader` — `fun loadConfig(provider): T`.
A manual loader for infrastructure config you load and register yourself before DI. Use only
when the DSL `database { }` doesn't cover the need.

## ConfigLoaders helpers

Two equivalent styles. **Kotlin extensions** on `ConfigProvider`
(`io.github.darkryh.katalyst.config.provider.*`):

```kotlin
provider.requiredString("k"); provider.requiredInt("k"); provider.requiredLong("k"); provider.requiredBoolean("k")
provider.stringOrNull("k");   provider.intOrNull("k");    provider.longOrNull("k")
provider.boolean("k")          // false if missing; throws if malformed
provider.optionalString("k", "default"); provider.optionalInt("k", 0); provider.optionalLong("k", 0L); provider.optionalBoolean("k", false)
```

**Static `ConfigLoaders`** (Java/object style, plus richer types):

```kotlin
ConfigLoaders.loadRequiredString(provider, "k")
ConfigLoaders.loadIntOrNull(provider, "k")
ConfigLoaders.loadBoolean(provider, "k")
ConfigLoaders.loadOptionalDuration(provider, "k", Duration.ofSeconds(30))
ConfigLoaders.loadOptionalList(provider, "k", emptyList())
ConfigLoaders.loadOptionalEnum(provider, "k", LogLevel.INFO)
```

Guidance: `required*` throws `ConfigException` on missing/blank (fail-fast); `*OrNull` returns
null if absent and throws if present-but-malformed; `boolean` defaults false; reach for
`optional*` only when a concrete fallback is part of the domain.

## Profiles and interpolation

- **Profiles:** set `KATALYST_PROFILE` (e.g. `prod`). Loads `application.yaml`, then
  `application-<profile>.yaml` overriding matching keys.
- **Interpolation:** `${VAR:default}` in any value. Env wins; otherwise the default after `:`.
  `${X:}` → empty string.

```
src/main/resources/
├── application.yaml
├── application-dev.yaml
├── application-staging.yaml
└── application-prod.yaml
```

## Database keys (read by database { fromConfiguration() })

Required: `database.url`, `database.driver`, `database.username`. Optional: `database.password`,
`database.pool.maxSize`, `database.pool.minIdle`, `database.pool.connectionTimeout`,
`database.pool.idleTimeout`, `database.pool.maxLifetime`, `database.autoCommit`,
`database.transactionIsolation`. Omitted pool keys use HikariCP-oriented defaults.

```yaml
database:
  url: ${DB_URL:jdbc:postgresql://localhost:5432/postgres}
  username: ${DB_USERNAME:postgres}
  password: ${DB_PASSWORD:}
  driver: ${DB_DRIVER:org.postgresql.Driver}
  pool:
    maxSize: ${DB_MAX_POOL:10}
    minIdle: ${DB_MIN_IDLE:1}
    connectionTimeout: ${DB_TIMEOUT:30000}
```

## Server deployment keys (enableServerConfiguration())

Under `ktor.deployment`: `host`, `port`, `sslPort`, `shutdownGracePeriod`, `shutdownTimeout`,
`shutdownUrl`, `rootPath`, `connectionGroupSize`, `workerGroupSize`, `callGroupSize`,
`maxInitialLineLength`, `maxHeaderSize`, `maxChunkSize`, `connectionIdleTimeoutMs`,
`requestTimeoutMs`, `maxThreads`/`minThreads` (Jetty only). TLS under `ktor.security.ssl`
(`keyStore`, `keyAlias`, `keyStorePassword`, `privateKeyPassword`, `trustStore`,
`trustStorePassword`, provider/factory options).

```yaml
ktor:
  deployment:
    host: ${SERVER_HOST:0.0.0.0}
    port: ${SERVER_PORT:8080}
    connectionGroupSize: ${CONNECTION_GROUP_SIZE:8}
    workerGroupSize: ${WORKER_GROUP_SIZE:8}
    callGroupSize: ${CALL_GROUP_SIZE:8}
```

## Custom providers

Implement `ConfigProvider` and register via a feature:

```kotlin
katalystApplication(args) { feature(YamlConfigurationFeature(myProvider)) /* … */ }
```

## Exceptions

`ConfigException` — required key missing/blank or a present value malformed. Surfaces during
bootstrap (fail-fast).
