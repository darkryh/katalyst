package io.github.darkryh.katalyst.di.config

import io.github.darkryh.katalyst.config.DatabaseConfig
import io.github.darkryh.katalyst.core.config.ConfigProvider
import io.github.darkryh.katalyst.core.di.KatalystContainer
import io.github.darkryh.katalyst.core.di.KatalystContainerProvider
import io.github.darkryh.katalyst.core.di.get
import io.github.darkryh.katalyst.core.di.getOrNull
import io.github.darkryh.katalyst.transactions.manager.DatabaseTransactionManager
import io.github.darkryh.katalyst.database.DatabaseFactory
import io.github.darkryh.katalyst.database.adapter.PersistenceTransactionAdapter
import io.github.darkryh.katalyst.di.exception.FatalDependencyValidationException
import io.github.darkryh.katalyst.di.feature.KatalystBeanContext
import io.github.darkryh.katalyst.di.feature.KatalystBeanEngine
import io.github.darkryh.katalyst.di.feature.KatalystBeanEngines
import io.github.darkryh.katalyst.di.feature.KatalystBeanModule
import io.github.darkryh.katalyst.di.feature.KatalystFeature
import io.github.darkryh.katalyst.di.feature.katalystBeanModule
import io.github.darkryh.katalyst.di.internal.ComponentRegistrationOrchestrator
import io.github.darkryh.katalyst.di.internal.TableRegistry
import io.github.darkryh.katalyst.di.lifecycle.BootstrapLifecycle
import io.github.darkryh.katalyst.di.lifecycle.BootstrapProgress
import io.github.darkryh.katalyst.di.lifecycle.StartupHookRunner
import io.github.darkryh.katalyst.di.lifecycle.ReadyHookRunner
import io.github.darkryh.katalyst.di.lifecycle.StartupWarnings
import io.github.darkryh.katalyst.di.lifecycle.StartupWarningsAggregator
import io.github.darkryh.katalyst.di.module.coreDIModule
import io.github.darkryh.katalyst.events.bus.ApplicationEventBus
import io.github.darkryh.katalyst.events.bus.adapter.EventsTransactionAdapter
import io.github.darkryh.katalyst.transactions.config.TransactionConfig
import io.github.darkryh.katalyst.transactions.config.TransactionIsolationLevel
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.core.Schema
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.migration.jdbc.MigrationUtils
import org.slf4j.LoggerFactory
import kotlin.time.DurationUnit
import kotlin.time.toDuration

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
private val shutdownLock = Any()

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
 * @property beanEngine Bean/injection engine selected explicitly by the application (required at bootstrap)
 * @property scanPackages Array of package names to scan for components (default: empty)
 * @property features Optional feature set applied during bootstrap
 */
data class KatalystDIOptions(
    val databaseConfig: DatabaseConfig,
    val beanEngine: KatalystBeanEngine? = null,
    val scanPackages: Array<String> = emptyArray(),
    val features: List<KatalystFeature> = emptyList(),
    val schemaManagement: SchemaManagementOptions = SchemaManagementOptions(),
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as KatalystDIOptions

        if (databaseConfig != other.databaseConfig) return false
        if (beanEngine != other.beanEngine) return false
        if (!scanPackages.contentEquals(other.scanPackages)) return false
        if (features != other.features) return false
        if (schemaManagement != other.schemaManagement) return false

        return true
    }

    override fun hashCode(): Int {
        var result = databaseConfig.hashCode()
        result = 31 * result + (beanEngine?.hashCode() ?: 0)
        result = 31 * result + scanPackages.contentHashCode()
        result = 31 * result + features.hashCode()
        result = 31 * result + schemaManagement.hashCode()
        return result
    }
}

/**
 * Bootstraps Katalyst dependency injection with automatic component discovery.
 *
 * This is the core initialization function that:
 * 1. Loads all Katalyst modules (core, scanner, scheduler if enabled)
 * 2. Starts or augments the installed bean container
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
 * This function can be called multiple times safely. If the installed bean
 * engine is already initialized, it augments the existing context instead of
 * creating a new one.
 *
 * @param databaseConfig Database connection configuration
 * @param scanPackages Package names to scan for components
 * @param features Optional feature set (scheduler, events, websockets, etc.)
 * @return The active Katalyst container facade with all modules loaded
 */
fun bootstrapKatalystContainer(
    databaseConfig: DatabaseConfig,
    scanPackages: Array<String> = emptyArray(),
    features: List<KatalystFeature> = emptyList(),
    serverConfig: ServerConfiguration,
    additionalModules: List<KatalystBeanModule> = emptyList(),
    allowOverrides: Boolean = false,
    schemaManagement: SchemaManagementOptions = SchemaManagementOptions(),
    beanEngine: KatalystBeanEngine? = null,
): KatalystContainer {
    val logger = LoggerFactory.getLogger("bootstrapKatalystContainer")
    val selectedBeanEngine = KatalystBeanEngines.activate(
        beanEngine ?: error(
            "No Katalyst bean engine was selected. Call beanEngine(...) in katalystApplication { } " +
                "before starting the server. For Koin, add `io.github.darkryh.katalyst:katalyst-koin-bean` " +
                "and call `beanEngine(KoinBeanEngine)`."
        )
    )

    val modules = mutableListOf(
        coreDIModule(databaseConfig)
    )


    // Register ServerConfiguration and engine as singletons for DI injection
    modules.add(
        katalystBeanModule {
            single { serverConfig }
        }
    )

    features.forEach { feature ->
        logger.debug("Including feature '{}' modules", feature.id)
        modules += feature.provideBeanModules()
    }

    modules += additionalModules

    BootstrapProgress.startLifecycle(BootstrapLifecycle.KOIN_DI_BOOTSTRAP)
    val container = try {
        selectedBeanEngine.currentOrNull()?.also {
            logger.info("Loading Katalyst modules into existing bean container")
            selectedBeanEngine.loadModules(modules, allowOverrides = allowOverrides)
        } ?: run {
            logger.info("Starting new {} bean container for Katalyst modules", selectedBeanEngine.id)
            selectedBeanEngine.start(modules, allowOverrides = allowOverrides)
        }
    } catch (e: Exception) {
        BootstrapProgress.failLifecycle(BootstrapLifecycle.KOIN_DI_BOOTSTRAP, e)
        throw e
    }
    BootstrapProgress.completeLifecycle(
        BootstrapLifecycle.KOIN_DI_BOOTSTRAP,
        "Bean container ready with ${modules.size} module(s)"
    )
    KatalystContainerProvider.set(container)

    // Register components including tables
    // PHASE 2: Component Discovery & Registration with Validation
    BootstrapProgress.startLifecycle(BootstrapLifecycle.COMPONENT_DISCOVERY_REGISTRATION)
    try {
        logger.info("Starting ComponentRegistrationOrchestrator with dependency validation...")
        val orchestrator = ComponentRegistrationOrchestrator(container, selectedBeanEngine, scanPackages)
        orchestrator.registerAllWithValidation()
        logger.info("ComponentRegistrationOrchestrator completed with full validation")
        BootstrapProgress.completeLifecycle(
            BootstrapLifecycle.COMPONENT_DISCOVERY_REGISTRATION,
            "Discovered repositories, services, components, and validators with dependency validation"
        )
    } catch (e: FatalDependencyValidationException) {
        logger.error(e.renderReport())
        BootstrapProgress.failLifecycle(BootstrapLifecycle.COMPONENT_DISCOVERY_REGISTRATION, e)
        throw e
    } catch (e: Exception) {
        logger.error("✗ Error during component registration: {}", e.message)
        BootstrapProgress.failLifecycle(BootstrapLifecycle.COMPONENT_DISCOVERY_REGISTRATION, e)
        throw e
    }

    val beanContext = KatalystBeanContext(KatalystContainerProvider.current())
    features.forEach { feature ->
        logger.debug("Executing onReady hook for feature '{}'", feature.id)
        feature.onReady(beanContext)
    }

    val phaseLoggingEnabled = resolveTransactionPhaseLoggingEnabled(container)
    val transactionDefaultsModule = katalystBeanModule {
        single<DatabaseTransactionManager> {
            val databaseFactory = get<DatabaseFactory>()
            DatabaseTransactionManager(
                database = databaseFactory.database,
                defaultTransactionConfig = TransactionConfig(
                    phaseLoggingEnabled = phaseLoggingEnabled
                )
            )
        }
    }
    selectedBeanEngine.loadModules(listOf(transactionDefaultsModule), allowOverrides = allowOverrides)

    // PHASE 3: Database Schema Initialization & Validation
    BootstrapProgress.startLifecycle(BootstrapLifecycle.DATABASE_SCHEMA_INITIALIZATION)
    try {
        logger.debug("Attempting to retrieve discovered Table instances from TableRegistry...")
        // Use TableRegistry instead of broad container lookup because:
        // - some container implementations do not reliably return dynamically registered singletons
        // - TableRegistry provides guaranteed access to all discovered tables from Phase 3
        val discoveredTables = TableRegistry.getAll()
        logger.info("Discovered {} table(s) for initialization", discoveredTables.size)

        if (schemaManagement.policy == SchemaPolicy.NONE) {
            logger.info("Schema management disabled - skipping {} discovered table(s)", discoveredTables.size)
        } else if (discoveredTables.isNotEmpty()) {
            logger.info(
                "Found {} table(s) for schema policy {}",
                discoveredTables.size,
                schemaManagement.policy,
            )

            // Tables from TableRegistry are already org.jetbrains.exposed.sql.Table instances
            val exposedTables = discoveredTables.toTypedArray()

            // Create new DatabaseFactory with discovered tables
            val databaseFactory = DatabaseFactory.create(databaseConfig)

            // Register it in the active container, replacing the one created by coreDIModule
            val databaseModule = katalystBeanModule {
                single<DatabaseFactory> { databaseFactory }
                single<DatabaseTransactionManager> {
                    logger.debug("Creating DatabaseTransactionManager with discovered tables")
                    DatabaseTransactionManager(
                        database = databaseFactory.database,
                        defaultTransactionConfig = TransactionConfig(
                            phaseLoggingEnabled = phaseLoggingEnabled
                        )
                    )
                }
            }
            selectedBeanEngine.loadModules(listOf(databaseModule), allowOverrides = true)
            logger.info("Registered DatabaseFactory with {} Exposed table(s)", exposedTables.size)

            logger.info("Applying schema policy {}", schemaManagement.policy)
            val transactionManager = container.get<DatabaseTransactionManager>()

            runBlocking {
                when (schemaManagement.policy) {
                    SchemaPolicy.NONE -> Unit
                    SchemaPolicy.CREATE_MISSING,
                    SchemaPolicy.CREATE_MISSING_AND_VALIDATE -> {
                        transactionManager.transaction {
                            val schemas = exposedTables
                                .mapNotNull { table -> table.schemaName?.let { Schema(it) } }
                                .distinct()
                                .toTypedArray()

                            if (schemas.isNotEmpty()) {
                                databaseFactory.createSchema(*schemas, inBatch = schemas.size > 1)
                                SchemaUtils.createSchema()
                                logger.info("Created {} schema(s) for discovered tables", schemas.size)
                            }

                            if (exposedTables.isNotEmpty()) {
                                databaseFactory.createTable(*exposedTables, inBatch = exposedTables.size > 1)
                                logger.info("Created {} table(s)", exposedTables.size)
                            }
                        }
                    }
                    SchemaPolicy.VALIDATE -> Unit
                }

                if (
                    schemaManagement.policy == SchemaPolicy.VALIDATE ||
                    schemaManagement.policy == SchemaPolicy.CREATE_MISSING_AND_VALIDATE
                ) {
                    transactionManager.transaction(
                        config = TransactionConfig(
                            timeout = 60.toDuration(DurationUnit.SECONDS),
                            isolationLevel = TransactionIsolationLevel.READ_COMMITTED
                        )
                    ) {
                        val pendingStatements = MigrationUtils.statementsRequiredForDatabaseMigration(
                            *exposedTables,
                            withLogs = true,
                        )
                        if (pendingStatements.isNotEmpty()) {
                            val message = buildString {
                                append("Database schema has ")
                                append(pendingStatements.size)
                                append(" pending migration statement(s). ")
                                append("Run migrations or use schema { createMissing() } for local/test boot.")
                            }
                            if (schemaManagement.failOnPendingStatements) {
                                error(message)
                            } else {
                                logger.warn(message)
                            }
                        } else {
                            logger.info("Database schema validated - no pending migration statements")
                        }
                    }
                }
            }

            logger.info("  ✓ Database schema policy {} completed", schemaManagement.policy)
        } else {
            logger.info("  ℹ  No tables registered - skipping schema management")
        }
        BootstrapProgress.completeLifecycle(
            BootstrapLifecycle.DATABASE_SCHEMA_INITIALIZATION,
            "Database schema policy ${schemaManagement.policy} applied to ${discoveredTables.size} tables"
        )
    } catch (e: Exception) {
        logger.warn("Error discovering tables or creating DatabaseFactory: {}", e.message)
        logger.debug("Full error during table discovery", e)
        BootstrapProgress.failLifecycle(BootstrapLifecycle.DATABASE_SCHEMA_INITIALIZATION, e)
        throw e
    }

    // PHASE 4: Transaction Adapter Registration
    BootstrapProgress.startLifecycle(BootstrapLifecycle.TRANSACTION_ADAPTER_REGISTRATION)
    try {
        logger.info("Registering transaction adapters...")
        val transactionManager = container.get<DatabaseTransactionManager>()

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
            val eventBus = container.get<ApplicationEventBus>()
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
        BootstrapProgress.completeLifecycle(
            BootstrapLifecycle.TRANSACTION_ADAPTER_REGISTRATION,
            "Registered $adaptersRegistered transaction adapter(s)"
        )
    } catch (e: Exception) {
        logger.warn("Error registering transaction adapters: {}", e.message)
        BootstrapProgress.failLifecycle(BootstrapLifecycle.TRANSACTION_ADAPTER_REGISTRATION, e)
        throw e
    }

    return container
}

/**
 * Returns the active framework-owned container facade.
 */
fun currentKatalystContainer(): KatalystContainer =
    KatalystContainerProvider.current()

/**
 * Initializes Katalyst DI for a standalone application (non-Ktor).
 *
 * This starts the active DI engine with all modules and should be called during application startup.
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
 *     initializeKatalystStandalone(options, serverConfig)
 *     // ... rest of application code
 *     stopKatalystStandalone()
 * }
 * ```
 */
fun initializeKatalystStandalone(
    options: KatalystDIOptions,
    serverConfiguration: ServerConfiguration,
    additionalModules: List<KatalystBeanModule> = emptyList(),
    allowOverrides: Boolean = false,
    activateRuntimeReadyInitializers: Boolean = true
): KatalystContainer {
    logger.info("Initializing Katalyst DI for standalone application")
    logger.debug("Features enabled: {}", options.features.joinToString { it.id })

    val container = bootstrapKatalystContainer(
        databaseConfig = options.databaseConfig,
        scanPackages = options.scanPackages,
        features = options.features,
        serverConfig = serverConfiguration,
        additionalModules = additionalModules,
        allowOverrides = allowOverrides,
        schemaManagement = options.schemaManagement,
        beanEngine = options.beanEngine,
    )
    runPreStartInitializers(container)
    if (activateRuntimeReadyInitializers) {
        runRuntimeReadyInitializers(container)
    }
    logger.info("Katalyst DI initialization completed successfully")
    return container
}

/**
 * Stops the active Katalyst DI engine for standalone applications.
 *
 * Should be called during application shutdown.
 *
 * **Usage:**
 * ```kotlin
 * fun main() {
 *     try {
 *         val options = KatalystDIOptions(DatabaseConfig(...))
 *         initializeKatalystStandalone(options)
 *         // ... application code
 *     } finally {
 *         stopKatalystStandalone()
 *     }
 * }
 * ```
 */
fun stopKatalystStandalone() {
    synchronized(shutdownLock) {
        logger.info("Stopping Katalyst DI")
        val engine = KatalystBeanEngines.activeOrNull()
        if (engine == null) {
            KatalystContainerProvider.reset()
            logger.info("Katalyst DI already stopped")
            return
        }

        try {
            engine.stop()
        } finally {
            KatalystBeanEngines.clearActive()
            KatalystContainerProvider.reset()
        }
        logger.info("Katalyst DI stopped successfully")
    }
}

fun runPreStartInitializers(container: KatalystContainer = KatalystContainerProvider.current()) {
    try {
        logger.info("Starting pre-start initialization lifecycle")
        val runner = StartupHookRunner(container)
        runBlocking {
            runner.invokeAll()
        }
        logger.info("Pre-start initialization lifecycle completed")
    } catch (e: Exception) {
        logger.error("Fatal error during pre-start initialization", e)
        throw e
    }
}

fun runRuntimeReadyInitializers(container: KatalystContainer = KatalystContainerProvider.current()) {
    try {
        BootstrapProgress.startLifecycleCompact(BootstrapLifecycle.RUNTIME_READY_INITIALIZERS)
        logger.info("Starting runtime-ready initialization lifecycle")
        val runner = ReadyHookRunner(container)
        runBlocking {
            runner.invokeAll()
        }
        logger.info("Runtime-ready initialization lifecycle completed")
        BootstrapProgress.completeLifecycle(
            BootstrapLifecycle.RUNTIME_READY_INITIALIZERS,
            "Runtime-ready initializers executed"
        )
    } catch (e: Exception) {
        BootstrapProgress.failLifecycle(BootstrapLifecycle.RUNTIME_READY_INITIALIZERS, e)
        logger.error("Fatal error during runtime-ready initialization", e)
        throw e
    }
}

private fun resolveTransactionPhaseLoggingEnabled(container: KatalystContainer): Boolean {
    return runCatching {
        val configProvider = container.get<ConfigProvider>()
        configProvider.getBoolean("transaction.logging.enabled", true)
    }.getOrElse {
        logger.debug(
            "ConfigProvider unavailable for transaction phase logging toggle; using default enabled=true"
        )
        true
    }
}
