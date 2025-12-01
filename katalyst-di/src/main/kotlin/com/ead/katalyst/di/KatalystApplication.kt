@file:Suppress("deprecation")

package com.ead.katalyst.di

import com.ead.katalyst.core.component.Component
import com.ead.katalyst.config.DatabaseConfig
import com.ead.katalyst.core.dsl.KatalystDslMarker
import com.ead.katalyst.di.config.BootstrapArgs
import com.ead.katalyst.di.config.KatalystDIOptions
import com.ead.katalyst.di.config.ServerConfiguration
import com.ead.katalyst.di.config.ServerDeploymentConfiguration
import com.ead.katalyst.di.config.ServerConfigurationResolver
import com.ead.katalyst.di.config.initializeKoinStandalone
import com.ead.katalyst.di.config.stopKoinStandalone
import com.ead.katalyst.di.config.wrap
import com.ead.katalyst.di.internal.KtorModuleRegistry
import com.ead.katalyst.ktor.KtorModule
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
class KatalystApplicationBuilder(
    bootstrapArgs: BootstrapArgs = BootstrapArgs.EMPTY
) {
    private val logger = LoggerFactory.getLogger("KatalystApplication")
    private val serverConfigurationResolver = ServerConfigurationResolver(bootstrapArgs, logger)

    private var databaseConfig: DatabaseConfig? = null
    private val features: MutableList<KatalystFeature> = mutableListOf()
    private var componentScanPackages: Array<String> = emptyArray()
    private var selectedEngine: EmbeddedServer<ApplicationEngine, ApplicationEngine.Configuration>? = null
    private var cachedDeploymentConfiguration: ServerDeploymentConfiguration? = null

    /**
     * Provide the database configuration used to bootstrap core persistence infrastructure.
     */
    @KatalystDslMarker
    fun database(config: DatabaseConfig): KatalystApplicationBuilder {
        logger.debug("Database configuration supplied for Katalyst DI")

        this.databaseConfig = config

        return this
    }

    /**
     * Configure packages that should be scanned for services, repositories, and validators.
     */
    @KatalystDslMarker
    fun scanPackages(vararg packages: String): KatalystApplicationBuilder {
        logger.debug("Setting component scan packages: {}", packages.joinToString(", "))

        @Suppress("UNCHECKED_CAST")
        val copy = packages.clone() as Array<String>

        this.componentScanPackages = copy

        return this
    }

    init {
        // Load config-provider feature if available on classpath (Yaml or other providers)
        loadOptionalFeature("com.ead.katalyst.config.yaml.ConfigProviderFeature")
            ?.also { features += it; logger.debug("Registered optional feature: {}", it.id) }

        // Always include server configuration feature (part of DI module)
        loadOptionalFeature("com.ead.katalyst.di.feature.ServerConfigurationFeature")
            ?.also { features += it; logger.debug("Registered feature: {}", it.id) }
    }

    private fun loadOptionalFeature(className: String): KatalystFeature? =
        runCatching {
            val clazz = Class.forName(className)
            val instance = clazz.kotlin.objectInstance
                ?: runCatching { clazz.getField("INSTANCE").get(null) }.getOrNull()
                ?: clazz.getDeclaredConstructor().newInstance()
            instance as? KatalystFeature
        }.onFailure { error ->
            logger.debug("Optional feature {} not loaded: {}", className, error.message)
        }.getOrNull()

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
    @KatalystDslMarker
    fun engine(engine: EmbeddedServer<ApplicationEngine, ApplicationEngine.Configuration>): KatalystApplicationBuilder {
        this.selectedEngine = engine
        return this
    }

    /**
     * Registers an optional feature (scheduler, events, websockets, etc.).
     */
    fun feature(feature: KatalystFeature): KatalystApplicationBuilder {
        if (features.any { it.id == feature.id }) {
            logger.debug("Feature {} already registered - skipping duplicate", feature.id)
            return this
        }
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

        val deployment = cachedDeploymentConfiguration ?: serverConfigurationResolver
            .resolveDeployment()
            .also { cachedDeploymentConfiguration = it }

        return ServerConfiguration(
            engine = engine,
            deployment = deployment
        ).also {
            logger.debug("Server bound to {}:{}", it.host, it.port)
        }
    }

    private fun resolveDatabaseConfigOrThrow(): DatabaseConfig {
        databaseConfig?.let { return it }

        tryAutoLoadDatabaseConfig()?.let { config ->
            databaseConfig = config
            return config
        }

        throw IllegalStateException("Database configuration must be supplied before starting Katalyst.")
    }

    private fun tryAutoLoadDatabaseConfig(): DatabaseConfig? {
        if (componentScanPackages.isEmpty()) {
            logger.debug("Skipping automatic database config loading (no scan packages configured)")
            return null
        }

        val provider = serverConfigurationResolver.bootstrapConfigProvider()
        if (provider == null) {
            logger.debug("ConfigProvider not available for automatic database loading")
            return null
        }

        return runCatching {
            val metadataClass = Class.forName("com.ead.katalyst.config.provider.ConfigMetadata")
            val discoverMethod = metadataClass.getMethod("discoverLoaders", Array<String>::class.java)
            @Suppress("UNCHECKED_CAST")
            val loaders = discoverMethod.invoke(null, componentScanPackages) as List<*>

            if (loaders.isEmpty()) {
                logger.debug("No ServiceConfigLoader implementations discovered for automatic database loading")
                return null
            }

            val helperClass = Class.forName("com.ead.katalyst.config.provider.ConfigBootstrapHelper")
            val helperInstance = helperClass.kotlin.objectInstance ?: helperClass.getDeclaredConstructor().newInstance()
            val loadMethod = helperClass.getMethod(
                "loadServiceConfig",
                com.ead.katalyst.core.config.ConfigProvider::class.java,
                Class.forName("com.ead.katalyst.config.provider.ServiceConfigLoader")
            )

            loaders.forEach { loader ->
                val loaded = loadMethod.invoke(helperInstance, provider, loader)
                if (loaded is DatabaseConfig) {
                    logger.info("Automatically loaded DatabaseConfig via {}", loader!!::class.java.simpleName)
                    return loaded
                }
            }

            null
        }.onFailure { error ->
            logger.debug("Automatic database configuration loading failed: {}", error.message)
            logger.trace("Full error while auto-loading database configuration", error)
        }.getOrNull()
    }

    private fun validateServiceConfigs() {
        val provider = serverConfigurationResolver.bootstrapConfigProvider() ?: return
        if (componentScanPackages.isEmpty()) return

        runCatching {
            val metadataClass = Class.forName("com.ead.katalyst.config.provider.ConfigMetadata")
            val discoverMethod = metadataClass.getMethod("discoverLoaders", Array<String>::class.java)
            val validateMethod = metadataClass.getMethod(
                "validateLoaders",
                com.ead.katalyst.core.config.ConfigProvider::class.java,
                List::class.java
            )

            @Suppress("UNCHECKED_CAST")
            val loaders = discoverMethod.invoke(null, componentScanPackages) as List<*>
            if (loaders.isEmpty()) {
                logger.debug("No ServiceConfigLoader implementations discovered for validation")
                return
            }

            logger.info("Validating {} ServiceConfigLoader implementation(s)", loaders.size)
            validateMethod.invoke(null, provider, loaders)
        }.onFailure { error ->
            logger.debug("Service config validation skipped: {}", error.message)
        }
    }

    /**
     * Initialize DI with all configured modules.
     */
    internal fun initializeDI() {
        val config = resolveDatabaseConfigOrThrow()

        val scanTargets = if (componentScanPackages.isNotEmpty()) componentScanPackages else emptyArray()

        // Validate all service config loaders (beyond DB) when a provider is available
        validateServiceConfigs()

        // Resolve server configuration (auto-detect if not explicitly set)
        val resolvedServerConfig = resolveServerConfiguration()

        val featureSummary = if (features.isEmpty()) "none" else features.joinToString { it.id }

        logger.info("Initializing Katalyst DI (features={})", featureSummary)

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
@KatalystDslMarker
fun katalystApplication(
    args: Array<String> = emptyArray(),
    block: suspend KatalystApplicationBuilder.() -> Unit
) {
    val logger = LoggerFactory.getLogger("katalystApplication")
    val bootStart = System.nanoTime()

    printKatalystBanner()

    val bootstrapArgs = BootstrapArgs.parse(args).also { it.applyProfileOverride() }
    val builder = KatalystApplicationBuilder(bootstrapArgs)

    try {
        logger.info("Starting Katalyst application")

        runBlocking {
            builder.block()
        }

        builder.initializeDI()
        val serverConfig = builder.resolveServerConfiguration()
        val embeddedServer = serverConfig.engine ?: throw IllegalStateException("There is not specified embeddedServer")

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
