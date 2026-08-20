package io.github.darkryh.katalyst.database

import io.github.darkryh.katalyst.config.DatabaseConfig
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import java.sql.Connection
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ManagedSqlExecutorTest {

    private var factory: DatabaseFactory? = null

    @AfterTest
    fun tearDown() {
        factory?.close()
        factory = null
    }

    @Test
    fun `executeUpdate and query should use managed datasource connection`() = runTest {
        val sqlExecutor = createExecutor()
        sqlExecutor.executeUpdate("CREATE TABLE test_users (id INT PRIMARY KEY, name VARCHAR(120))")
        val inserted = sqlExecutor.executeUpdate(
            "INSERT INTO test_users (id, name) VALUES (?, ?)",
            listOf(1, "alice")
        )

        val users = sqlExecutor.query(
            "SELECT id, name FROM test_users ORDER BY id",
            mapper = { row -> row.getInt("id") to row.getString("name") }
        )

        assertEquals(1, inserted)
        assertEquals(listOf(1 to "alice"), users)
    }

    @Test
    fun `queryOne should return null when result set is empty`() = runTest {
        val sqlExecutor = createExecutor()
        sqlExecutor.executeUpdate("CREATE TABLE test_users (id INT PRIMARY KEY, name VARCHAR(120))")

        val user = sqlExecutor.queryOne(
            "SELECT id, name FROM test_users WHERE id = ?",
            parameters = listOf(99),
            mapper = { row -> row.getInt("id") to row.getString("name") }
        )

        assertEquals(null, user)
    }

    @Test
    fun `executeBatch should support bootstrap schema preconditions`() = runTest {
        val sqlExecutor = createExecutor()
        sqlExecutor.executeBatch(
            listOf(
                "CREATE TABLE test_bootstrap (id INT PRIMARY KEY, enabled BOOLEAN)",
                "INSERT INTO test_bootstrap (id, enabled) VALUES (1, TRUE)"
            )
        )

        val rows = sqlExecutor.queryOne(
            "SELECT COUNT(*) AS total FROM test_bootstrap",
            mapper = { row -> row.getInt("total") }
        )

        assertEquals(1, rows)
    }

    @Test
    fun `withConnection should reuse current Exposed transaction connection`() = runTest {
        val localFactory = createFactory()
        val sqlExecutor = localFactory.createSqlExecutor()

        suspendTransaction(localFactory.database) {
            val txConnection = connection.connection as Connection
            val actual = sqlExecutor.withConnection { connection -> connection }
            assertSame(txConnection, actual)
        }
    }

    @Test
    fun `query should bind null parameters`() = runTest {
        val sqlExecutor = createExecutor()
        sqlExecutor.executeUpdate("CREATE TABLE test_notes (id INT PRIMARY KEY, note VARCHAR(120))")
        sqlExecutor.executeUpdate(
            "INSERT INTO test_notes (id, note) VALUES (?, ?)",
            listOf(1, null)
        )

        val isNull = sqlExecutor.queryOne(
            "SELECT note FROM test_notes WHERE id = ?",
            parameters = listOf(1),
            mapper = { row -> row.getString("note") == null }
        )

        assertNotNull(isNull)
        assertTrue(isNull)
    }

    private fun createExecutor(): SqlExecutor = createFactory().createSqlExecutor()

    private fun createFactory(): DatabaseFactory {
        val localFactory = DatabaseFactory.create(createConfig())
        factory = localFactory
        return localFactory
    }

    private fun createConfig(): DatabaseConfig {
        return DatabaseConfig(
            url = "jdbc:h2:mem:managed_sql_${System.nanoTime()};DB_CLOSE_DELAY=-1",
            driver = "org.h2.Driver",
            username = "sa",
            password = ""
        )
    }
}
