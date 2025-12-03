package io.github.darkryh.katalyst.transactions

import io.github.darkryh.katalyst.transactions.manager.DatabaseTransactionManager
import io.github.darkryh.katalyst.transactions.manager.TransactionManager
import org.koin.core.module.Module
import org.koin.dsl.module
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("TransactionModule")

/**
 * Koin DI module for katalyst-transactions.
 *
 * Registers the transaction manager and hook registry.
 * This module should be loaded BEFORE modules that depend on transactions
 * (e.g., katalyst-events-bus, katalyst-persistence).
 *
 * **Usage in your application:**
 * ```kotlin
 * fun appModule() = module {
 *     includes(
 *         transactionModule(),  // Load first
 *         databaseModule(config),
 *         eventBusModule()
 *     )
 * }
 *
 * startKoin {
 *     modules(appModule())
 * }
 * ```
 *
 * **What gets registered:**
 * - `TransactionManager` (interface) - singleton
 * - `DatabaseTransactionManager` (implementation) - singleton
 * - Hook registry - managed internally
 */
fun transactionModule(): Module = module {
    logger.info("Configuring transaction module")

    // Placeholder: Actual registration happens in DatabaseModule
    // which creates DatabaseTransactionManager with the database instance

    logger.info("Transaction module configured successfully")
}
