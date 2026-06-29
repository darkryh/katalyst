package io.github.darkryh.katalyst.di

import io.github.darkryh.katalyst.core.component.Component
import io.github.darkryh.katalyst.config.DatabaseConfig
import io.github.darkryh.katalyst.core.config.ConfigProvider
import io.github.darkryh.katalyst.core.di.KatalystContainerProvider
import io.github.darkryh.katalyst.core.di.getAll
import io.github.darkryh.katalyst.core.dsl.KatalystDslMarker
import io.github.darkryh.katalyst.di.config.BootstrapArgs
import io.github.darkryh.katalyst.di.config.BootstrapArgsHolder
import io.github.darkryh.katalyst.di.config.DatabaseConfigurationBuilder
import io.github.darkryh.katalyst.di.config.KatalystDIOptions
import io.github.darkryh.katalyst.di.config.KatalystServerEngine
import io.github.darkryh.katalyst.di.config.ServerConfiguration
import io.github.darkryh.katalyst.di.config.ServerDeploymentConfiguration
import io.github.darkryh.katalyst.di.config.ServerConfigurationResolver
import io.github.darkryh.katalyst.di.config.SchemaManagementBuilder
import io.github.darkryh.katalyst.di.config.SchemaManagementOptions
import io.github.darkryh.katalyst.di.config.initializeKatalystStandalone
import io.github.darkryh.katalyst.di.config.runRuntimeReadyInitializers
import io.github.darkryh.katalyst.di.config.stopKatalystStandalone
import io.github.darkryh.katalyst.di.config.wrap
import io.github.darkryh.katalyst.di.exception.KatalystDIException
import io.github.darkryh.katalyst.di.internal.KtorModuleRegistry
import io.github.darkryh.katalyst.ktor.KtorModule
import io.github.darkryh.katalyst.di.feature.KatalystBeanEngine
import io.github.darkryh.katalyst.di.feature.KatalystFeature
import io.github.darkryh.katalyst.di.lifecycle.BootstrapLifecycle
import io.github.darkryh.katalyst.di.lifecycle.StartupWarnings
import io.github.darkryh.katalyst.di.lifecycle.BootstrapProgress
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStarting
import io.ktor.server.application.ApplicationStopping
import io.ktor.server.application.ServerReady
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import kotlinx.coroutines.runBlocking
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
 *     enableYamlConfiguration()
 *     scanPackages("com.example.app")
 * }
 * ```
 *
 * The `katalystApplication` block automatically:
 * 1. Initializes the installed bean container with all Katalyst modules
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
 * `enableX()` extension functions to the `features { ... }` scope. Simply add the
 * dependency for the feature you need and call the extension inside that block.
 */
@KatalystDslMarker
class KatalystApplicationBuilder(
    bootstrapArgs: BootstrapArgs = BootstrapArgs.EMPTY
) {
    private val logger = LoggerFactory.getLogger("KatalystApplication")
    private val serverConfigurationResolver = ServerConfigurationResolver(
        bootstrapArgs = bootstrapArgs,
        logger = logger,
        configurationSource = { configurationSource },
    )

    private var databaseConfig: DatabaseConfig? = null
    private var beanEngine: KatalystBeanEngine? = null
    private var configurationSource: ConfigProvider? = null
    private val features: MutableList<KatalystFeature> = mutableListOf()
    private var componentScanPackages: Array<String> = emptyArray()
    private var selectedEngine: EmbeddedServer<ApplicationEngine, ApplicationEngine.Configuration>? = null
    private var cachedDeploymentConfiguration: ServerDeploymentConfiguration? = null
    private var schemaManagement: SchemaManagementOptions = SchemaManagementOptions()

    /**
     * Provide the database configuration used to bootstrap core persistence infrastructure.
     */
    fun database(config: DatabaseConfig): KatalystApplicationBuilder {
        logger.debug("Database configuration supplied for Katalyst DI")

        this.databaseConfig = config

        return this
    }

    /**
     * Configure the database through the application DSL.
     *
     * Call `fromConfiguration()` inside this block to read standard `database.*`
     * keys from the installed [ConfigProvider], then override any Hikari/Exposed
     * values explicitly in code.
     */
    fun database(configure: DatabaseConfigurationBuilder.() -> Unit): KatalystApplicationBuilder {
        databaseConfig = DatabaseConfigurationBuilder(configurationSource)
            .apply(configure)
            .build()
            .also { config ->
                logger.debug("Database configuration supplied through DSL: url={}, driver={}", config.url, config.driver)
            }
        return this
    }

    /**
     * Provide the configuration source used during bootstrap and by configuration-backed features.
     *
     * Katalyst does not auto-select a YAML/properties implementation from the classpath. Applications
     * must explicitly choose one, for example through `enableYamlConfiguration()` from
     * `katalyst-config-yaml`, or by passing a custom source here.
     */
    fun configuration(source: ConfigProvider): KatalystApplicationBuilder {
        logger.debug("Configuration source supplied: {}", source::class.qualifiedName)
        this.configurationSource = source
        return this
    }

    /**
     * Select the bean/injection engine used by Katalyst bootstrap.
     *
     * Katalyst does not auto-select an adapter from the classpath. Applications must
     * explicitly install one so the selected dependency injection backend is obvious
     * and future adapters can coexist safely.
     */
    fun beanEngine(engine: KatalystBeanEngine): KatalystApplicationBuilder {
        logger.debug("Bean engine selected: {}", engine.id)
        this.beanEngine = engine
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
     * Configure how Katalyst handles discovered database tables during boot.
     *
     * Reflection-based table discovery still happens through [scanPackages].
     * This block controls whether boot creates missing tables, validates the
     * schema, or leaves schema management entirely to migrations/operations.
     */
    fun schema(configure: SchemaManagementBuilder.() -> Unit): KatalystApplicationBuilder {
        schemaManagement = SchemaManagementBuilder().apply(configure).build()
        return this
    }

    /**
     * Configure optional Katalyst features such as scheduler, events, migrations,
     * WebSockets, and server deployment configuration.
     */
    fun features(configure: KatalystFeaturesBuilder.() -> Unit): KatalystApplicationBuilder {
        KatalystFeaturesBuilder(this).apply(configure)
        return this
    }

    init {
        // Always include server configuration feature (part of DI module)
        loadOptionalFeature("io.github.darkryh.katalyst.di.feature.ServerConfigurationFeature")
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
     *     enableYamlConfiguration()
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
    fun engine(engine: EmbeddedServer<ApplicationEngine, ApplicationEngine.Configuration>): KatalystApplicationBuilder {
        this.selectedEngine = engine
        return this
    }

    /**
     * Explicitly select the Ktor engine provider for this application.
     */
    fun engine(engine: KatalystServerEngine): KatalystApplicationBuilder =
        engine(engine.build())

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
     * For custom YAML loading behavior, call enableServerTuning() in the feature scope:
     * ```kotlin
     * fun main() = katalystApplication {
     *     engine(NettyEngine)
     *     database(...)
     *     scanPackages(...)
     *     features {
     *         enableServerTuning()  // Customize YAML loading
     *     }
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
            logger.debug("Server bound to {}:{}", it.deployment.host, it.deployment.port)
        }
    }

    private fun resolveDatabaseConfigOrThrow(): DatabaseConfig {
        databaseConfig?.let { return it }

        throw IllegalStateException(
            "Database configuration must be supplied before starting Katalyst. " +
                "Use database { fromConfiguration() } after enableYamlConfiguration()/configuration(...), " +
                "or use database(DatabaseConfig(...)) for fully programmatic configuration."
        )
    }

    private fun resolveBeanEngineOrThrow(): KatalystBeanEngine =
        beanEngine ?: throw IllegalStateException(
            "Bean engine must be explicitly selected before starting Katalyst. " +
                "Call beanEngine(...) in the katalystApplication block. " +
                "For Koin, add `io.github.darkryh.katalyst:katalyst-koin-bean` and call `beanEngine(KoinBeanEngine)`."
        )

    /**
     * Initialize DI with all configured modules.
     */
    internal fun initializeDI() {
        val config = resolveDatabaseConfigOrThrow()

        val scanTargets = if (componentScanPackages.isNotEmpty()) componentScanPackages else emptyArray()

        // Resolve server configuration (auto-detect if not explicitly set)
        val resolvedServerConfig = resolveServerConfiguration()
        val selectedBeanEngine = resolveBeanEngineOrThrow()

        val featureSummary = if (features.isEmpty()) "none" else features.joinToString { it.id }

        logger.info("Initializing Katalyst DI (features={})", featureSummary)

        initializeKatalystStandalone(
            KatalystDIOptions(
                databaseConfig = config,
                beanEngine = selectedBeanEngine,
                scanPackages = scanTargets,
                features = registeredFeatures(),
                schemaManagement = schemaManagement,
            ),
            serverConfiguration = resolvedServerConfig,
            activateRuntimeReadyInitializers = false
        )

        logger.info("Katalyst DI initialized successfully")
    }

    /**
     * Configure the Ktor application with DI and all modules.
     */
    internal fun configureApplication(application: Application) {
        logger.info("Configuring Ktor application with Katalyst modules")

        val container = KatalystContainerProvider.current()

        runCatching { container.getAll<Component>().size }
            .onSuccess { size -> logger.debug("Auto-discovered components registered: {}", size) }
            .onFailure { error -> logger.warn("Unable to inspect auto-discovered components", error) }

        // IMPORTANT: Keep all discovered route functions (RouteFunctionModule instances are unique by identity)
        // Only deduplicate actual KtorModule implementations to avoid installing the same class twice
        val registryModules = KtorModuleRegistry.consume()
        val containerModules = container.getAll<KtorModule>()
            .distinctBy { it::class }  // Deduplicate KtorModule implementations only
        val ktorModules = registryModules + containerModules

        logger.info("Discovered {} Ktor module(s) for installation", ktorModules.size)
        val routeFunctionModuleCount = ktorModules.count { it is io.github.darkryh.katalyst.di.internal.RouteModuleMarker }
        val frameworkModuleCount = ktorModules.size - routeFunctionModuleCount
        logger.info(
            "Ktor module install plan: {} framework module(s), {} route-function module(s)",
            frameworkModuleCount,
            routeFunctionModuleCount
        )

        ktorModules
            .sortedBy { it.order }
            .forEach { module ->
                if (module is io.github.darkryh.katalyst.di.internal.RouteModuleMarker) {
                    logger.debug(
                        "Installing route-function module [{}] order={}",
                        module::class.qualifiedName,
                        module.order
                    )
                } else {
                    logger.info("Installing Ktor module {}", module::class.qualifiedName)
                }
                module.install(application)
            }

        logger.info("Ktor application configured successfully")
    }
}

/**
 * DSL receiver for optional Katalyst feature toggles.
 */
@KatalystDslMarker
class KatalystFeaturesBuilder internal constructor(
    private val application: KatalystApplicationBuilder,
) {
    /**
     * Register an optional feature with the enclosing application builder.
     */
    fun feature(feature: KatalystFeature): KatalystFeaturesBuilder {
        application.feature(feature)
        return this
    }

    /**
     * Install the configuration source on the enclosing application builder.
     *
     * Exposed here so configuration-backed feature toggles (e.g.
     * `enableYamlConfiguration()` from `katalyst-config-yaml`) can live inside the
     * `features { }` block. The source is recorded synchronously while the block runs,
     * which is before DI initialization and server-configuration resolution, so Phase-0
     * ordering is preserved.
     */
    fun configuration(source: ConfigProvider): KatalystFeaturesBuilder {
        application.configuration(source)
        return this
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
 *     enableYamlConfiguration()
 *     scanPackages("com.example.app")
 * }
 * ```
 *
 * **With explicit engine selection:**
 * ```kotlin
 * fun main(args: Array<String>) = katalystApplication(args) {
 *     enableYamlConfiguration()
 *     scanPackages("com.example.app")
 *     engine(JettyEngine)  // Explicitly select Jetty engine
 *     features {
 *         enableScheduler()
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

    val bootstrapArgs = BootstrapArgs.parse(args).also { it.applyProfileOverride() }
    BootstrapArgsHolder.set(bootstrapArgs)
    val builder = KatalystApplicationBuilder(bootstrapArgs)

    try {
        logger.info("Starting Katalyst application")

        runBlocking {
            builder.block()
        }

        builder.initializeDI()
        BootstrapProgress.displayProgressSummary(includePending = false)
        StartupWarnings.display()
        val serverConfig = builder.resolveServerConfiguration()
        val embeddedServer = serverConfig.engine ?: throw IllegalStateException("There is not specified embeddedServer")

        embeddedServer.monitor.subscribe(ApplicationStarting) { application ->
            // PHASE 6: Ktor Engine Startup
            BootstrapProgress.startLifecycle(BootstrapLifecycle.KTOR_ENGINE_STARTUP)
            logger.info(
                "LIFECYCLE_RUNTIME_READY_INITIALIZERS pending: will execute on ServerReady"
            )

            runCatching {
                val wrappedApplication = application.wrap(serverConfig.applicationWrapper)
                builder.configureApplication(wrappedApplication)
            }.onFailure { error ->
                logger.error("Failed to configure Ktor application", error)
                BootstrapProgress.failLifecycle(BootstrapLifecycle.KTOR_ENGINE_STARTUP, error)
                throw error
            }
        }

        embeddedServer.monitor.subscribe(ServerReady) {
            val elapsedSeconds = (System.nanoTime() - bootStart) / 1_000_000_000.0
            logger.info("Katalyst started in {} s (actual)", String.format("%.3f", elapsedSeconds))

            BootstrapProgress.completeLifecycle(BootstrapLifecycle.KTOR_ENGINE_STARTUP, "Ktor server is listening")

            val runtimeReadyResult = runCatching {
                runRuntimeReadyInitializers()
            }
            runtimeReadyResult.onFailure { error ->
                logger.error("Runtime-ready initialization failed after ServerReady", error)
                runCatching { embeddedServer.stop(500, 2_000) }
                    .onFailure { stopError -> logger.warn("Failed to stop server after runtime-ready failure", stopError) }
                throw error
            }

            logger.info(
                "Application startup complete: ready for traffic (server + runtime-ready lifecycle initialized)"
            )
        }

        embeddedServer.monitor.subscribe(ApplicationStopping) {
            runCatching { stopKatalystStandalone() }
                .onFailure { error -> logger.warn("Error while stopping Katalyst DI", error) }
        }

        serverConfig.serverWrapper?.let { wrapper ->
            runCatching { wrapper(embeddedServer.engine) }
                .onFailure { error ->
                    logger.warn("Server wrapper execution failed: {}", error.message)
                    logger.debug("Full server wrapper error", error)
                }
        }

        embeddedServer.start(wait = true)

    } catch (e: KatalystDIException) {
        logger.debug("Katalyst DI startup failed", e)
        throw e
    } catch (e: Exception) {
        logger.error("Failed to start Katalyst application", e)
        throw e
    } finally {
        stopKatalystStandalone()
        BootstrapArgsHolder.clear()
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
