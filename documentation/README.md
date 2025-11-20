# Documentation Overview

Katalyst provides a comprehensive stack for building type-safe, auto-wired Kotlin applications. The documentation is organized by feature and use case.

## Recommended Reading Order

### Core Concepts (Start Here)

1. **[auto-wiring.md](auto-wiring.md)** – How components, services, scheduler jobs, events, routes, middleware, websockets, and **configuration objects** are automatically discovered and injected.

2. **[configuration.md](configuration.md)** – Complete guide to Katalyst's configuration system:
   - **`ServiceConfigLoader`** – Manual pattern for infrastructure config (database, ports, TLS)
   - **`AutomaticServiceConfigLoader`** – Modern pattern for service config (SMTP, APIs, feature flags)
   - YAML structure, environment variables, profiles, validation, type-safety
   - Real working example: SmtpConfig from the Boshi SMTP server project

### Persistence & Database

3. **[exposed-database-setup.md](exposed-database-setup.md)** – Exposed 0.52.0 JDBC integration, imports, transaction patterns, DSL reference.

4. **[persistence.md](persistence.md)** – Defining tables, repositories, and custom queries with Exposed/Hikari.

### Testing

5. **[testing.md](testing.md)** – Using `katalystTestEnvironment`/`katalystTestApplication`, overrides, Postgres/Testcontainers, coverage.

## Quick Navigation

| Need | See |
|------|-----|
| Choose between configuration patterns | [configuration.md](configuration.md) → **Choosing the Right Pattern** |
| Add service configuration (SMTP, APIs, etc.) | [configuration.md](configuration.md) → **AutomaticServiceConfigLoader** |
| Add infrastructure config (database, ports, etc.) | [configuration.md](configuration.md) → **ServiceConfigLoader** |
| Wire components, services, routes | [auto-wiring.md](auto-wiring.md) |
| Work with databases and repositories | [persistence.md](persistence.md) |
| Write tests and integration tests | [testing.md](testing.md) |
| Understand full JDBC integration | [exposed-database-setup.md](exposed-database-setup.md) |

## Configuration Patterns

Katalyst supports **two configuration patterns**. They are **not alternatives**—each solves a different problem and both are essential:

### Pattern 1: ServiceConfigLoader (Essential for Infrastructure)

For configuration needed **before DI bootstrap begins** (Phase 0):

```kotlin
// ServiceConfigLoader for infrastructure config
object DatabaseConfigLoader : ServiceConfigLoader<DatabaseConfig> {
    override fun loadConfig(provider: ConfigProvider): DatabaseConfig { ... }
}

// Used in bootstrap phase, before DI starts
fun main() = katalystApplication {
    database(DbConfigImpl.loadDatabaseConfig())  // ← Phase 0
    scanPackages("...")
    // ... DI bootstrap happens next
}
```

**Use for:** Database, HTTP ports, TLS certificates—infrastructure needed in bootstrap.

### Pattern 2: AutomaticServiceConfigLoader (Recommended for Service Config)

For configuration **injected into components during DI** (Phase 5a):

```kotlin
// AutomaticServiceConfigLoader for service config
object SmtpConfigLoader : AutomaticServiceConfigLoader<SmtpConfig> {
    override val configType = SmtpConfig::class
    override fun loadConfig(provider: ConfigProvider): SmtpConfig { ... }
    override fun validate(config: SmtpConfig) { ... }
}

// Auto-injected into components
class SmtpDeliveryService(val smtpConfig: SmtpConfig) : Service { ... }
```

**Use for:** SMTP, API credentials, feature toggles—anything component-scoped.

**Advantages:** Automatic discovery, constructor injection, fail-fast validation, no boilerplate.

For decision framework and detailed comparison, see **[configuration.md](configuration.md)** → **Choosing the Right Pattern for Your Configuration**.
