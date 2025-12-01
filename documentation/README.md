# Documentation Overview

Katalyst provides a comprehensive stack for building type-safe, auto-wired Kotlin applications. The documentation is organized by feature and use case.

## Recommended Reading Order

### Core Concepts (Start Here)

1. **[bootstrap.md](bootstrap.md)** – How to start a Katalyst app (engine, database, `scanPackages`, optional features like scheduler/events/websockets/config provider) using the same DSL as the sample and Boshi server.

2. **[auto-wiring.md](auto-wiring.md)** – How components, services, scheduler jobs, events, routes, middleware, websockets, and **configuration objects** are automatically discovered and injected.

3. **[configuration.md](configuration.md)** – Complete guide to Katalyst's configuration system:
   - **`ServiceConfigLoader`** – Manual pattern for infrastructure config (database, ports, TLS)
   - **`AutomaticServiceConfigLoader`** – Modern pattern for service config (SMTP, APIs, feature flags)
   - YAML structure, environment variables, profiles, validation, type-safety
   - Real working example: Notification API config loader; the Boshi SMTP module offers an email-focused variant if you want a production-size sample.

### Persistence & Database

4. **[exposed-database-setup.md](exposed-database-setup.md)** – Exposed 1.0.0-rc-3 JDBC integration, imports, transaction patterns, DSL reference.

5. **[persistence.md](persistence.md)** – Defining tables, repositories, and custom queries with Exposed/Hikari.

### Testing

6. **[testing.md](testing.md)** – Using `katalystTestEnvironment`/`katalystTestApplication`, overrides, Postgres/Testcontainers, coverage.

## Quick Navigation

| Need | See |
|------|-----|
| Bootstrap a server with scheduler/events/websockets/config provider | [bootstrap.md](bootstrap.md) |
| Choose between configuration patterns | [configuration.md](configuration.md) → **Choosing the Right Pattern** |
| Add service configuration (APIs, messaging, feature flags) | [configuration.md](configuration.md) → **AutomaticServiceConfigLoader** |
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
object NotificationApiConfigLoader : AutomaticServiceConfigLoader<NotificationApiConfig> {
    override val configType = NotificationApiConfig::class
    override fun loadConfig(provider: ConfigProvider): NotificationApiConfig { ... }
    override fun validate(config: NotificationApiConfig) { ... }
}

// Auto-injected into components
class NotificationService(val config: NotificationApiConfig) : Service { ... }
```

**Use for:** API credentials, messaging endpoints, feature toggles—anything component-scoped.

**Advantages:** Automatic discovery, constructor injection, fail-fast validation, no boilerplate.

For decision framework and detailed comparison, see **[configuration.md](configuration.md)** → **Choosing the Right Pattern for Your Configuration**.
