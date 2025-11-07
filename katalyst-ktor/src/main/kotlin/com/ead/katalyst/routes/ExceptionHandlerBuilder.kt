package com.ead.katalyst.routes

import io.ktor.server.application.*
import io.ktor.server.plugins.statuspages.*
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("ExceptionHandlerBuilder")

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
 * **Example Usage in Application.exceptionHandler():**
 * ```kotlin
 * fun Application.exceptionHandler() = katalystExceptionHandler {
 *     exception<MyValidationException> { call, cause ->
 *         call.respond(HttpStatusCode.BadRequest, mapOf(
 *             "error" to "VALIDATION_ERROR",
 *             "message" to cause.message
 *         ))
 *     }
 *
 *     exception<MyNotFoundException> { call, cause ->
 *         call.respond(HttpStatusCode.NotFound, mapOf(
 *             "error" to "NOT_FOUND",
 *             "message" to cause.message
 *         ))
 *     }
 *
 *     // Catch-all for unhandled exceptions
 *     exception<Throwable> { call, cause ->
 *         logger.error("Unexpected exception", cause)
 *         call.respond(HttpStatusCode.InternalServerError, mapOf(
 *             "error" to "INTERNAL_SERVER_ERROR",
 *             "message" to cause.message ?: "An unexpected error occurred"
 *         ))
 *     }
 * }
 * ```
 */
class ExceptionHandlerBuilder(
    private val install: (StatusPagesConfig.() -> Unit) -> Unit
) {
    val registrations: MutableList<StatusPagesConfig.() -> Unit> = mutableListOf()

    /**
     * Registers a custom exception mapping that will be added to the underlying [StatusPages] plugin.
     */
    inline fun <reified T : Throwable> exception(
        noinline handler: suspend (call: ApplicationCall, cause: T) -> Unit
    ) {
        registrations += {
            exception(handler)
        }
    }

    /**
     * Build and install the exception handlers into the application.
     */
    fun build() {
        logger.info("Exception handlers configured")
        install {
            registrations.forEach { registration -> registration(this) }
        }
        logger.info("Exception handlers installed successfully")
    }
}

/**
 * DSL function to configure exception handlers.
 *
 * Developers register their own exceptions and handlers in the block.
 *
 * @param block Configuration block for exception handlers
 */
fun Application.katalystExceptionHandler(block: ExceptionHandlerBuilder.() -> Unit) {
    val builder = ExceptionHandlerBuilder { install(StatusPages, it) }
    builder.block()
    builder.build()
}