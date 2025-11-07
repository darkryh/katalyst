package com.ead.katalyst.di

import com.ead.katalyst.components.Component
import com.ead.katalyst.database.DatabaseConfig
import com.ead.katalyst.di.internal.KtorModuleRegistry
import com.ead.katalyst.events.EventConfiguration
import com.ead.katalyst.routes.KtorModule
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStarted
import io.ktor.server.application.ApplicationStarting
import io.ktor.server.application.ApplicationStopping
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.netty.EngineMain
import kotlinx.coroutines.runBlocking
import org.koin.core.context.GlobalContext
import org.slf4j.LoggerFactory

/**
 * Katalyst Application DSL Builder.
 *
 * Provides a fluent configuration interface for setting up a Ktor application
 * with Katalyst library dependencies, services, repositories, and other components.
 *
 * **Usage:**
 * ```kotlin
 * fun main(args: Array<String>) = katalystApplication(args) {
 *     database(DatabaseConfig(...))
 *     scanPackages("com.example.app")
 * }
 * ```
 *
 * The `katalystApplication` block automatically:
 * 1. Initializes Koin DI with all Katalyst modules
 * 2. Configures the Ktor application through Application.module()
 * 3. Ensures all services, repositories, and validators are available
 * 4. Starts the server engine
 *
 * Developers can optionally add custom configuration in Application.module():
 * ```kotlin
 * fun Application.module() {
 *     // Library automatically handles DI setup here
 *     // Add any custom configuration:
 *     customMiddleware()
 *     additionalRoutes()
 * }
 * ```
 */
class KatalystApplicationBuilder {
    private val logger = LoggerFactory.getLogger("KatalystApplication")

    private var databaseConfig: DatabaseConfig? = null
    private var enableScheduler: Boolean = false
    private var enableWebSockets: Boolean = false
    private var eventConfiguration: EventConfiguration? = null
    private var componentScanPackages: Array<String> = emptyArray()
    private var serverConfig: ServerConfiguration = ServerConfiguration.netty()

    /**
     * Provide the database configuration used to bootstrap core persistence infrastructure.
     */
    fun database(config: DatabaseConfig): KatalystApplicationBuilder {
        logger.debug("Database configuration supplied for Katalyst DI")
        this.databaseConfig = config
        return this
    }

    /**
     * Configure packages that should be scanned for services, repositories, and validators.
     */
    fun scanPackages(vararg packages: String): KatalystApplicationBuilder {
        logger.debug("Setting component scan packages: {}", packages.joinToString(", "))
        @Suppress("UNCHECKED_CAST")
        val copy = packages.clone() as Array<String>
        this.componentScanPackages = copy
        return this
    }

    /**
     * Enable the scheduler module.
     */
    fun enableScheduler(): KatalystApplicationBuilder {
        logger.debug("Enabling scheduler")
        this.enableScheduler = true
        return this
    }

    /**
     * Enable the event subsystem (application bus + optional bridge).
     */
    fun enableEvents(configure: EventConfiguration.() -> Unit = {}): KatalystApplicationBuilder {
        logger.debug("Enabling events")
        val config = EventConfiguration().apply(configure)
        this.eventConfiguration = config
        return this
    }

    /**
     * Enable WebSocket support (installs Ktor WebSockets plugin + DSL).
     */
    fun enableWebSockets(): KatalystApplicationBuilder {
        logger.debug("Enabling WebSockets")
        this.enableWebSockets = true
        return this
    }

    /**
     * Configure the server engine (Netty, Jetty, CIO).
     *
     * @param config Server configuration
     */
    fun withServerConfig(config: ServerConfiguration): KatalystApplicationBuilder {
        logger.debug("Setting server configuration: {}", config.engineType)
        this.serverConfig = config
        return this
    }

    /**
     * Configure the server engine with a builder.
     *
     * @param builder Server configuration builder
     */
    fun withServerConfig(builder: ServerConfigurationBuilder.() -> Unit): KatalystApplicationBuilder {
        logger.debug("Setting server configuration with builder")
        val configBuilder = ServerConfigurationBuilder()
        configBuilder.builder()
        this.serverConfig = configBuilder.build()
        return this
    }

    internal fun resolveServerConfiguration(): ServerConfiguration = serverConfig

    /**
     * Initialize DI with all configured modules.
     */
    internal fun initializeDI() {
        val config = databaseConfig
            ?: throw IllegalStateException("Database configuration must be supplied before starting Katalyst.")

        val scanTargets = if (componentScanPackages.isNotEmpty()) componentScanPackages else emptyArray()

        logger.info(
            "Initializing Katalyst DI (scheduler={}, webSockets={}, events={})",
            enableScheduler,
            enableWebSockets,
            eventConfiguration != null
        )
        initializeKoinStandalone(
            KatalystDIOptions(
                databaseConfig = config,
                enableScheduler = enableScheduler,
                enableWebSockets = enableWebSockets,
                scanPackages = scanTargets,
                eventConfiguration = eventConfiguration
            )
        )
        logger.info("Katalyst DI initialized successfully")
    }

    /**
     * Configure the Ktor application with DI and all modules.
     */
    internal fun configureApplication(application: Application) {
        logger.info("Configuring Ktor application with Katalyst modules")

        // NOTE: We do NOT install the Koin plugin here because it creates a separate Koin context.
        // Instead, routes will use GlobalContext.get() directly, which has all registered components
        // from initializeKoinStandalone().
        // This is more reliable than trying to link plugin contexts.

        // Get the Koin instance from the global context that was initialized in initializeDI()
        val koin = GlobalContext.get()

        runCatching { koin.getAll<Component>().size }
            .onSuccess { size -> logger.debug("Auto-discovered components registered: {}", size) }
            .onFailure { error -> logger.warn("Unable to inspect auto-discovered components", error) }

        // IMPORTANT: Keep all discovered route functions (RouteFunctionModule instances are unique by identity)
        // Only deduplicate actual KtorModule implementations to avoid installing the same class twice
        val registryModules = KtorModuleRegistry.consume()
        val koinModules = koin.getAll<KtorModule>()
            .distinctBy { it::class }  // Deduplicate KtorModule implementations only
        val ktorModules = registryModules + koinModules

        logger.info("Discovered {} Ktor module(s) for installation", ktorModules.size)
        ktorModules
            .sortedBy { it.order }
            .forEach { module ->
                logger.info("Installing Ktor module {}", module::class.qualifiedName)
                module.install(application)
            }

        logger.info("Ktor application configured successfully")
    }
}

/**
 * Entry point DSL for Katalyst Ktor applications.
 *
 * Initializes all Katalyst library modules and dependency injection,
 * then executes the provided block (which typically starts the Ktor engine).
 *
 * **Usage:**
 * ```kotlin
 * fun main(args: Array<String>) = katalystApplication(args) {
 *     database(DatabaseConfig(...))
 *     scanPackages("com.example.app")
 * }
 * ```
 *
 * **With custom configuration:**
 * ```kotlin
 * fun main(args: Array<String>) = katalystApplication(args) {
 *     enableScheduler()
 *     withServerConfig {
 *         netty()
 *         withServerWrapper(ServerEngines.withLogging())
 *     }
 * }
 * ```
 *
 * The Application.module() function (defined in your Ktor app) is automatically called
 * by Ktor during startup. By the time it's called, all DI is pre-initialized.
 *
 * You can still add custom logic in Application.module():
 * ```kotlin
 * fun Application.module() {
 *     // DI is already initialized here
 *     // Add custom routes, middleware, or configuration:
 *     setupCustomMiddleware()
 *     installCustomPlugins()
 * }
 * ```
 *
 * @param block Configuration builder executed before the Ktor engine starts
 */
fun katalystApplication(
    args: Array<String> = emptyArray(),
    block: suspend KatalystApplicationBuilder.() -> Unit
) {
    val logger = LoggerFactory.getLogger("katalystApplication")
    val bootStart = System.nanoTime()

    printKatalystBanner()
    val builder = KatalystApplicationBuilder()

    try {
        logger.info("Starting Katalyst application")

        runBlocking {
            builder.block()
        }

        builder.initializeDI()
        val serverConfig = builder.resolveServerConfiguration()
        val embeddedServer = createEmbeddedServer(args, serverConfig, logger)

        embeddedServer.monitor.subscribe(ApplicationStarting) { application ->
            runCatching {
                val wrappedApplication = application.wrap(serverConfig.applicationWrapper)
                builder.configureApplication(wrappedApplication)
            }.onFailure { error ->
                logger.error("Failed to configure Ktor application", error)
                throw error
            }
        }

        embeddedServer.monitor.subscribe(ApplicationStarted) {
            val elapsedSeconds = (System.nanoTime() - bootStart) / 1_000_000_000.0
            logger.info("Katalyst started in {} s (actual)", String.format("%.3f", elapsedSeconds))
        }

        embeddedServer.monitor.subscribe(ApplicationStopping) {
            runCatching { stopKoinStandalone() }
                .onFailure { error -> logger.warn("Error while stopping Koin", error) }
        }

        serverConfig.serverWrapper?.let { wrapper ->
            runCatching { wrapper(embeddedServer.engine) }
                .onFailure { error ->
                    logger.warn("Server wrapper execution failed: {}", error.message)
                    logger.debug("Full server wrapper error", error)
                }
        }

        embeddedServer.start(wait = true)

        logger.info("Katalyst application started successfully")
    } catch (e: Exception) {
        logger.error("Failed to start Katalyst application", e)
        throw e
    }
}

private fun printKatalystBanner() {
    val blue = "\u001B[94m"
    val reset = "\u001B[0m"
    val banner = """
██╗  ██╗  █████╗  ████████╗  █████╗  ██╗   ██╗   ██╗ ███████╗ ████████╗
██║ ██╔╝ ██╔══██╗ ╚══██╔══╝ ██╔══██╗ ██║   ╚██╗ ██╔╝ ██╔════╝ ╚══██╔══╝
█████╔╝  ███████║    ██║    ███████║ ██║    ╚████╔╝  ███████╗    ██║   
██╔═██╗  ██╔══██║    ██║    ██╔══██║ ██║     ╚██╔╝   ╚════██║    ██║   
██║  ██╗ ██║  ██║    ██║    ██║  ██║ ███████╗ ██║    ███████║    ██║   
╚═╝  ╚═╝ ╚═╝  ╚═╝    ╚═╝    ╚═╝  ╚═╝ ╚══════╝ ╚═╝    ╚══════╝    ╚═╝                                                              
    """.trimIndent()
    println("$blue$banner$reset")
}

private fun createEmbeddedServer(
    args: Array<String>,
    serverConfiguration: ServerConfiguration,
    logger: org.slf4j.Logger
): EmbeddedServer<out ApplicationEngine, *> =
    when (serverConfiguration.engineType.lowercase()) {
        "netty" -> EngineMain.createServer(args)
        else -> {
            logger.warn(
                "Engine type '{}' not yet supported by Katalyst bootstrap; falling back to Netty",
                serverConfiguration.engineType
            )
            EngineMain.createServer(args)
        }
    }
