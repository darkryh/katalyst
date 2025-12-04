package io.github.darkryh.katalyst.ktor.middleware

import io.github.darkryh.katalyst.ktor.extension.getKoinInstance
import io.ktor.server.application.Application
import io.ktor.server.request.ApplicationRequest
import org.koin.core.parameter.ParametersDefinition
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
}

/**
 * DSL function to configure middleware.
 *
 * @param block Configuration block for middleware
 */
fun Application.katalystMiddleware(block: MiddlewareBuilder.() -> Unit) {
    val builder = MiddlewareBuilder(this)
    builder.block()
    logger.info("Middleware configured: ${builder.getMiddlewares().size} middleware components registered")
}


inline fun <reified T : Any> MiddlewareBuilder.ktInject(noinline parameters: ParametersDefinition? = null): Lazy<T> =
    lazy { getKoinInstance().get(parameters = parameters) }