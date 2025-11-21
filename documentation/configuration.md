# Katalyst Configuration Pipeline

Katalyst ships a consistent configuration story so you can describe settings in YAML, validate them in code, and inject strongly typed values anywhere without hand-written wiring. This document explains how the pieces fit together and which modules participate in the flow.

## Module Map

| Concern | Module | Key APIs |
| --- | --- | --- |
| Runtime config tree + DI integration | `katalyst-config-provider` | `ConfigProvider`, `ConfigLoaders`, `ConfigBootstrapHelper` |
| YAML loader with profile support | `katalyst-config-yaml` | `YamlConfigProvider`, `${ENV:default}` interpolation |
| Bootstrapping + discovery | `katalyst-di`, `katalyst-scanner` | `katalystApplication`, `initializeKoinStandalone`, auto-binding registrar |
| Service-specific config loaders (manual) | Your module (e.g., `katalyst-example`) | `ServiceConfigLoader<T>` implementations such as `DatabaseConfigLoader` |
| Service-specific config loaders (automatic) | Your module (e.g., `boshi-shared`) | `AutomaticServiceConfigLoader<T>` implementations such as `SmtpConfigLoader` |

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

2. **Load the configuration provider** before DI boot:
   ```kotlin
   import com.ead.katalyst.config.provider.ConfigBootstrapHelper
   import com.ead.katalyst.config.yaml.YamlConfigProvider

   object DbConfigImpl {
       fun loadDatabaseConfig(): DatabaseConfig {
           val config = ConfigBootstrapHelper.loadConfig(YamlConfigProvider::class.java)
           return ConfigBootstrapHelper.loadServiceConfig(config, DatabaseConfigLoader)
       }
   }
   ```
   - `ConfigBootstrapHelper` comes from `katalyst-config-provider`.
   - `YamlConfigProvider` lives in `katalyst-config-yaml` and automatically merges base + profile-specific YAML files (controlled via `KATALYST_PROFILE`).

3. **Register the config with the application builder**:
   ```kotlin
   import com.ead.katalyst.di.katalystApplication
   import com.ead.katalyst.di.feature.enableServerConfiguration
   import com.ead.katalyst.config.yaml.enableConfigProvider
   import com.ead.katalyst.ktor.engine.netty.NettyEngine

   fun main(args: Array<String>) = katalystApplication(args) {
       engine(NettyEngine)
       database(DbConfigImpl.loadDatabaseConfig())
       scanPackages("com.ead.katalyst.example")
       enableServerConfiguration()
       enableConfigProvider()
       // …
   }
   ```
   The builder lives in `katalyst-di`; calling `database(...)` ensures the persistence layer receives a fully validated `DatabaseConfig` before component discovery begins.

## Writing a ServiceConfigLoader

Service-specific configuration objects should implement `ServiceConfigLoader<T>` so they can be discovered/validated automatically.

```kotlin
import com.ead.katalyst.config.provider.ServiceConfigLoader
import com.ead.katalyst.config.provider.ConfigLoaders
import com.ead.katalyst.core.config.ConfigProvider

object DatabaseConfigLoader : ServiceConfigLoader<DatabaseConfig> {
    override fun loadConfig(provider: ConfigProvider): DatabaseConfig {
        val url = ConfigLoaders.loadRequiredString(provider, "database.url")
        val driver = ConfigLoaders.loadRequiredString(provider, "database.driver")
        val username = ConfigLoaders.loadRequiredString(provider, "database.username")
        val password = ConfigLoaders.loadOptionalString(provider, "database.password")
        return DatabaseConfig(url = url, driver = driver, username = username, password = password)
    }

    override fun validate(config: DatabaseConfig) {
        Class.forName(config.driver) // fail fast if JDBC driver missing
    }
}
```

- `ConfigLoaders` (katalyst-config-provider) supply helpers for required/optional primitives, durations, lists, enums, etc.
- `validate` should throw `ConfigException` when values are invalid; Katalyst surfaces the error during bootstrap.

## Injecting Config at Runtime

Any constructor parameter typed as `ConfigProvider` (or a config object loaded via `ServiceConfigLoader`) is injected automatically—no manual `GlobalContext` lookups required. Example:

```kotlin
import com.ead.katalyst.core.component.Service
import com.ead.katalyst.core.config.ConfigProvider

class JwtSettingsService(config: ConfigProvider) : Service {
    private val secret = config.getString("jwt.secret")
    private val issuer = config.getString("jwt.issuer")
    init { require(secret.isNotBlank()) { "jwt.secret must not be blank" } }
}
```

## Advanced: Custom Profiles & Overrides

- **Profiles**: Set `KATALYST_PROFILE=prod` (or `dev`, `staging`) and `YamlConfigProvider` automatically loads `application-prod.yaml` after `application.yaml`, overriding matching keys.
- **External overrides**: Environment variables always win thanks to SnakeYAML interpolation.
- **Custom providers**: Implement `ConfigProvider` (e.g., to read from Consul/Secrets Manager) and register it via a feature in `katalystApplication { feature(ConfigProviderFeature(myProvider)) }`.

Keep secrets out of source control, rely on `${ENV:default}` interpolation, and use `ConfigLoaders.loadRequired*` helpers so misconfigurations fail fast during bootstrap.

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
data class SmtpConfig(
    val host: String,
    val port: Int = 25,
    val username: String = "",
    val password: String = "",
    val useTls: Boolean = false,
    val connectionTimeoutSeconds: Int = 30,
    val readTimeoutSeconds: Int = 30
)
```

**Step 2: Implement AutomaticServiceConfigLoader**

```kotlin
import com.ead.katalyst.config.provider.AutomaticServiceConfigLoader
import com.ead.katalyst.config.provider.ConfigLoaders
import com.ead.katalyst.core.config.ConfigProvider
import kotlin.reflect.KClass

object SmtpConfigLoader : AutomaticServiceConfigLoader<SmtpConfig> {

    // REQUIRED: Tell the framework what type this loader produces
    override val configType: KClass<SmtpConfig> = SmtpConfig::class

    // REQUIRED: Load configuration from ConfigProvider
    override fun loadConfig(provider: ConfigProvider): SmtpConfig {
        return SmtpConfig(
            // Use ConfigLoaders for consistent patterns
            host = ConfigLoaders.loadRequiredString(provider, "smtp.host"),
            port = ConfigLoaders.loadOptionalInt(provider, "smtp.port", 25),
            username = ConfigLoaders.loadOptionalString(provider, "smtp.username", ""),
            password = ConfigLoaders.loadOptionalString(provider, "smtp.password", ""),
            useTls = ConfigLoaders.loadOptionalBoolean(provider, "smtp.useTls", false),
            connectionTimeoutSeconds = ConfigLoaders.loadOptionalInt(provider, "smtp.connectionTimeoutSeconds", 30),
            readTimeoutSeconds = ConfigLoaders.loadOptionalInt(provider, "smtp.readTimeoutSeconds", 30)
        )
    }

    // OPTIONAL: Validate loaded configuration
    // Validation errors are caught at startup (fail-fast)
    override fun validate(config: SmtpConfig) {
        require(config.host.isNotBlank()) { "SMTP host is required" }
        require(config.port > 0) { "SMTP port must be > 0" }
        require(config.port <= 65535) { "SMTP port must be <= 65535" }
        require(config.connectionTimeoutSeconds > 0) { "Connection timeout must be > 0" }
        require(config.readTimeoutSeconds > 0) { "Read timeout must be > 0" }
    }
}
```

**Step 3: Inject into your service/component**

```kotlin
import com.ead.katalyst.core.component.Component

class SmtpClient(
    val smtpConfig: SmtpConfig  // ✅ Auto-injected by DI!
) : Component {

    fun sendEmail(host: String, email: String, subject: String, body: String) {
        // Use smtpConfig directly - it's already loaded and validated
        val port = smtpConfig.port
        val auth = smtpConfig.username to smtpConfig.password

        // Send email...
    }
}
```

That's it! No manual loading, no helper functions, just clean constructor injection.

### Configuration YAML

Define your configuration in `application.yaml` (or profile-specific variants):

```yaml
smtp:
  # Client configuration for sending emails via external SMTP server
  host: ${SMTP_HOST:localhost}                     # REQUIRED (or empty default)
  port: ${SMTP_PORT:25}                            # OPTIONAL: default 25
  username: ${SMTP_USERNAME:}                      # OPTIONAL
  password: ${SMTP_PASSWORD:}                      # OPTIONAL
  useTls: ${SMTP_USE_TLS:false}                    # OPTIONAL: default false
  connectionTimeoutSeconds: ${SMTP_TIMEOUT:30}     # OPTIONAL: default 30
  readTimeoutSeconds: ${SMTP_READ_TIMEOUT:30}      # OPTIONAL: default 30
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

### Real-World Example: SmtpConfig

The Boshi SMTP server project includes a complete working example:

**File:** `projects/boshi/boshi-server/boshi-shared/src/main/kotlin/com/ead/boshi/shared/config/SmtpConfigLoader.kt`

```kotlin
@Suppress("unused")
object SmtpConfigLoader : AutomaticServiceConfigLoader<SmtpConfig> {
    override val configType: KClass<SmtpConfig> = SmtpConfig::class

    override fun loadConfig(provider: ConfigProvider): SmtpConfig {
        return SmtpConfig(
            host = ConfigLoaders.loadRequiredString(provider, "smtp.host"),
            port = ConfigLoaders.loadOptionalInt(provider, "smtp.port", 25),
            localHostname = ConfigLoaders.loadOptionalString(provider, "smtp.localHostname", "boshi.local"),
            username = ConfigLoaders.loadOptionalString(provider, "smtp.username", ""),
            password = ConfigLoaders.loadOptionalString(provider, "smtp.password", ""),
            useTls = ConfigLoaders.loadOptionalBoolean(provider, "smtp.useTls", false),
            connectionTimeoutSeconds = ConfigLoaders.loadOptionalInt(provider, "smtp.connectionTimeoutSeconds", 30),
            readTimeoutSeconds = ConfigLoaders.loadOptionalInt(provider, "smtp.readTimeoutSeconds", 30)
        )
    }

    override fun validate(config: SmtpConfig) {
        require(config.host.isNotBlank()) { "SMTP host is required and cannot be blank" }
        require(config.port > 0) { "SMTP port must be > 0" }
        require(config.port <= 65535) { "SMTP port must be <= 65535" }
        require(config.connectionTimeoutSeconds > 0) { "Connection timeout must be > 0" }
        require(config.readTimeoutSeconds > 0) { "Read timeout must be > 0" }
    }
}
```

**File:** `projects/boshi/boshi-server/boshi-smtp/src/main/kotlin/com/ead/boshi/smtp/clients/SmtpClient.kt`

```kotlin
class SmtpClient(
    val smtpConfig: SmtpConfig  // ✅ Auto-injected by DI!
) : Component {

    fun sendEmail(
        smtpHost: String,
        senderEmail: String,
        recipientEmail: String,
        subject: String,
        body: String,
        messageId: String
    ): String {
        // SmtpConfig is already loaded, validated, and available
        val socket = Socket()
        socket.connect(
            java.net.InetSocketAddress(smtpHost, smtpConfig.port),
            SMTP_CONNECT_TIMEOUT_MS
        )
        // ... SMTP protocol implementation using smtpConfig
        return acceptResponse
    }
}
```

At startup, the output shows:

```
2025-11-20 15:06:41.565 INFO  ComponentRegistrationOrchestrator - Phase 5a: Automatic Configuration Loading and Registration
2025-11-20 15:06:41.565 INFO  ComponentRegistrationOrchestrator - Discovering AutomaticServiceConfigLoader implementations...
2025-11-20 15:06:41.565 INFO  ComponentRegistrationOrchestrator - Discovered 1 automatic config loader(s)
2025-11-20 15:06:41.565 INFO  ComponentRegistrationOrchestrator - Loading configuration for SmtpConfig
2025-11-20 15:06:41.565 INFO  ComponentRegistrationOrchestrator - ✓ Registered SmtpConfig configuration
2025-11-20 15:06:41.565 INFO  ComponentRegistrationOrchestrator - ✓ All 1 automatic configuration(s) loaded and registered
```

### Error Handling & Validation

Configuration errors are caught **at startup** (fail-fast), preventing silent failures:

```kotlin
// Example YAML (missing required smtp.host)
smtp:
  port: 587
  # ERROR: host is missing!
```

At startup, you'll see:

```
ConfigException: Required configuration key 'smtp.host' is missing or blank
  at SmtpConfigLoader.validate()
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
| Do you need this config injected into components? | Yes (e.g., SMTP settings) | **AutomaticServiceConfigLoader** |
| Must this be available in `katalystApplication` block? | Yes | **ServiceConfigLoader** |
| Does this only affect components/services? | Yes | **AutomaticServiceConfigLoader** |
| Do you want constructor injection? | Yes | **AutomaticServiceConfigLoader** |
| Do you want manual bootstrap control? | Yes | **ServiceConfigLoader** |

### Example: Choosing for SmtpConfig

**If you were using ServiceConfigLoader for SmtpConfig:**

```kotlin
// ServiceConfigLoader pattern (manual loading)
object SmtpConfigLoader : ServiceConfigLoader<SmtpConfig> {
    override fun loadConfig(provider: ConfigProvider): SmtpConfig { ... }
}

object SmtpConfigImpl {
    fun loadConfig(): SmtpConfig = /* manual loading logic */
}

class SmtpClient() : Component {
    val smtpConfig = SmtpConfigImpl.loadConfig()  // Manual loading
}
```

**Better approach: Use AutomaticServiceConfigLoader**

```kotlin
// AutomaticServiceConfigLoader pattern (automatic)
object SmtpConfigLoader : AutomaticServiceConfigLoader<SmtpConfig> {
    override val configType = SmtpConfig::class  // ← Type declaration
    override fun loadConfig(provider: ConfigProvider): SmtpConfig { ... }
}

// No helper function needed!

class SmtpClient(
    val smtpConfig: SmtpConfig  // ← Clean constructor injection
) : Component { ... }
```

**Why this is better for SmtpConfig specifically:**
- SmtpConfig is only needed by components (not infrastructure)
- Components declare their dependency clearly (constructor parameter)
- No manual helper functions needed
- Automatic discovery during Phase 5a
- Fail-fast validation at startup

### ServiceConfigLoader Is Still Essential

ServiceConfigLoader remains the **correct choice** for infrastructure configuration:

```kotlin
// ServiceConfigLoader MUST be used for database config
object DatabaseConfigLoader : ServiceConfigLoader<DatabaseConfig> {
    override fun loadConfig(provider: ConfigProvider): DatabaseConfig { ... }
}

// Used in bootstrap (Phase 0, before DI starts)
fun main(args: Array<String>) = katalystApplication(args) {
    database(DbConfigImpl.loadDatabaseConfig())  // ← Bootstrap phase
    // ... rest of setup
}
```

You cannot use AutomaticServiceConfigLoader for database config because:
1. Database must be ready in Phase 0 (before DI bootstrap)
2. AutomaticServiceConfigLoader loads in Phase 5a (during DI)
3. Phase 5a is too late for database configuration

### Pattern Comparison: Not Migration, Just Right Tool for Job

Both patterns serve different purposes and are both essential. Choose ServiceConfigLoader for infrastructure config (database, ports, TLS) needed before DI bootstrap (Phase 0). Choose AutomaticServiceConfigLoader for service config (SMTP, APIs, feature toggles) injected during DI Phase 5a. See "Choosing the Right Pattern for Your Configuration" section for the decision framework.

---

## ConfigLoaders Utilities

Both patterns use `ConfigLoaders` for consistent, type-safe configuration extraction:

```kotlin
// Required values (throw ConfigException if missing/blank)
val host = ConfigLoaders.loadRequiredString(provider, "smtp.host")
val port = ConfigLoaders.loadRequiredInt(provider, "smtp.port")

// Optional values (return default if missing)
val username = ConfigLoaders.loadOptionalString(provider, "smtp.username", "")
val timeout = ConfigLoaders.loadOptionalLong(provider, "timeout.ms", 30_000L)

// Special types
val duration = ConfigLoaders.loadOptionalDuration(provider, "request.timeout", Duration.ofSeconds(30))
val list = ConfigLoaders.loadOptionalList(provider, "allowed.hosts", emptyList())
val enum = ConfigLoaders.loadOptionalEnum(provider, "log.level", LogLevel.INFO)
```

Always prefer `ConfigLoaders` helpers over direct `ConfigProvider.getString()` calls for consistency and better error messages.
