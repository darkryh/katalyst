package com.ead.katalyst.example.routes

import com.ead.katalyst.example.api.CreateUserRequest
import com.ead.katalyst.example.api.UserResponse
import com.ead.katalyst.example.service.UserService
import com.ead.katalyst.routes.inject
import com.ead.katalyst.routes.katalystRouting
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route

/**
 * User Routes
 *
 * Automatically discovered and installed by Katalyst framework.
 * Demonstrates comprehensive REST API implementation with auto-injection.
 *
 * **Automatic Features:**
 * - Route function auto-discovered via katalystRouting DSL
 * - UserService automatically available via call.inject<UserService>()
 * - All service dependencies already wired (Repository, Validator, Notification, Audit services)
 * - No manual service instantiation needed
 * - Exception handling automatically applied
 * - Transaction management handled by service
 *
 * **Dependency Chain (All Automatic):**
 * ```
 * UserRoutes
 *   └─> UserService (auto-injected)
 *       ├─> UserRepository (auto-injected to UserService)
 *       ├─> UserValidator (auto-injected to UserService)
 *       ├─> NotificationService (auto-injected to UserService)
 *       │   └─> EmailNotificationComponent (auto-injected to NotificationService)
 *       └─> AuditService (auto-injected to UserService)
 * ```
 *
 * **REST API Endpoints:**
 * - POST   /api/users           - Create new user
 * - GET    /api/users           - List all users
 * - GET    /api/users/{id}      - Get user by ID
 * - PUT    /api/users/{id}      - Update user (placeholder)
 * - DELETE /api/users/{id}      - Delete user (placeholder)
 *
 * **Response Format:**
 * All responses return UserResponse objects with consistent structure:
 * ```json
 * {
 *     "id": 1,
 *     "name": "John Doe",
 *     "email": "john@example.com"
 * }
 * ```
 *
 * **Error Handling:**
 * - 400 Bad Request: Invalid input (handled by exceptionHandlers)
 * - 404 Not Found: User not found
 * - 500 Internal Server Error: Unexpected errors (handled by exceptionHandlers)
 */
@Suppress("unused")
fun Route.userRoutes() = katalystRouting {
    route("/api/users") {
        /**
         * POST /api/users - Create a new user
         *
         * Request body:
         * ```json
         * {
         *     "name": "John Doe",
         *     "email": "john@example.com"
         * }
         * ```
         *
         * Response: 201 Created
         * ```json
         * {
         *     "id": 1,
         *     "name": "John Doe",
         *     "email": "john@example.com"
         * }
         * ```
         *
         * **Automatic Flow:**
         * 1. UserService is auto-injected from DI container
         * 2. UserService has all dependencies ready (Repository, Validator, Notification, Audit services)
         * 3. createUser() is called with validation
         * 4. User is saved to database in transaction
         * 5. Notification email is sent automatically
         * 6. Audit log is created automatically
         * 7. Response is returned to client
         * 8. If exception occurs, exceptionHandlers catches and formats response
         */
        post {
            val service = call.inject<UserService>()
            val request = call.receive<CreateUserRequest>()
            val created = service.createUser(request)
            call.respond(HttpStatusCode.Created, UserResponse.from(created))
        }

        /**
         * GET /api/users - List all users
         *
         * Query parameters: None currently, but can be extended with:
         * - ?limit=10
         * - ?offset=0
         * - ?search=name
         *
         * Response: 200 OK
         * ```json
         * [
         *     { "id": 1, "name": "John Doe", "email": "john@example.com" },
         *     { "id": 2, "name": "Jane Smith", "email": "jane@example.com" }
         * ]
         * ```
         *
         * **Automatic Flow:**
         * 1. UserService is auto-injected
         * 2. listUsers() retrieves all users from database in transaction
         * 3. Results are mapped to UserResponse objects
         * 4. List is returned to client
         */
        get {
            val service = call.inject<UserService>()
            val users = service.listUsers().map(UserResponse::from)
            call.respond(users)
        }

        /**
         * GET /api/users/{id} - Get user by ID
         *
         * Path parameters:
         * - id: User ID (required, must be numeric)
         *
         * Response: 200 OK
         * ```json
         * {
         *     "id": 1,
         *     "name": "John Doe",
         *     "email": "john@example.com"
         * }
         * ```
         *
         * Error cases:
         * - 400: Invalid ID format
         * - 404: User not found
         *
         * **Automatic Flow:**
         * 1. UserService is auto-injected
         * 2. getUser() retrieves user from database in transaction
         * 3. If not found, UserExampleValidationException is thrown
         * 4. exceptionHandlers catches exception and returns 400 Bad Request
         * 5. If found, UserResponse is returned to client
         */
        get("/{id}") {
            val service = call.inject<UserService>()
            val id = call.parameters["id"]?.toLongOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "invalid id"))
            val user = service.getUser(id)
            call.respond(UserResponse.from(user))
        }

        /**
         * PUT /api/users/{id} - Update user (placeholder)
         *
         * This is a placeholder for demonstration purposes.
         * In a real application, this would:
         * - Accept updated user data
         * - Validate changes
         * - Update database
         * - Audit the change
         * - Return updated user
         */
        put("/{id}") {
            call.respond(
                HttpStatusCode.NotImplemented,
                mapOf("message" to "Update user endpoint not yet implemented")
            )
        }

        /**
         * DELETE /api/users/{id} - Delete user (placeholder)
         *
         * This is a placeholder for demonstration purposes.
         * In a real application, this would:
         * - Accept user ID
         * - Delete user from database in transaction
         * - Send account closure email via NotificationService
         * - Audit the deletion via AuditService
         * - Return 204 No Content on success
         */
        delete("/{id}") {
            call.respond(
                HttpStatusCode.NotImplemented,
                mapOf("message" to "Delete user endpoint not yet implemented")
            )
        }
    }
}
