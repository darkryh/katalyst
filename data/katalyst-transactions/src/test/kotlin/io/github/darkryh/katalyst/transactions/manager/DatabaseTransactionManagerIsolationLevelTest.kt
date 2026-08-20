package io.github.darkryh.katalyst.transactions.manager

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.github.darkryh.katalyst.transactions.config.TransactionConfig
import io.github.darkryh.katalyst.transactions.config.TransactionIsolationLevel
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.core.Transaction
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.slf4j.LoggerFactory
import java.io.PrintWriter
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException
import java.util.concurrent.atomic.AtomicInteger
import javax.sql.DataSource
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Behavioural tests for [TransactionConfig.isolationLevel].
 *
 * The configured level used to be accepted and then dropped on the floor: the manager opened
 * `suspendTransaction(database)` without an isolation argument, so an application asking for
 * `SERIALIZABLE` silently got whatever the pool/driver default was. Every assertion here reads
 * the **actual** `java.sql.Connection.getTransactionIsolation()` from inside the running
 * transaction, so it fails if the setting is ignored again.
 */
class DatabaseTransactionManagerIsolationLevelTest {

    private val dbCounter = AtomicInteger(0)

    private val managerLogger = LoggerFactory.getLogger(DatabaseTransactionManager::class.java) as Logger
    private val appender = ListAppender<ILoggingEvent>()

    init {
        appender.start()
        managerLogger.addAppender(appender)
    }

    @AfterTest
    fun resetAppender() {
        appender.list.clear()
    }

    /** The isolation level the driver is actually running this transaction at. */
    private fun Transaction.actualJdbcIsolation(): Int {
        val raw = (this as JdbcTransaction).connection.connection
        return (raw as Connection).transactionIsolation
    }

    private fun urlFor(name: String): String =
        "jdbc:h2:mem:${name}_${dbCounter.incrementAndGet()};DB_CLOSE_DELAY=-1;MODE=PostgreSQL"

    private fun directDatabase(name: String): Database =
        Database.connect(url = urlFor(name), driver = "org.h2.Driver")

    /**
     * Stands in for HikariCP's `transactionIsolation` setting: every connection handed out is
     * pre-set to [isolation], exactly like a pool-configured default.
     */
    private fun pooledDatabase(name: String, isolation: Int): Database =
        Database.connect(PresetIsolationDataSource(urlFor(name), isolation))

    // ========== The configured level reaches the driver ==========

    @Test
    fun `SERIALIZABLE is applied to the JDBC connection`() = runBlocking {
        val manager = DatabaseTransactionManager(directDatabase("isolation_serializable"))

        val actual = manager.transaction(
            config = TransactionConfig(isolationLevel = TransactionIsolationLevel.SERIALIZABLE)
        ) {
            actualJdbcIsolation()
        }

        assertEquals(
            Connection.TRANSACTION_SERIALIZABLE,
            actual,
            "TransactionConfig.isolationLevel = SERIALIZABLE must reach the JDBC connection"
        )
    }

    @Test
    fun `REPEATABLE_READ is applied to the JDBC connection`() = runBlocking {
        val manager = DatabaseTransactionManager(directDatabase("isolation_repeatable_read"))

        val actual = manager.transaction(
            config = TransactionConfig(isolationLevel = TransactionIsolationLevel.REPEATABLE_READ)
        ) {
            actualJdbcIsolation()
        }

        assertEquals(Connection.TRANSACTION_REPEATABLE_READ, actual)
    }

    @Test
    fun `READ_UNCOMMITTED is applied to the JDBC connection`() = runBlocking {
        val manager = DatabaseTransactionManager(directDatabase("isolation_read_uncommitted"))

        val actual = manager.transaction(
            config = TransactionConfig(isolationLevel = TransactionIsolationLevel.READ_UNCOMMITTED)
        ) {
            actualJdbcIsolation()
        }

        assertEquals(Connection.TRANSACTION_READ_UNCOMMITTED, actual)
    }

    @Test
    fun `the manager-wide default transaction config isolation is applied`() = runBlocking {
        val manager = DatabaseTransactionManager(
            database = directDatabase("isolation_manager_default"),
            defaultTransactionConfig = TransactionConfig(
                isolationLevel = TransactionIsolationLevel.SERIALIZABLE
            )
        )

        // No per-call config: the manager-wide default must still be honoured.
        val actual = manager.transaction { actualJdbcIsolation() }

        assertEquals(Connection.TRANSACTION_SERIALIZABLE, actual)
    }

    @Test
    fun `each transaction gets its own configured level`() = runBlocking {
        val manager = DatabaseTransactionManager(directDatabase("isolation_per_transaction"))

        val serializable = manager.transaction(
            config = TransactionConfig(isolationLevel = TransactionIsolationLevel.SERIALIZABLE)
        ) { actualJdbcIsolation() }

        val repeatableRead = manager.transaction(
            config = TransactionConfig(isolationLevel = TransactionIsolationLevel.REPEATABLE_READ)
        ) { actualJdbcIsolation() }

        assertEquals(Connection.TRANSACTION_SERIALIZABLE, serializable)
        assertEquals(Connection.TRANSACTION_REPEATABLE_READ, repeatableRead)
    }

    // ========== Interaction with the pool-level default ==========

    @Test
    fun `a non-default level overrides the pool default`() = runBlocking {
        val manager = DatabaseTransactionManager(
            pooledDatabase("isolation_override_pool", Connection.TRANSACTION_READ_COMMITTED)
        )

        val actual = manager.transaction(
            config = TransactionConfig(isolationLevel = TransactionIsolationLevel.SERIALIZABLE)
        ) {
            actualJdbcIsolation()
        }

        assertEquals(
            Connection.TRANSACTION_SERIALIZABLE,
            actual,
            "An explicit non-default level must win over the pool-configured default"
        )
    }

    @Test
    fun `the default config leaves the pool-configured isolation untouched`() = runBlocking {
        // Regression guard for the documented nuance: katalyst-persistence configures HikariCP's
        // transactionIsolation (default TRANSACTION_REPEATABLE_READ). A transaction that does not
        // ask for a specific level must not silently downgrade that pool default.
        val manager = DatabaseTransactionManager(
            pooledDatabase("isolation_pool_default_kept", Connection.TRANSACTION_SERIALIZABLE)
        )

        val actual = manager.transaction { actualJdbcIsolation() }

        assertEquals(
            Connection.TRANSACTION_SERIALIZABLE,
            actual,
            "The default TransactionConfig must inherit the pool default rather than override it"
        )
    }

    // ========== Inertness is made loud where it cannot be honoured ==========

    @Test
    fun `joining an active transaction with a different requested level warns`() = runBlocking {
        val manager = DatabaseTransactionManager(directDatabase("isolation_join_warn"))

        manager.transaction(
            config = TransactionConfig(isolationLevel = TransactionIsolationLevel.READ_UNCOMMITTED)
        ) {
            // Joins the outer transaction: the requested SERIALIZABLE cannot be applied to an
            // already-open connection, so the framework must say so instead of silently ignoring it.
            manager.transaction(
                config = TransactionConfig(isolationLevel = TransactionIsolationLevel.SERIALIZABLE)
            ) {
                assertEquals(Connection.TRANSACTION_READ_UNCOMMITTED, actualJdbcIsolation())
            }
        }

        val warnings = appender.list.filter { it.level == Level.WARN }
        assertTrue(
            warnings.any {
                it.formattedMessage.contains("SERIALIZABLE") &&
                    it.formattedMessage.contains("joining")
            },
            "Expected a WARN about the un-appliable isolation level, got: ${warnings.map { it.formattedMessage }}"
        )
    }

    @Test
    fun `joining an active transaction with the same level does not warn`() = runBlocking {
        val manager = DatabaseTransactionManager(directDatabase("isolation_join_no_warn"))

        manager.transaction(
            config = TransactionConfig(isolationLevel = TransactionIsolationLevel.SERIALIZABLE)
        ) {
            manager.transaction(
                config = TransactionConfig(isolationLevel = TransactionIsolationLevel.SERIALIZABLE)
            ) {
                actualJdbcIsolation()
            }
        }

        assertTrue(
            appender.list.none { it.level == Level.WARN && it.formattedMessage.contains("isolation") },
            "A matching isolation level must not produce warning noise: " +
                "${appender.list.filter { it.level == Level.WARN }.map { it.formattedMessage }}"
        )
    }

    /**
     * Minimal [DataSource] that pre-sets the isolation level of every connection it hands out,
     * mirroring what HikariCP does with its `transactionIsolation` property.
     */
    private class PresetIsolationDataSource(
        private val url: String,
        private val isolation: Int
    ) : DataSource {
        override fun getConnection(): Connection =
            DriverManager.getConnection(url).apply { transactionIsolation = isolation }

        override fun getConnection(username: String?, password: String?): Connection = connection

        override fun getLogWriter(): PrintWriter? = null

        override fun setLogWriter(out: PrintWriter?) = Unit

        override fun setLoginTimeout(seconds: Int) = Unit

        override fun getLoginTimeout(): Int = 0

        override fun getParentLogger(): java.util.logging.Logger =
            java.util.logging.Logger.getLogger(PresetIsolationDataSource::class.java.name)

        override fun <T : Any?> unwrap(iface: Class<T>?): T =
            throw SQLException("unwrap is not supported by the test data source")

        override fun isWrapperFor(iface: Class<*>?): Boolean = false
    }
}
