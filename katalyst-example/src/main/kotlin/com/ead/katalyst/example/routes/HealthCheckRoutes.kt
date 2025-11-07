package com.ead.katalyst.example.routes

import com.ead.katalyst.example.api.DetailedHealthResponse
import com.ead.katalyst.example.api.HealthStatusResponse
import com.ead.katalyst.example.service.UserService
import com.ead.katalyst.routes.inject
import com.ead.katalyst.routes.katalystRouting
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

/**
 * Health Check Routes
 *
 * Automatically discovered and installed by Katalyst framework.
 * Demonstrates simple route patterns and service injection.
 *
 * **Automatic Features:**
 * - Route function auto-discovered via katalystRouting DSL
 * - Services automatically available via call.inject<ServiceType>()
 * - No manual route registration needed
 * - Framework handles routing pipeline automatically
 *
 * **Discovery Process:**
 * 1. Framework scans packages for route extension functions
 * 2. Finds functions decorated with @Suppress("unused")
 * 3. Discovers katalystRouting DSL blocks
 * 4. Automatically installs routes in correct order
 * 5. Services are available for injection in route handlers
 *
 * **Usage Pattern:**
 * ```kotlin
 * fun Route.myRoutes() = katalystRouting {
 *     route("/api/resource") {
 *         get {
 *             val service = call.inject<MyService>()
 *             // All dependencies are already wired
 *             val data = service.getData()
 *             call.respond(data)
 *         }
 *     }
 * }
 * ```
 *
 * **Route Organization:**
 * Routes are organized by functional domain:
 * - userRoutes() - User CRUD operations
 * - healthCheckRoutes() - Application health and status
 * - New route functions can be added and auto-discovered
 */
@Suppress("unused")
fun Route.healthCheckRoutes() = katalystRouting {
    route("/health") {
        /**
         * GET /health - Simple health check
         *
         * Returns basic application status.
         * Useful for load balancers and monitoring.
         *
         * Response: { "status": "UP" }
         */
        get {
            call.respond(
                HttpStatusCode.OK,
                HealthStatusResponse(
                    status = "UP",
                    timestamp = System.currentTimeMillis()
                )
            )
        }

        /**
         * GET /health/detailed - Detailed health check with service status
         *
         * Demonstrates service injection to show database connectivity.
         * Real implementation would check database, cache, external services, etc.
         */
        get("/detailed") {
            try {
                // Example: Inject UserService to verify database connectivity
                val userService = call.inject<UserService>()

                call.respond(
                    HttpStatusCode.OK,
                    DetailedHealthResponse(
                        status = "UP",
                        services = mapOf(
                            "database" to "CONNECTED",
                            "scheduler" to "RUNNING"
                        ),
                        timestamp = System.currentTimeMillis()
                    )
                )
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.ServiceUnavailable,
                    HealthStatusResponse(
                        status = "DOWN",
                        timestamp = System.currentTimeMillis()
                    )
                )
            }
        }
    }
}
