# Configuration

Katalyst's configuration system loads settings from YAML, validates them in code, and injects
strongly typed values without hand-written wiring. This page documents the `ConfigProvider`
interface, the two loader patterns, the `ConfigLoaders` helpers, and every recognized key.

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
anything beyond a few ad-hoc reads, prefer a typed config object loaded by a loader and the
`ConfigLoaders` helpers below.

## The two loader patterns

Katalyst has two configuration loader interfaces. They are **not** alternatives — each
solves a different problem.

| | `ServiceConfigLoader<T>` | `AutomaticServiceConfigLoader<T>` |
|---|---|---|
| Discovery | Manual | Automatic via classpath scan |
| Registration | Manual | Automatic in the container |
| Timing | Before DI (bootstrap) | During DI bootstrap |
| Injection | Manual call | Constructor parameter |
| Use case | Infrastructure config | Service/component config |

### Choosing a pattern

- Need the config **before DI can start** (database, ports)? Use the application DSL's
  `database { … }` block, or a `ServiceConfigLoader` for other infrastructure.
- Need the config **injected into components** (API keys, feature flags, messaging)? Use
  `AutomaticServiceConfigLoader`.

You cannot use `AutomaticServiceConfigLoader` for the database, because it loads during DI —
too late for infrastructure the container itself depends on.

### AutomaticServiceConfigLoader

```kotlin
interface AutomaticServiceConfigLoader<T : Any> {
    val configType: KClass<T>
    fun loadConfig(provider: ConfigProvider): T
    fun validate(config: T) {}        // optional; throw to fail fast
}
```

An implementation under a scanned package is discovered, loaded, validated, and registered as
a singleton during bootstrap, then injected by constructor type. Full walkthrough:
[Add typed service configuration](../how-to/add-service-config.md).

### ServiceConfigLoader

```kotlin
interface ServiceConfigLoader<T : Any> {
    fun loadConfig(provider: ConfigProvider): T
}
```

A manual loader for infrastructure config you load and register yourself in the bootstrap
phase, before DI. Use it when a config must exist before the container starts and the
`database { … }` block does not cover it.

## ConfigLoaders helpers

The `katalyst-config-provider` module supplies type-safe extraction helpers. Two equivalent
styles exist: Kotlin extensions on `ConfigProvider`, and static `ConfigLoaders` methods.

### Kotlin extensions

```kotlin
provider.requiredString("notification.baseUrl")   // throws ConfigException if missing/blank
provider.requiredInt("notification.port")
provider.requiredLong("notification.windowMs")
provider.requiredBoolean("notification.strict")

provider.stringOrNull("notification.region")       // null if missing; throws if malformed
provider.intOrNull("notification.timeoutSeconds")
provider.longOrNull("notification.windowMs")

provider.boolean("notification.enabled")           // false if missing; throws if malformed

provider.optionalString("notification.region", "us-east")  // concrete fallback
provider.optionalInt("notification.timeoutSeconds", 30)
provider.optionalLong("notification.windowMs", 1000L)
provider.optionalBoolean("notification.enabled", true)
```

### Static ConfigLoaders

`ConfigLoaders` mirrors the extensions for Java/object-style usage and adds richer types:

```kotlin
ConfigLoaders.loadRequiredString(provider, "notification.baseUrl")
ConfigLoaders.loadIntOrNull(provider, "notification.retryCount")
ConfigLoaders.loadBoolean(provider, "notification.enabled")
ConfigLoaders.loadOptionalDuration(provider, "request.timeout", Duration.ofSeconds(30))
ConfigLoaders.loadOptionalList(provider, "allowed.hosts", emptyList())
ConfigLoaders.loadOptionalEnum(provider, "log.level", LogLevel.INFO)
```

Prefer `required*` and `*OrNull` for clarity; reach for `optional*` only when a concrete
fallback is part of the domain. Required helpers throw `ConfigException` on missing or blank
values, which fails the bootstrap with the offending key named.

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

Read when `enableServerConfiguration()` is set. All live under `ktor.deployment`.

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
  `enableServerConfiguration`.

