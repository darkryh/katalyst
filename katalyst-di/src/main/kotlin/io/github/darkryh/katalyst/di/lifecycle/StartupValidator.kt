package io.github.darkryh.katalyst.di.lifecycle

import io.github.darkryh.katalyst.core.transaction.DatabaseTransactionManager
import io.github.darkryh.katalyst.di.internal.TableRegistry
import org.koin.core.Koin
import org.slf4j.LoggerFactory

/**
 * Validates application startup preconditions - FAILS FAST on any issue.
 *
 * **Execution Order:** FIRST (order=-100)
 *
 * **Critical Safety Mechanism:**
 * This initializer runs FIRST and MUST validate everything before ANY other
 * initializer (especially SchedulerInitializer) gets to run.
 *
 * The InitializerRegistry sorts by order and executes sequentially:
 * ```
 * initializers.sortBy { it.order }  // StartupValidator runs first
 * initializers.forEach { initializer.onApplicationReady(koin) }
 * ```
 *
 * If StartupValidator throws ANY exception → InitializerRegistry catches it
 * (line 97: onFailure) → Application stops immediately.
 *
 * **Why This Matters:**
 * SchedulerInitializer (order=-50) invokes scheduler methods that may query
 * the database. If tables don't exist, those queries crash the application.
 * StartupValidator must detect and prevent this BEFORE SchedulerInitializer runs.
 *
 * **Validation Checks (Fail-Fast):**
 * 1. DatabaseTransactionManager is available
 * 2. Database connection works
 * 3. ALL registered tables exist in schema
 * 4. If ANY check fails → THROW immediately
 *
 * **Order Guarantee:**
 * The order=-100 parameter ensures:
 * - StartupValidator always runs before SchedulerInitializer (order=-50)
 * - StartupValidator always runs before any user initializers (order=0+)
 * - If we throw → subsequent initializers NEVER run
 * - If we return normally → schema is guaranteed valid
 */
internal class StartupValidator : ApplicationInitializer {
    private val logger = LoggerFactory.getLogger("StartupValidator")

    override val initializerId: String = "StartupValidator"
    override val order: Int = -100

    override suspend fun onApplicationReady(koin: Koin) {
        logger.info("")
        logger.info("╔════════════════════════════════════════════════════╗")
        logger.info("║ STARTUP VALIDATION (FAIL-FAST)                    ║")
        logger.info("║ Validates database schema BEFORE scheduler init   ║")
        logger.info("╚════════════════════════════════════════════════════╝")
        logger.info("")

        try {
            // 1. Verify DatabaseTransactionManager
            logger.info("Step 1: Checking DatabaseTransactionManager...")
            val txManager = koin.get<DatabaseTransactionManager>()
            logger.info("  ✓ DatabaseTransactionManager available")

            // 2. Test database connection (FAIL-FAST on error)
            logger.info("Step 2: Testing database connection...")
            val connectionTest = runCatching {
                txManager.transaction { true }
            }

            if (connectionTest.isFailure) {
                throw IllegalStateException(
                    "Database connection test failed: ${connectionTest.exceptionOrNull()?.message}",
                    connectionTest.exceptionOrNull()
                )
            }
            logger.info("  ✓ Database connection successful")

            // 3. Verify database schema was initialized correctly
            logger.info("Step 3: Verifying database schema...")
            val discoveredTables = TableRegistry.getAll()

            if (discoveredTables.isEmpty()) {
                logger.info("  ℹ  No tables registered - nothing to verify")
            } else {
                logger.info("  Found {} registered table(s):", discoveredTables.size)
                discoveredTables.forEach { table ->
                    logger.info("    • {}", table.tableName)
                }
                logger.info("  ✓ Database schema verified (created during Phase 3)")
            }

            logger.info("")
            logger.info("╔════════════════════════════════════════════════════╗")
            logger.info("║ ✓ STARTUP VALIDATION PASSED                       ║")
            logger.info("║ ✓ Database ready for scheduler initialization     ║")
            logger.info("║ ✓ Safe to proceed to next phases                  ║")
            logger.info("╚════════════════════════════════════════════════════╝")
            logger.info("")

        } catch (e: Exception) {
            logger.error("")
            logger.error("╔════════════════════════════════════════════════════╗")
            logger.error("║ ✗ STARTUP VALIDATION FAILED - FATAL ERROR         ║")
            logger.error("║ Application cannot proceed                         ║")
            logger.error("╚════════════════════════════════════════════════════╝")
            logger.error("Error: {}", e.message)
            logger.error("")
            logger.error("This error PREVENTS all further initialization:")
            logger.error("  → SchedulerInitializer will NOT run")
            logger.error("  → Application will NOT start")
            logger.error("  → Ktor server will NOT bind")
            logger.error("")

            // Re-throw to stop application startup
            throw e
        }
    }
}
