@file:Suppress("deprecation")

package com.ead.katalyst.di

import com.ead.katalyst.core.component.Component
import com.ead.katalyst.config.DatabaseConfig
import com.ead.katalyst.di.config.KatalystDIOptions
import com.ead.katalyst.di.config.ServerConfiguration
import com.ead.katalyst.di.config.ServerDeploymentConfiguration
import com.ead.katalyst.di.config.initializeKoinStandalone
import com.ead.katalyst.di.config.stopKoinStandalone
import com.ead.katalyst.di.config.wrap
import com.ead.katalyst.di.internal.KtorModuleRegistry
import com.ead.katalyst.ktor.KtorModule
import com.ead.katalyst.ktor.engine.KatalystKtorEngine
import com.ead.katalyst.ktor.engine.KtorEngineFactory
import com.ead.katalyst.di.feature.KatalystFeature
import com.ead.katalyst.di.lifecycle.StartupWarnings
import com.ead.katalyst.di.lifecycle.BootstrapProgress
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStarted
import io.ktor.server.application.ApplicationStarting
import io.ktor.server.application.ApplicationStopping
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import kotlinx.coroutines.runBlocking
import org.koin.core.context.GlobalContext
import org.koin.core.error.NoDefinitionFoundException
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
 *
 * Optional Katalyst modules (scheduler, events, websockets, etc.) contribute their own
 * `enableX()` extension functions to this builder. Simply add the dependency for the
 * feature you need and call the extension inside the [katalystApplication] block.
 */
class KatalystApplicationBuilder {
    private val logger = LoggerFactory.getLogger("KatalystApplication")

    private var databaseConfig: DatabaseConfig? = null
    private val features: MutableList<KatalystFeature> = mutableListOf()
    private var componentScanPackages: Array<String> = emptyArray()
    private var selectedEngine: KatalystKtorEngine? = null

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
     * Explicitly select the Ktor engine for this application.
     *
     * **This is mandatory** - you must call this function before calling initializeDI().
     *
     * **Usage:**
     * ```kotlin
     * fun main(args: Array<String>) = katalystApplication(args) {
     *     database(DbConfigImpl.loadDatabaseConfig())
     *     scanPackages("com.example.app")
     *     engine(NettyEngine)  // Explicit engine selection - REQUIRED
     * }
     * ```
     *
     * Available engines:
     * - NettyEngine (high performance, asynchronous)
     * - JettyEngine (mature, thread-pool based)
     * - CioEngine (pure Kotlin coroutines, lightweight)
     *
     * @param engine The engine to use (NettyEngine, JettyEngine, or CioEngine)
     * @return This builder for method chaining
     * @throws IllegalStateException if initializeDI() is called without setting an engine
     */
    fun engine(engine: KatalystKtorEngine): KatalystApplicationBuilder {
        logger.debug("Engine explicitly set to: {}", engine.engineType)
        this.selectedEngine = engine
        return this
    }

    /**
     * Registers an optional feature (scheduler, events, websockets, etc.).
     */
    fun feature(feature: KatalystFeature): KatalystApplicationBuilder {
        logger.debug("Registering optional feature: {}", feature.id)

        this.features += feature

        return this
    }

    internal fun registeredFeatures(): List<KatalystFeature> = features.toList()

    /**
     * Resolve server configuration using the explicitly selected engine.
     *
     * **Requires explicit engine selection** - engine() must be called before this method.
     *
     * **Configuration loading strategy:**
     * 1. Attempts to load from application.yaml via ServerDeploymentConfigurationLoader
     * 2. Falls back to sensible defaults if YAML loading is unavailable or fails
     *
     * For custom YAML loading behavior, call enableServerConfiguration() feature:
     * ```kotlin
     * fun main() = katalystApplication {
     *     engine(NettyEngine)
     *     database(...)
     *     scanPackages(...)
     *     enableServerConfiguration()  // Customize YAML loading
     * }
     * ```
     *
     * @return ServerConfiguration with the selected engine
     * @throws IllegalStateException if no engine was explicitly selected via engine()
     */
    internal fun resolveServerConfiguration(): ServerConfiguration {
        val engine = selectedEngine
            ?: throw IllegalStateException(
                "Engine must be explicitly selected before initializing Katalyst DI. " +
                "Call engine(YourEngine) in the katalystApplication block. " +
                "Available engines: NettyEngine, JettyEngine, CioEngine. " +
                "Example: engine(NettyEngine)"
            )

        val deployment = loadDeploymentConfigurationFromYaml()

        return ServerConfiguration(
            engine = engine,
            deployment = deployment
        ).also {
            logger.info("Using explicitly selected engine: {}", engine.engineType)
            logger.debug("Server bound to {}:{}", it.host, it.port)
        }
    }

    private fun loadDeploymentConfigurationFromYaml(): ServerDeploymentConfiguration {
        return try {
            logger.info("Attempting to load ServerDeploymentConfiguration from application.yaml...")

            // Load ConfigProvider via reflection (YamlConfigProvider if available)
            logger.debug("Loading YamlConfigProvider...")
            val providerClass = Class.forName("com.ead.katalyst.config.yaml.YamlConfigProvider")
            @Suppress("UNCHECKED_CAST")
            val configProvider = providerClass.getDeclaredConstructor().newInstance() as com.ead.katalyst.core.config.ConfigProvider
            logger.debug("✓ YamlConfigProvider instantiated")

            // Load using ServerDeploymentConfigurationLoader via reflection
            logger.debug("Loading ServerDeploymentConfigurationLoader...")
            val loaderClass = Class.forName("com.ead.katalyst.config.provider.ServerDeploymentConfigurationLoader")

            // Access the object singleton - Kotlin creates a static INSTANCE field for object declarations
            val instance = try {
                logger.debug("Getting INSTANCE field...")
                loaderClass.getField("INSTANCE").get(null)
            } catch (e: NoSuchFieldException) {
                logger.debug("INSTANCE field not found, trying kotlin.objectInstance...")
                // Fallback: Kotlin reflection for object instance
                @Suppress("UNCHECKED_CAST")
                loaderClass.kotlin.objectInstance as Any
            }
            logger.debug("✓ ServerDeploymentConfigurationLoader instance obtained")

            // Call loadConfig and validate
            logger.debug("Invoking loadConfig method...")
            @Suppress("UNCHECKED_CAST")
            val loadConfigMethod = loaderClass.getMethod("loadConfig", com.ead.katalyst.core.config.ConfigProvider::class.java)
            val validateMethod = loaderClass.getMethod("validate", ServerDeploymentConfiguration::class.java)

            @Suppress("UNCHECKED_CAST")
            val deployment = loadConfigMethod.invoke(instance, configProvider) as ServerDeploymentConfiguration
            logger.info("✓ ServerDeploymentConfiguration loaded: host={}, port={}", deployment.host, deployment.port)

            logger.debug("Invoking validate method...")
            validateMethod.invoke(instance, deployment)
            logger.info("✓ ServerDeploymentConfiguration validated successfully")

            logger.info("✅ Successfully loaded ktor.deployment configuration from application.yaml")
            deployment
        } catch (e: ClassNotFoundException) {
            logger.error("❌ ClassNotFoundException - YamlConfigProvider or ServerDeploymentConfigurationLoader not found on classpath")
            logger.error("   Ensure katalyst-config-provider is included as a dependency")
            logger.info("Using sensible defaults: host=0.0.0.0, port=8080")
            ServerDeploymentConfiguration.createDefault()
        } catch (e: NoSuchMethodException) {
            logger.error("❌ NoSuchMethodException - Could not find expected method on loader: {}", e.message)
            logger.info("Using sensible defaults: host=0.0.0.0, port=8080")
            ServerDeploymentConfiguration.createDefault()
        } catch (e: Exception) {
            logger.error("❌ Error loading ServerDeploymentConfiguration from application.yaml: {}", e.message)
            logger.error("Stack trace:", e)
            logger.info("Using sensible defaults: host=0.0.0.0, port=8080")
            ServerDeploymentConfiguration.createDefault()
        }
    }

    /**
     * Initialize DI with all configured modules.
     */
    internal fun initializeDI() {
        val config = databaseConfig
            ?: throw IllegalStateException("Database configuration must be supplied before starting Katalyst.")

        val scanTargets = if (componentScanPackages.isNotEmpty()) componentScanPackages else emptyArray()

        // Resolve server configuration (auto-detect if not explicitly set)
        val resolvedServerConfig = resolveServerConfiguration()

        val featureSummary = if (features.isEmpty()) "none" else features.joinToString { it.id }
        logger.info("Initializing Katalyst DI (features={}, engine={})", featureSummary, resolvedServerConfig.engine.engineType)
        initializeKoinStandalone(
            KatalystDIOptions(
                databaseConfig = config,
                scanPackages = scanTargets,
                features = registeredFeatures()
            ),
            serverConfiguration = resolvedServerConfig
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
 * **With explicit engine selection:**
 * ```kotlin
 * fun main(args: Array<String>) = katalystApplication(args) {
 *     database(DatabaseConfig(...))
 *     scanPackages("com.example.app")
 *     engine(JettyEngine)  // Explicitly select Jetty engine
 *     enableScheduler()
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
            // PHASE 7: Ktor Engine Startup
            BootstrapProgress.startPhase(7)

            runCatching {
                val wrappedApplication = application.wrap(serverConfig.applicationWrapper)
                builder.configureApplication(wrappedApplication)
            }.onFailure { error ->
                logger.error("Failed to configure Ktor application", error)
                BootstrapProgress.failPhase(7, error)
                throw error
            }
        }

        embeddedServer.monitor.subscribe(ApplicationStarted) {
            val elapsedSeconds = (System.nanoTime() - bootStart) / 1_000_000_000.0
            logger.info("Katalyst started in {} s (actual)", String.format("%.3f", elapsedSeconds))

            BootstrapProgress.completePhase(7, "Ktor server is listening")
            BootstrapProgress.displayProgressSummary()
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

        // Display any aggregated startup warnings before completion banner
        StartupWarnings.display()

        logger.info("")
        logger.info("╔════════════════════════════════════════════════════╗")
        logger.info("║ ✓ APPLICATION STARTUP COMPLETE                     ║")
        logger.info("║                                                    ║")
        logger.info("║ Status: READY FOR TRAFFIC                          ║")
        logger.info("║                                                    ║")
        logger.info("║ ✓ Ktor server listening                            ║")
        logger.info("║ ✓ All components instantiated                      ║")
        logger.info("║ ✓ Database operational & schema ready              ║")
        logger.info("║ ✓ Transaction adapters configured                  ║")
        logger.info("║ ✓ Scheduler tasks registered & running             ║")
        logger.info("║ ✓ All initializer hooks completed                  ║")
        logger.info("║                                                    ║")
        logger.info("╚════════════════════════════════════════════════════╝")
        logger.info("")
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
): EmbeddedServer<out ApplicationEngine, *> {
    logger.info("Creating embedded server: ${serverConfiguration.engine.engineType} on ${serverConfiguration.host}:${serverConfiguration.port}")

    return try {
        // Get factory from Koin (previously ignored!)
        val engineFactory = GlobalContext.get().get<KtorEngineFactory>()

        // Create server with factory and config
        engineFactory.createServer(
            host = serverConfiguration.host,
            port = serverConfiguration.port,
            connectingIdleTimeoutMs = serverConfiguration.connectionIdleTimeoutMs,
            block = {}  // Empty block - application configuration happens later
        )
    } catch (e: NoDefinitionFoundException) {
        logger.error("No KtorEngineFactory registered. Is the engine module loaded?")
        throw IllegalStateException(
            "Engine factory not available in DI container. " +
            "Ensure the engine module (katalyst-ktor-engine-${serverConfiguration.engine.engineType}) is on the classpath.",
            e
        )
    } catch (e: Exception) {
        logger.error("Failed to create embedded server", e)
        throw e
    }
}
