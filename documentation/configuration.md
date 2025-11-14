# Katalyst Configuration Pipeline

Katalyst ships a consistent configuration story so you can describe settings in YAML, validate them in code, and inject strongly typed values anywhere without hand-written wiring. This document explains how the pieces fit together and which modules participate in the flow.

## Module Map

| Concern | Module | Key APIs |
| --- | --- | --- |
| Runtime config tree + DI integration | `katalyst-config-provider` | `ConfigProvider`, `ConfigLoaders`, `ConfigBootstrapHelper` |
| YAML loader with profile support | `katalyst-config-yaml` | `YamlConfigProvider`, `${ENV:default}` interpolation |
| Bootstrapping + discovery | `katalyst-di`, `katalyst-scanner` | `katalystApplication`, `initializeKoinStandalone`, auto-binding registrar |
| Service-specific config loaders | Your module (e.g., `katalyst-example`) | `ServiceConfigLoader<T>` implementations such as `DatabaseConfigLoader` |

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
   fun main(args: Array<String>) = katalystApplication(args) {
       database(DbConfigImpl.loadDatabaseConfig())
       scanPackages("com.ead.katalyst.example")
       enableConfigProvider()
       // …
   }
   ```
   The builder lives in `katalyst-di`; calling `database(...)` ensures the persistence layer receives a fully validated `DatabaseConfig` before component discovery begins.

## Writing a ServiceConfigLoader

Service-specific configuration objects should implement `ServiceConfigLoader<T>` so they can be discovered/validated automatically.

```kotlin
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
