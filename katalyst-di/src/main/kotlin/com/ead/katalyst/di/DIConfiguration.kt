package com.ead.katalyst.di

import com.ead.katalyst.tables.Table
import com.ead.katalyst.database.DatabaseConfig
import com.ead.katalyst.database.DatabaseFactory
import com.ead.katalyst.database.DatabaseTransactionManager
import com.ead.katalyst.di.internal.AutoBindingRegistrar
import io.ktor.server.application.Application
import io.ktor.server.application.install
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.module.Module
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin
import org.slf4j.LoggerFactory

/**
 * Complete DI Configuration for the Katalyst Application.
 *
 * Orchestrates all modules (Core, Scanner, Scheduler) and provides
 * convenient initialization methods.
 */

/**
 * Logger for DI configuration logging.
 */
private val logger = LoggerFactory.getLogger("DIConfiguration")

data class KatalystDIOptions(
    val databaseConfig: DatabaseConfig,
    val enableScheduler: Boolean = false,
    val scanPackages: Array<String> = emptyArray()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as KatalystDIOptions

        if (enableScheduler != other.enableScheduler) return false
        if (databaseConfig != other.databaseConfig) return false
        if (!scanPackages.contentEquals(other.scanPackages)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = enableScheduler.hashCode()
        result = 31 * result + databaseConfig.hashCode()
        result = 31 * result + scanPackages.contentHashCode()
        return result
    }
}

/**
 * Starts or augments the global Koin context with Katalyst modules, then performs
 * automatic discovery of services, repositories, validators, and event handlers.
 */
fun bootstrapKatalystDI(
    databaseConfig: DatabaseConfig,
    enableScheduler: Boolean = false,
    scanPackages: Array<String> = emptyArray()
): org.koin.core.Koin {
    val logger = LoggerFactory.getLogger("bootstrapKatalystDI")

    val modules = mutableListOf<Module>(
        coreDIModule(databaseConfig),
        scannerDIModule()
    )

    if (enableScheduler) {
        modules += schedulerDIModule()
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
    logger.info("Starting AutoBindingRegistrar to discover components...")
    AutoBindingRegistrar(koin, scanPackages).registerAll()
    logger.info("AutoBindingRegistrar completed")

    // Now that tables are discovered and registered in Koin, create DatabaseFactory with them
    try {
        logger.debug("Attempting to retrieve discovered Table instances from Koin...")
        val discoveredTables = koin.getAll<Table>()
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
            }
        }
    } catch (e: Exception) {
        logger.warn("Error discovering tables or creating DatabaseFactory: {}", e.message)
        logger.debug("Full error during table discovery", e)
        // Continue with the default DatabaseFactory created by coreDIModule
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
 *         enableScheduler = true,
 *         scanPackages = arrayOf("com.example.app")
 *     )
 *     initializeKoinStandalone(options)
 *     // ... rest of application code
 *     stopKoinStandalone()
 * }
 * ```
 */
fun initializeKoinStandalone(options: KatalystDIOptions): org.koin.core.Koin {
    logger.info("Initializing Koin DI for standalone application")
    logger.debug("Scheduler enabled: {}", options.enableScheduler)

    return bootstrapKatalystDI(
        databaseConfig = options.databaseConfig,
        enableScheduler = options.enableScheduler,
        scanPackages = options.scanPackages
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
 * Installs Koin DI for a Ktor application.
 *
 * This should be called within the application {} block in your Ktor main module.
 *
 * **Usage:**
 * ```kotlin
 * fun Application.main() {
 *     val options = KatalystDIOptions(
 *         databaseConfig = DatabaseConfig(...),
 *         enableScheduler = true,
 *         scanPackages = arrayOf("com.example.app")
 *     )
 *     installKoinDI(options)
 * }
 * ```
 */
fun Application.installKoinDI(
    options: KatalystDIOptions
) {
    logger.info("Installing Koin DI for Ktor application")
    logger.debug("Scheduler enabled: {}", options.enableScheduler)

    bootstrapKatalystDI(
        databaseConfig = options.databaseConfig,
        enableScheduler = options.enableScheduler,
        scanPackages = options.scanPackages
    )

    install(Koin) {}

    logger.info("Koin DI installed successfully for Ktor application")
}

/**
 * Builder for custom DI configurations.
 *
 */
class DIConfigurationBuilder {
    private val modules = mutableListOf<Module>()
    private var databaseConfig: DatabaseConfig? = null

    /**
     * Supplies the database configuration used by subsequent [coreModules] calls.
     */
    fun database(config: DatabaseConfig): DIConfigurationBuilder {
        this.databaseConfig = config
        return this
    }

    /**
     * Includes core DI modules (services, handlers, validators).
     */
    fun coreModules(): DIConfigurationBuilder {
        val config = databaseConfig
            ?: throw IllegalStateException("Call database(...) before coreModules() to provide DatabaseConfig")
        logger.debug("Adding core modules")
        modules.add(coreDIModule(config))
        return this
    }

    /**
     * Includes scanner DI modules (reflection utilities).
     */
    fun scannerModules(): DIConfigurationBuilder {
        logger.debug("Adding scanner modules")
        modules.add(scannerDIModule())
        return this
    }

    /**
     * Includes scheduler DI modules (task scheduling).
     */
    fun schedulerModules(): DIConfigurationBuilder {
        logger.debug("Adding scheduler modules")
        modules.add(schedulerDIModule())
        return this
    }

    /**
     * Adds custom Koin modules.
     *
     * @param customModules Custom modules to include
     */
    fun customModules(vararg customModules: Module): DIConfigurationBuilder {
        logger.debug("Adding {} custom module(s)", customModules.size)
        modules.addAll(customModules)
        return this
    }

    /**
     * Builds and returns the list of modules.
     */
    fun build(): List<Module> = modules.toList()
}

/**
 * Installs Koin DI with custom configuration for a Ktor application.
 *
 * Provides a builder-style configuration for flexible module setup.
 *
 * **Usage:**
 * ```kotlin
 * fun Application.main() {
 *     installKoinDI {
 *         coreModules()
 *         scannerModules()
 *         schedulerModules()
 *     }
 *     configureSchedulerTasks()
 * }
 * ```
 *
 * @param builder Configuration builder lambda
 */
fun Application.installKoinDI(builder: DIConfigurationBuilder.() -> Unit) {
    logger.info("Installing Koin DI with custom configuration for Ktor application")

    val diBuilder = DIConfigurationBuilder()
    diBuilder.builder()
    val modules = diBuilder.build()

    logger.info("Installing Koin with {} module(s) into Ktor application", modules.size)

    install(Koin) {
        modules(*modules.toTypedArray())
    }

    logger.info("Koin DI installed successfully with custom configuration")
}

private fun currentKoinOrNull(): org.koin.core.Koin? =
    runCatching { org.koin.core.context.GlobalContext.get() }.getOrNull()
