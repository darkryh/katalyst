package com.ead.katalyst.di.lifecycle

import com.ead.katalyst.ktor.engine.KtorEngineConfiguration
import org.koin.core.Koin
import org.slf4j.LoggerFactory

/**
 * Application lifecycle initializer for Ktor engine configuration.
 *
 * This initializer discovers and validates the Ktor engine configuration
 * registered in Koin. It ensures that exactly one engine implementation
 * is available and properly configured before the application starts.
 *
 * Execution Order: -40 (after StartupValidator at -50, before other features)
 */
class EngineInitializer : ApplicationInitializer {
    companion object {
        private val logger = LoggerFactory.getLogger(EngineInitializer::class.java)
    }

    override val order: Int = -40

    override suspend fun onApplicationReady(koin: Koin) {
        logger.info("Initializing Ktor engine configuration...")

        try {
            // Discover engine configuration from Koin
            val engineConfig = koin.get<KtorEngineConfiguration>()

            logger.info(
                "Ktor engine configuration loaded: $engineConfig"
            )

            logger.info(
                "Engine ready: listening on ${engineConfig.host}:${engineConfig.port}"
            )
        } catch (e: Exception) {
            logger.error("Failed to initialize Ktor engine configuration", e)
            val message = buildString {
                append("No KtorEngineConfiguration found in Koin. ")
                append("Ensure an engine implementation module is on the classpath (e.g., katalyst-ktor-engine-netty). ")
                append("Underlying error: ${e.message}")
            }
            throw InitializerFailedException(
                initializerName = initializerId,
                message = message,
                cause = e
            )
        }
    }
}
