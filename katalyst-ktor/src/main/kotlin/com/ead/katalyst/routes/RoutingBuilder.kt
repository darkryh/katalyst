package com.ead.katalyst.routes

import io.ktor.server.application.Application
import io.ktor.server.routing.Route
import io.ktor.server.routing.Routing
import io.ktor.server.routing.application
import io.ktor.server.routing.routing
import org.koin.ktor.ext.getKoin
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("RoutingBuilder")

/**
 * DSL builder for configuring custom routes in the Ktor application.
 *
 * This builder provides a clean API for developers to register REST API routes
 * with automatic dependency injection support. All services and dependencies
 * registered in the Koin DI container are automatically available via the `inject()` helpers.
 *
 * **Features:**
 * - Type-safe route definitions
 * - Automatic DI context availability
 * - RESTful API patterns
 * - Request/response serialization
 * - Integration with exception handlers
 *
 * **Dependency Injection Pattern:**
 * All dependencies are retrieved via `call.inject<T>()` or `route.inject<T>()`.
 * The transactionManager is automatically injected into all Service implementations.
 *
 * **Example Usage in Application.configureRouting():**
 * ```kotlin
 * fun Application.configureRouting() = katalystRouting {
 *     route("/api/users") {
 *         post {
 *             val userService = call.inject<UserService>()
 *             val dto = call.receive<CreateUserDTO>()
 *             val user = userService.createUser(dto)
 *             call.respond(HttpStatusCode.Created, user)
 *         }
 *
 *         get {
 *             val userService = call.inject<UserService>()
 *             val users = userService.listUsers()
 *             call.respond(users)
 *         }
 *
 *         get("/{id}") {
 *             val userService = call.inject<UserService>()
 *             val id = call.parameters["id"]?.toLongOrNull()
 *                 ?: throw IllegalArgumentException("Invalid user ID")
 *             val user = userService.getUserById(id)
 *             call.respond(user)
 *         }
 *     }
 * }
 * ```
 *
 * **Transaction Handling Pattern:**
 * Services automatically wrap repository operations in transactions via the
 * injected DatabaseTransactionManager:
 * ```kotlin
 * class UserService(
 *     private val repository: UserRepository
 * ) : Service {
 *     override lateinit var transactionManager: DatabaseTransactionManager
 *
 *     suspend fun createUser(dto: CreateUserDTO): User {
 *         return transactionManager.transaction {
 *             val user = repository.save(User.from(dto))
 *             user
 *         }
 *     }
 * }
 * ```
 *
 * **Route Organization:**
 * Routes can be organized hierarchically using the routing DSL:
 * ```kotlin
 * katalystRouting {
 *     route("/api") {
 *         route("/users") { /* user routes */ }
 *         route("/products") { /* product routes */ }
 *         route("/orders") { /* order routes */ }
 *     }
 * }
 * ```
 *
 * **Middleware Integration:**
 * Middleware can be applied at any level:
 * ```kotlin
 * katalystRouting {
 *     route("/api") {
 *         // Apply authentication middleware to all /api routes
 *         intercept(ApplicationPhase.Setup) {
 *             val authService = call.inject<AuthService>()
 *             authService.validateRequest(call.request)
 *         }
 *
 *         route("/users") { /* routes */ }
 *     }
 * }
 * ```
 */
class RoutingBuilder(private val application: Application) {

    /**
     * Configures routes within the application.
     *
     * This extension function on Application allows access to the Ktor routing DSL
     * while maintaining DI context awareness.
     *
     * @param block The routing configuration block
     */
    fun configureRoutes(block: Routing.() -> Unit) {
        logger.info("Configuring custom routes")
        application.routing(block)
        logger.debug("Custom routes configured successfully")
    }
}

/**
 * DSL function for configuring custom routes in the Ktor application.
 *
 * This is the main entry point for developers to add REST API routes.
 * It should be called from Application.configureRouting().
 *
 * The DI container is automatically available for dependency injection within all route handlers
 * via the `call.inject<T>()` helper.
 *
 * **Example:**
 * ```kotlin
 * fun Application.configureRouting() = katalystRouting {
 *     get("/health") {
 *         call.respond(mapOf("status" to "ok"))
 *     }
 *
 *     route("/api/items") {
 *         get {
 *             val itemService = call.inject<ItemService>()
 *             call.respond(itemService.listItems())
 *         }
 *
 *         post {
 *             val itemService = call.inject<ItemService>()
 *             val dto = call.receive<CreateItemDTO>()
 *             call.respond(HttpStatusCode.Created, itemService.createItem(dto))
 *         }
 *     }
 * }
 * ```
 *
 * **Error Handling:**
 * Exceptions thrown in route handlers are automatically caught and handled
 * by the exception handlers configured in Application.exceptionHandler().
 *
 * @param block The routing configuration block
 */
fun Application.katalystRouting(block: Routing.() -> Unit) {
    logger.info("Starting route configuration")

    try {
        // Verify DI container is initialized
        runCatching { getKoin() }
            .onSuccess { logger.debug("Koin DI container is initialized and available") }
            .onFailure {
                logger.warn(
                    "Koin DI container not initialized. Routes may fail when resolving dependencies via katalystRouting.",
                    it
                )
            }

        // Configure routes
        RoutingBuilder(this).configureRoutes(block)

        logger.info("Route configuration completed successfully")
    } catch (e: Exception) {
        logger.error("Error during route configuration", e)
        throw e
    }
}

/**
 * Nested DSL helper so feature modules can wrap their own route declarations
 * with the same logging/guardrails used at the Application level.
 */
fun Route.katalystRouting(block: Route.() -> Unit) {
    logger.info("Starting route configuration")

    try {
        // Verify DI container is initialized
        runCatching { getKoin() }
            .onSuccess { logger.debug("Koin DI container is initialized and available") }
            .onFailure {
                logger.warn(
                    "Koin DI container not initialized. Routes may fail when resolving dependencies via katalystRouting.",
                    it
                )
            }

        // Configure routes
        RoutingBuilder(this.application).configureRoutes(block)

        logger.info("Route configuration completed successfully")
    } catch (e: Exception) {
        logger.error("Error during route configuration", e)
        throw e
    }
}
