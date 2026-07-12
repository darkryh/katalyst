# Configuration

Katalyst's configuration system loads settings from YAML, validates them in code, and injects
strongly typed values without hand-written wiring. This page documents the `ConfigProvider`
interface, the typed configuration binding patterns, the `ConfigProvider` read extensions, and
every recognized key.

For task-oriented help, see [Configure with YAML](../how-to/configure-yaml.md) and
[Add typed service configuration](../how-to/add-service-config.md).

## ConfigProvider

The runtime configuration tree. Inject it into any component to read keys directly.

```kotlin
class JwtSettingsService(config: ConfigProvider) : Service {
    private val secret = config.getString("jwt.secret")
}
```

| Method | Returns |
|--------|---------|
| `getString(key, default = "")` | `String` |
| `getInt(key, default = 0)` | `Int` |
| `getLong(key, default = 0L)` | `Long` |
| `getBoolean(key, default = false)` | `Boolean` |
| `getList(key, default = emptyList())` | `List<String>` |
| `getAllKeys()` | `Set<String>` — every known key |

Keys are dotted paths into the YAML tree (`jwt.secret`, `database.pool.maxSize`). For
anything beyond a few ad-hoc reads, prefer a typed config object bound by `ConfigBinder` and
the read extensions below.

## Typed configuration binding

`katalyst-config-provider` supports two binding styles for typed config classes, both
discovered automatically during component scanning by `ConfigBinder`. They are **not**
alternatives to the application DSL's `database { … }` block — that block still owns
infrastructure config needed before DI starts.

### Choosing a pattern

- Need the config **before DI can start** (database, server port)? Use the application DSL's
  `database { … }` block. `ConfigBinder` types are not bound yet at that point.
- Need the config **injected into a component** (API keys, feature flags, messaging
  endpoints)? Use `@ConfigPrefix`/`@ConfigKey`, or `ConfigBinding` for imperative logic.

| | Annotation-driven (`@ConfigPrefix`) | Code escape hatch (`ConfigBinding`) |
|---|---|---|
| Discovery | `@ConfigPrefix`-annotated class | `ConfigBinding` implementor |
| Binding | Each primary-constructor property reads a derived key (`prefix.kebab-case(name)`), or an explicit `@ConfigKey` override | Imperative — the constructor reads whatever it needs from the injected `ConfigProvider` |
| Constructor shape | One parameter per bound property (`String`, `Int`, `Long`, `Boolean`) | Single `ConfigProvider` parameter |
| Validation | `init { require(...) }` on the data class | Whatever the constructor body does |
| Use case | Straightforward key-per-property config | Derived defaults, cross-key validation, or custom parsing |

Both styles are discovered by `ConfigBinder.discoverConfigTypes(scanPackages)`, bound by
`ConfigBinder.bindAll(...)`, and registered as a singleton in the container — inject the
config type as a constructor parameter like any other dependency.

### Annotation-driven: @ConfigPrefix / @ConfigKey

```kotlin
@ConfigPrefix("notification")
data class NotificationApiConfig(
    val baseUrl: String,
    val apiKey: String,
    @ConfigKey("notification.timeout-seconds") val timeoutSeconds: Int = 30
) {
    init {
        require(baseUrl.isNotBlank()) { "notification.base-url is required" }
    }
}
```

Each property binds to `notification.<kebab-case(property)>` (`baseUrl` becomes
`notification.base-url`) unless overridden with `@ConfigKey` on that parameter. A missing
required key, or a failed `init` `require`, throws `ConfigException` and fails bootstrap.

### Code escape hatch: ConfigBinding

```kotlin
class SmtpConfig(provider: ConfigProvider) : ConfigBinding {
    val host: String = provider.requiredString("smtp.host")
    val port: Int = provider.intOrNull("smtp.port") ?: 25
}
```

Implementors must declare a primary constructor taking a single `ConfigProvider` parameter.
Use this when keys map to values through logic a declarative annotation cannot express.

Full walkthrough: [Add typed service configuration](../how-to/add-service-config.md).

## ConfigProvider read extensions

`katalyst-config-provider` supplies nullable-first Kotlin extensions on `ConfigProvider` —
used internally by `ConfigBinder` and available for imperative reads (for example, inside a
`ConfigBinding`):

```kotlin
provider.requiredString("notification.baseUrl")   // throws ConfigException if missing/blank
provider.requiredInt("notification.port")
provider.requiredLong("notification.windowMs")
provider.requiredBoolean("notification.strict")

provider.stringOrNull("notification.region")       // null if missing; throws if present but malformed
provider.intOrNull("notification.timeoutSeconds")
provider.longOrNull("notification.windowMs")
provider.booleanOrNull("notification.enabled")
```

`requiredX` fails fast with a `ConfigException` naming the key when it is missing (or blank,
for strings) or malformed. `xOrNull` returns `null` when the key is absent, but still throws
when the key is present with a malformed value — combine it with `?:` to supply a default.

## Profiles and interpolation

- **Profiles:** set `KATALYST_PROFILE` (for example `prod`). The YAML provider loads
  `application.yaml`, then `application-<profile>.yaml`, with the profile overriding matching
  keys.
- **Interpolation:** any value may use `${VAR:default}`; the environment variable wins when
  set, otherwise the default after the colon is used.

## Custom providers

Implement `ConfigProvider` (for example, backed by Consul or a secrets manager) and register
it through a configuration feature:

```kotlin
katalystApplication(args) {
    feature(YamlConfigurationFeature(myProvider))
    // …
}
```

Katalyst does not install a configuration source from the classpath automatically — you must
call `enableYamlConfiguration()` or register a source.

## Database keys

Read by `database { fromConfiguration() }`. Required: `database.url`, `database.driver`,
`database.username`.

| Key | Type | Notes |
|-----|------|-------|
| `database.url` | string | JDBC URL. **Required.** |
| `database.driver` | string | JDBC driver class. **Required.** |
| `database.username` | string | **Required.** |
| `database.password` | string | Optional. |
| `database.pool.maxSize` | int | Hikari max pool size. |
| `database.pool.minIdle` | int | Hikari minimum idle connections. |
| `database.pool.connectionTimeout` | long (ms) | |
| `database.pool.idleTimeout` | long (ms) | |
| `database.pool.maxLifetime` | long (ms) | |
| `database.autoCommit` | boolean | |
| `database.transactionIsolation` | string | JDBC isolation level name. |

Omitted pool keys fall back to HikariCP-oriented defaults. Override individual values in code
through the `database { … }` block.

## Server deployment keys

Read when `enableServerTuning()` is set. All live under `ktor.deployment`.

| Key | Type | Notes |
|-----|------|-------|
| `host` | string | Bind address. |
| `port` | int | HTTP port. |
| `sslPort` | int | Set to enable HTTPS. |
| `shutdownGracePeriod` | long (ms) | |
| `shutdownTimeout` | long (ms) | |
| `shutdownUrl` | string | Optional graceful-shutdown endpoint. |
| `rootPath` | string | Context path. |
| `connectionGroupSize` | int | Accept threads (Netty). |
| `workerGroupSize` | int | Parsing threads (Netty). |
| `callGroupSize` | int | Application threads (Netty). |
| `maxInitialLineLength` | int (bytes) | |
| `maxHeaderSize` | int (bytes) | |
| `maxChunkSize` | int (bytes) | |
| `connectionIdleTimeoutMs` | long (ms) | |
| `requestTimeoutMs` | long (ms) | Optional. |
| `maxThreads` / `minThreads` | int | Jetty thread pool; ignored by other engines. |

TLS settings live under `ktor.security.ssl` (`keyStore`, `keyAlias`, `keyStorePassword`,
`privateKeyPassword`, `trustStore`, `trustStorePassword`, and provider/factory options).

## Exceptions

| Exception | Thrown when |
|-----------|-------------|
| `ConfigException` | A required key is missing/blank, or a present value is malformed. Surfaces during bootstrap. |

## See also

- [Configure with YAML](../how-to/configure-yaml.md)
- [Add typed service configuration](../how-to/add-service-config.md)
- [Application DSL](application-dsl.md) — `enableYamlConfiguration`, `database`,
  `enableServerTuning`.

