package com.ead.katalyst.di.internal

import com.ead.katalyst.ktor.engine.KatalystKtorEngine
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
 * This is the bridge between engine objects (user intent) and actual engine
 * module loading (Koin factories).
 *
 * Discovery process:
 * 1. Accept a KatalystKtorEngine object (NettyEngine, JettyEngine, or CioEngine)
 * 2. Use reflection to load the corresponding engine module
 * 3. Register only the selected engine's module
 * 4. No unused engines loaded (clean, minimal dependencies)
 *
 * Supported engines:
 * - NettyEngine → com.ead.katalyst.ktor.engine.netty.NettyEngineModuleKt::getNettyEngineModule
 * - JettyEngine → com.ead.katalyst.ktor.engine.jetty.JettyEngineModuleKt::getJettyEngineModule
 * - CioEngine → com.ead.katalyst.ktor.engine.cio.CioEngineModuleKt::getCioEngineModule
 *
 * @param engine The engine object selected for this application
 */
class EngineRegistrar(
    private val engine: KatalystKtorEngine,
    private val logger: org.slf4j.Logger = LoggerFactory.getLogger(EngineRegistrar::class.java)
) {

    /**
     * Attempt to discover and load an engine module.
     *
     * Uses reflection to dynamically load engine modules only when they're
     * on the classpath and selected via the engine object.
     *
     * For standard engines (netty, jetty, cio), attempts to load the corresponding module.
     * For unknown engine types (e.g., test engines), skips module registration gracefully.
     *
     * @return Koin Module if engine is available, or null if not on classpath or unknown engine type
     * @throws EngineNotAvailableException if loading fails for known engine types
     */
    fun discoverSelectedEngine(): Module? {
        logger.info("Looking for Ktor engine: {}", engine.engineType)

        // Determine which module to load based on engine type
        val moduleInfo = when (engine.engineType) {
            "netty" -> "com.ead.katalyst.ktor.engine.netty.NettyEngineModuleKt" to "getNettyEngineModule"
            "jetty" -> "com.ead.katalyst.ktor.engine.jetty.JettyEngineModuleKt" to "getJettyEngineModule"
            "cio" -> "com.ead.katalyst.ktor.engine.cio.CioEngineModuleKt" to "getCioEngineModule"
            else -> {
                // For unknown engine types (e.g., test engines), skip module registration
                logger.debug("Engine type '{}' does not have a module provider. Skipping module registration.", engine.engineType)
                return null
            }
        }

        val (className, methodName) = moduleInfo
        return try {
            val moduleClass = Class.forName(className)
            val method = moduleClass.getDeclaredMethod(methodName)
            method.isAccessible = true

            val module = method.invoke(null) as? Module
                ?: throw EngineNotAvailableException(
                    "Engine module provider returned null for: $className.$methodName"
                )

            logger.info("✓ Engine module loaded: {}", engine.engineType)
            return module

        } catch (e: ClassNotFoundException) {
            throw EngineNotAvailableException(
                "Engine '${engine.engineType}' not available on classpath. " +
                "Did you forget to add katalyst-ktor-engine-${engine.engineType} dependency?",
                cause = e
            )
        } catch (e: NoSuchMethodException) {
            throw EngineNotAvailableException(
                "Engine module provider method not found. " +
                "Is katalyst-ktor-engine-${engine.engineType} compatible with this version?",
                cause = e
            )
        } catch (e: Exception) {
            throw EngineNotAvailableException(
                "Failed to load engine module for '${engine.engineType}': ${e.message}",
                cause = e
            )
        }
    }

    /**
     * Register the selected engine module with the provided modules list.
     *
     * This method:
     * 1. Discovers the selected engine module
     * 2. Adds the module to the list
     *
     * This ensures that:
     * - Only the selected engine is loaded (no unused dependencies)
     * - Clear error messages if engine is not found
     *
     * @param modules Mutable list of Koin modules to register into
     * @throws EngineNotAvailableException if engine cannot be found or loaded
     */
    fun registerEngineModules(modules: MutableList<Module>) {
        // Discover and register selected engine
        try {
            val engineModule = discoverSelectedEngine()
            if (engineModule != null) {
                modules.add(engineModule)
                logger.info("✓ Engine registered: {}", engine.engineType)
            }
        } catch (e: EngineNotAvailableException) {
            logger.error("Failed to register engine: {}", e.message)
            throw e
        }
    }
}
