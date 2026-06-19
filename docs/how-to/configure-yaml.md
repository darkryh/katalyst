# Configure your application with YAML

Katalyst loads infrastructure and runtime settings from YAML. This guide shows how to set
up the configuration source, use profiles, interpolate environment variables, and supply the
keys Katalyst recognizes. For typed config objects injected into your components, see
[Add typed service configuration](add-service-config.md).

## Install the YAML source

Katalyst does not install a configuration source from the classpath automatically. Call
`enableYamlConfiguration()` once in your bootstrap:

```kotlin
fun main(args: Array<String>) = katalystApplication(args) {
    engine(NettyServer)
    beanEngine(KoinBeanEngine)
    enableYamlConfiguration()          // installs the YAML source
    database { fromConfiguration() }   // reads database.* from it
    scanPackages("com.example")
    features { enableServerConfiguration() }
}
```

Place `application.yaml` under `src/main/resources`.

## Supply the database keys

`database { fromConfiguration() }` reads the `database.*` tree before dependency injection
starts. Required keys are `database.url`, `database.driver`, and `database.username`.

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
    idleTimeout: ${DB_IDLE_TIMEOUT:600000}
    maxLifetime: ${DB_MAX_LIFETIME:1800000}
```

Pool keys are optional; Katalyst applies HikariCP-oriented defaults for any you omit. You
can override individual pool values in code, which is useful when one value must be computed:

```kotlin
database {
    fromConfiguration()
    maxPoolSize = 20
    minIdleConnections = 4
}
```

The full key list is in the [configuration reference](../reference/configuration.md#database-keys).

## Interpolate environment variables

Any value may reference an environment variable with `${VAR:default}`. The variable wins
when set; otherwise the default after the colon is used. An empty default (`${DB_PASSWORD:}`)
resolves to an empty string.

```yaml
jwt:
  secret: ${JWT_SECRET:local-secret-for-development-only}
  issuer: ${JWT_ISSUER:katalyst-example}
```

Keep secrets out of source control: commit safe defaults and inject real values through the
environment in deployed environments.

## Use profiles

Set the `KATALYST_PROFILE` environment variable to layer a profile file on top of the base
`application.yaml`. With `KATALYST_PROFILE=prod`, Katalyst loads `application.yaml` first,
then `application-prod.yaml`, with the profile overriding matching keys.

```bash
export KATALYST_PROFILE=prod
java -jar app.jar
```

Typical layout:

```
src/main/resources/
├── application.yaml          # shared defaults
├── application-dev.yaml      # local development overrides
├── application-staging.yaml
└── application-prod.yaml     # production overrides (mostly ${ENV} references)
```

## Load the server deployment block

To configure the Ktor engine (host, port, thread pools, timeouts, TLS) from YAML, enable
the server configuration feature and add a `ktor.deployment` block:

```kotlin
features { enableServerConfiguration() }
```

```yaml
ktor:
  deployment:
    host: ${SERVER_HOST:0.0.0.0}
    port: ${SERVER_PORT:8080}
    shutdownGracePeriod: ${SHUTDOWN_GRACE_PERIOD:1000}
    connectionGroupSize: ${CONNECTION_GROUP_SIZE:8}
    workerGroupSize: ${WORKER_GROUP_SIZE:8}
    callGroupSize: ${CALL_GROUP_SIZE:8}
```

See the [server deployment keys](../reference/configuration.md#server-deployment-keys) for the
complete set, including TLS.

## Read config values directly

Any component can receive the `ConfigProvider` by constructor parameter and read arbitrary
keys:

```kotlin
class JwtSettingsService(config: ConfigProvider) : Service {
    private val secret = config.getString("jwt.secret")
    private val issuer = config.getString("jwt.issuer")
    init { require(secret.isNotBlank()) { "jwt.secret must not be blank" } }
}
```

For anything beyond a handful of ad-hoc reads, prefer a typed config object — see
[Add typed service configuration](add-service-config.md).

## Related

- [Configuration reference](../reference/configuration.md) — every key and helper.
- [Add typed service configuration](add-service-config.md) — `AutomaticServiceConfigLoader`.
- [Choose a server engine](choose-an-engine.md) — how the deployment block maps to engines.

