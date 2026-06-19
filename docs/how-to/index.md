# How-to guides

How-to guides are recipes. Each one solves a single real task and assumes you already know
the basics (if you don't, start with the [tutorial](../getting-started.md)). For the full
option set behind any task, follow the links into the [reference](../reference/index.md).

## Configuration

- [Configure your application with YAML](configure-yaml.md) — profiles, environment
  variables, database keys, server deployment.
- [Add typed service configuration](add-service-config.md) — load and inject your own
  config objects with `AutomaticServiceConfigLoader`.

## Persistence

- [Define tables and repositories](define-tables-and-repositories.md) — map entities, add
  custom queries, run managed raw SQL.
- [Run database migrations](run-migrations.md) — write migrations, control startup
  execution, check status and dry-runs.

## Application features

- [Schedule background jobs](schedule-jobs.md) — cron, fixed-delay, fixed-rate, and
  one-time jobs.
- [Publish and handle events](publish-and-handle-events.md) — domain events, handlers, and
  transaction-aware publishing.
- [Add routes, middleware, and exception handlers](add-routes-and-middleware.md) — the Ktor
  DSLs Katalyst installs for you.
- [Add WebSockets](add-websockets.md) — real-time endpoints with auto-discovery.

## Runtime

- [Choose a server engine](choose-an-engine.md) — Netty, Jetty, or CIO.
- [Test your application](test-applications.md) — unit, integration, and end-to-end tests.

