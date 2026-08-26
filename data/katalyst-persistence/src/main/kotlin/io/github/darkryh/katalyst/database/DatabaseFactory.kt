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
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

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
     * Waits, for at most [timeout], until nothing is holding a pooled connection.
     *
     * The last line of defence in Katalyst's shutdown, and the only one that does not depend on the
     * application having declared anything. [ShutdownHook][io.github.darkryh.katalyst.di.lifecycle]
     * covers work that says how to stop itself, and running the application's teardown before this
     * one covers work wired to Ktor's events — but a `job.cancel()` is not a `join()`, and a
     * coroutine parked inside a blocking JDBC call does not observe cancellation until that call
     * returns. Between "stop was requested" and "the driver came back" there is a window in which
     * closing the pool severs a live socket, and the application sees `SQLSTATE 08006` from work it
     * had already told to stop.
     *
     * So the pool is asked to go quiet before it is closed. In the ordinary case every connection is
     * already back and this returns on the first check.
     *
     * Bounded on purpose. Waiting forever would trade a noisy shutdown for one that never finishes,
     * and background work that is *still running* is not something this can fix — only report, which
     * it does: the returned [DatabaseQuiesceResult] names how many connections were still checked
     * out, and that is a far better diagnosis than the stack trace their next statement would throw.
     *
     * Never throws: it is called on the way out, where an exception can only make things worse.
     */
    @KatalystInternalApi
    fun quiesce(timeout: Duration = DEFAULT_QUIESCE_TIMEOUT): DatabaseQuiesceResult {
        val startedAt = System.nanoTime()
        fun elapsedMillis() = (System.nanoTime() - startedAt) / 1_000_000

        val activeAtStart = runCatching { activeConnections() }.getOrDefault(0)
        if (closed.get() || activeAtStart == 0) {
            return DatabaseQuiesceResult(activeAtStart, activeAtStart, elapsedMillis(), drained = activeAtStart == 0)
        }

        logger.debug("Waiting up to {} for {} active connection(s) to be returned", timeout, activeAtStart)
        val deadlineNanos = startedAt + timeout.inWholeNanoseconds
        var active = activeAtStart
        while (active > 0 && System.nanoTime() < deadlineNanos) {
            runCatching { Thread.sleep(QUIESCE_POLL_INTERVAL.inWholeMilliseconds) }
                .onFailure {
                    Thread.currentThread().interrupt()
                    return DatabaseQuiesceResult(activeAtStart, active, elapsedMillis(), drained = false)
                }
            active = runCatching { activeConnections() }.getOrDefault(0)
        }

        val result = DatabaseQuiesceResult(activeAtStart, active, elapsedMillis(), drained = active == 0)
        if (result.drained) {
            logger.debug("Connection pool went quiet after {} ms", result.waitedMillis)
        } else {
            logger.warn(
                "Connection pool still has {} active connection(s) after waiting {} ms - something is " +
                    "still using the database during shutdown. Give that work a ShutdownHook so it is " +
                    "stopped before the pool closes; otherwise its next statement will fail as the " +
                    "pool goes away.",
                result.activeAtEnd,
                result.waitedMillis,
            )
        }
        return result
    }

    private fun activeConnections(): Int =
        if (dataSource.isClosed) 0 else dataSource.hikariPoolMXBean?.activeConnections ?: 0

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

/**
 * How long a shutdown waits for in-flight database work before the pool is closed anyway.
 *
 * Short: by the time this runs the application's own teardown has already been given its turn, so a
 * connection still checked out means something is misbehaving, and the wait exists to make that
 * case survivable rather than to accommodate it.
 */
private val DEFAULT_QUIESCE_TIMEOUT: Duration = 2.seconds

/** How often [DatabaseFactory.quiesce] re-checks the pool while waiting. */
private val QUIESCE_POLL_INTERVAL: Duration = 25.milliseconds

/** What [DatabaseFactory.quiesce] observed. */
data class DatabaseQuiesceResult(
    val activeAtStart: Int,
    val activeAtEnd: Int,
    val waitedMillis: Long,
    val drained: Boolean,
)

data class DatabasePoolSnapshot(
    val active: Int,
    val idle: Int,
    val pending: Int,
    val total: Int,
    val closed: Boolean,
)
