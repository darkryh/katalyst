package com.ead.katalyst.di.internal

import com.ead.katalyst.di.config.ServerConfiguration
import com.ead.katalyst.di.lifecycle.LifecycleException
import org.koin.core.module.Module
import org.koin.dsl.module
import org.slf4j.LoggerFactory

/**
 * Exception thrown when engine cannot be found or loaded.
 */
class EngineNotAvailableException(
    message: String,
    cause: Throwable? = null
) : LifecycleException(message, cause)

/**
 * Discovers available Ktor engine implementations and registers the selected one.
 *
 * This is the bridge between ServerConfiguration (user intent) and actual engine
 * creation (Koin factories).
 *
 * Discovery process:
 * 1. Scan classpath for available engine implementations
 * 2. Match against ServerConfiguration.engineType
 * 3. Register only the selected engine's module
 * 4. No unused engines loaded (clean, minimal dependencies)
 *
 * Supported engines:
 * - Netty: com.ead.katalyst.ktor.engine.netty.NettyEngineModuleKt::getNettyEngineModule
 * - Jetty: com.ead.katalyst.ktor.engine.jetty.JettyEngineModuleKt::getJettyEngineModule
 * - CIO: com.ead.katalyst.ktor.engine.cio.CioEngineModuleKt::getCioEngineModule
 *
 * @param serverConfig Server configuration containing desired engine type
 */
class EngineRegistrar(
    private val serverConfig: ServerConfiguration,
    private val logger: org.slf4j.Logger = LoggerFactory.getLogger(EngineRegistrar::class.java)
) {

    /**
     * Available engines: maps engineType name to (class name, method name) tuple
     * Used for reflection-based discovery to avoid hardcoded engine dependencies
     */
    private val availableEngines = mapOf(
        "netty" to ("com.ead.katalyst.ktor.engine.netty.NettyEngineModuleKt" to "getNettyEngineModule"),
        "jetty" to ("com.ead.katalyst.ktor.engine.jetty.JettyEngineModuleKt" to "getJettyEngineModule"),
        "cio" to ("com.ead.katalyst.ktor.engine.cio.CioEngineModuleKt" to "getCioEngineModule")
    )

    /**
     * Attempt to discover and load an engine module.
     *
     * Uses reflection to dynamically load engine modules only when they're
     * on the classpath and selected in ServerConfiguration.
     *
     * @return Koin Module if engine is available, or null if not on classpath
     * @throws EngineNotAvailableException if engine type is unknown or loading fails
     */
    @Suppress("KotlinUnreachableCode")
    fun discoverSelectedEngine(): Module? {
        val engineType = serverConfig.engineType.lowercase()

        logger.info("Looking for Ktor engine: {}", engineType)

        val (className, methodName) = availableEngines[engineType] ?:
        throw EngineNotAvailableException("Unknown engine type: $engineType. Available: ${availableEngines.keys.joinToString(", ")}")

        return try {
            val moduleClass = Class.forName(className)
            val method = moduleClass.getDeclaredMethod(methodName)
            method.isAccessible = true

            val module = method.invoke(null) as? Module
                ?: throw EngineNotAvailableException(
                    "Engine module provider returned null for: $className.$methodName"
                )

            logger.info("✓ Engine module loaded: {}", engineType)
            return module

        } catch (e: ClassNotFoundException) {
            throw EngineNotAvailableException(
                "Engine '$engineType' not available on classpath. " +
                "Did you forget to add katalyst-ktor-engine-$engineType dependency?",
                cause = e
            )
        } catch (e: NoSuchMethodException) {
            throw EngineNotAvailableException(
                "Engine module provider method not found. " +
                "Is katalyst-ktor-engine-$engineType compatible with this version?",
                cause = e
            )
        } catch (e: Exception) {
            throw EngineNotAvailableException(
                "Failed to load engine module for '$engineType': ${e.message}",
                cause = e
            )
        }
    }

    /**
     * Register the selected engine module with the provided modules list.
     *
     * This method:
     * 1. Creates a module that registers ServerConfiguration as a singleton
     * 2. Discovers the selected engine module
     * 3. Adds both modules to the list
     *
     * This ensures that:
     * - ServerConfiguration is available to all engine modules for dependency injection
     * - Only the selected engine is loaded (no unused dependencies)
     * - Clear error messages if engine is not found
     *
     * @param modules Mutable list of Koin modules to register into
     * @throws EngineNotAvailableException if engine cannot be found or loaded
     */
    fun registerEngineModules(modules: MutableList<Module>) {
        // Register ServerConfiguration as singleton - available to all components
        modules.add(
            module {
                single {
                    serverConfig
                }
            }
        )

        // Discover and register selected engine
        try {
            val engineModule = discoverSelectedEngine()
            if (engineModule != null) {
                modules.add(engineModule)
                logger.info("✓ Engine registered: {}", serverConfig.engineType)
            }
        } catch (e: EngineNotAvailableException) {
            logger.error("Failed to register engine: {}", e.message)
            throw e
        }
    }
}
