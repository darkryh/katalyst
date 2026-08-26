package io.github.darkryh.katalyst.di.config

import io.github.darkryh.katalyst.config.DatabaseConfig
import io.github.darkryh.katalyst.core.config.ConfigProvider
import io.github.darkryh.katalyst.core.di.KatalystContainer
import io.github.darkryh.katalyst.core.di.KatalystContainerProvider
import io.github.darkryh.katalyst.core.di.get
import io.github.darkryh.katalyst.core.di.getOrNull
import io.github.darkryh.katalyst.core.lifecycle.ApplicationShutdown
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
import io.github.darkryh.katalyst.di.lifecycle.ShutdownHookRunner
import io.github.darkryh.katalyst.di.lifecycle.StartupWarnings
import io.github.darkryh.katalyst.di.lifecycle.StartupWarningsAggregator
import io.github.darkryh.katalyst.di.module.coreDIModule
import io.github.darkryh.katalyst.di.registry.RegistryManager
import io.github.darkryh.katalyst.events.bus.ApplicationEventBus
import io.github.darkryh.katalyst.events.bus.GlobalEventHandlerRegistry
import io.github.darkryh.katalyst.events.bus.adapter.EventsTransactionAdapter
import io.github.darkryh.katalyst.transactions.config.TransactionConfig
import io.github.darkryh.katalyst.transactions.config.TransactionIsolationLevel
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.core.Schema
import org.jetbrains.exposed.v1.core.Table
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
 * The features this bootstrap installed, kept so the shutdown phase can stop them.
 *
 * Features are supplied to [initializeKatalystStandalone] and never reach the container, so without
 * holding on to them here there is nothing left at teardown to call [KatalystFeature.onShutdown] on.
 * Boot-scoped like every other global in [resetBootScopedGlobals]: cleared on both edges so one
 * bootstrap can never stop another's features.
 */
@Volatile
private var activeFeatures: List<KatalystFeature> = emptyList()

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

    // Start from clean global state. stopKatalystStandalone() also resets, but a bootstrap
    // that threw before completing never reaches shutdown, so entering a fresh boot with
    // leftovers from a previous (possibly failed) one must not be possible.
    resetBootScopedGlobals()

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

    BootstrapProgress.startLifecycle(BootstrapLifecycle.BEAN_CONTAINER_BOOTSTRAP)
    val container = try {
        selectedBeanEngine.currentOrNull()?.also {
            logger.info("Loading Katalyst modules into existing bean container")
            selectedBeanEngine.loadModules(modules, allowOverrides = allowOverrides)
        } ?: run {
            logger.info("Starting new {} bean container for Katalyst modules", selectedBeanEngine.id)
            selectedBeanEngine.start(modules, allowOverrides = allowOverrides)
        }
    } catch (e: Exception) {
        BootstrapProgress.failLifecycle(BootstrapLifecycle.BEAN_CONTAINER_BOOTSTRAP, e)
        throw e
    }
    BootstrapProgress.completeLifecycle(
        BootstrapLifecycle.BEAN_CONTAINER_BOOTSTRAP,
        "Bean container ready with ${modules.size} module(s)"
    )
    KatalystContainerProvider.set(container)

    // Register components including tables
    // PHASE 2: Component Discovery & Registration with Validation
    BootstrapProgress.startLifecycle(BootstrapLifecycle.COMPONENT_DISCOVERY_REGISTRATION)
    try {
        logger.info("Starting ComponentRegistrationOrchestrator with dependency validation...")
        val orchestrator = ComponentRegistrationOrchestrator(
            container,
            selectedBeanEngine,
            scanPackages,
            enabledFeatureIds = features.map { it.id }.toSet(),
        )
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
    // Recorded before the ready hooks run: from here on a feature is live and has to be stopped,
    // including when a later bootstrap phase throws and the entry point unwinds into teardown.
    activeFeatures = features
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

            // Reuse the DatabaseFactory already in the container rather than building a second
            // one. DatabaseFactory.create() opens its own HikariCP pool and takes no tables, so
            // a second instance bought nothing and cost a connection pool that was never closed
            // — while also splitting the boot across two pools: anything that ran earlier
            // (feature onReady hooks, and therefore migrations) used the first pool, schema
            // validation the second.
            val databaseFactory = container.getOrNull<DatabaseFactory>()
                ?: DatabaseFactory.create(databaseConfig)

            // Rebind the transaction manager so it carries the resolved phase-logging config.
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

                                // "Missing" has to mean the same thing for a column as it does for
                                // a table. CREATE TABLE IF NOT EXISTS skips an existing table
                                // WHOLE, so without this step a column added to a Table after the
                                // first boot never reaches the database and only surfaces as a
                                // query-time SQL error. Additive only — see
                                // DatabaseFactory.addMissingColumns — and switchable for schemas
                                // whose columns are owned by migrations.
                                if (schemaManagement.createMissingColumns) {
                                    addMissingColumns(databaseFactory, exposedTables)
                                } else {
                                    logger.debug(
                                        "createMissingColumns = false - leaving columns of existing tables alone"
                                    )
                                }
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
            resetBootScopedGlobals()
            logger.info("Katalyst DI already stopped")
            return
        }

        try {
            runShutdownPhase(engine)
            engine.stop()
        } finally {
            KatalystBeanEngines.clearActive()
            KatalystContainerProvider.reset()
            resetBootScopedGlobals()
        }
        logger.info("Katalyst DI stopped successfully")
    }
}

/**
 * Clears the JVM-global state that describes a single bootstrap.
 *
 * Every singleton touched here is scoped to one container even though it lives for the whole
 * process, so it has to be cleared on both edges — a boot must not inherit anything, and a
 * stopped container must not leave anything behind:
 * - registries keep handing out instances that belong to an already-stopped container, so a
 *   later bootstrap would union those stale instances with the fresh ones and execute every
 *   discovered hook twice;
 * - [GlobalEventHandlerRegistry] is a hand-off buffer that only the events feature drains.
 *   With that feature disabled nothing drains it, so discovered handlers sat there until some
 *   later boot enabled events and subscribed a dead container's handlers;
 * - the bootstrap report ([BootstrapProgress], [StartupWarnings]) otherwise describes the
 *   *previous* boot: warnings accumulate across boots, and phases the current boot never
 *   reached still read as completed.
 */
private fun resetBootScopedGlobals() {
    // Withdrawn here as well as in katalystApplication's finally: on SIGINT it is Ktor's own shutdown
    // hook that stops the server, and the JVM can halt before the thread parked in start(wait = true)
    // unwinds far enough to reach that finally. Leaving the action installed would advertise a
    // shutdown seam for a container that is already gone.
    ApplicationShutdown.uninstall()
    activeFeatures = emptyList()
    RegistryManager.resetAll()
    GlobalEventHandlerRegistry.consumeAll()
    BootstrapProgress.clear()
    StartupWarnings.clear()
}

/**
 * Stops everything the application owns, while everything the framework owns still works.
 *
 * This is the phase whose absence made shutdowns noisy. Katalyst used to go straight from "the
 * server stopped" to closing the connection pool and unregistering the database, with application
 * background work — the polling loops and queue consumers [ReadyHook] explicitly invites — still
 * running. The pool would close under an in-flight statement and the shutdown would fill with
 * `SQLSTATE 08006 / Socket closed` followed by `No transaction manager for db ExposedDatabase[...]`,
 * from work the application had every intention of stopping and was simply never asked to.
 *
 * Three steps, narrowest contract first:
 * 1. [ShutdownHook]s, awaited, so a worker can *join* what it cancelled rather than only signal it.
 * 2. [KatalystFeature.onShutdown], in reverse installation order.
 * 3. A bounded wait for the connection pool to go quiet, covering whatever declared neither.
 *
 * Nothing here may throw. Every step is a best effort whose failure is reported and stepped over,
 * because the alternative — an exception escaping into [stopKatalystStandalone] — would skip the
 * teardown that follows and leak the pool, the container and the engine.
 */
private fun runShutdownPhase(engine: KatalystBeanEngine) {
    val container = engine.currentOrNull() ?: KatalystContainerProvider.currentOrNull()

    runCatching {
        runBlocking { ShutdownHookRunner(container).invokeAll() }
    }.onFailure { error ->
        logger.warn("Shutdown hook lifecycle failed: {}: {}", error::class.simpleName, error.message, error)
    }

    if (container != null) {
        val beanContext = KatalystBeanContext(container)
        // Reverse installation order, matching the hooks: a feature is stopped before the ones it
        // was installed after, so nothing is torn down while something that needs it is still up.
        activeFeatures.asReversed().forEach { feature ->
            runCatching { feature.onShutdown(beanContext) }
                .onFailure { error ->
                    logger.warn(
                        "Feature '{}' failed to stop: {}: {}",
                        feature.id,
                        error::class.simpleName,
                        error.message,
                        error,
                    )
                }
        }
    }

    runCatching { container?.getOrNull<DatabaseFactory>()?.quiesce() }
        .onFailure { error -> logger.debug("Could not wait for the connection pool to go quiet", error) }
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

/**
 * Applies the additive column half of the creating schema policies.
 *
 * Split out of the boot block so the failure it can produce gets a diagnosis rather than a raw
 * `SQLException`: the one statement Exposed generates that a database routinely refuses is
 * `ALTER TABLE ... ADD COLUMN x NOT NULL` against a table that already has rows, and the SQLSTATE
 * alone does not tell a developer which of the three fixes they want.
 */
private fun addMissingColumns(
    databaseFactory: DatabaseFactory,
    exposedTables: Array<Table>,
) {
    val added = runCatching { databaseFactory.addMissingColumns(*exposedTables) }
        .getOrElse { failure ->
            throw IllegalStateException(
                "Schema policy could not add a missing column: ${failure.message}. " +
                    "A NOT NULL column with no default cannot be added to a table that already has " +
                    "rows — make the column nullable, give it a default, add it with a migration, " +
                    "or turn column creation off with schema { createMissing(createMissingColumns = false) }.",
                failure,
            )
        }

    if (added.isEmpty()) {
        logger.debug("No missing columns to add - every existing table already matches its definition")
    } else {
        logger.info(
            "Added {} missing column/constraint statement(s): {}",
            added.size,
            added.joinToString("; "),
        )
    }
}
