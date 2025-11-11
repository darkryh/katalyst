package com.ead.katalyst.di.config

import com.ead.katalyst.config.DatabaseConfig
import com.ead.katalyst.database.DatabaseFactory
import com.ead.katalyst.core.transaction.DatabaseTransactionManager
import com.ead.katalyst.di.internal.AutoBindingRegistrar
import com.ead.katalyst.di.internal.EngineRegistrar
import com.ead.katalyst.core.persistence.Table
import com.ead.katalyst.database.adapter.PersistenceTransactionAdapter
import org.jetbrains.exposed.sql.SchemaUtils
import com.ead.katalyst.di.module.coreDIModule
import com.ead.katalyst.di.feature.KatalystFeature
import com.ead.katalyst.di.module.scannerDIModule
import com.ead.katalyst.events.bus.ApplicationEventBus
import com.ead.katalyst.events.bus.adapter.EventsTransactionAdapter
import com.ead.katalyst.di.lifecycle.BootstrapProgress
import com.ead.katalyst.di.lifecycle.StartupWarnings
import com.ead.katalyst.di.lifecycle.StartupWarningsAggregator
import io.ktor.server.application.Application
import io.ktor.server.application.install
import kotlinx.coroutines.runBlocking
import kotlin.reflect.KClass
import org.koin.core.Koin
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.module.Module
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin
import org.slf4j.LoggerFactory

/**
 * Complete DI Configuration for the Katalyst Application.
 *
 * Orchestrates the core modules (core + scanner) and provides
 * convenient initialization methods. Optional subsystems (scheduler, events,
 * websockets, etc.) plug in via [KatalystFeature] implementations.
 */

/**
 * Logger for DI configuration logging.
 */
private val logger = LoggerFactory.getLogger("DIConfiguration")

/**
 * Configuration options for Katalyst dependency injection.
 *
 * Encapsulates all settings needed to bootstrap the Katalyst DI system.
 *
 * **Properties:**
 * - [databaseConfig]: Database connection configuration
 * - [scanPackages]: Package names to scan for auto-discovery
 * - [features]: Optional Katalyst feature set (scheduler, events, websockets, etc.)
 *
 * @property databaseConfig Database connection settings (required)
 * @property scanPackages Array of package names to scan for components (default: empty)
 * @property features Optional feature set applied during bootstrap
 */
data class KatalystDIOptions(
    val databaseConfig: DatabaseConfig,
    val scanPackages: Array<String> = emptyArray(),
    val features: List<KatalystFeature> = emptyList()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as KatalystDIOptions

        if (databaseConfig != other.databaseConfig) return false
        if (!scanPackages.contentEquals(other.scanPackages)) return false
        if (features != other.features) return false

        return true
    }

    override fun hashCode(): Int {
        var result = databaseConfig.hashCode()
        result = 31 * result + scanPackages.contentHashCode()
        result = 31 * result + features.hashCode()
        return result
    }
}

/**
 * Bootstraps Katalyst dependency injection with automatic component discovery.
 *
 * This is the core initialization function that:
 * 1. Loads all Katalyst modules (core, scanner, scheduler if enabled)
 * 2. Starts or augments the global Koin context
 * 3. Performs automatic discovery and registration of components
 * 4. Discovers and registers database tables
 * 5. Initializes the database schema with discovered tables
 *
 * **Component Discovery:**
 * Automatically discovers and registers:
 * - Services (Service implementations)
 * - Repositories (Repository implementations)
 * - Event handlers (EventHandler implementations)
 * - Ktor modules (KtorModule implementations)
 * - Database tables (Table implementations)
 * - Route functions (extension functions using Katalyst DSL)
 *
 * **Usage Note:**
 * This function can be called multiple times safely. If Koin is already initialized,
 * it augments the existing context instead of creating a new one.
 *
 * @param databaseConfig Database connection configuration
 * @param scanPackages Package names to scan for components
 * @param features Optional feature set (scheduler, events, websockets, etc.)
 * @return The active Koin instance with all modules loaded
 */
fun bootstrapKatalystDI(
    databaseConfig: DatabaseConfig,
    scanPackages: Array<String> = emptyArray(),
    features: List<KatalystFeature> = emptyList(),
    serverConfig: ServerConfiguration = ServerConfiguration()
): Koin {
    val logger = LoggerFactory.getLogger("bootstrapKatalystDI")

    val modules = mutableListOf(
        coreDIModule(databaseConfig),
        scannerDIModule()
    )

    // Discover and register the selected engine
    logger.info("▶ Phase 2: Discovering and registering engine")
    val engineRegistrar = EngineRegistrar(serverConfig, logger)
    engineRegistrar.registerEngineModules(modules)

    features.forEach { feature ->
        logger.debug("Including feature '{}' modules", feature.id)
        modules += feature.provideModules()
    }

    val koin = currentKoinOrNull()?.also {
        logger.info("Loading Katalyst modules into existing Koin context")
        it.loadModules(modules, createEagerInstances = true)
    } ?: run {
        logger.info("Starting new Koin context for Katalyst modules")
        startKoin {
            modules(modules)
        }.koin
    }

    // Register components including tables
    // PHASE 3: Component Discovery & Registration
    BootstrapProgress.startPhase(3)
    try {
        logger.info("Starting AutoBindingRegistrar to discover components...")
        AutoBindingRegistrar(koin, scanPackages).registerAll()
        logger.info("AutoBindingRegistrar completed")
        BootstrapProgress.completePhase(3, "Discovered repositories, services, components, and validators")
    } catch (e: Exception) {
        BootstrapProgress.failPhase(3, e)
        throw e
    }

    features.forEach { feature ->
        logger.debug("Executing onKoinReady hook for feature '{}'", feature.id)
        feature.onKoinReady(koin)
    }

    // PHASE 4: Database Schema Initialization & Table Creation
    BootstrapProgress.startPhase(4)
    try {
        logger.debug("Attempting to retrieve discovered Table instances from Koin...")
        // Note: Table interface now has two type parameters <Id, Entity>
        // Since we can't easily express Table<*, *> in Koin reified generics,
        // we cast the result to List<Any> and access as objects
        val discoveredTables = koin.getAll<Any>().filterIsInstance<org.jetbrains.exposed.sql.Table>()
        logger.info("Discovered {} table(s) for initialization", discoveredTables.size)

        if (discoveredTables.isNotEmpty()) {
            // Cast discovered tables (which implement both our Table marker and org.jetbrains.exposed.sql.Table)
            @Suppress("UNCHECKED_CAST")
            val exposedTables = discoveredTables.mapNotNull { it as? org.jetbrains.exposed.sql.Table }

            logger.info("Found {} Exposed table(s) for schema creation", exposedTables.size)

            if (exposedTables.isNotEmpty()) {
                // Create new DatabaseFactory with discovered tables
                val databaseFactory = DatabaseFactory.create(databaseConfig, exposedTables)

                // Register it in Koin, replacing the one created by coreDIModule
                val databaseModule = module {
                    single<DatabaseFactory> { databaseFactory }
                    single<DatabaseTransactionManager> {
                        logger.debug("Creating DatabaseTransactionManager with discovered tables")
                        DatabaseTransactionManager(databaseFactory.database)
                    }
                }
                koin.loadModules(listOf(databaseModule), createEagerInstances = true)
                logger.info("Registered DatabaseFactory with {} Exposed table(s)", exposedTables.size)

                // Auto-create missing tables in schema using Exposed's SchemaUtils
                logger.info("Ensuring database schema...")
                val txManager = koin.get<DatabaseTransactionManager>()
                runBlocking {
                    txManager.transaction {
                        SchemaUtils.createMissingTablesAndColumns(*exposedTables.toTypedArray())
                    }
                }
                logger.info("  ✓ Database schema ensured - all tables created if needed")
            }
        }
        BootstrapProgress.completePhase(4, "Database schema initialized with ${discoveredTables.size} tables")
    } catch (e: Exception) {
        logger.warn("Error discovering tables or creating DatabaseFactory: {}", e.message)
        logger.debug("Full error during table discovery", e)
        BootstrapProgress.failPhase(4, e)
        throw e
    }

    // PHASE 5: Transaction Adapter Registration
    BootstrapProgress.startPhase(5)
    try {
        logger.info("Registering transaction adapters...")
        val transactionManager = koin.get<DatabaseTransactionManager>()

        var adaptersRegistered = 0

        // Register Persistence adapter (always available)
        try {
            val persistenceAdapter = PersistenceTransactionAdapter()
            transactionManager.addAdapter(persistenceAdapter)
            logger.info("Registered Persistence transaction adapter")
            adaptersRegistered++
        } catch (e: Exception) {
            logger.warn("Failed to register Persistence adapter: {}", e.message)
        }

        // Register Events adapter if ApplicationEventBus is available
        try {
            val eventBus = koin.get<ApplicationEventBus>()
            val eventsAdapter = EventsTransactionAdapter(eventBus)
            transactionManager.addAdapter(eventsAdapter)
            logger.info("Registered Events transaction adapter")
            adaptersRegistered++
        } catch (e: Exception) {
            logger.debug("ApplicationEventBus not available, skipping Events adapter registration: {}", e.message)
            StartupWarnings.add(
                category = "Optional Adapters",
                message = "Events transaction adapter not available",
                severity = StartupWarningsAggregator.WarningSeverity.INFO,
                hint = "Add katalyst-events dependency to enable event-driven transactions"
            )
        }

        logger.info("Transaction adapter registration completed with {} adapter(s)", adaptersRegistered)
        BootstrapProgress.completePhase(5, "Registered $adaptersRegistered transaction adapter(s)")
    } catch (e: Exception) {
        logger.warn("Error registering transaction adapters: {}", e.message)
        BootstrapProgress.failPhase(5, e)
        throw e
    }

    // Execute application initialization lifecycle
    // This runs after all DI is complete and database is ready
    try {
        logger.info("Starting application initialization lifecycle...")
        val registry = com.ead.katalyst.di.lifecycle.InitializerRegistry(koin)

        // Must block - initialization must complete synchronously
        runBlocking {
            registry.invokeAll()
        }

        logger.info("Application initialization lifecycle completed")
    } catch (e: Exception) {
        logger.error("Fatal error during application initialization", e)
        throw e
    }

    return koin
}

/**
 * Initializes Koin DI for a standalone application (non-Ktor).
 *
 * This starts Koin with all modules and should be called during application startup.
 *
 * **Usage:**
 * ```kotlin
 * fun main() {
 *     val options = KatalystDIOptions(
 *         databaseConfig = DatabaseConfig(...),
 *         scanPackages = arrayOf("com.example.app"),
 *         features = listOf(MyCustomFeature)
 *     )
 *     val serverConfig = ServerConfiguration(engineType = "netty", port = 9090)
 *     initializeKoinStandalone(options, serverConfig)
 *     // ... rest of application code
 *     stopKoinStandalone()
 * }
 * ```
 */
fun initializeKoinStandalone(
    options: KatalystDIOptions,
    serverConfiguration: ServerConfiguration = ServerConfiguration()
): Koin {
    logger.info("Initializing Koin DI for standalone application")
    logger.debug("Features enabled: {}", options.features.joinToString { it.id })

    return bootstrapKatalystDI(
        databaseConfig = options.databaseConfig,
        scanPackages = options.scanPackages,
        features = options.features,
        serverConfig = serverConfiguration
    ).also {
        logger.info("Koin initialization completed successfully")
    }
}

/**
 * Stops Koin for standalone applications.
 *
 * Should be called during application shutdown.
 *
 * **Usage:**
 * ```kotlin
 * fun main() {
 *     try {
 *         val options = KatalystDIOptions(DatabaseConfig(...))
 *         initializeKoinStandalone(options)
 *         // ... application code
 *     } finally {
 *         stopKoinStandalone()
 *     }
 * }
 * ```
 */
fun stopKoinStandalone() {
    logger.info("Stopping Koin DI")
    stopKoin()
    logger.info("Koin stopped successfully")
}


/**
 * Builder for custom DI configurations.
 *
 * Provides a fluent API for composing Koin modules with granular control.
 * This is useful when you need to customize which Katalyst modules are loaded
 * or when you want to add custom modules alongside Katalyst.
 *
 * **Usage Example:**
 * ```kotlin
 * val modules = DIConfigurationBuilder()
 *     .database(DatabaseConfig(...))
 *     .coreModules()
 *     .scannerModules()
 *     .customModules(myCustomModule)
 *     .build()
 * ```
 */
class DIConfigurationBuilder {
    private val modules = mutableListOf<Module>()
    private var databaseConfig: DatabaseConfig? = null

    /**
     * Sets the database configuration.
     *
     * This must be called before [coreModules] as it's required for database initialization.
     *
     * @param config Database connection settings
     * @return This builder for method chaining
     */
    fun database(config: DatabaseConfig): DIConfigurationBuilder {
        this.databaseConfig = config
        return this
    }

    /**
     * Includes core Katalyst modules.
     *
     * Adds the core DI module which provides:
     * - Database connection pooling (HikariCP)
     * - Transaction management (DatabaseTransactionManager)
     * - Foundation for service/repository registration
     *
     * **Prerequisites:** [database] must be called first
     *
     * @return This builder for method chaining
     * @throws IllegalStateException if database config is not set
     */
    fun coreModules(): DIConfigurationBuilder {
        val config = databaseConfig
            ?: throw IllegalStateException("Call database(...) before coreModules() to provide DatabaseConfig")
        logger.debug("Adding core modules")
        modules.add(coreDIModule(config))
        return this
    }

    /**
     * Includes scanner modules for component discovery.
     *
     * Adds the scanner module which enables:
     * - Classpath scanning for component types
     * - Reflection-based type discovery
     * - Generic type resolution utilities
     *
     * **Note:** This is typically needed for automatic component registration.
     *
     * @return This builder for method chaining
     */
    fun scannerModules(): DIConfigurationBuilder {
        logger.debug("Adding scanner modules")
        modules.add(scannerDIModule())
        return this
    }

    /**
     * Adds custom Koin modules to the configuration.
     *
     * Use this to include your own application-specific modules alongside
     * Katalyst framework modules.
     *
     * **Example:**
     * ```kotlin
     * val myModule = module {
     *     single { MyCustomService() }
     * }
     * builder.customModules(myModule)
     * ```
     *
     * @param customModules One or more custom Koin modules to include
     * @return This builder for method chaining
     */
    fun customModules(vararg customModules: Module): DIConfigurationBuilder {
        logger.debug("Adding {} custom module(s)", customModules.size)
        modules.addAll(customModules)
        return this
    }

    /**
     * Builds and returns the final list of modules.
     *
     * @return Immutable list of all configured Koin modules
     */
    fun build(): List<Module> = modules.toList()
}


/**
 * Safely retrieves the current Koin instance if one exists.
 *
 * @return The active Koin instance, or null if Koin is not initialized
 */
private fun currentKoinOrNull(): Koin? =
    runCatching { GlobalContext.get() }.getOrNull()
