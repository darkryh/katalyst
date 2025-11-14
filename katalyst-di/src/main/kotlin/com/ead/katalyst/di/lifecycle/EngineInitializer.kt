package com.ead.katalyst.di.lifecycle

import com.ead.katalyst.di.config.ServerConfiguration
import com.ead.katalyst.ktor.engine.KtorEngineFactory
import org.koin.core.Koin
import org.koin.core.error.NoDefinitionFoundException
import org.slf4j.LoggerFactory

/**
 * Application lifecycle initializer for Ktor engine configuration and validation.
 *
 * This initializer discovers and validates the Ktor engine configuration
 * registered in Koin. It ensures that:
 * - The selected engine is available and properly registered
 * - The engine factory can be instantiated
 * - Configuration values are valid and properly propagated
 *
 * Execution Order: -40 (after StartupValidator at -50, before other features)
 */
class EngineInitializer : ApplicationInitializer {
    companion object {
        private val logger = LoggerFactory.getLogger(EngineInitializer::class.java)
    }

    override val initializerId = "EngineInitializer"
    override val order: Int = -40

    override suspend fun onApplicationReady(koin: Koin) {
        logger.info("▶ Validating Ktor engine configuration")

        try {
            // Get server configuration (specifies which engine to use)
            val serverConfig = koin.get<ServerConfiguration>()
            logger.debug("Server configuration: engine={}, host={}, port={}",
                serverConfig.engine.engineType, serverConfig.host, serverConfig.port)

            // Verify engine factory is available
            val engineFactory = koin.get<KtorEngineFactory>()
            logger.info("✓ Engine factory resolved: {}", engineFactory::class.simpleName)

            // Verify the factory matches the requested engine type
            if (engineFactory.engineType != serverConfig.engine.engineType) {
                logger.warn(
                    "Engine type mismatch: configured={}, factory={}",
                    serverConfig.engine.engineType, engineFactory.engineType
                )
            }

            // Log engine configuration
            logger.info("✓ Engine initialized: {}", engineFactory.engineType)
            logger.info("✓ Server binding: {}:{}", serverConfig.host, serverConfig.port)
            logger.info("✓ Connection idle timeout: {}ms", serverConfig.connectionIdleTimeoutMs)
            logger.info("✓ Worker threads: {}", serverConfig.workerThreads)

        } catch (e: NoDefinitionFoundException) {
            logger.error("Engine factory not found in DI container")
            val message = buildString {
                append("No KtorEngineFactory found in Koin. ")
                append("Ensure the engine implementation module is on the classpath. ")
                append("For Netty: include katalyst-ktor-engine-netty dependency. ")
                append("For Jetty: include katalyst-ktor-engine-jetty dependency. ")
                append("For CIO: include katalyst-ktor-engine-cio dependency. ")
                append("Underlying error: ${e.message}")
            }
            throw InitializerFailedException(
                initializerName = initializerId,
                message = message,
                cause = e
            )
        } catch (e: Exception) {
            logger.error("Failed to initialize Ktor engine configuration", e)
            val message = buildString {
                append("Engine initialization failed. ")
                append("Check that your engine module is properly configured. ")
                append("Error: ${e.message}")
            }
            throw InitializerFailedException(
                initializerName = initializerId,
                message = message,
                cause = e
            )
        }
    }
}
