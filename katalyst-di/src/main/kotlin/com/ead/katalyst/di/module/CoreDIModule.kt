package com.ead.katalyst.di.module

import com.ead.katalyst.config.DatabaseConfig
import com.ead.katalyst.database.databaseModule
import org.koin.core.module.Module
import org.koin.dsl.module
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
fun coreDIModule(config: DatabaseConfig): Module = module {
    val logger = LoggerFactory.getLogger("CoreDIModule")
    logger.info("Initializing Core DI Module with database and transaction management")
    includes(databaseModule(config))

    logger.info("Core DI Module initialized successfully")
}
