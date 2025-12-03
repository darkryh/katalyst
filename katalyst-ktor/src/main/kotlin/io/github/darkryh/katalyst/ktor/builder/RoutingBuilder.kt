package io.github.darkryh.katalyst.ktor.builder

import io.github.darkryh.katalyst.ktor.extension.getKoinInstance
import io.ktor.server.application.Application
import io.ktor.server.routing.Route
import io.ktor.server.routing.Routing
import io.ktor.server.routing.routing
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("RoutingBuilder")

/**
 * Verify that Koin DI container is initialized and available.
 *
 * Used by DSL functions to ensure Koin is ready before processing.
 */
fun verifyKoin() {
    runCatching { getKoinInstance() }
        .onSuccess { logger.debug("Koin DI container is initialized and available") }
        .onFailure {
            logger.warn(
                "Koin DI container not initialized. Routes may fail when resolving dependencies via katalystRouting.",
                it
            )
        }
}

/**
 * DSL function for configuring custom routes in the Ktor application.
 *
 * This is the main entry point for developers to add REST API routes.
 * It should be called from Application.configureRouting().
 */
fun Application.katalystRouting(block: Routing.() -> Unit) {
    logger.info("Starting route configuration")

    try {
        verifyKoin()
        routing {
            block()
        }
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
        verifyKoin()
        this.apply(block)
        logger.info("Route configuration completed successfully")
    } catch (e: Exception) {
        logger.error("Error during route configuration", e)
        throw e
    }
}
