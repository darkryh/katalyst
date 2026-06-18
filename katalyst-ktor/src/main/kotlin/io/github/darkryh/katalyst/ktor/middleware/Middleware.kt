package io.github.darkryh.katalyst.ktor.middleware

import io.github.darkryh.katalyst.ktor.extension.getKatalystContainer
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.PipelineCall
import io.ktor.server.application.Plugin
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.util.pipeline.PipelineContext
import io.ktor.util.pipeline.PipelinePhase
import io.ktor.server.request.ApplicationRequest
import io.ktor.server.response.respondText
import io.ktor.http.HttpStatusCode
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("Middleware")

/**
 * Result of middleware processing.
 *
 * Indicates whether to continue processing or abort the request.
 */
sealed class MiddlewareResult {
    /**
     * Continue processing the request through the middleware chain.
     */
    object Continue : MiddlewareResult()

    /**
     * Abort the request with a specific status code and message.
     */
    data class Abort(val statusCode: Int, val message: String) : MiddlewareResult()
}

/**
 * Base interface for middleware components.
 *
 * Middleware provides cross-cutting concerns such as:
 * - Authentication and authorization
 * - Request logging
 * - Rate limiting
 * - CORS handling
 * - Request validation
 */
interface Middleware {
    /**
     * Process a request through this middleware.
     *
     * @param request The incoming HTTP request
     * @return MiddlewareResult indicating whether to continue or abort
     */
    suspend fun process(request: ApplicationRequest): MiddlewareResult

    /**
     * Runs after the downstream Ktor pipeline completes successfully.
     *
     * Override this for response logging, metrics, cleanup, or other post-call work.
     */
    suspend fun after(call: ApplicationCall) {
    }

    /**
     * Runs when the downstream Ktor pipeline throws.
     *
     * Return `true` when the middleware handled the exception and Katalyst should stop
     * propagation. Return `false` to let Ktor's normal exception handling continue.
     */
    suspend fun onException(call: ApplicationCall, cause: Throwable): Boolean = false
}

/**
 * DSL builder for configuring middleware in the application.
 */
class MiddlewareBuilder(val application: Application) {
    private val middlewares = mutableListOf<Middleware>()

    /**
     * Register a middleware component.
     *
     * @param middleware The middleware to register
     */
    fun use(middleware: Middleware) {
        logger.debug("Registering middleware: ${middleware::class.simpleName}")
        middlewares.add(middleware)
    }

    /**
     * Get the list of registered middleware.
     */
    fun getMiddlewares(): List<Middleware> = middlewares.toList()

    /**
     * Installs a native Ktor plugin from inside a `katalystMiddleware` setup block.
     */
    fun <B : Any, F : Any> install(
        plugin: Plugin<Application, B, F>,
        configure: B.() -> Unit = {}
    ): F = application.install(plugin, configure)

    /**
     * Adds a native Ktor pipeline interceptor from inside a `katalystMiddleware` setup block.
     */
    fun intercept(
        phase: PipelinePhase,
        block: suspend PipelineContext<Unit, PipelineCall>.(Unit) -> Unit
    ) {
        application.intercept(phase, block)
    }

    internal fun install() {
        if (middlewares.isEmpty()) {
            return
        }

        val installedMiddlewares = getMiddlewares()
        application.intercept(ApplicationCallPipeline.Plugins) {
            val currentCall = call
            try {
                for (middleware in installedMiddlewares) {
                    when (val result = middleware.process(currentCall.request)) {
                        MiddlewareResult.Continue -> Unit
                        is MiddlewareResult.Abort -> {
                            currentCall.respondText(
                                text = result.message,
                                status = HttpStatusCode.fromValue(result.statusCode)
                            )
                            return@intercept
                        }
                    }
                }

                proceed()

                for (middleware in installedMiddlewares.asReversed()) {
                    middleware.after(currentCall)
                }
            } catch (cause: Throwable) {
                for (middleware in installedMiddlewares.asReversed()) {
                    if (middleware.onException(currentCall, cause)) {
                        return@intercept
                    }
                }
                throw cause
            }
        }
    }
}

/**
 * DSL function to configure middleware.
 *
 * @param block Configuration block for middleware
 */
fun Application.katalystMiddleware(block: MiddlewareBuilder.() -> Unit) {
    val builder = MiddlewareBuilder(this)
    builder.block()
    val count = builder.getMiddlewares().size
    if (count == 0) {
        logger.debug("Middleware configured: 0 middleware components registered")
    } else {
        builder.install()
        logger.info("Middleware configured: {} middleware components registered", count)
    }
}


inline fun <reified T : Any> MiddlewareBuilder.ktInject(): Lazy<T> =
    lazy { getKatalystContainer().get(T::class) }
