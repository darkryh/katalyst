package io.github.darkryh.katalyst.database

import io.github.darkryh.katalyst.config.DatabaseConfig
import org.slf4j.LoggerFactory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DatabaseFactoryStatementExtensionsTest {
    private var factory: DatabaseFactory? = null

    @AfterTest
    fun tearDown() {
        factory?.close()
        factory = null
    }

    @Test
    fun `withStatement executes block with requested autoCommit`() {
        val localFactory = createFactory()
        factory = localFactory

        val observedAutoCommit = kotlinx.coroutines.runBlocking {
            localFactory.withStatement(autoCommit = false) { statement ->
                statement.connection.autoCommit
            }
        }

        assertFalse(observedAutoCommit)
    }

    @Test
    fun `withStatement skip overload swallows failures from statement block`() {
        val localFactory = createFactory()
        factory = localFactory
        val logger = LoggerFactory.getLogger("DatabaseFactoryStatementExtensionsTest")
        var executed = false

        kotlinx.coroutines.runBlocking {
            localFactory.withStatement(
                log = logger,
                skipLabel = "statement extension failure path"
            ) {
                executed = true
                error("forced failure")
            }
        }

        assertTrue(executed)
    }

    @Test
    fun `withStatement can execute simple ddl and dml`() {
        val localFactory = createFactory()
        factory = localFactory

        val insertedRows = kotlinx.coroutines.runBlocking {
            localFactory.withStatement { statement ->
                statement.execute("CREATE TABLE IF NOT EXISTS statement_test(id INT PRIMARY KEY, name VARCHAR(50))")
                statement.executeUpdate("INSERT INTO statement_test(id, name) VALUES (1, 'ok')")
            }
        }

        assertEquals(1, insertedRows)
    }

    private fun createFactory(): DatabaseFactory {
        return DatabaseFactory.create(
            DatabaseConfig(
                url = "jdbc:h2:mem:statement_ext_${System.nanoTime()};DB_CLOSE_DELAY=-1",
                driver = "org.h2.Driver",
                username = "sa",
                password = "",
                maxPoolSize = 2
            )
        )
    }
}
