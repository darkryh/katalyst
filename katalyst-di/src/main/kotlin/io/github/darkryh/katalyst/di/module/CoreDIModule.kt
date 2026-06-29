package io.github.darkryh.katalyst.di.module

import io.github.darkryh.katalyst.config.DatabaseConfig
import io.github.darkryh.katalyst.database.DatabaseFactory
import io.github.darkryh.katalyst.database.SqlExecutor
import io.github.darkryh.katalyst.di.feature.KatalystBeanModule
import io.github.darkryh.katalyst.di.feature.katalystBeanModule
import io.github.darkryh.katalyst.transactions.manager.DatabaseTransactionManager
import org.slf4j.LoggerFactory

/**
 * Core DI Module that orchestrates database configuration and transaction management.
 *
 * This module provides the fundamental framework components needed by all applications:
 * - Database connection pooling (HikariCP)
 * - Transaction management (suspend-based)
 *
 * **Usage:**
 * ```kotlin
 * fun Application.module() {
 *     initializeKatalystDI(
 *         databaseConfig = DatabaseConfig(
 *             url = "jdbc:postgresql://localhost/mydb",
 *             driver = "org.postgresql.Driver",
 *             username = "user",
 *             password = "pass"
 *         )
 *     )
 * }
 * ```
 *
 * **Developer Responsibilities:**
 * Developers should register their own:
 * - Services (implement Service interface)
 * - Repositories (implement Repository interface)
 * - Validators (implement Validator interface)
 * - Event handlers (implement EventHandler interface)
 * - HTTP handlers (implement HttpHandler interface)
 *
 * See the examples module for reference implementations.
 */
internal fun coreDIModule(config: DatabaseConfig): KatalystBeanModule = katalystBeanModule {
    val logger = LoggerFactory.getLogger("CoreDIModule")
    logger.info("Initializing Core DI Module with database and transaction management")
    single<DatabaseConfig> { config }
    single<DatabaseFactory> { DatabaseFactory.create(config) }
    // The one injectable raw-SQL escape hatch. Transaction-aware: reuses the active Exposed
    // transaction connection when present, else a pooled connection.
    single<SqlExecutor> { get<DatabaseFactory>().createSqlExecutor() }
    single<DatabaseTransactionManager> {
        DatabaseTransactionManager(
            database = get<DatabaseFactory>().database,
        )
    }

    logger.info("Core DI Module initialized successfully")
}
