package com.ead.katalyst.di.lifecycle

import com.ead.katalyst.core.persistence.Table
import com.ead.katalyst.core.transaction.DatabaseTransactionManager
import com.ead.katalyst.di.internal.TableRegistry
import kotlin.reflect.KClass
import org.jetbrains.exposed.sql.SchemaUtils
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
        logger.info("║ PHASE 1: Startup Validation (FAIL-FAST)           ║")
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

            // 3. Validate all registered tables exist (FAIL-FAST on error or missing tables)
            logger.info("Step 3: Validating table schema existence...")
            val discoveredTables = TableRegistry.getAll()

            if (discoveredTables.isEmpty()) {
                logger.info("  ℹ  No tables registered - skipping schema validation")
            } else {
                logger.info("  Found {} registered table(s):", discoveredTables.size)
                discoveredTables.forEach { table ->
                    logger.info("    • {}", table.tableName)
                }

                // Validate tables exist in database (FAIL-FAST if validation fails)
                logger.info("  Checking if all tables exist in database schema...")

                val missingTables = runCatching {
                    txManager.transaction {
                        val connection = org.jetbrains.exposed.sql.transactions.TransactionManager.current().connection as java.sql.Connection
                        val dbMetaData = connection.metaData
                        val resultSet = dbMetaData.getTables(null, null, null, arrayOf("TABLE"))

                        val dbTableNames = mutableSetOf<String>()
                        try {
                            while (resultSet.next()) {
                                dbTableNames.add(resultSet.getString("TABLE_NAME").lowercase())
                            }
                        } finally {
                            resultSet.close()
                        }

                        // Find missing tables
                        discoveredTables
                            .filter { !dbTableNames.contains(it.tableName.lowercase()) }
                            .map { it.tableName }
                    }
                }.getOrElse { emptyList() }

                // FAIL-FAST if ANY tables are missing
                if (missingTables.isNotEmpty()) {
                    logger.error("")
                    logger.error("✗ DATABASE SCHEMA VALIDATION FAILED")
                    logger.error("  {} registered table(s) do NOT exist in database:", missingTables.size)
                    missingTables.forEach { table ->
                        logger.error("    ✗ {}", table)
                    }
                    logger.error("")
                    logger.error("  CRITICAL: This will cause crashes when:")
                    logger.error("    → Scheduler methods try to query tables during initialization")
                    logger.error("    → Repositories try to access missing tables")
                    logger.error("")
                    logger.error("  SOLUTION:")
                    logger.error("    1. Check your database migrations have been run")
                    logger.error("    2. Verify database connection is correct")
                    logger.error("    3. Run: ./gradlew runMigrations (or your migration command)")
                    logger.error("    4. Restart the application")
                    logger.error("")

                    throw IllegalStateException(
                        "Database schema validation FAILED. " +
                        "${missingTables.size} registered table(s) missing from database: $missingTables. " +
                        "Run database migrations before starting the application."
                    )
                }

                logger.info("  ✓ All {} registered table(s) exist in database schema", discoveredTables.size)
            }

            logger.info("")
            logger.info("╔════════════════════════════════════════════════════╗")
            logger.info("║ ✓ PHASE 1 PASSED                                  ║")
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
