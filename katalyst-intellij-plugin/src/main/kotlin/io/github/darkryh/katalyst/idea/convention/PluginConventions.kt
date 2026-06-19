package io.github.darkryh.katalyst.idea.convention

/**
 * The Katalyst discovery contract, as needed by the IDE.
 *
 * This is a deliberate, vendored mirror of `io.github.darkryh.katalyst.conventions.KatalystConventions`
 * from the `katalyst-conventions` module. The plugin is a separate composite build (it applies the
 * IntelliJ Platform Gradle plugin and is not a normal library), so rather than wire a cross-build
 * project dependency we copy the small, closed set of constants here.
 *
 * Keep this in sync with `katalyst-conventions`. The surface is intentionally tiny — four DSL
 * function names and a handful of marker FQNs — so drift is easy to spot and cheap to fix; the
 * canonical module remains the single source of truth, and `katalyst-analysis` carries a contract
 * test that fails if any of these FQNs stop resolving.
 */
object PluginConventions {

    const val SERVICE = "io.github.darkryh.katalyst.core.component.Service"
    const val COMPONENT = "io.github.darkryh.katalyst.core.component.Component"
    const val CRUD_REPOSITORY = "io.github.darkryh.katalyst.repositories.CrudRepository"
    const val TABLE = "io.github.darkryh.katalyst.core.persistence.Table"
    const val EVENT_HANDLER = "io.github.darkryh.katalyst.events.EventHandler"
    const val KTOR_MODULE = "io.github.darkryh.katalyst.ktor.KtorModule"
    const val KATALYST_MIGRATION = "io.github.darkryh.katalyst.migrations.KatalystMigration"
    const val APPLICATION_INITIALIZER = "io.github.darkryh.katalyst.di.lifecycle.ApplicationInitializer"
    const val APPLICATION_READY_INITIALIZER = "io.github.darkryh.katalyst.di.lifecycle.ApplicationReadyInitializer"
    const val AUTOMATIC_SERVICE_CONFIG_LOADER = "io.github.darkryh.katalyst.config.provider.AutomaticServiceConfigLoader"
    const val SCHEDULER_JOB_HANDLE = "io.github.darkryh.katalyst.scheduler.job.SchedulerJobHandle"

    /** Marker interfaces whose implementations are Katalyst-managed (never "unused"). */
    val markerInterfaces: List<String> = listOf(
        SERVICE,
        COMPONENT,
        CRUD_REPOSITORY,
        TABLE,
        EVENT_HANDLER,
        KTOR_MODULE,
        KATALYST_MIGRATION,
        APPLICATION_INITIALIZER,
        APPLICATION_READY_INITIALIZER,
        AUTOMATIC_SERVICE_CONFIG_LOADER,
    )

    const val DSL_ROUTING = "katalystRouting"
    const val DSL_WEBSOCKETS = "katalystWebSockets"
    const val DSL_EXCEPTION_HANDLER = "katalystExceptionHandler"
    const val DSL_MIDDLEWARE = "katalystMiddleware"

    val dslMethodNames: Set<String> = setOf(
        DSL_ROUTING,
        DSL_WEBSOCKETS,
        DSL_EXCEPTION_HANDLER,
        DSL_MIDDLEWARE,
    )

    /** Dotted package-qualified names of the file classes that declare the DSL functions. */
    val dslOwnerQualifiedNames: Set<String> = setOf(
        "io.github.darkryh.katalyst.ktor.builder.RoutingBuilderKt",
        "io.github.darkryh.katalyst.ktor.websocket.WebSocketBuilderKt",
        "io.github.darkryh.katalyst.websockets.builder.WebSocketBuilderKt",
        "io.github.darkryh.katalyst.ktor.builder.ExceptionHandlerBuilderKt",
        "io.github.darkryh.katalyst.ktor.middleware.MiddlewareKt",
    )
}
