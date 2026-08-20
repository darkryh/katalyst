package io.github.darkryh.katalyst.ktor.builder

import io.github.darkryh.katalyst.ktor.extension.getKatalystContainer
import io.ktor.server.application.Application
import io.ktor.server.routing.Route
import io.ktor.server.routing.Routing
import io.ktor.server.routing.routing
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("RoutingBuilder")

/**
 * Verify that the Katalyst container is initialized and available.
 *
 * Used by DSL functions to ensure dependency resolution is ready before processing.
 */
fun verifyKatalystContainer() {
    runCatching { getKatalystContainer() }
        .onSuccess { logger.debug("Katalyst container is initialized and available") }
        .onFailure {
            logger.warn(
                "Katalyst container not initialized. Routes may fail when resolving dependencies via katalystRouting.",
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
    logger.debug("Starting route configuration")

    try {
        verifyKatalystContainer()
        routing {
            block()
        }
        logger.debug("Route configuration completed successfully")
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
    logger.debug("Starting route configuration")

    try {
        verifyKatalystContainer()
        this.apply(block)
        logger.debug("Route configuration completed successfully")
    } catch (e: Exception) {
        logger.error("Error during route configuration", e)
        throw e
    }
}
