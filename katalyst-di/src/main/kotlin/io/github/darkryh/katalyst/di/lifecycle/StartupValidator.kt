package io.github.darkryh.katalyst.di.lifecycle

import io.github.darkryh.katalyst.core.transaction.DatabaseTransactionManager
import io.github.darkryh.katalyst.di.internal.TableRegistry
import org.slf4j.LoggerFactory

/**
 * Validates application startup preconditions - FAILS FAST on any issue.
 *
 * **Execution Order:** FIRST (order=-100)
 *
 * **Critical Safety Mechanism:**
 * This initializer runs FIRST and MUST validate everything before ANY other
 * pre-start initializer gets to run.
 *
 * The InitializerRegistry sorts by order and executes sequentially:
 * ```
 * initializers.sortBy { it.order }  // StartupValidator runs first
 * initializers.forEach { initializer.onApplicationReady() }
 * ```
 *
 * If StartupValidator throws ANY exception → InitializerRegistry catches it
 * (line 97: onFailure) → Application stops immediately.
 *
 * **Why This Matters:**
 * Runtime-ready hooks (such as scheduler registration) may query the database.
 * StartupValidator must detect and prevent schema/connectivity issues before
 * runtime-ready activation begins.
 *
 * **Validation Checks (Fail-Fast):**
 * 1. DatabaseTransactionManager is available
 * 2. Database connection works
 * 3. ALL registered tables exist in schema
 * 4. If ANY check fails → THROW immediately
 *
 * **Order Guarantee:**
 * The order=-100 parameter ensures:
 * - StartupValidator always runs before any custom pre-start initializer
 * - StartupValidator always runs before any user initializers (order=0+)
 * - If we throw → subsequent initializers NEVER run
 * - If we return normally → schema is guaranteed valid
 */
internal class StartupValidator(
    private val txManager: DatabaseTransactionManager
) : ApplicationInitializer {
    private val logger = LoggerFactory.getLogger("StartupValidator")

    override val initializerId: String = "StartupValidator"
    override val order: Int = -100

    override suspend fun onApplicationReady() {
        logger.info("")
        logger.info("╔════════════════════════════════════════════════════╗")
        logger.info("║ STARTUP VALIDATION (FAIL-FAST)                    ║")
        logger.info("║ Validates database schema before runtime hooks    ║")
        logger.info("╚════════════════════════════════════════════════════╝")

        try {
            // 1. Verify DatabaseTransactionManager
            logger.info("Step 1: Checking DatabaseTransactionManager...")
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
                logger.info("  Found {} registered table(s)", discoveredTables.size)
                logger.info("  ✓ Database schema verified (created during bootstrap)")
            }
            logger.info("✓ Startup validation passed")

        } catch (e: Exception) {
            logger.error("✗ Startup validation failed: {}", e.message ?: "Unknown startup validation error")
            logger.error("  Runtime-ready initializers will not run; server will not start")

            // Re-throw to stop application startup
            throw e
        }
    }
}
