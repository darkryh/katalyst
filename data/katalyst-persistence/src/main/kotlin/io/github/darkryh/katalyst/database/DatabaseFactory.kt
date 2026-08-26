package io.github.darkryh.katalyst.database

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.github.darkryh.katalyst.config.DatabaseConfig
import io.github.darkryh.katalyst.core.annotation.KatalystInternalApi
import io.github.darkryh.katalyst.database.DatabaseFactory.Companion.create
import org.jetbrains.annotations.TestOnly
import org.jetbrains.exposed.v1.core.Schema
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.exists
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.vendors.currentDialectMetadata
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicBoolean

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
 *
 * Framework-internal: applications inject [SqlExecutor] for raw SQL rather than this
 * pool/lifecycle/schema infrastructure. The Exposed [database] handle remains reachable
 * for advanced migration use. See [KatalystInternalApi].
 */
@KatalystInternalApi
class DatabaseFactory private constructor(
    val database: Database,
    private val dataSource: HikariDataSource
) : AutoCloseable {

    private val closed = AtomicBoolean(false)

    /**
     * Creates a managed SQL executor backed by this factory's datasource.
     *
     * The returned executor reuses the current Exposed transaction connection when available,
     * otherwise it opens pooled connections from this factory.
     */
    fun createSqlExecutor(): SqlExecutor = ManagedSqlExecutor(dataSource)

    /** Returns connection-pool cardinalities without exposing the datasource. */
    fun poolSnapshot(): DatabasePoolSnapshot {
        val pool = dataSource.hikariPoolMXBean
        return DatabasePoolSnapshot(
            active = pool?.activeConnections ?: 0,
            idle = pool?.idleConnections ?: 0,
            pending = pool?.threadsAwaitingConnection ?: 0,
            total = pool?.totalConnections ?: 0,
            closed = dataSource.isClosed,
        )
    }

    companion object {
        private val logger = LoggerFactory.getLogger(DatabaseFactory::class.java)

        /**
         * Creates a new DatabaseFactory with the given configuration.
         *
         * @param config The database configuration
         * @return DatabaseFactory instance
         * @throws IllegalArgumentException if config is invalid
         * @throws Exception if database connection fails
         */
        fun create(config: DatabaseConfig): DatabaseFactory {
            // Never log the raw JDBC URL: it can carry credentials (userinfo or
            // password query params). Log a redacted form instead.
            logger.info("Initializing DatabaseFactory with URL: {}", sanitizeJdbcUrl(config.url))

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
            return DatabaseFactory(database, dataSource)
        }
    }

    @TestOnly
    fun createSchema(vararg schema : Schema, inBatch: Boolean = false) {
        transaction(database) {
            SchemaUtils.createSchema(*schema, inBatch = inBatch)
        }
    }

    @TestOnly
    fun createTable(vararg table : Table, inBatch: Boolean = false) {
        transaction(database) {
            SchemaUtils.create(*table, inBatch = inBatch)
        }
    }

    /**
     * Adds every column a [table] declares that the live table does not have yet, together with the
     * indices those new columns are part of.
     *
     * This is what makes "create what is missing" mean the same thing for a column as it does for a
     * table. [SchemaUtils.create] only ever issues `CREATE TABLE IF NOT EXISTS`, so a table that
     * already exists is skipped whole — a column added to the Kotlin [Table] afterwards never
     * reaches the database, and the first query that selects it fails at runtime instead of at boot.
     *
     * **Additive by construction, not by filtering.** The diff is computed here — live column names
     * from [currentDialectMetadata] against the ones the [Table] declares — and only genuinely
     * absent columns are turned into DDL. A [Column]'s own `ddl` is always the `ALTER TABLE ... ADD`
     * form, so nothing else can come out of this. That is deliberately NARROWER than Exposed's
     * [SchemaUtils.addMissingColumnsStatements], which despite the name also emits `ALTER COLUMN`
     * for an existing column whose type, nullability or default drifted, and drop/recreate pairs for
     * foreign keys whose rules changed. Those are not "missing" anything: they rewrite a column that
     * already holds data, they routinely fail outright (`ALTER COLUMN ... NOT NULL` against rows
     * that are null), and deciding them is a migration's job. Katalyst reports them instead — that
     * is what `CREATE_MISSING_AND_VALIDATE` is for — and never executes them at boot.
     *
     * The one failure this CAN produce is `ADD COLUMN x NOT NULL` with no default against a table
     * that already has rows, which every database rejects. That is a genuine "your schema and your
     * code disagree in a way only you can settle" and is surfaced as such by the caller.
     *
     * Tables that do not exist are filtered out first. Creating them is [createTable]'s job, and
     * Exposed's metadata reader throws a bare `IllegalStateException` rather than returning nothing
     * when asked for the columns of a table that is not there.
     *
     * Runs in its own transaction and commits, so callers see the new columns immediately (the
     * dialect metadata cache is reset for the same reason). Statements are executed one at a time
     * rather than as a JDBC batch: this only runs when something is genuinely missing, so the volume
     * is tiny, and a failure then names the exact statement the database rejected.
     *
     * @return the statements that were executed; empty when the live schema already had every column.
     */
    @KatalystInternalApi
    fun addMissingColumns(vararg table: Table): List<String> {
        if (table.isEmpty()) return emptyList()
        return transaction(database) {
            // SQLite (and anything else without ALTER TABLE ADD COLUMN) cannot do this at all;
            // emitting the DDL anyway would just trade a missing column for a boot failure.
            if (!db.supportsAlterTableWithAddColumn) return@transaction emptyList()

            val existingTables = table.filter { it.exists() }
            if (existingTables.isEmpty()) return@transaction emptyList()

            val liveColumns = currentDialectMetadata.tableColumns(*existingTables.toTypedArray())
            val statements = buildList {
                existingTables.forEach { target ->
                    val live = liveColumns[target].orEmpty()
                    if (live.isEmpty()) return@forEach

                    val missing = target.columns.filter { column ->
                        live.none { it.name.equals(column.nameUnquoted(), ignoreCase = true) }
                    }
                    if (missing.isEmpty()) return@forEach

                    missing.forEach { column -> addAll(column.ddl) }
                    target.indices
                        .filter { index -> index.columns.any { it in missing } }
                        .forEach { index -> addAll(index.createStatement()) }
                }
            }

            if (statements.isNotEmpty()) {
                statements.forEach { statement -> exec(statement) }
                commit()
                currentDialectMetadata.resetCaches()
            }
            statements
        }
    }

    /**
     * Closes the database connection and shuts down the connection pool.
     *
     * Should be called when the application shuts down to properly clean up resources.
     *
     * Two things are released, in this order:
     * 1. The Exposed registration. `Database.connect(dataSource)` put [database] into Exposed's
     *    *process-global* `TransactionManager` containers (a databases deque, a manager map and
     *    a coroutine context-key map) and made the most recently connected instance the implicit
     *    default for a bare `transaction { }`. Closing only the pool would leak one
     *    Database + manager + context key per create/close cycle — unbounded across hot reloads
     *    and across a test suite — and would leave a *closed* pool installed as the process
     *    default, so a later bare `transaction { }` (which the undo strategies use when their
     *    `database` is null) would fail with "HikariDataSource has been closed" and report a
     *    workflow rollback as FAILED_UNDO with no usable cause. Unregistering first also closes
     *    the window in which a new transaction could still resolve to a pool that is going away.
     * 2. The HikariCP pool itself.
     *
     * Idempotent: repeated calls are no-ops.
     */
    override fun close() {
        if (!closed.compareAndSet(false, true)) {
            logger.debug("DatabaseFactory already closed; ignoring repeated close()")
            return
        }

        logger.info("Closing DatabaseFactory and HikariDataSource")

        try {
            TransactionManager.closeAndUnregister(database)
            logger.debug("Unregistered database from Exposed TransactionManager registry")
        } catch (e: Exception) {
            logger.error("Error unregistering database from Exposed TransactionManager", e)
        }

        try {
            dataSource.close()
            logger.info("HikariDataSource closed successfully")
        } catch (e: Exception) {
            logger.error("Error closing HikariDataSource", e)
        }
    }
}

data class DatabasePoolSnapshot(
    val active: Int,
    val idle: Int,
    val pending: Int,
    val total: Int,
    val closed: Boolean,
)
