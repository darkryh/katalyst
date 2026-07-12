# Architecture & bootstrap lifecycle

This page explains what happens when a Katalyst application starts and how the pieces relate.
It is background reading; for instructions, see the [how-to guides](../how-to/index.md), and
for the API, the [reference](../reference/index.md).

## The shape of an application

A Katalyst application is an ordinary Ktor server with a discovery-and-injection layer in
front of it. You write plain Kotlin classes and functions; Katalyst finds them by interface,
works out how they depend on each other, builds them in a safe order, and hands the wired
graph to Ktor. The `katalystApplication { … }` block is the only place where this is
configured, and it stays deliberately explicit — every capability is opted into by a visible
line, nothing is switched on by magic.

The layers, from the outside in:

- **Engine** (`katalyst-ktor-engine-*`) — the Ktor server (Netty, Jetty, or CIO).
- **Ktor integration** (`katalyst-ktor`) — the routing, middleware, WebSocket, and
  exception-handler DSLs, plus `ktInject`.
- **Container** (`katalyst-di` + a bean engine like `katalyst-koin-bean`) — discovery,
  dependency analysis, validation, ordering, and instantiation.
- **Domain modules** (`katalyst-persistence`, `katalyst-transactions`, `katalyst-scheduler`,
  `katalyst-events*`, `katalyst-migrations`) — the capabilities your code uses.
- **Configuration** (`katalyst-config-*`) — the values that feed all of the above.

## The bootstrap phases

When `katalystApplication` runs, the container performs an ordered bootstrap. Thinking of it
as phases explains why some things must be configured in the DSL and others can be injected.

```
Phase 0  Infrastructure setup     engine, bean engine, config source, database
─────────────────────────────────────────────────────────────────────────────
Phase 1  Component discovery      scan packages; find components, services, repos,
                                  tables, handlers, routes, jobs, migrations, loaders
Phase 2  Dependency analysis      build the dependency graph from constructor params
Phase 3  Dependency validation    check every dependency is resolvable; fail fast
Phase 4  Order computation        topological sort for safe instantiation order
─────────────────────────────────────────────────────────────────────────────
Phase 5a Configuration loading    discover `@ConfigPrefix` and `ConfigBinding` types via
                                  `ConfigBinder`, bind, validate, and register them
Phase 5b Component registration   instantiate components in the computed order
Phase 6  Schema/table handling    apply the schema policy; run migrations if enabled
Phase 7  Route registration       install routes, middleware, WebSockets, handlers
```

Two things follow directly from this ordering:

- **Infrastructure config must exist in Phase 0.** The database is needed before the
  container can build anything that touches it, so it is configured in the DSL
  (`database { … }`), not injected. This is why the database cannot use a `ConfigBinder`-bound
  `@ConfigPrefix`/`ConfigBinding` type, which is only bound in Phase 5a.
- **Service config is injected.** A bound config object is registered in Phase 5a, just before
  components are built in Phase 5b, so any component can receive it by constructor parameter.

## Discovery and the dependency graph

Discovery is structural, not annotation-based. A class is a service because it implements
`Service`; a repository because it implements `CrudRepository`; a route because the function
calls `katalystRouting`. Katalyst reads each discovered class's constructor and treats every
parameter as a dependency edge. From those edges it builds a `DependencyGraph`, validates it
(`DependencyValidator`), and computes an instantiation order (`ComponentOrderComputer`) so a
component is always built after the things it needs.

Because the graph is known before anything is instantiated, whole classes of failure are
caught at startup rather than at the first request: a missing binding
(`MissingDependencyError`), a cycle (`CircularDependencyError`), or a type that cannot be
constructed (`UninstantiableTypeError`). The result is reported as a `ValidationReport`, and
fatal problems throw before the server ever binds a port.

## Why a bean engine is a separate choice

Katalyst owns discovery, analysis, validation, and ordering. The final step — actually
registering and resolving singletons — is delegated to a *bean engine*. In this alpha the
only adapter is `KoinBeanEngine`, but the split means the public DSL never exposes Koin types;
your code depends on Katalyst interfaces, not on the DI library. That keeps the door open for
a container SPI later without rewriting applications. See
[Design decisions](design-decisions.md) for the reasoning.

## Transactions and events

The persistence and event modules cooperate through the transaction manager. When a service
runs `transactionManager.transaction { … }`, the manager drives the transaction through
phases and lets registered adapters participate. The event bus registers such an adapter, so
events published inside a transaction are queued and flushed only on commit — and discarded on
rollback. This is why event handlers never see state that was rolled back, and it is handled
entirely by the framework; your code just publishes inside a transaction.

## See also

- [Application DSL](../reference/application-dsl.md) — the blocks that drive Phase 0.
- [DI & auto-wiring](../reference/di-auto-wiring.md) — discovery and injection rules.
- [Design decisions](design-decisions.md) — the rationale behind these choices.

