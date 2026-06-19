# Design decisions

Katalyst is shaped by a few deliberate choices. This page explains the reasoning and the
trade-offs, so you understand not just how the framework behaves but why. It is a discussion,
not a how-to.

## Spring Boot's experience, the Kotlin way

Katalyst is inspired by Spring Boot: a starter stack, autowiring, sensible defaults, and a
focus on writing business logic instead of plumbing. If you have built Spring services, the
mental model transfers — you declare a component and it gets wired; you add a starter and a
capability appears.

What Katalyst deliberately does *not* copy is the annotation-and-reflection-heavy programming
model and the large runtime that comes with a general-purpose framework. It targets Ktor
specifically, embraces coroutines and Kotlin's type system, and keeps the surface small. The
goal is the productivity of Spring Boot without leaving idiomatic Kotlin.

## Discovery by interface, not annotation

The most visible choice is that discovery is structural. You implement `Service`,
`Component`, `CrudRepository`, or `EventHandler`, or you return the right type from a function
— and that *is* the registration. There are no `@Service`, `@Component`, or `@Bean`
annotations and no module files.

The reasoning:

- **The type system carries the intent.** Implementing `Service` already says "this is a
  transactional service." An annotation would restate, in a weaker form, what the interface
  already guarantees — and the compiler can check the interface.
- **Less to forget, less to misuse.** There is one way to register a thing. You cannot
  annotate a class that does not fit, or forget the annotation on one that does and get a
  confusing runtime gap.
- **Honest dependencies.** Because wiring is constructor injection by type, a class's
  dependencies are exactly its constructor parameters — visible, final, and testable without
  the framework.

The trade-off is less flexibility than an annotation system that can attach arbitrary
metadata. Katalyst accepts that: where disambiguation is genuinely needed (two
implementations of one type), it offers a single, optional `@InjectNamed` qualifier, and
nothing more.

## Explicit bootstrap over convention-only magic

Spring Boot leans heavily on classpath-driven auto-configuration: adding a dependency can
silently change behavior. Katalyst keeps the bootstrap explicit. You choose the engine, the
bean engine, the config source, the database, the scanned packages, the schema policy, and
each feature, all in one visible `katalystApplication { … }` block.

The reasoning is readability and predictability. A new reader can understand what an
application does — and does not — by reading one block. Nothing is enabled by mere presence on
the classpath; for example, Katalyst will not install a configuration source automatically, so
you call `enableYamlConfiguration()` and the source is never ambiguous. The cost is a few more
lines at startup. The benefit is that those lines are the truth.

## A required, separate bean engine

Dependency resolution is delegated to a *bean engine*, and you must select one explicitly with
`beanEngine(KoinBeanEngine)`. Startup fails fast if you do not.

Two reasons drive this:

- **Adapter neutrality.** Katalyst owns the interesting parts — discovery, dependency
  analysis, validation, ordering — and treats the DI library as a replaceable backend. Keeping
  it behind an engine means the public DSL never leaks Koin types, so application code does not
  depend on the DI implementation. A container SPI can introduce other adapters later without
  changing how you write code.
- **Fail-fast over silent defaults.** Rather than guessing an engine from the classpath,
  Katalyst makes the choice explicit and visible. A missing adapter is reported immediately at
  boot, not later as a confusing lazy-injection failure.

In this alpha, Koin is the only adapter — but the design treats that as an implementation
detail, not the long-term public contract.

## Fail-fast at startup

Katalyst validates the whole dependency graph, the configuration, and the migration set
*before* the server accepts traffic. Missing dependencies, cycles, invalid config, and
checksum drift all surface during boot with diagnostics that name the cause.

The philosophy is that a service that cannot be correct should refuse to start, loudly,
rather than start and fail intermittently under load. Catching these problems at boot turns a
class of production incidents into a failed startup in development or CI. The trade-off is a
slightly heavier bootstrap; in return, "it started" means "it is wired correctly."

## Transaction-aware events by default

Events published inside a transaction are delivered only on commit. This is not optional
behavior bolted on — it is how the event bus integrates with the transaction manager through a
transaction adapter.

The reason is correctness. The most common event bug is a handler reacting to a change that
later rolls back: a welcome email for a registration that failed, a downstream update for a
charge that was reversed. By deferring delivery to commit, Katalyst removes that whole failure
mode. The cost is that handlers run slightly later (after commit) and must be idempotent,
since the same event may be retried — both reasonable expectations for event-driven code.

## See also

- [Architecture & bootstrap lifecycle](architecture.md) — how these decisions play out at
  runtime.
- [DI & auto-wiring](../reference/di-auto-wiring.md) — the discovery and injection rules.
- [Application DSL](../reference/application-dsl.md) — the explicit bootstrap surface.

