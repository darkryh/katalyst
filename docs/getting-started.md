# Getting started

This tutorial walks you through building your first Katalyst service from an empty Gradle
project to a running HTTP endpoint backed by a database. By the end you will have a
service, a repository, a table, and a route — all discovered and wired automatically — and
you will understand the shape of every Katalyst application.

You will build a small **bookmarks API**: store a URL, list the stored URLs.

## Before you begin

You need:

- **JDK 21 or newer** (`java -version` to check).
- **Gradle** (the wrapper below handles the version).
- Familiarity with Kotlin and basic Ktor concepts (routes, `call.respond`).

No database server is required — this tutorial uses an embedded H2 database.

## Step 1: Create the project

Create a new directory and a Gradle build. This tutorial uses a single-module layout.

```bash
mkdir bookmarks && cd bookmarks
gradle init --type kotlin-application --dsl kotlin --package com.example --project-name bookmarks
```

Replace the generated `app/build.gradle.kts` with this:

```kotlin
plugins {
    kotlin("jvm") version "2.4.0"
    id("io.ktor.plugin") version "3.5.0"
    id("org.jetbrains.kotlin.plugin.serialization") version "2.4.0"
    application
}

repositories { mavenCentral() }

dependencies {
    val katalyst = "1.0.0-alpha01"
    implementation(platform("io.github.darkryh.katalyst:katalyst-bom:$katalyst"))
    implementation("io.github.darkryh.katalyst:katalyst-starter-web")
    implementation("io.github.darkryh.katalyst:katalyst-starter-persistence")
}

application {
    mainClass.set("com.example.ApplicationKt")
}
```

You declare two things: the **BOM** and the **starters** you need. The BOM
(`platform(...)`) pins every Katalyst artifact to one version, so the starter coordinates
below carry no version of their own. Each starter then brings its external dependencies
transitively — you never list Ktor, Exposed, HikariCP, or a JDBC driver yourself:

- `katalyst-starter-web` pulls in Ktor (server, content negotiation, JSON serialization)
  and the Netty engine.
- `katalyst-starter-persistence` pulls in Exposed, HikariCP, and the H2 and PostgreSQL
  drivers.

Run `./gradlew build` once to confirm the dependencies resolve. You should see
`BUILD SUCCESSFUL`.

## Step 2: Add configuration

Katalyst reads infrastructure settings from YAML. Create
`app/src/main/resources/application.yaml`:

```yaml
ktor:
  deployment:
    host: 0.0.0.0
    port: 8080

database:
  url: jdbc:h2:mem:bookmarks;DB_CLOSE_DELAY=-1
  username: sa
  password: ""
  driver: org.h2.Driver
```

The `${VAR:default}` syntax is available for environment overrides; you will see it in the
[configuration how-to](how-to/configure-yaml.md). For now, plain values are fine.

## Step 3: Write the bootstrap

Create `app/src/main/kotlin/com/example/Application.kt`:

```kotlin
package com.example

import io.github.darkryh.katalyst.config.yaml.enableYamlConfiguration
import io.github.darkryh.katalyst.di.feature.enableServerConfiguration
import io.github.darkryh.katalyst.di.katalystApplication
import io.github.darkryh.katalyst.koin.KoinBeanEngine
import io.github.darkryh.katalyst.ktor.engine.netty.NettyServer

fun main(args: Array<String>) = katalystApplication(args) {
    engine(NettyServer)
    beanEngine(KoinBeanEngine)
    enableYamlConfiguration()
    database { fromConfiguration() }
    scanPackages("com.example")
    schema { createMissing() }          // create tables that don't exist yet
    features { enableServerConfiguration() }
}
```

`schema { createMissing() }` tells Katalyst to create any table it discovers but cannot
find in the database — convenient for local development. In production you use
`validateOnStartup()` and run [migrations](how-to/run-migrations.md).

## Step 4: Define the table and entity

Create `app/src/main/kotlin/com/example/Bookmark.kt`:

```kotlin
package com.example

import io.github.darkryh.katalyst.core.persistence.Table
import io.github.darkryh.katalyst.core.persistence.mapping
import io.github.darkryh.katalyst.repositories.Identifiable
import org.jetbrains.exposed.v1.core.dao.id.LongIdTable

data class Bookmark(
    override val id: Long? = null,
    val url: String,
    val createdAtMillis: Long
) : Identifiable<Long>

object BookmarksTable : LongIdTable("bookmarks"), Table<Long, Bookmark> {
    val url = varchar("url", 2048)
    val createdAtMillis = long("created_at_millis")

    override val mapping = mapping<Long, Bookmark> {
        generatedId(id, Bookmark::id)
        field(url, Bookmark::url)
        field(createdAtMillis, Bookmark::createdAtMillis)

        construct {
            Bookmark(
                id = this[id],
                url = this[url],
                createdAtMillis = this[createdAtMillis]
            )
        }
    }
}
```

The entity is a plain data class that implements `Identifiable<Long>` — `Table` and
`CrudRepository` both require it, so the framework knows where the primary key lives. Keep `id`
nullable with a `null` default: it is `null` before the row is inserted, and `save` uses that to
decide between insert and update.

A table is an Exposed `LongIdTable` that also implements `Table<Id, Entity>`. The
`mapping { … }` block tells Katalyst how to read a row and how to write inserts and
updates — you never touch Exposed's `UpdateBuilder` directly.

## Step 5: Define the repository

Create `app/src/main/kotlin/com/example/BookmarkRepository.kt`:

```kotlin
package com.example

import io.github.darkryh.katalyst.repositories.CrudRepository
import org.jetbrains.exposed.v1.core.dao.id.LongIdTable

class BookmarkRepository : CrudRepository<Long, Bookmark> {
    override val table: LongIdTable = BookmarksTable
}
```

Implementing `CrudRepository<Id, Entity>` gives you `save`, `findById`, `findAll`,
`deleteById`, and more — for free. Because the class is under a scanned package, Katalyst
registers it; you never new it up.

## Step 6: Define the service

Create `app/src/main/kotlin/com/example/BookmarkService.kt`:

```kotlin
package com.example

import io.github.darkryh.katalyst.core.component.Service

class BookmarkService(private val repository: BookmarkRepository) : Service {

    suspend fun add(url: String): Bookmark = transactionManager.transaction {
        repository.save(Bookmark(id = null, url = url, createdAtMillis = System.currentTimeMillis()))
    }

    suspend fun list(): List<Bookmark> = transactionManager.transaction {
        repository.findAll()
    }
}
```

Implementing `Service` marks the class for discovery and gives you `transactionManager`,
which wraps every database operation in a transaction. The `BookmarkRepository` constructor
parameter is injected by type — no wiring code.

## Step 7: Add the route

Create `app/src/main/kotlin/com/example/BookmarkRoutes.kt`:

```kotlin
package com.example

import io.github.darkryh.katalyst.ktor.builder.katalystRouting
import io.github.darkryh.katalyst.ktor.extension.ktInject
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import kotlinx.serialization.Serializable

@Serializable
data class AddBookmarkRequest(val url: String)

fun Route.bookmarkRoutes() = katalystRouting {
    post("/bookmarks") {
        val service = call.ktInject<BookmarkService>()
        val request = call.receive<AddBookmarkRequest>()
        call.respond(HttpStatusCode.Created, service.add(request.url))
    }
    get("/bookmarks") {
        val service = call.ktInject<BookmarkService>()
        call.respond(service.list())
    }
}
```

You also need JSON content negotiation installed. Add a middleware in
`app/src/main/kotlin/com/example/JsonMiddleware.kt`:

```kotlin
package com.example

import io.github.darkryh.katalyst.ktor.middleware.katalystMiddleware
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation

fun Application.jsonMiddleware() = katalystMiddleware {
    install(ContentNegotiation) { json() }
}
```

`katalystRouting`, `katalystMiddleware`, and `katalystWebSockets` are the three Ktor entry
points Katalyst installs automatically once DI is ready. Inside a route, `call.ktInject<T>()`
resolves a dependency.

Katalyst calls these top-level functions for you, so your own code never references them — your
IDE may flag them as unused. Install the [Katalyst IDE plugin](how-to/install-ide-plugin.md) and
the IDE recognizes them as framework entrypoints, so no `@Suppress("unused")` is needed.

## Step 8: Run it

```bash
./gradlew run
```

You should see the bootstrap log, ending with the server starting:

```
INFO  Katalyst - Bootstrap complete
INFO  Application - Responding at http://0.0.0.0:8080
```

In another terminal, add and list a bookmark:

```bash
curl -X POST http://localhost:8080/bookmarks \
  -H "Content-Type: application/json" \
  -d '{"url":"https://kotlinlang.org"}'
```

```json
{"id":1,"url":"https://kotlinlang.org","createdAtMillis":1718700000000}
```

```bash
curl http://localhost:8080/bookmarks
```

```json
[{"id":1,"url":"https://kotlinlang.org","createdAtMillis":1718700000000}]
```

It works. The table was created at startup, the repository and service were discovered and
injected, and the route was registered — and you wrote no DI configuration.

## What you built

You now have a complete Katalyst application and have seen its five moving parts:

- The **bootstrap** (`katalystApplication { … }`) selecting an engine, DI adapter, config
  source, database, scanned package, and schema policy.
- A **table** (`Table<Id, Entity>` + `mapping`) and a **repository** (`CrudRepository`).
- A **service** (`Service`) with transactional methods.
- A **route** (`katalystRouting`) and a **middleware** (`katalystMiddleware`).

### Where to go next

- Add an event when a bookmark is created → [Publish and handle events](how-to/publish-and-handle-events.md).
- Run a periodic cleanup job → [Schedule background jobs](how-to/schedule-jobs.md).
- Move to a real Postgres database and profiles → [Configure with YAML](how-to/configure-yaml.md).
- Write tests for this service → [Test your application](how-to/test-applications.md).
- Understand what happens during boot → [Architecture & bootstrap lifecycle](explanation/architecture.md).

