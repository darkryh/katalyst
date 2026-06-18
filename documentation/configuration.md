# Katalyst Configuration Pipeline

Katalyst ships a consistent configuration story so you can describe settings in YAML, validate them in code, and inject strongly typed values anywhere without hand-written wiring. This document explains how the pieces fit together and which modules participate in the flow.

## Module Map

| Concern | Module | Key APIs |
| --- | --- | --- |
| Runtime config tree + DI integration | `katalyst-config-provider` | `ConfigProvider`, `ConfigLoaders`, `ConfigBootstrapHelper` |
| Config loader SPI (format-agnostic) | `katalyst-config-spi` | `ConfigLoader`, `ProfileAwareConfigLoader`, `ConfigLoaderResolver` |
| YAML loader with profile support | `katalyst-config-yaml` | `YamlApplicationConfigLoader` (SPI), `${ENV:default}` interpolation |
| Bootstrapping + discovery | `katalyst-di`, `katalyst-scanner` | `katalystApplication`, `initializeKoinStandalone`, auto-binding registrar |
| Infrastructure config DSL | `katalyst-di` | `database { fromConfiguration() }`, `database(DatabaseConfig(...))` |
| Service-specific config loaders (automatic) | Your module (e.g., `katalyst-example`, feature modules) | `AutomaticServiceConfigLoader<T>` implementations such as `NotificationApiConfigLoader` |
| Force flag behavior | CLI | `force`/`--force` loads server config from CLI/defaults only; other configs (DB, services) still load via loaders/YAML |

## From YAML to Injected Values

1. **Define layered YAML** (`application.yaml`, `application-dev.yaml`, `application-prod.yaml`, etc.) inside the module’s `resources`. You can reference environment variables using `${VAR:default}`.
   ```yaml
   database:
     url: ${DB_URL:jdbc:postgresql://localhost:5432/postgres}
     username: ${DB_USERNAME:postgres}
     password: ${DB_PASSWORD:}
     driver: ${DB_DRIVER:org.postgresql.Driver}
   jwt:
     secret: ${JWT_SECRET:local-secret}
     issuer: ${JWT_ISSUER:katalyst-example}
   ```

2. **Install the configuration source once and declare database in the application DSL**:
   ```kotlin
   import io.github.darkryh.katalyst.config.yaml.enableYamlConfiguration
   import io.github.darkryh.katalyst.di.feature.enableServerConfiguration
   import io.github.darkryh.katalyst.di.katalystApplication
   import io.github.darkryh.katalyst.koin.KoinBeanEngine
   import io.github.darkryh.katalyst.ktor.engine.netty.NettyServer

   fun main(args: Array<String>) = katalystApplication(args) {
       engine(NettyServer)
       beanEngine(KoinBeanEngine)
       enableYamlConfiguration()
       database {
           fromConfiguration()
           // maxPoolSize = 20
           // minIdleConnections = 4
       }
       scanPackages("io.github.darkryh.katalyst.example")
       schema {
           validateOnStartup() // default when schema { ... } is omitted
       }
       features {
           enableServerConfiguration()
       }
       // …
   }
   ```
   `enableYamlConfiguration()` is the single YAML source for database bootstrap, server deployment, and runtime `ConfigProvider` injection. `database { fromConfiguration() }` reads `database.*` keys before DI starts and applies Hikari-oriented defaults for omitted pool values. If you prefer full code configuration, call `database(DatabaseConfig(...))`.

## Database DSL

Database configuration is infrastructure, so keep it visible in `Application.kt`:

```kotlin
database {
    fromConfiguration()
    maxPoolSize = 20
    minIdleConnections = 4
    connectionTimeout = 30_000L
}
```

Required YAML keys are `database.url`, `database.driver`, and `database.username`. Optional keys include `database.password`, `database.pool.maxSize`, `database.pool.minIdle`, `database.pool.connectionTimeout`, `database.pool.idleTimeout`, `database.pool.maxLifetime`, `database.autoCommit`, and `database.transactionIsolation`.

## Writing Service Config Loaders

Service-specific configuration objects should implement `AutomaticServiceConfigLoader<T>` so they can be discovered, validated, and injected automatically.

- `katalyst-config-provider` supplies Kotlin extension helpers such as `requiredString`, `requiredInt`, `requiredLong`, and `requiredBoolean` for mandatory values.
- Prefer nullable helpers for optional non-Boolean values: `stringOrNull`, `intOrNull`, and `longOrNull`. Missing values return `null`; malformed present values fail fast.
- Prefer `boolean("feature.enabled")` for optional Boolean flags. Missing values return `false`; malformed present values fail fast.
- The older `optional*` helpers remain available as compatibility wrappers when you intentionally want a non-null default fallback.
- `ConfigLoaders` remains available for Java/object-style usage and advanced helpers such as durations, lists, ranges, and enums.
- `validate` should throw `ConfigException` when values are invalid; Katalyst surfaces the error during bootstrap.

## Injecting Config at Runtime

Any constructor parameter typed as `ConfigProvider` (or a config object loaded via `AutomaticServiceConfigLoader`) is injected automatically. Example:

```kotlin
import io.github.darkryh.katalyst.core.component.Service
import io.github.darkryh.katalyst.core.config.ConfigProvider

class JwtSettingsService(config: ConfigProvider) : Service {
    private val secret = config.getString("jwt.secret")
    private val issuer = config.getString("jwt.issuer")
    init { require(secret.isNotBlank()) { "jwt.secret must not be blank" } }
}
```

## Advanced: Custom Profiles & Overrides

- **Profiles**: Set `KATALYST_PROFILE=prod` (or `dev`, `staging`); the YAML provider (if on the classpath) automatically loads `application-prod.yaml` after `application.yaml`, overriding matching keys.
- **External overrides**: Environment variables always win thanks to SnakeYAML interpolation.
- **Custom providers**: Implement `ConfigProvider` (e.g., to read from Consul/Secrets Manager) and register it via a feature in `katalystApplication { feature(YamlConfigurationFeature(myProvider)) }`.

Keep secrets out of source control, rely on `${ENV:default}` interpolation, and use `required*` helpers so misconfigurations fail fast during bootstrap.

## DI Parameter Validation

Katalyst DI keeps constructor and framework-function parameters strict:

- Object and config parameters are resolved from Koin.
- Kotlin default values are used when no binding exists for an optional parameter.
- Nullable missing parameters receive `null`.
- Required scalar values should be represented as a config object or have a Kotlin default.
- Missing required dependencies fail fast with actionable diagnostics.

If multiple implementations exist, use `@InjectNamed("...")` on the constructor parameter to disambiguate.

---

## AutomaticServiceConfigLoader: Automatic Discovery & Injection

While `ServiceConfigLoader` requires manual loading in the bootstrap phase, **`AutomaticServiceConfigLoader`** is the modern approach. It enables automatic discovery, loading, and injection of configuration objects during DI bootstrap—without any manual wiring code.

This pattern is ideal for **service configurations** that components need injected, as opposed to **infrastructure configurations** (like database) that must be loaded before DI begins.

### Overview

`AutomaticServiceConfigLoader<T>` is automatically discovered by Katalyst's DI system during **Phase 5a** of component registration. Each loader specifies:
- The configuration type it produces (`configType`)
- How to load the configuration from YAML (`loadConfig()`)
- How to validate it (`validate()`)

The framework then:
1. Discovers all implementations via classpath scanning
2. Loads each configuration in order
3. Validates each configuration (fail-fast)
4. Registers the result in Koin as a singleton
5. Makes it available for constructor injection

### Key Advantages

| Feature | ServiceConfigLoader | AutomaticServiceConfigLoader |
|---------|-------|---------|
| **Discovery** | Manual | Automatic via classpath scanning |
| **Registration** | Manual | Automatic via Koin |
| **Bootstrap timing** | Before DI (Phase 0) | During DI Phase 5a |
| **Injection** | Manual method call | Constructor parameter |
| **Boilerplate** | More (helper functions) | Less (single loader class) |
| **Error timing** | When config is loaded | At startup (fail-fast) |
| **Use case** | Infrastructure config (database) | Service/component config |
| **Type safety** | High | High |
| **Validation** | Required | Built-in |

See "Pattern Comparison: Not Migration, Just Right Tool for Job" section below for more details.

### Implementing AutomaticServiceConfigLoader

A complete implementation requires three steps:

**Step 1: Define your config data class**

```kotlin
data class NotificationApiConfig(
    val baseUrl: String,
    val apiKey: String,
    val timeoutSeconds: Int = 30,
    val retryCount: Int = 3
)
```

**Step 2: Implement AutomaticServiceConfigLoader**

```kotlin
import io.github.darkryh.katalyst.config.provider.AutomaticServiceConfigLoader
import io.github.darkryh.katalyst.config.provider.ConfigLoaders
import io.github.darkryh.katalyst.core.config.ConfigProvider
import kotlin.reflect.KClass

object NotificationApiConfigLoader : AutomaticServiceConfigLoader<NotificationApiConfig> {

    // REQUIRED: Tell the framework what type this loader produces
    override val configType: KClass<NotificationApiConfig> = NotificationApiConfig::class

    // REQUIRED: Load configuration from ConfigProvider
    override fun loadConfig(provider: ConfigProvider): NotificationApiConfig {
        return NotificationApiConfig(
            baseUrl = ConfigLoaders.loadRequiredString(provider, "notification.baseUrl"),
            apiKey = ConfigLoaders.loadRequiredString(provider, "notification.apiKey"),
            timeoutSeconds = ConfigLoaders.loadOptionalInt(provider, "notification.timeoutSeconds", 30),
            retryCount = ConfigLoaders.loadOptionalInt(provider, "notification.retryCount", 3)
        )
    }

    // OPTIONAL: Validate loaded configuration
    // Validation errors are caught at startup (fail-fast)
    override fun validate(config: NotificationApiConfig) {
        require(config.baseUrl.isNotBlank()) { "notification.baseUrl is required" }
        require(config.apiKey.isNotBlank()) { "notification.apiKey is required" }
        require(config.timeoutSeconds > 0) { "timeoutSeconds must be > 0" }
        require(config.retryCount >= 0) { "retryCount must be >= 0" }
    }
}
```

**Step 3: Inject into your service/component**

```kotlin
import io.github.darkryh.katalyst.core.component.Component

class NotificationClient(
    val config: NotificationApiConfig  // ✅ Auto-injected by DI!
) : Component {

    fun sendMessage(recipient: String, body: String) {
        // Use config directly - it's already loaded and validated
        val url = "${config.baseUrl}/messages"
        // call the API with config.apiKey, config.timeoutSeconds, config.retryCount...
    }
}
```

That's it! No manual loading, no helper functions, just clean constructor injection.

### Configuration YAML

Define your configuration in `application.yaml` (or profile-specific variants):

```yaml
notification:
  baseUrl: ${NOTIFICATION_BASE_URL:https://api.notifications.local}
  apiKey: ${NOTIFICATION_API_KEY:dev-api-key}
  timeoutSeconds: ${NOTIFICATION_TIMEOUT_SECONDS:30}
  retryCount: ${NOTIFICATION_RETRY_COUNT:3}
```

Environment variables always override YAML defaults via `${VAR:default}` interpolation.

### How Discovery Works (Phase 5a)

During DI bootstrap, the framework executes **7 phases** in order:

```
Phase 1: Component Discovery           (scan packages, find components/services)
Phase 2: Dependency Analysis           (build dependency graph)
Phase 3: Dependency Validation         (check all dependencies are resolvable)
Phase 4: Order Computation             (topological sort for safe instantiation)
─────────────────────────────────────────────────────────────────
Phase 5a: Automatic Configuration Loading   ✨ NEW
  ├─ Discover AutomaticServiceConfigLoader implementations
  ├─ Load each configuration from YAML
  ├─ Validate each configuration (fail-fast)
  └─ Register each configuration in Koin
─────────────────────────────────────────────────────────────────
Phase 5b: Component Registration       (instantiate components in safe order)
Phase 6: Database Table Discovery      (create/migrate tables)
Phase 7: Route Discovery               (register HTTP routes)
```

**Phase 5a key points:**
- Happens **before** components are instantiated (Phase 5b)
- ConfigProvider is already available from features (Phase 0)
- All configs are discovered via bytecode scanning
- Errors are fatal (fail-fast) and prevent startup
- Registered configs are available for Phase 5b component injection

### Startup trace example

Expect to see your loaders discovered and registered during Phase 5a:

```
2025-11-20 15:06:41.565 INFO  ComponentRegistrationOrchestrator - Phase 5a: Automatic Configuration Loading and Registration
2025-11-20 15:06:41.565 INFO  ComponentRegistrationOrchestrator - Discovering AutomaticServiceConfigLoader implementations...
2025-11-20 15:06:41.565 INFO  ComponentRegistrationOrchestrator - Discovered 1 automatic config loader(s)
2025-11-20 15:06:41.565 INFO  ComponentRegistrationOrchestrator - Loading configuration for NotificationApiConfig
2025-11-20 15:06:41.565 INFO  ComponentRegistrationOrchestrator - ✓ Registered NotificationApiConfig configuration
2025-11-20 15:06:41.565 INFO  ComponentRegistrationOrchestrator - ✓ All 1 automatic configuration(s) loaded and registered
```

Need a fully fleshed example? The Boshi SMTP module under `projects/boshi/boshi-server` shows a production-grade loader and client wired the same way; the pattern is identical for any external API config.

### Error Handling & Validation

Configuration errors are caught **at startup** (fail-fast), preventing silent failures:

```kotlin
// Example YAML (missing required notification.baseUrl)
notification:
  apiKey: abc123
  # ERROR: baseUrl is missing!
```

At startup, you'll see:

```
ConfigException: Required configuration key 'notification.baseUrl' is missing or blank
  at NotificationApiConfigLoader.validate()
  at ComponentRegistrationOrchestrator.registerAutomaticConfigurations()
```

This prevents the application from starting with invalid configuration. The alternative (manual `ServiceConfigLoader`) might fail later when the config is first used.

---

## Choosing the Right Pattern for Your Configuration

Both `ServiceConfigLoader` and `AutomaticServiceConfigLoader` are first-class patterns in Katalyst. They are **not alternatives to each other**—each solves a different problem and both are essential:

- **`ServiceConfigLoader`** → Infrastructure config needed **before** DI bootstrap (Phase 0)
- **`AutomaticServiceConfigLoader`** → Service/component config injected **during** DI bootstrap (Phase 5a)

### Decision Framework

| Question | Answer | Use Pattern |
|----------|--------|-------------|
| Do you need this config before DI can start? | Yes (e.g., database) | **ServiceConfigLoader** |
| Do you need this config injected into components? | Yes (e.g., API keys, feature toggles) | **AutomaticServiceConfigLoader** |
| Must this be available in `katalystApplication` block? | Yes | **ServiceConfigLoader** |
| Does this only affect components/services? | Yes | **AutomaticServiceConfigLoader** |
| Do you want constructor injection? | Yes | **AutomaticServiceConfigLoader** |
| Do you want manual bootstrap control? | Yes | **ServiceConfigLoader** |

### Example: Choosing for NotificationApiConfig

**If you used ServiceConfigLoader for NotificationApiConfig (manual):**

```kotlin
object NotificationApiConfigLoader : ServiceConfigLoader<NotificationApiConfig> {
    override fun loadConfig(provider: ConfigProvider): NotificationApiConfig { ... }
}

object NotificationApiConfigImpl {
    fun loadConfig(): NotificationApiConfig = /* manual loading logic */
}

class NotificationClient() : Component {
    val config = NotificationApiConfigImpl.loadConfig()  // Manual loading
}
```

**Preferred approach: AutomaticServiceConfigLoader**

```kotlin
object NotificationApiConfigLoader : AutomaticServiceConfigLoader<NotificationApiConfig> {
    override val configType = NotificationApiConfig::class
    override fun loadConfig(provider: ConfigProvider): NotificationApiConfig { ... }
}

class NotificationClient(
    val config: NotificationApiConfig  // Clean constructor injection
) : Component { ... }
```

**Why this is better for NotificationApiConfig:**
- The config is only needed by components (not infrastructure)
- Components declare their dependency clearly (constructor parameter)
- No manual helper functions needed
- Automatic discovery during Phase 5a
- Fail-fast validation at startup

### Database DSL Is Still Essential

The application DSL remains the **correct choice** for database infrastructure configuration:

```kotlin
// Used in bootstrap (Phase 0, before DI starts)
fun main(args: Array<String>) = katalystApplication(args) {
    enableYamlConfiguration()                    // source installed once
    database {
        fromConfiguration()                      // reads database.* before DI starts
        maxPoolSize = 20                         // optional code override
    }
    scanPackages("com.example")
    // ... rest of setup
}
```

You cannot use AutomaticServiceConfigLoader for database config because:
1. Database must be ready in Phase 0 (before DI bootstrap)
2. AutomaticServiceConfigLoader loads in Phase 5a (during DI)
3. Phase 5a is too late for database configuration

### Pattern Comparison: Not Migration, Just Right Tool for Job

Both patterns serve different purposes and are both essential. Choose the database DSL for database infrastructure needed before DI bootstrap (Phase 0). Choose AutomaticServiceConfigLoader for service config (SMTP, APIs, feature toggles) injected during DI Phase 5a. See "Choosing the Right Pattern for Your Configuration" section for the decision framework.

### Startup controls

- **Explicit source required**: Katalyst does not install a configuration source from the classpath. Call `enableYamlConfiguration()` or `configuration(customSource)` before startup.
- **Force flag**: Use `force`/`--force` to bypass the configured source for server deployment (ktor.deployment.*) and rely on CLI config. Other configs still load from the explicit source you pass.

---

## ConfigLoaders Utilities

Both patterns use `ConfigLoaders` for consistent, type-safe configuration extraction:

```kotlin
// Required values (throw ConfigException if missing/blank)
val baseUrl = ConfigLoaders.loadRequiredString(provider, "notification.baseUrl")
val apiKey = ConfigLoaders.loadRequiredString(provider, "notification.apiKey")

// Optional non-Boolean values (return null if missing, throw if malformed)
val timeout = ConfigLoaders.loadLongOrNull(provider, "notification.timeoutMillis")
val retryCount = ConfigLoaders.loadIntOrNull(provider, "notification.retryCount")

// Optional Boolean flags (false if missing, throw if malformed)
val enabled = ConfigLoaders.loadBoolean(provider, "notification.enabled")

// Special types
val duration = ConfigLoaders.loadOptionalDuration(provider, "request.timeout", Duration.ofSeconds(30))
val list = ConfigLoaders.loadOptionalList(provider, "allowed.hosts", emptyList())
val enum = ConfigLoaders.loadOptionalEnum(provider, "log.level", LogLevel.INFO)
```

Always prefer `ConfigLoaders` helpers or the Kotlin extensions over direct `ConfigProvider.getString()` calls for consistency and better error messages. Use `optional*` only when a concrete fallback is part of the domain, not just to avoid nullable properties.
