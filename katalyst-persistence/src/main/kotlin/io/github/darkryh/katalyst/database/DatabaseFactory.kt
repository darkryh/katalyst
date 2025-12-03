package io.github.darkryh.katalyst.database

import io.github.darkryh.katalyst.config.DatabaseConfig
import io.github.darkryh.katalyst.database.DatabaseFactory.Companion.create
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.jetbrains.exposed.v1.core.Schema
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.slf4j.LoggerFactory

/**
 * Database factory for managing database connections with HikariCP connection pooling.
 *
 * This factory creates and manages the database connection using HikariCP for
 * efficient connection pooling. It also handles schema initialization.
 *
 * **Features:**
 * - HikariCP connection pooling for optimal performance
 * - Automatic schema creation on startup
 * - Configurable connection pool settings
 * - Thread-safe connection management
 * - Proper resource cleanup via AutoCloseable
 *
 * **Example Usage:**
 * ```kotlin
 * val config = DatabaseConfig(
 *     url = "jdbc:postgresql://localhost:5432/katalyst",
 *     driver = "org.postgresql.Driver",
 *     username = "user",
 *     password = "pass"
 * )
 *
 * val factory = DatabaseFactory.create(
 *     config = config,
 *     tables = listOf(UsersTable, ProductsTable)
 * )
 *
 * // Use factory.database for queries
 * // Don't forget to close:
 * factory.close()
 * ```
 *
 * @property database The Exposed Database instance for running queries
 * @constructor Private constructor - use factory method [create] instead
 */
class DatabaseFactory private constructor(
    val database: Database,
    private val dataSource: HikariDataSource
) : AutoCloseable {

    companion object {
        private val logger = LoggerFactory.getLogger(DatabaseFactory::class.java)

        /**
         * Creates a new DatabaseFactory with the given configuration.
         *
         * @param config The database configuration
         * @param tables List of table schemas to create (can be empty if tables already exist)
         * @return DatabaseFactory instance
         * @throws IllegalArgumentException if config is invalid
         * @throws Exception if database connection fails
         */
        fun create(config: DatabaseConfig, tables: List<Table> = emptyList()): DatabaseFactory {
            logger.info("Initializing DatabaseFactory with URL: {}", config.url)

            // Configure HikariCP
            val hikariConfig = HikariConfig().apply {
                jdbcUrl = config.url
                driverClassName = config.driver
                username = config.username
                password = config.password
                maximumPoolSize = config.maxPoolSize
                minimumIdle = config.minIdleConnections
                connectionTimeout = config.connectionTimeout
                idleTimeout = config.idleTimeout
                maxLifetime = config.maxLifetime
                isAutoCommit = config.autoCommit
                transactionIsolation = config.transactionIsolation

                logger.debug(
                    "HikariCP Configuration: maxPoolSize={}, minIdle={}, autoCommit={}",
                    config.maxPoolSize,
                    config.minIdleConnections,
                    config.autoCommit
                )
            }

            // Create data source
            val dataSource = try {
                HikariDataSource(hikariConfig)
            } catch (e: Exception) {
                logger.error("Failed to create HikariDataSource", e)
                throw e
            }

            logger.info("HikariDataSource created successfully")

            // Create Exposed Database instance
            val database = Database.connect(dataSource)
            logger.info("Exposed Database connected successfully")

            // Create tables if provided
            if (tables.isNotEmpty()) {
                transaction(database) {
                    val schemas = tables.mapNotNull { it.schemaName }.distinct()
                    if (schemas.isNotEmpty()) {
                        SchemaUtils.createSchema(*schemas.map(::Schema).toTypedArray())
                        logger.info("Created {} schema(s)", schemas.size)
                    }

                    SchemaUtils.create(*tables.toTypedArray())
                    logger.info("Created {} table(s)", tables.size)
                }
            }

            logger.info("DatabaseFactory initialization completed")
            return DatabaseFactory(database, dataSource)
        }
    }

    /**
     * Closes the database connection and shuts down the connection pool.
     *
     * Should be called when the application shuts down to properly clean up resources.
     */
    override fun close() {
        logger.info("Closing DatabaseFactory and HikariDataSource")
        try {
            dataSource.close()
            logger.info("HikariDataSource closed successfully")
        } catch (e: Exception) {
            logger.error("Error closing HikariDataSource", e)
        }
    }
}
