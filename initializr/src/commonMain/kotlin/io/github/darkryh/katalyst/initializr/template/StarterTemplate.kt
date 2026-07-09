package io.github.darkryh.katalyst.initializr.template

import io.github.darkryh.katalyst.initializr.model.Feature
import io.github.darkryh.katalyst.initializr.model.FeatureSelection
import io.github.darkryh.katalyst.initializr.model.ProjectConfig

/**
 * The "minimal runnable app" starter template: a complete, idiomatic Katalyst project expressed as
 * placeholdered files, *assembled from the user's [FeatureSelection]*. Unlike a static template, the
 * dependency list, the `Application.kt` DSL body, `application.yaml`, the README and the set of
 * demonstrator files all vary with which starters are selected — so the download only ever contains
 * (and only ever references) the features the user actually chose.
 *
 * The generator ([io.github.darkryh.katalyst.initializr.generate.ProjectGenerator]) then substitutes
 * the remaining `{{...}}` identity tokens (`{{PACKAGE}}`, `{{GROUP_ID}}`, …). This file owns *feature
 * composition*; the generator owns *identity substitution*.
 *
 * ### Tokens left for the generator
 * `{{PROJECT_NAME}}` `{{GROUP_ID}}` `{{ARTIFACT_ID}}` `{{PACKAGE}}` `{{PACKAGE_PATH}}`
 * `{{APP_VERSION}}` `{{KATALYST_VERSION}}` `{{KOTLIN_VERSION}}` `{{GRADLE_VERSION}}` `{{JVM}}`
 * and `{{D}}` — a literal `$` (so bash/YAML `${...}` and Kotlin templates stay readable here).
 */
object StarterTemplate {
    /** The Katalyst artifact version the generated project depends on — injected at build time. */
    val KATALYST_VERSION: String = BuildInfo.KATALYST_VERSION

    /** Kotlin version for the generated project — kept in lockstep with the library's own. */
    const val KOTLIN_VERSION: String = "2.4.0"

    /** Gradle version the generated wrapper targets (used in the run.sh bootstrap hint). */
    const val GRADLE_VERSION: String = "9.5.0"

    /** JVM toolchain the generated project compiles against. */
    const val JVM_TARGET: String = "21"

    private const val PKG = "src/main/kotlin/{{PACKAGE_PATH}}"

    /** The raw, placeholdered files for [config]. Paths are repository-relative. */
    fun files(config: ProjectConfig): List<TemplateFile> {
        val sel = config.selection
        return buildList {
            add(TemplateFile("settings.gradle.kts", SETTINGS_GRADLE))
            add(TemplateFile("gradle.properties", GRADLE_PROPERTIES))
            add(TemplateFile("build.gradle.kts", buildGradle(sel)))
            add(TemplateFile(".gitignore", GITIGNORE))
            add(TemplateFile("README.md", readme(sel)))
            add(TemplateFile("src/main/resources/application.yaml", applicationYaml(sel)))
            add(TemplateFile("src/main/resources/logback.xml", LOGBACK_XML))
            add(TemplateFile("$PKG/Application.kt", applicationKt(sel)))
            add(TemplateFile("$PKG/routes/HealthCheckRoutes.kt", HEALTH_ROUTES))

            if (sel.isEnabled(Feature.PERSISTENCE)) {
                add(TemplateFile("$PKG/domain/Note.kt", NOTE))
                add(TemplateFile("$PKG/infra/database/entities/NoteEntity.kt", NOTE_ENTITY))
                add(TemplateFile("$PKG/infra/database/tables/NotesTable.kt", NOTES_TABLE))
                add(TemplateFile("$PKG/infra/database/repositories/NoteRepository.kt", NOTE_REPOSITORY))
                add(TemplateFile("$PKG/routes/NoteRoutes.kt", NOTE_ROUTES))
            }
            if (sel.isEnabled(Feature.MIGRATIONS)) {
                add(TemplateFile("$PKG/migrations/V1CreateNotes.kt", V1_CREATE_NOTES))
            }
            if (sel.isEnabled(Feature.SCHEDULER)) {
                add(TemplateFile("$PKG/scheduler/HeartbeatTask.kt", HEARTBEAT_TASK))
            }
            if (sel.isEnabled(Feature.WEBSOCKETS)) {
                add(TemplateFile("$PKG/routes/NotificationSocketRoutes.kt", NOTIFICATION_SOCKET))
            }
            // The run.sh debug launcher exists to attach the embedded TUI inspector to a real
            // terminal — so it ships only when the observability starter is selected.
            if (sel.isEnabled(Feature.OBSERVABILITY)) {
                add(TemplateFile("run.sh", RUN_SH, executable = true))
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Assembled (feature-dependent) files
    // ---------------------------------------------------------------------------------------------

    private fun dep(artifact: String) = "    implementation(\"io.github.darkryh.katalyst:$artifact\")"

    // These assembled files vary line-by-line with the selection, so they are built as explicit line
    // lists (never a trimIndent block with an interpolated multi-line piece — that would key trimIndent
    // off the interpolated content's indentation and corrupt the output).
    private fun buildGradle(sel: FeatureSelection): String =
        buildList {
            add("plugins {")
            add("    // The single Katalyst plugin applies Kotlin JVM, kotlinx.serialization and the")
            add("    // application plugin. Every version resolves from the BOM below.")
            add("    id(\"io.github.darkryh.katalyst\") version \"{{KATALYST_VERSION}}\"")
            add("}")
            add("")
            add("group = \"{{GROUP_ID}}\"")
            add("version = \"{{APP_VERSION}}\"")
            add("")
            add("application { mainClass = \"{{PACKAGE}}.ApplicationKt\" }")
            add("")
            add("// Only katalyst-* artifacts — Ktor, Exposed, Koin and serialization arrive transitively")
            add("// through the starters. The engine is chosen by exactly one katalyst-starter-engine-*.")
            add("dependencies {")
            add("    implementation(platform(\"io.github.darkryh.katalyst:katalyst-bom:{{KATALYST_VERSION}}\"))")
            add(dep("katalyst-starter-web"))
            add(dep(sel.engine.starter))
            if (sel.isEnabled(Feature.PERSISTENCE)) add(dep("katalyst-starter-persistence"))
            if (sel.isEnabled(Feature.MIGRATIONS)) add(dep("katalyst-starter-migrations"))
            if (sel.isEnabled(Feature.SCHEDULER)) add(dep("katalyst-starter-scheduler"))
            if (sel.isEnabled(Feature.WEBSOCKETS)) add(dep("katalyst-starter-websockets"))
            if (sel.isEnabled(Feature.OBSERVABILITY)) add(dep("katalyst-starter-observability"))
            add("    testImplementation(\"io.github.darkryh.katalyst:katalyst-starter-test\")")
            add("}")
            add("")
            add("tasks.test {")
            add("    useJUnitPlatform()")
            add("}")
        }.joinToString("\n") + "\n"

    private fun applicationKt(sel: FeatureSelection): String {
        val imports =
            buildList {
                add("io.github.darkryh.katalyst.config.yaml.enableYamlConfiguration")
                add("io.github.darkryh.katalyst.di.feature.enableServerTuning")
                add("io.github.darkryh.katalyst.di.feature.enableEvents")
                add("io.github.darkryh.katalyst.di.katalystApplication")
                add("io.github.darkryh.katalyst.koin.KoinBeanEngine")
                add(sel.engine.importFqn)
                if (sel.isEnabled(Feature.MIGRATIONS)) add("io.github.darkryh.katalyst.migrations.extensions.enableMigrations")
                if (sel.isEnabled(Feature.SCHEDULER)) add("io.github.darkryh.katalyst.scheduler.enableScheduler")
                if (sel.isEnabled(Feature.WEBSOCKETS)) add("io.github.darkryh.katalyst.websockets.enableWebSockets")
            }.sorted()

        return buildList {
            add("package {{PACKAGE}}")
            add("")
            imports.forEach { add("import $it") }
            add("")
            add("/**")
            add(" * Application entry point. One `katalystApplication { }` block declares the engine, the DI")
            add(" * adapter, the config source, the database, the scanned packages and the feature toggles.")
            add(" * Services, repositories, routes, event handlers and scheduled jobs under `{{PACKAGE}}` are")
            add(" * discovered, validated, ordered and injected at startup — no annotations, no module files.")
            add(" *")
            add(" * Select a config profile with the KATALYST_PROFILE env var (dev | staging | prod).")
            add(" */")
            add("fun main(args: Array<String>) = katalystApplication(args) {")
            add("    engine(${sel.engine.serverObject})")
            add("    beanEngine(KoinBeanEngine)")
            add("")
            add("    features {")
            // enableYamlConfiguration() installs the config source synchronously here, so it must
            // precede database { fromConfiguration() } (which reads database.* from it).
            add("        enableYamlConfiguration()")
            add("        enableServerTuning()")
            add("        enableEvents()")
            if (sel.isEnabled(Feature.MIGRATIONS)) add("        enableMigrations()")
            if (sel.isEnabled(Feature.SCHEDULER)) add("        enableScheduler()")
            if (sel.isEnabled(Feature.WEBSOCKETS)) add("        enableWebSockets()")
            add("    }")
            if (sel.isEnabled(Feature.PERSISTENCE)) {
                add("")
                add("    database { fromConfiguration() }")
            }
            add("")
            add("    scanPackages(\"{{PACKAGE}}\")")
            if (sel.isEnabled(Feature.PERSISTENCE)) add("    schema { createMissing() }")
            add("}")
        }.joinToString("\n") + "\n"
    }

    private fun applicationYaml(sel: FeatureSelection): String =
        buildList {
            add("# {{PROJECT_NAME}} — base configuration.")
            add("# Committed to git: public defaults + structure. Secrets come from environment variables.")
            add("ktor:")
            add("  deployment:")
            add("    host: {{D}}{SERVER_HOST:0.0.0.0}")
            add("    port: {{D}}{SERVER_PORT:8080}")
            add("    shutdownGracePeriod: {{D}}{SHUTDOWN_GRACE_PERIOD:1000}")
            add("    shutdownTimeout: {{D}}{SHUTDOWN_TIMEOUT:5000}")
            if (sel.isEnabled(Feature.PERSISTENCE)) {
                add("")
                add("# Database — env vars fill secrets. schema { createMissing() } creates the mapped")
                add("# tables on first run; switch to validateOnStartup() / none() for stricter control.")
                add("database:")
                add("  url: {{D}}{DB_URL:jdbc:postgresql://localhost:5432/postgres}")
                add("  username: {{D}}{DB_USERNAME:postgres}")
                add("  password: {{D}}{DB_PASSWORD:}")
                add("  driver: {{D}}{DB_DRIVER:org.postgresql.Driver}")
                add("  pool:")
                add("    maxSize: {{D}}{DB_MAX_POOL:10}")
                add("    minIdle: {{D}}{DB_MIN_IDLE:1}")
            }
            add("")
            add("app:")
            add("  environment: {{D}}{APP_ENVIRONMENT:development}")
        }.joinToString("\n") + "\n"

    private fun readme(sel: FeatureSelection): String =
        buildList {
            add("# {{PROJECT_NAME}}")
            add("")
            add("A backend service built with [Katalyst](https://github.com/darkryh/katalyst) — convention-driven")
            add("Kotlin + Ktor, Spring Boot's developer experience the Kotlin way.")
            add("")
            add("## Run it")
            add("")
            if (sel.isEnabled(Feature.OBSERVABILITY)) {
                add("A terminal UI needs a real TTY, so launch with the bundled `run.sh`: it installs a native")
                add("launcher with `installDist` and execs it directly. Run it from a **real terminal**")
                add("(Terminal.app, iTerm, the IntelliJ Terminal tab, or ssh) and the embedded **Katalyst TUI")
                add("inspector** takes over as the default developer view — live telemetry for routing, persistence,")
                add("transactions, scheduler, events and websockets. Double `Ctrl+C` quits it and stops the service.")
                add("Without a TTY (piped output, an IDE Run window, `journalctl`) it falls back to plain logs.")
                add("(Don't use `./gradlew run` — Gradle captures stdin/stdout and garbles the dashboard.)")
                add("")
                add("This starter does not bundle the Gradle wrapper jar. Generate it once (needs a local Gradle —")
                add("`brew install gradle` or `sdk install gradle`), then run:")
                add("")
                add("```bash")
                add("gradle wrapper --gradle-version {{GRADLE_VERSION}}")
                add("./run.sh")
                add("```")
                add("")
                add("Opt out of the inspector: `KATALYST_TUI_ENABLED=false ./run.sh`.")
            } else {
                add("Build a native launcher and run it:")
                add("")
                add("```bash")
                add("gradle wrapper --gradle-version {{GRADLE_VERSION}}   # once — the wrapper jar isn't bundled")
                add("./gradlew installDist")
                add("./build/install/{{ARTIFACT_ID}}/bin/{{ARTIFACT_ID}}")
                add("```")
                add("")
                add("Select a config profile with `KATALYST_PROFILE=dev` (dev | staging | prod).")
            }
            add("")
            add("## Endpoints")
            add("")
            add("- `GET /health` — liveness check")
            if (sel.isEnabled(Feature.PERSISTENCE)) add("- `GET /api/notes` · `POST /api/notes` — a sample persisted resource")
            if (sel.isEnabled(Feature.WEBSOCKETS)) add("- `WS /ws/notifications` — a sample socket (send `ping`, receive `pong`)")
            add("")
            add("## Project layout")
            add("")
            add("```")
            add("build.gradle.kts                       the Katalyst plugin + starters (Maven Central)")
            add("src/main/resources/application.yaml    configuration (env-var overridable)")
            add("src/main/kotlin/{{PACKAGE_PATH}}/")
            add("  Application.kt                       the one katalystApplication { } bootstrap block")
            add("  routes/HealthCheckRoutes.kt          an auto-discovered route")
            add("```")
            add("")
            add("Add a component by implementing an interface (`Service`, `CrudRepository`, `EventHandler`, …)")
            add("under `{{PACKAGE}}` — Katalyst discovers, validates and injects it at startup. No annotations,")
            add("no module files.")
            add("")
            add("Generated with the Katalyst initializr.")
        }.joinToString("\n") + "\n"

    // ---------------------------------------------------------------------------------------------
    // Static files
    // ---------------------------------------------------------------------------------------------

    private val SETTINGS_GRADLE =
        """
        pluginManagement {
            repositories {
                mavenCentral()
                gradlePluginPortal()
            }
        }

        rootProject.name = "{{ARTIFACT_ID}}"
        """.trimIndent() + "\n"

    private val GRADLE_PROPERTIES =
        """
        kotlin.code.style=official
        org.gradle.jvmargs=-Xmx1g -Dfile.encoding=UTF-8
        org.gradle.caching=true
        """.trimIndent() + "\n"

    private val GITIGNORE =
        """
        .gradle/
        build/
        .idea/
        *.iml
        *.log
        .DS_Store
        """.trimIndent() + "\n"

    // Root logger inheritance matters: the embedded TUI inspector silences the console by raising the
    // ROOT logger to WARN when it takes over the terminal. A logger pinned to an explicit level keeps
    // printing over the dashboard — so pin a level only to quiet a noisy library (below), or briefly
    // while debugging.
    private val LOGBACK_XML =
        """
        <configuration>
            <property name="ROOT_LOG_LEVEL" value="{{D}}{ROOT_LOG_LEVEL:-INFO}"/>

            <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
                <encoder class="ch.qos.logback.classic.encoder.PatternLayoutEncoder">
                    <pattern>%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n</pattern>
                    <charset>UTF-8</charset>
                </encoder>
            </appender>

            <root level="{{D}}{ROOT_LOG_LEVEL}">
                <appender-ref ref="STDOUT"/>
            </root>

            <logger name="org.reflections" level="WARN"/>
        </configuration>
        """.trimIndent() + "\n"

    private val HEALTH_ROUTES =
        """
        package {{PACKAGE}}.routes

        import io.github.darkryh.katalyst.ktor.builder.katalystRouting
        import io.ktor.http.HttpStatusCode
        import io.ktor.server.response.respond
        import io.ktor.server.routing.Route
        import io.ktor.server.routing.get
        import kotlinx.serialization.Serializable

        @Serializable
        data class HealthStatus(val status: String)

        /**
         * Auto-discovered by Katalyst — a route is any `Route` extension function using the
         * `katalystRouting { }` DSL. No manual registration.
         */
        @Suppress("unused")
        fun Route.healthCheckRoutes() = katalystRouting {
            get("/health") {
                call.respond(HttpStatusCode.OK, HealthStatus(status = "UP"))
            }
        }
        """.trimIndent() + "\n"

    // ---- persistence demonstrator (Note) ----

    private val NOTE =
        """
        package {{PACKAGE}}.domain

        import kotlinx.serialization.Serializable

        /** API/domain model for a note. */
        @Serializable
        data class Note(
            val id: Long,
            val title: String,
            val createdAtMillis: Long,
        )
        """.trimIndent() + "\n"

    private val NOTE_ENTITY =
        """
        package {{PACKAGE}}.infra.database.entities

        import io.github.darkryh.katalyst.repositories.Identifiable

        /** Persistence entity — `id` is null until first saved. */
        data class NoteEntity(
            override val id: Long? = null,
            val title: String,
            val createdAtMillis: Long,
        ) : Identifiable<Long>
        """.trimIndent() + "\n"

    private val NOTES_TABLE =
        """
        package {{PACKAGE}}.infra.database.tables

        import {{PACKAGE}}.infra.database.entities.NoteEntity
        import io.github.darkryh.katalyst.core.persistence.Table
        import io.github.darkryh.katalyst.core.persistence.mapping
        import org.jetbrains.exposed.v1.core.dao.id.LongIdTable

        /**
         * Exposed table + the row<->entity mapping Katalyst repositories use. `schema { createMissing() }`
         * creates this table on first run from the mapping below.
         */
        object NotesTable : LongIdTable("notes"), Table<Long, NoteEntity> {
            val title = varchar("title", 200)
            val createdAtMillis = long("created_at_millis")

            override val mapping = mapping<Long, NoteEntity> {
                generatedId(id, NoteEntity::id)
                field(title, NoteEntity::title)
                field(createdAtMillis, NoteEntity::createdAtMillis)

                construct {
                    NoteEntity(
                        id = this[id],
                        title = this[title],
                        createdAtMillis = this[createdAtMillis],
                    )
                }
            }
        }
        """.trimIndent() + "\n"

    private val NOTE_REPOSITORY =
        """
        package {{PACKAGE}}.infra.database.repositories

        import {{PACKAGE}}.infra.database.entities.NoteEntity
        import {{PACKAGE}}.infra.database.tables.NotesTable
        import io.github.darkryh.katalyst.repositories.CrudRepository
        import org.jetbrains.exposed.v1.core.dao.id.LongIdTable

        /**
         * Discovered because it implements `CrudRepository`. You get save / saveAll / findById /
         * findAll / count / delete for free — just point it at the table.
         */
        class NoteRepository : CrudRepository<Long, NoteEntity> {
            override val table: LongIdTable = NotesTable
        }
        """.trimIndent() + "\n"

    private val NOTE_ROUTES =
        """
        package {{PACKAGE}}.routes

        import {{PACKAGE}}.domain.Note
        import {{PACKAGE}}.infra.database.entities.NoteEntity
        import {{PACKAGE}}.infra.database.repositories.NoteRepository
        import io.github.darkryh.katalyst.ktor.builder.katalystRouting
        import io.github.darkryh.katalyst.ktor.extension.ktInject
        import io.github.darkryh.katalyst.transactions.manager.DatabaseTransactionManager
        import io.ktor.http.HttpStatusCode
        import io.ktor.server.request.receive
        import io.ktor.server.response.respond
        import io.ktor.server.routing.Route
        import io.ktor.server.routing.get
        import io.ktor.server.routing.post
        import io.ktor.server.routing.route
        import kotlinx.serialization.Serializable

        @Serializable
        data class CreateNoteRequest(val title: String)

        /**
         * Injects the repository and the transaction manager with `by call.ktInject<T>()`; repository
         * reads/writes run inside `transactionManager.transaction { }`.
         */
        @Suppress("unused")
        fun Route.noteRoutes() = katalystRouting {
            route("/api/notes") {
                get {
                    val repository by call.ktInject<NoteRepository>()
                    val transactionManager by call.ktInject<DatabaseTransactionManager>()
                    val notes = transactionManager.transaction { repository.findAll() }
                    call.respond(notes.map(NoteEntity::toNote))
                }

                post {
                    val repository by call.ktInject<NoteRepository>()
                    val transactionManager by call.ktInject<DatabaseTransactionManager>()
                    val request = call.receive<CreateNoteRequest>()
                    val saved = transactionManager.transaction {
                        repository.save(
                            NoteEntity(title = request.title, createdAtMillis = System.currentTimeMillis()),
                        )
                    }
                    call.respond(HttpStatusCode.Created, saved.toNote())
                }
            }
        }

        private fun NoteEntity.toNote() = Note(
            id = requireNotNull(id) { "persisted note is missing an id" },
            title = title,
            createdAtMillis = createdAtMillis,
        )
        """.trimIndent() + "\n"

    // ---- migrations demonstrator ----

    private val V1_CREATE_NOTES =
        """
        package {{PACKAGE}}.migrations

        import io.github.darkryh.katalyst.database.DatabaseFactory
        import io.github.darkryh.katalyst.migrations.KatalystMigration
        import org.jetbrains.exposed.v1.jdbc.transactions.transaction

        /**
         * A migration is any class implementing `KatalystMigration`; ordering is by the numeric prefix
         * of `id`. `CREATE TABLE IF NOT EXISTS` keeps it idempotent alongside `schema { createMissing() }`;
         * under a stricter `schema { none() }` policy this migration becomes the source of truth.
         */
        class V1CreateNotes(
            private val databaseFactory: DatabaseFactory,
        ) : KatalystMigration {

            override val id: String = "1_create_notes"
            override val description: String = "Create the notes table"

            override fun up() {
                transaction(databaseFactory.database) {
                    exec(
                        "CREATE TABLE IF NOT EXISTS notes (" +
                            "id BIGSERIAL PRIMARY KEY, " +
                            "title VARCHAR(200) NOT NULL, " +
                            "created_at_millis BIGINT NOT NULL)",
                    )
                }
            }
        }
        """.trimIndent() + "\n"

    // ---- scheduler demonstrator ----

    private val HEARTBEAT_TASK =
        """
        package {{PACKAGE}}.scheduler

        import io.github.darkryh.katalyst.core.component.Service
        import io.github.darkryh.katalyst.scheduler.extension.requireScheduler
        import io.github.darkryh.katalyst.scheduler.job.SchedulerJobHandle
        import org.slf4j.LoggerFactory
        import kotlin.time.Duration.Companion.seconds

        /**
         * A scheduled job is a parameterless function returning `SchedulerJobHandle` on a discovered
         * `Service` — that return type is the discovery signal Katalyst invokes at startup.
         */
        class HeartbeatTask : Service {
            private val log = LoggerFactory.getLogger(HeartbeatTask::class.java)
            private val scheduler = requireScheduler()

            @Suppress("unused")
            fun jobs(): SchedulerJobHandle = scheduler.jobs {
                fixedRate("heartbeat", 30.seconds) {
                    log.info("heartbeat {}", System.currentTimeMillis())
                }
            }
        }
        """.trimIndent() + "\n"

    // ---- websockets demonstrator ----

    private val NOTIFICATION_SOCKET =
        """
        package {{PACKAGE}}.routes

        import io.github.darkryh.katalyst.ktor.websocket.katalystWebSockets
        import io.ktor.server.websocket.webSocket
        import io.ktor.server.routing.Route
        import io.ktor.websocket.Frame
        import io.ktor.websocket.readText

        /**
         * A socket route uses the `katalystWebSockets { }` DSL. This one greets on connect and replies
         * `pong` to a `ping`. Requires `enableWebSockets()`.
         */
        @Suppress("unused")
        fun Route.notificationSocketRoutes() = katalystWebSockets {
            webSocket("/ws/notifications") {
                send(Frame.Text("{\"type\":\"welcome\"}"))
                for (frame in incoming) {
                    if (frame is Frame.Text && frame.readText() == "ping") {
                        send(Frame.Text("{\"type\":\"pong\"}"))
                    }
                }
            }
        }
        """.trimIndent() + "\n"

    // ---- run.sh (observability only) ----
    // Bash uses `$`, which is also Kotlin's template marker — so every `$` here is the `{{D}}` token,
    // restored to a literal `$` by the generator.
    private val RUN_SH =
        """
        #!/usr/bin/env bash
        set -euo pipefail

        # Build and run this Katalyst service in the current terminal.
        #
        # Run from a REAL terminal (Terminal.app, iTerm, the IntelliJ Terminal tab, or ssh): once the app
        # is ready, the embedded Katalyst TUI inspector takes over as the default developer view — live
        # telemetry for routing, persistence, transactions, scheduler, events and websockets. Double
        # Ctrl+C quits it and stops the service. Without a TTY (piped output, an IDE Run window,
        # journalctl) the app falls back to plain logs and prints a one-time notice instead.
        #
        # A terminal UI needs a real TTY, so this installs a native launcher with installDist and execs
        # it directly — do NOT use ./gradlew run (it captures stdin/stdout and garbles the dashboard).
        #
        # Opt out of the inspector:  KATALYST_TUI_ENABLED=false ./run.sh
        # Select a config profile:   KATALYST_PROFILE=dev ./run.sh   (dev | staging | prod)

        SCRIPT_DIR="{{D}}( cd "{{D}}( dirname "{{D}}{BASH_SOURCE[0]}" )" && pwd )"
        cd "{{D}}SCRIPT_DIR"

        APP_NAME="{{ARTIFACT_ID}}"
        BIN_FILE="{{D}}SCRIPT_DIR/build/install/{{D}}APP_NAME/bin/{{D}}APP_NAME"

        # Prefer the Gradle wrapper; fall back to a system Gradle if the wrapper jar isn't generated yet.
        if [ -f "{{D}}SCRIPT_DIR/gradle/wrapper/gradle-wrapper.jar" ]; then
            GRADLE="./gradlew"
        elif command -v gradle >/dev/null 2>&1; then
            GRADLE="gradle"
        else
            echo "Neither the Gradle wrapper nor a system 'gradle' was found."
            echo "Install Gradle (brew install gradle / sdk install gradle), then run:"
            echo "  gradle wrapper --gradle-version {{GRADLE_VERSION}}"
            echo "  ./run.sh"
            exit 1
        fi

        echo "Building {{D}}APP_NAME..."
        "{{D}}GRADLE" installDist --warning-mode all

        if [ ! -x "{{D}}BIN_FILE" ]; then
            echo "Error: launcher not found at {{D}}BIN_FILE"
            exit 1
        fi

        if [ -t 0 ] && [ -t 1 ]; then
            echo "Launching {{D}}APP_NAME — bootstrap renders in the TUI, then the inspector takes over."
        else
            echo "No interactive terminal detected: running with plain logs (no TUI)."
        fi
        exec "{{D}}BIN_FILE" "{{D}}@"
        """.trimIndent() + "\n"
}
