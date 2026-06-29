package io.github.darkryh.katalyst.conventions

/**
 * The canonical, dependency-free description of how Katalyst recognises framework
 * entrypoints.
 *
 * Katalyst is annotation-free: a class or function is "managed" because it implements
 * one of a small set of marker interfaces, or because a top-level function calls one of
 * a small set of routing DSL functions, or because a member function returns
 * [SCHEDULER_JOB_HANDLE]. Those rules are enforced at runtime by `katalyst-di`
 * (`AutoBindingRegistrar` / `ComponentRegistrationOrchestrator`).
 *
 * Historically the constants encoding those rules were duplicated as `private` values
 * inside the runtime. Three different layers need the *same* rules:
 *
 *  - the **runtime** ([katalyst-di]) — the ultimate source of truth, what actually boots,
 *  - the **static analysis** layer ([katalyst-analysis]) — mirrors the runtime without
 *    booting an application, for Gradle tasks, CLIs, tests and reports,
 *  - the **IntelliJ plugin** — recognises the same entrypoints in the editor (PSI) so it
 *    can suppress false "unused" warnings and render gutter icons.
 *
 * Centralising the rules here means the runtime, analysis and IDE cannot disagree about
 * what a Katalyst entrypoint is. This object is pure data — no Ktor, Exposed, Koin or
 * reflection — so any module can depend on it freely.
 */
object KatalystConventions {

    // ---------------------------------------------------------------------------------
    // Marker interface fully-qualified names (dot form).
    //
    // A concrete class implementing one of these (within a scanned package) is discovered
    // and registered by Katalyst. These mirror the base types used in
    // `ComponentRegistrationOrchestrator.discoverAllComponents()` plus the persistence,
    // lifecycle and config-loader contracts discovered elsewhere in the runtime.
    // ---------------------------------------------------------------------------------

    const val SERVICE: String = "io.github.darkryh.katalyst.core.component.Service"
    const val COMPONENT: String = "io.github.darkryh.katalyst.core.component.Component"
    const val CRUD_REPOSITORY: String = "io.github.darkryh.katalyst.repositories.CrudRepository"
    const val TABLE: String = "io.github.darkryh.katalyst.core.persistence.Table"
    const val IDENTIFIABLE: String = "io.github.darkryh.katalyst.repositories.Identifiable"
    const val EVENT_HANDLER: String = "io.github.darkryh.katalyst.events.EventHandler"
    const val KTOR_MODULE: String = "io.github.darkryh.katalyst.ktor.KtorModule"
    const val KATALYST_MIGRATION: String = "io.github.darkryh.katalyst.migrations.KatalystMigration"
    const val APPLICATION_INITIALIZER: String =
        "io.github.darkryh.katalyst.di.lifecycle.StartupHook"
    const val APPLICATION_READY_INITIALIZER: String =
        "io.github.darkryh.katalyst.di.lifecycle.ReadyHook"
    const val CONFIG_BINDING: String =
        "io.github.darkryh.katalyst.config.provider.ConfigBinding"

    /**
     * A method returning this type (on a discovered [SERVICE]) is a scheduler entrypoint.
     * See `katalyst-scheduler`'s `SchedulerInitializer.discoverCandidateMethods`.
     */
    const val SCHEDULER_JOB_HANDLE: String =
        "io.github.darkryh.katalyst.scheduler.job.SchedulerJobHandle"

    /** Exposed's base table type; a Katalyst [TABLE] is discovered only when it also extends this. */
    const val EXPOSED_TABLE: String = "org.jetbrains.exposed.v1.core.Table"

    // ---------------------------------------------------------------------------------
    // Ktor receiver types for function entrypoints.
    //
    // A route/middleware/websocket/exception-handler function is a top-level function
    // whose first (receiver) parameter is one of these.
    // ---------------------------------------------------------------------------------

    const val KTOR_APPLICATION: String = "io.ktor.server.application.Application"
    const val KTOR_ROUTE: String = "io.ktor.server.routing.Route"

    val ktorReceiverTypes: Set<String> = setOf(KTOR_APPLICATION, KTOR_ROUTE)

    /**
     * Every marker interface whose concrete implementations are Katalyst-managed and
     * therefore should never be reported as "unused" by the IDE.
     */
    val markerInterfaces: Set<String> = setOf(
        SERVICE,
        COMPONENT,
        CRUD_REPOSITORY,
        TABLE,
        EVENT_HANDLER,
        KTOR_MODULE,
        KATALYST_MIGRATION,
        APPLICATION_INITIALIZER,
        APPLICATION_READY_INITIALIZER,
        CONFIG_BINDING,
    )

    // ---------------------------------------------------------------------------------
    // Routing DSL functions.
    //
    // A top-level function with a Ktor receiver is only registered as a route module when
    // its body actually invokes one of these DSL functions. The runtime confirms this with
    // ASM bytecode analysis (`AutoBindingRegistrar.scanForKatalystDslCalls`); the IDE
    // confirms it with PSI call resolution. Both use the names below.
    // ---------------------------------------------------------------------------------

    const val DSL_ROUTING: String = "katalystRouting"
    const val DSL_WEBSOCKETS: String = "katalystWebSockets"
    const val DSL_EXCEPTION_HANDLER: String = "katalystExceptionHandler"
    const val DSL_MIDDLEWARE: String = "katalystMiddleware"

    val dslMethodNames: Set<String> = setOf(
        DSL_ROUTING,
        DSL_WEBSOCKETS,
        DSL_EXCEPTION_HANDLER,
        DSL_MIDDLEWARE,
    )

    /**
     * JVM internal (slash-separated) names of the file classes that declare the DSL
     * functions. Used as the `owner` filter during bytecode analysis so that only genuine
     * Katalyst DSL calls — not same-named functions from other libraries — count.
     */
    val dslOwnerInternalNames: Set<String> = setOf(
        "io/github/darkryh/katalyst/ktor/builder/RoutingBuilderKt",
        "io/github/darkryh/katalyst/ktor/websocket/WebSocketBuilderKt",
        "io/github/darkryh/katalyst/websockets/builder/WebSocketBuilderKt",
        "io/github/darkryh/katalyst/ktor/builder/ExceptionHandlerBuilderKt",
        "io/github/darkryh/katalyst/ktor/middleware/MiddlewareKt",
    )

    /**
     * Dot-separated package-qualified names of the DSL owner file classes, for PSI-based
     * tools (IntelliJ) that resolve calls to a fully-qualified name rather than bytecode.
     */
    val dslOwnerQualifiedNames: Set<String> =
        dslOwnerInternalNames.map { it.replace('/', '.') }.toSet()

    /**
     * Installation-order hint for a discovered route function, mirroring the runtime's
     * `RouteFunctionModule.order`: exception handlers install first, then middleware, then
     * regular routes/websockets.
     */
    fun installOrderFor(dslCalls: Set<String>, functionName: String): Int = when {
        DSL_EXCEPTION_HANDLER in dslCalls -> -100
        functionName.contains("exception", ignoreCase = true) -> -100
        DSL_MIDDLEWARE in dslCalls -> -50
        functionName.contains("middleware", ignoreCase = true) -> -50
        functionName.contains("plugin", ignoreCase = true) -> -50
        else -> 0
    }

    // ---------------------------------------------------------------------------------
    // Discovery category labels.
    //
    // These exact strings are the keys used by the runtime's `DiscoverySnapshot`
    // (`ComponentRegistrationOrchestrator.discoverAllComponents`). Analysis reuses them so
    // its snapshots are interchangeable with the runtime's validation map.
    // ---------------------------------------------------------------------------------

    object Categories {
        const val REPOSITORIES: String = "repositories"
        const val COMPONENTS: String = "components"
        const val SERVICES: String = "services"
        const val EVENT_HANDLERS: String = "event handlers"
        const val KTOR_MODULES: String = "ktor modules"
        const val MIGRATIONS: String = "migrations"
    }
}
