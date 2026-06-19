---
name: katalyst
description: >-
  Authoritative, source-grounded reference for building and modifying applications with
  Katalyst — the Kotlin + Ktor backend framework (Spring Boot-style autowiring, Exposed +
  HikariCP persistence, transactions, migrations, scheduler, in-process event bus,
  WebSockets, YAML config). USE THIS SKILL WHENEVER the task touches a Katalyst app or the
  Katalyst codebase itself: writing or editing a service, component, repository, table,
  route, middleware, WebSocket, event handler, scheduled job, migration, config loader, or
  the katalystApplication bootstrap; debugging startup/DI/transaction/config failures; or
  any work mentioning katalystApplication, KoinBeanEngine, CrudRepository, katalystRouting,
  ktInject, transactionManager, requireScheduler, EventBus, or io.github.darkryh.katalyst.*.
  Trigger even when the user does not say "Katalyst" but is clearly working in a project
  that uses it. Following these conventions exactly is what makes a Katalyst app compile,
  boot, and wire correctly — guessing the API leads to startup failures.
---

# Katalyst

Katalyst is a convention-driven backend framework for Kotlin on Ktor 3. You write plain
classes and functions that implement a small set of interfaces; Katalyst scans your packages,
builds a dependency graph, validates it, instantiates everything in a safe order, and hands
the wired application to Ktor. It bundles dependency injection, YAML configuration, Exposed +
HikariCP persistence, transactions, migrations, a scheduler, an in-process transactional event
bus, and WebSockets.

This skill is the complete, source-accurate reference. The public docs under `docs/` cover
*most* of this for end users; this skill covers *all* of it for agents that must produce code
that compiles and boots on the first try. When a detail here and a doc disagree, trust this
skill and the source.

## The mental model (read this first)

Five facts explain almost everything. Internalize them before writing code.

1. **Discovery is by interface, not annotation.** A class is managed because it implements
   `Service`, `Component`, `CrudRepository`, `Table`, `EventHandler`, `ApplicationInitializer`,
   `KatalystMigration`, or `AutomaticServiceConfigLoader` — or because a function calls
   `katalystRouting` / `katalystMiddleware` / `katalystWebSockets` / `katalystExceptionHandler`,
   or returns `SchedulerJobHandle`. There are **no** `@Service`/`@Component`/`@Bean`
   annotations. Some KDoc in the source shows `@Component` in examples — that is misleading and
   does not reflect the real mechanism. The only DI annotation is the optional `@InjectNamed`
   qualifier.

2. **Everything must live under a scanned package.** A class outside the roots passed to
   `scanPackages(...)` is invisible — it will not be discovered or injected, with no error.

3. **The bootstrap is explicit.** `engine(...)` and `beanEngine(...)` are required.
   `database { ... }` is required. YAML needs `enableYamlConfiguration()`. Nothing is enabled
   merely by being on the classpath. Missing pieces fail fast at startup, loudly.

4. **Constructor parameters are dependencies, resolved by type.** Declare what you need as
   constructor parameters; Katalyst injects them. Optional → Kotlin default or nullable type.
   Scalars (`Int`/`Long`/`Boolean`/`String`) must have a default or come from a registered
   config object. No `Provider`/`Lazy`/`() -> T` wrappers.

5. **Persistence is Exposed v1 + the `Identifiable` contract.** Entities implement
   `Identifiable<Id>` (with `override val id: Id? = null`); tables implement `Table<Id, Entity>`;
   repositories implement `CrudRepository<Id, Entity>`. All Exposed imports use the
   `org.jetbrains.exposed.v1.*` package set. Wrap writes in `transactionManager.transaction { }`.

## The golden-path bootstrap

Every Katalyst app has one `main` like this. Memorize the shape and the import paths — wrong
imports are the most common failure.

```kotlin
import io.github.darkryh.katalyst.di.katalystApplication
import io.github.darkryh.katalyst.koin.KoinBeanEngine
import io.github.darkryh.katalyst.ktor.engine.netty.NettyServer
import io.github.darkryh.katalyst.config.yaml.enableYamlConfiguration
import io.github.darkryh.katalyst.di.feature.enableServerConfiguration
import io.github.darkryh.katalyst.di.feature.enableEvents
import io.github.darkryh.katalyst.migrations.extensions.enableMigrations
import io.github.darkryh.katalyst.scheduler.enableScheduler
import io.github.darkryh.katalyst.websockets.enableWebSockets

fun main(args: Array<String>) = katalystApplication(args) {
    engine(NettyServer)                 // REQUIRED — NettyServer | JettyServer | CioServer
    beanEngine(KoinBeanEngine)          // REQUIRED — only adapter in this alpha
    enableYamlConfiguration()           // REQUIRED for YAML-backed config
    database { fromConfiguration() }    // REQUIRED — reads database.* before DI
    scanPackages("com.example")         // REQUIRED — discovery roots
    schema { validateOnStartup() }      // default; createMissing() for local/test; none() external
    features {                          // OPTIONAL — opt into non-core features
        enableServerConfiguration()
        enableEvents()
        enableMigrations()
        enableScheduler()
        enableWebSockets()
    }
}
```

Details, every block, every option, the `force` flag, and `ApplicationInitializer`:
see `references/bootstrap.md`.

## How to do common tasks

Each row points to the reference with exact signatures, imports, full examples, and gotchas.
Read the relevant reference before writing code for that subsystem — the APIs are specific and
easy to get subtly wrong.

| Task | Reference |
|------|-----------|
| Bootstrap, engines, bean engine, `features`, `schema`, initializers | `references/bootstrap.md` |
| Add a service/component; understand injection rules, qualifiers, failures | `references/di-and-discovery.md` |
| YAML config, `ConfigProvider`, the two loader patterns, every key | `references/configuration.md` |
| Define entities/tables/repositories, custom queries, `SqlExecutor`, the `mapping` DSL | `references/persistence.md` |
| Transactional methods, retry, isolation, timeouts | `references/transactions.md` |
| Write/run migrations, status/validate/dry-run | `references/migrations.md` |
| Cron / fixed-delay / fixed-rate / one-time jobs | `references/scheduler.md` |
| Domain events, handlers, transaction-aware publishing, side effects, dedup | `references/events.md` |
| Routes, middleware, WebSockets, exception handlers, `ktInject` | `references/ktor.md` |
| Tests: `katalystTestEnvironment`, `katalystTestApplication`, fakes | `references/testing.md` |
| Module/artifact map, versions, the import sets | `references/modules.md` |

## A correct vertical slice

This is what a complete, discovered feature looks like — entity, table, repository, service,
route. Use it as the canonical pattern; expand each layer with its reference.

```kotlin
// 1. Entity — implements Identifiable<Id>
data class Bookmark(
    override val id: Long? = null,
    val url: String,
    val createdAtMillis: Long
) : Identifiable<Long>

// 2. Table — Exposed LongIdTable + Table<Id, Entity> + mapping
object BookmarksTable : LongIdTable("bookmarks"), Table<Long, Bookmark> {
    val url = varchar("url", 2048)
    val createdAtMillis = long("created_at_millis")
    override val mapping = mapping<Long, Bookmark> {
        generatedId(id, Bookmark::id)
        field(url, Bookmark::url)
        field(createdAtMillis, Bookmark::createdAtMillis)
        construct { Bookmark(id = this[id], url = this[url], createdAtMillis = this[createdAtMillis]) }
    }
}

// 3. Repository — CrudRepository<Id, Entity>
class BookmarkRepository : CrudRepository<Long, Bookmark> {
    override val table: LongIdTable = BookmarksTable
}

// 4. Service — implements Service; transactionManager available
class BookmarkService(private val repository: BookmarkRepository) : Service {
    suspend fun add(url: String): Bookmark = transactionManager.transaction {
        repository.save(Bookmark(url = url, createdAtMillis = System.currentTimeMillis()))
    }
    suspend fun list(): List<Bookmark> = transactionManager.transaction { repository.findAll() }
}

// 5. Route — katalystRouting; ktInject inside handlers
@Suppress("unused")
fun Route.bookmarkRoutes() = katalystRouting {
    get("/bookmarks") { call.respond(call.ktInject<BookmarkService>().list()) }
    post("/bookmarks") {
        val req = call.receive<AddBookmarkRequest>()
        call.respond(HttpStatusCode.Created, call.ktInject<BookmarkService>().add(req.url))
    }
}
```

The `@Suppress("unused")` on discovered top-level functions is conventional: Katalyst calls
them, your code never does, so the compiler would warn otherwise.

## Common failures and their fixes

These are the mistakes that break Katalyst apps. Check here first when something fails.

| Symptom | Cause | Fix |
|---------|-------|-----|
| A class is never injected / route 404s | It is outside the `scanPackages` roots | Move it under a scanned package |
| Startup fails: "no bean engine" | `beanEngine(...)` not called | Add `beanEngine(KoinBeanEngine)` |
| `requireScheduler()` throws `SchedulerServiceNotAvailableException` | Scheduler feature off | Add `enableScheduler()` in `features { }` |
| Config value missing at runtime | No config source installed | Call `enableYamlConfiguration()` |
| `MissingDependencyError` at boot | A required ctor param has no binding | Provide the dependency, make it nullable, or give a default |
| `CircularDependencyError` | Two components depend on each other | Break the cycle; observe via events instead |
| `Cannot resolve symbol 'selectAll'` | Wrong Exposed import | Use `org.jetbrains.exposed.v1.jdbc.selectAll` |
| `Unresolved reference: Service` / wrong import | Guessed the package | `Service`/`Component` are in `io.github.darkryh.katalyst.core.component`; check `references/modules.md` for every import path |
| Entity won't compile as `Table`/`CrudRepository` type arg | Entity is not `Identifiable<Id>` | Add `: Identifiable<Long>` and `override val id: Long? = null` |
| Event handler reacts to rolled-back data | Published outside a transaction | Publish inside `transactionManager.transaction { }` |
| Adding a dependency "auto-enables" nothing | Bootstrap is explicit by design | Enable the feature in `features { }` |

## Working on the framework itself

When modifying Katalyst's own modules (not an app that uses it): each module has an
`api/<module>.api` file that is the committed public surface — changing public API updates that
file (`./gradlew apiDump`). Tests live in `src/test/kotlin`; run `./gradlew :module:test`. The
sample app under `samples/katalyst-example` is the integration reference and exercises the full
stack. `references/modules.md` maps every module to its responsibility.
