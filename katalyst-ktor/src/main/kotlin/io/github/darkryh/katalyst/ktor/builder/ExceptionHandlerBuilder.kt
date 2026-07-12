package io.github.darkryh.katalyst.ktor.builder

import io.ktor.server.application.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.http.HttpStatusCode
import io.ktor.util.AttributeKey
import io.ktor.utils.io.KtorDsl
import org.slf4j.LoggerFactory
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

private val logger = LoggerFactory.getLogger("ExceptionHandlerBuilder")

/**
 * Per-[Application] state backing [ExceptionHandlerRegistry].
 *
 * Stored in [Application.attributes] so it lives and dies with the [Application] it belongs
 * to, instead of a process-global object shared by every [Application] in the JVM.
 */
private class ExceptionHandlerState {
    val handlers: MutableList<StatusPagesConfig.() -> Unit> = CopyOnWriteArrayList()
    val scheduled: AtomicBoolean = AtomicBoolean(false)
}

private val ExceptionHandlerStateKey = AttributeKey<ExceptionHandlerState>("KatalystExceptionHandlerState")

/**
 * Thread-safe, per-[Application] registry for collecting exception handlers across multiple files.
 *
 * This registry allows multiple `katalystExceptionHandler` calls from different files
 * to register their handlers, which are then installed together when the StatusPages
 * plugin is first installed.
 *
 * **Why This Is Needed:**
 * Ktor's StatusPages plugin can only be installed once per application. If you try to
 * install it multiple times (e.g., from different exception handler files), Ktor throws
 * a `DuplicatePluginException`. This registry collects all handlers and installs them
 * together in a single StatusPages installation.
 *
 * **Deferred Installation:**
 * Handlers are collected during module configuration, but StatusPages is installed
 * only once when the first request is about to be processed, ensuring all handlers
 * from all `katalystExceptionHandler` calls are included.
 *
 * **Per-Application Isolation:**
 * State (the pending handler list and the "already scheduled" flag) is stored per
 * [Application] in [Application.attributes], not in process-global state. Two different
 * [Application] instances (e.g. two hosted apps in the same JVM, or two tests) never see
 * each other's handlers, and the state is naturally released when the [Application] is
 * garbage collected.
 */
object ExceptionHandlerRegistry {
    private fun stateFor(application: Application): ExceptionHandlerState =
        application.attributes.computeIfAbsent(ExceptionHandlerStateKey) { ExceptionHandlerState() }

    /**
     * Registers a handler configuration to be applied when StatusPages is installed
     * for the given [application].
     *
     * @param application The Ktor application this handler belongs to
     * @param handler The StatusPagesConfig configuration block
     */
    fun register(application: Application, handler: StatusPagesConfig.() -> Unit) {
        stateFor(application).handlers.add(handler)
    }

    /**
     * Schedules StatusPages installation for the given application.
     *
     * Installation is deferred until all module configuration is complete,
     * ensuring all handlers from multiple `katalystExceptionHandler` calls
     * are collected before StatusPages is installed.
     *
     * @param application The Ktor application to install StatusPages into
     */
    fun scheduleInstall(application: Application) {
        val state = stateFor(application)

        // Only schedule once per application
        if (!state.scheduled.compareAndSet(false, true)) {
            logger.debug("Installation already scheduled for this application")
            return
        }

        // Use MonitoringReady phase which fires after all modules are loaded but before serving
        application.monitor.subscribe(ApplicationStarted) {
            installNow(application)
        }

        logger.debug("Scheduled StatusPages installation for application startup")
    }

    /**
     * Installs StatusPages immediately with all handlers registered for [application].
     *
     * This is called automatically when the application starts, but can also
     * be called manually in tests after all handlers are registered.
     *
     * @param application The Ktor application to install StatusPages into
     */
    fun installNow(application: Application) {
        // Check if already installed in this specific application
        val isInstalled = application.pluginOrNull(StatusPages) != null
        if (isInstalled) {
            logger.debug("StatusPages already installed, skipping")
            return
        }

        val handlers = stateFor(application).handlers
        if (handlers.isEmpty()) {
            logger.debug("No exception handlers registered, skipping StatusPages installation")
            return
        }

        logger.info("Installing StatusPages with {} handler configuration(s)", handlers.size)

        application.install(StatusPages) {
            handlers.forEach { handler -> handler(this) }
        }

        logger.info("StatusPages installed successfully with all registered handlers")
    }

    /**
     * Resets the registry state for the given [application]. Call this in test setup to
     * ensure a clean per-application slate.
     */
    fun reset(application: Application) {
        application.attributes.remove(ExceptionHandlerStateKey)
    }

    /**
     * Returns the number of handlers registered for [application]. Useful for testing/debugging.
     */
    fun handlerCount(application: Application): Int = stateFor(application).handlers.size
}

/**
 * DSL builder for configuring exception handlers in the Ktor application.
 *
 * This builder provides a hook for developers to register custom exception handlers
 * and configure error responses using Ktor's StatusPages plugin.
 *
 * **How to use:**
 * 1. Define your own exception types in your application
 * 2. Register handlers in the DSL block
 * 3. Map exceptions to appropriate HTTP status codes
 *
 * **Multiple Files Support:**
 * You can call `katalystExceptionHandler` from multiple files. All handlers are
 * collected and installed together when the application starts, avoiding
 * `DuplicatePluginException`.
 *
 * **Example Usage in Application.exceptionHandler():**
 * ```kotlin
 * // File: AuthExceptionHandlers.kt
 * fun Application.authExceptionHandler() = katalystExceptionHandler {
 *     exception<AuthenticationException> { call, cause ->
 *         call.respond(HttpStatusCode.Unauthorized, mapOf(
 *             "error" to "UNAUTHORIZED",
 *             "message" to cause.message
 *         ))
 *     }
 * }
 *
 * // File: ValidationExceptionHandlers.kt
 * fun Application.validationExceptionHandler() = katalystExceptionHandler {
 *     exception<ValidationException> { call, cause ->
 *         call.respond(HttpStatusCode.BadRequest, mapOf(
 *             "error" to "VALIDATION_ERROR",
 *             "message" to cause.message
 *         ))
 *     }
 * }
 * ```
 *
 * Both handlers will be collected and installed together in a single StatusPages
 * plugin installation.
 */
class ExceptionHandlerBuilder {
    @PublishedApi
    internal val registrations: MutableList<StatusPagesConfig.() -> Unit> = mutableListOf()

    /**
     * Registers a custom exception mapping that will be added to the underlying [StatusPages] plugin.
     */
    @KtorDsl
    inline fun <reified T : Throwable> exception(
        noinline handler: suspend (call: ApplicationCall, cause: T) -> Unit
    ) {
        registrations += {
            exception(handler)
        }
    }

    /**
     * Registers a status code handler for the given status codes.
     */
    @KtorDsl
    fun status(vararg statusCodes: HttpStatusCode, handler: suspend StatusPagesConfig.(ApplicationCall, HttpStatusCode) -> Unit) {
        registrations += {
            status(*statusCodes) { call, code -> handler(call, code) }
        }
    }
}

/**
 * DSL function to configure exception handlers.
 *
 * This function collects exception handler configurations and registers them
 * with the [ExceptionHandlerRegistry]. StatusPages installation happens automatically
 * when the application starts serving requests.
 *
 * **Multiple Calls Supported:**
 * You can safely call this function from multiple files. All handlers will be
 * collected and installed together when the application is ready, avoiding
 * `DuplicatePluginException`.
 *
 * **Test Usage:**
 * For tests, call `ExceptionHandlerRegistry.reset(application)` before configuring the
 * test application to ensure clean state between test runs.
 *
 * @param block Configuration block for exception handlers
 */
fun Application.katalystExceptionHandler(block: ExceptionHandlerBuilder.() -> Unit) {
    val builder = ExceptionHandlerBuilder()
    builder.block()

    // Register all handlers from this builder with this application's own registry state
    builder.registrations.forEach { registration ->
        ExceptionHandlerRegistry.register(this, registration)
    }

    logger.debug("Registered {} exception handler(s) from katalystExceptionHandler call", builder.registrations.size)

    // Install StatusPages using ApplicationStarted event to ensure all handlers are registered
    // This defers installation until all module configuration is complete
    ExceptionHandlerRegistry.scheduleInstall(this)
}