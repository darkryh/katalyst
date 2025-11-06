package com.ead.katalyst.database

import org.koin.dsl.module
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("DatabaseModule")


/**
 * Extension function to create a complete database module with configuration.
 *
 * This is a convenience function for applications that want to create the
 * database module with a specific configuration inline.
 *
 * **Example:**
 * ```kotlin
 * fun appModule() = module {
 *     includes(databaseModule(
 *         DatabaseConfig(
 *             url = "jdbc:postgresql://localhost:5432/mydb",
 *             driver = "org.postgresql.Driver",
 *             username = "user",
 *             password = "pass"
 *         )
 *     ))
 * }
 * ```
 *
 * @param config The database configuration
 * @return A Koin module with database components
 */
fun databaseModule(config: DatabaseConfig) = module {
    logger.info("Registering database module with provided configuration")

    // Register the config
    single { config }

    // Register DatabaseFactory
    single<DatabaseFactory> {
        logger.debug("Creating DatabaseFactory with provided config")
        DatabaseFactory.create(config)
    }

    // Register DatabaseTransactionManager
    single<DatabaseTransactionManager> {
        logger.debug("Creating DatabaseTransactionManager")
        val factory = get<DatabaseFactory>()
        DatabaseTransactionManager(factory.database)
    }

    logger.info("Database module registered with configuration")
}
