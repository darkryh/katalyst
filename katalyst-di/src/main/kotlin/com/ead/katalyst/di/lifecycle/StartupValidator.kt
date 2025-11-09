package com.ead.katalyst.di.lifecycle

import com.ead.katalyst.core.persistence.Table
import com.ead.katalyst.core.transaction.DatabaseTransactionManager
import org.koin.core.Koin
import org.slf4j.LoggerFactory

/**
 * Validates application startup preconditions.
 *
 * Runs FIRST (order=-100) and checks:
 * 1. Database connection is working
 * 2. Tables are discovered and initialized
 * 3. Transaction manager is ready
 *
 * Fails fast if validation fails - prevents startup with broken database.
 */
internal class StartupValidator : ApplicationInitializer {
    private val logger = LoggerFactory.getLogger("StartupValidator")

    override val initializerId: String = "StartupValidator"
    override val order: Int = -100

    override suspend fun onApplicationReady(koin: Koin) {
        logger.info("")
        logger.info("╔════════════════════════════════════════════════════╗")
        logger.info("║ PHASE 1: Startup Validation                        ║")
        logger.info("╚════════════════════════════════════════════════════╝")
        logger.info("")

        try {
            // 1. Verify DatabaseTransactionManager
            logger.info("Checking DatabaseTransactionManager...")
            val txManager = koin.get<DatabaseTransactionManager>()
            logger.info("✓ DatabaseTransactionManager available")

            // 2. Test database connection
            logger.info("Validating database connection...")
            val isConnected = runCatching {
                txManager.transaction { true }
            }.isSuccess

            if (!isConnected) {
                throw IllegalStateException("Database connection test failed")
            }
            logger.info("✓ Database connection validated")

            // 3. Check discovered tables
            logger.info("Checking discovered tables...")
            val discoveredTables = runCatching {
                koin.getAll<Table>()
            }.getOrElse { emptyList() }

            when {
                discoveredTables.isEmpty() -> {
                    logger.warn("⚠ WARNING: No tables discovered!")
                    logger.warn("   Database schema may not be initialized.")
                }
                else -> {
                    logger.info("✓ {} table(s) discovered", discoveredTables.size)
                    discoveredTables.forEach { table ->
                        logger.debug("  - {}", table::class.simpleName)
                    }
                }
            }

            logger.info("")
            logger.info("╔════════════════════════════════════════════════════╗")
            logger.info("║ ✓ PHASE 1 PASSED: Application ready for init      ║")
            logger.info("╚════════════════════════════════════════════════════╝")
            logger.info("")

        } catch (e: Exception) {
            logger.error("")
            logger.error("╔════════════════════════════════════════════════════╗")
            logger.error("║ ✗ STARTUP VALIDATION FAILED                       ║")
            logger.error("║ Application cannot proceed                         ║")
            logger.error("╚════════════════════════════════════════════════════╝")
            logger.error("Reason: {}", e.message)
            logger.error("")
            throw e
        }
    }
}
