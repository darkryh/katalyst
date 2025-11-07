package com.ead.katalyst.example.exceptionHandler

import com.ead.katalyst.example.api.ErrorResponse
import com.ead.katalyst.example.domain.exception.TestException
import com.ead.katalyst.example.domain.exception.UserExampleValidationException
import com.ead.katalyst.routes.katalystExceptionHandler
import io.ktor.http.*
import io.ktor.server.application.Application
import io.ktor.server.response.*
import io.ktor.util.logging.KtorSimpleLogger

/**
 * Exception Handlers
 *
 * Automatically discovered and installed by Katalyst framework.
 * Demonstrates typed exception handling with katalystExceptionHandler DSL.
 *
 * **Automatic Features:**
 * - Discovers exception handler functions via katalystExceptionHandler DSL
 * - Automatically installs handlers into the application
 * - Type-safe exception matching
 * - Standardized error responses
 * - No manual handler registration needed
 *
 * **How It Works:**
 * 1. Framework scans packages and finds this function
 * 2. katalystExceptionHandler DSL registers all exception types
 * 3. When an exception is thrown in a route handler:
 *    - Framework checks if handler exists for that exception type
 *    - If found, calls the handler to generate response
 *    - If not found, falls back to default Ktor behavior
 * 4. Response is automatically sent to client
 *
 * **Usage in Service/Route:**
 * ```kotlin
 * post("/users") {
 *     val service = call.inject<UserService>()
 *     // If service.createUser throws UserExampleValidationException,
 *     // it will be caught and handled by exceptionHandlers()
 *     val user = service.createUser(request)
 *     call.respond(user)
 * }
 * ```
 *
 * **Best Practices:**
 * - Create specific exception types for different error cases
 * - Use appropriate HTTP status codes (400, 404, 500, etc.)
 * - Include helpful error messages
 * - Log important errors for debugging
 * - Consider client API contracts when designing responses
 */
@Suppress("unused")
fun Application.exceptionHandlers() = katalystExceptionHandler {
    val logger = KtorSimpleLogger("ExceptionHandler")

    /**
     * Handles validation errors
     *
     * Demonstrates: Custom business exception handling
     * Status Code: 400 Bad Request (client error - invalid input)
     *
     * Example triggers:
     * - Email validation failed
     * - Required field missing
     * - Format validation failed
     */
    exception<UserExampleValidationException> { call, exception ->
        logger.warn("Validation error: ${exception.message}")
        call.respond(
            HttpStatusCode.BadRequest,
            ErrorResponse(
                error = "VALIDATION_ERROR",
                message = exception.message,
                timestamp = System.currentTimeMillis()
            )
        )
    }

    /**
     * Handles test/demo exceptions
     *
     * Demonstrates: Different exception type handling
     * Status Code: 409 Conflict (operation conflict)
     *
     * Example triggers:
     * - Scheduled task failures (in demo)
     * - State conflicts in business logic
     */
    exception<TestException> { call, exception ->
        logger.error("Test exception occurred: ${exception.message}")
        call.respond(
            HttpStatusCode.Conflict,
            ErrorResponse(
                error = "OPERATION_CONFLICT",
                message = exception.message,
                timestamp = System.currentTimeMillis()
            )
        )
    }

    /**
     * Fallback handler for unexpected exceptions
     *
     * Demonstrates: Generic exception handling
     * Status Code: 500 Internal Server Error (server error)
     *
     * Catches all unhandled exceptions and returns generic error message
     * Details are logged for debugging but not exposed to client
     */
    exception<Exception> { call, exception ->
        logger.error("Unexpected exception: ${exception.message}", exception)
        call.respond(
            HttpStatusCode.InternalServerError,
            ErrorResponse(
                error = "INTERNAL_SERVER_ERROR",
                message = "An unexpected error occurred. Please try again later.",
                timestamp = System.currentTimeMillis()
            )
        )
    }
}