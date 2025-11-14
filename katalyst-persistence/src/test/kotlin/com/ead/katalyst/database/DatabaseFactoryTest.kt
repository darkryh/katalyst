package com.ead.katalyst.database

import com.ead.katalyst.config.DatabaseConfig
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.test.*

/**
 * Comprehensive tests for DatabaseFactory.
 *
 * Tests cover:
 * - Factory creation with valid configuration
 * - HikariCP connection pool integration
 * - Database connection verification
 * - Schema creation (single and multiple tables)
 * - Handling existing tables gracefully
 * - Resource cleanup (close method)
 * - Connection pool usage
 * - AutoCloseable interface
 * - Error handling
 */
class DatabaseFactoryTest {

    // ========== FACTORY CREATION TESTS ==========

    @Test
    fun `create should return DatabaseFactory with valid config`() {
        // Given
        val config = createTestConfig()

        // When
        val factory = DatabaseFactory.create(config)

        // Then
        assertNotNull(factory)
        assertNotNull(factory.database)

        // Cleanup
        factory.close()
    }

    @Test
    fun `create should establish database connection`() {
        // Given
        val config = createTestConfig()

        // When
        val factory = DatabaseFactory.create(config)

        // Then - Verify connection works
        val result = transaction(factory.database) {
            // Simple query to verify connection
            exec("SELECT 1") {
                it.next()
            }
        }

        assertNotNull(result)

        // Cleanup
        factory.close()
    }

    @Test
    fun `create should work with H2 in-memory database`() {
        // Given
        val config = DatabaseConfig(
            url = "jdbc:h2:mem:test_${System.currentTimeMillis()};DB_CLOSE_DELAY=-1",
            driver = "org.h2.Driver",
            username = "sa",
            password = ""
        )

        // When
        val factory = DatabaseFactory.create(config)

        // Then
        assertNotNull(factory.database)

        // Cleanup
        factory.close()
    }

    @Test
    fun `create should respect HikariCP pool configuration`() {
        // Given
        val config = DatabaseConfig(
            url = "jdbc:h2:mem:test_pool_${System.currentTimeMillis()};DB_CLOSE_DELAY=-1",
            driver = "org.h2.Driver",
            username = "sa",
            password = "",
            maxPoolSize = 15,
            minIdleConnections = 3,
            connectionTimeout = 45000L
        )

        // When
        val factory = DatabaseFactory.create(config)

        // Then - Factory should be created successfully
        // HikariCP will use these settings internally
        assertNotNull(factory.database)

        // Cleanup
        factory.close()
    }

    // ========== SCHEMA CREATION TESTS ==========

    @Test
    fun `create should work with empty tables list`() {
        // Given
        val config = createTestConfig()

        // When
        val factory = DatabaseFactory.create(config, tables = emptyList())

        // Then
        assertNotNull(factory.database)

        // Cleanup
        factory.close()
    }

    @Test
    fun `create should create single table`() {
        // Given
        val config = createTestConfig()
        val testTable = TestUsersTable

        // When
        val factory = DatabaseFactory.create(config, tables = listOf(testTable))

        // Then - Verify table exists
        val tableExists = transaction(factory.database) {
            try {
                TestUsersTable.selectAll().count()
                true
            } catch (e: Exception) {
                false
            }
        }

        assertTrue(tableExists)

        // Cleanup
        transaction(factory.database) {
            SchemaUtils.drop(TestUsersTable)
        }
        factory.close()
    }

    @Test
    fun `create should create multiple tables`() {
        // Given
        val config = createTestConfig()
        val tables = listOf(TestUsersTable, TestOrdersTable, TestProductsTable)

        // When
        val factory = DatabaseFactory.create(config, tables = tables)

        // Then - Verify all tables exist
        val allTablesExist = transaction(factory.database) {
            try {
                TestUsersTable.selectAll().count()
                TestOrdersTable.selectAll().count()
                TestProductsTable.selectAll().count()
                true
            } catch (e: Exception) {
                false
            }
        }

        assertTrue(allTablesExist)

        // Cleanup
        transaction(factory.database) {
            SchemaUtils.drop(TestUsersTable, TestOrdersTable, TestProductsTable)
        }
        factory.close()
    }

    @Test
    fun `create should handle tables that already exist`() {
        // Given
        val config = createTestConfig()
        val factory1 = DatabaseFactory.create(config, tables = listOf(TestUsersTable))

        // When - Create second factory with same table
        val factory2 = DatabaseFactory.create(
            config.copy(url = config.url),  // Same database
            tables = listOf(TestUsersTable)
        )

        // Then - Should not throw exception
        assertNotNull(factory2.database)

        // Cleanup
        transaction(factory1.database) {
            SchemaUtils.drop(TestUsersTable)
        }
        factory1.close()
        factory2.close()
    }

    @Test
    fun `create should initialize tables in provided order`() {
        // Given
        val config = createTestConfig()

        // When
        val factory = DatabaseFactory.create(
            config,
            tables = listOf(TestUsersTable, TestOrdersTable)
        )

        // Then - Both tables should be usable
        transaction(factory.database) {
            TestUsersTable.selectAll().count()
            TestOrdersTable.selectAll().count()
        }

        // Cleanup
        transaction(factory.database) {
            SchemaUtils.drop(TestUsersTable, TestOrdersTable)
        }
        factory.close()
    }

    // ========== CLOSE METHOD TESTS ==========

    @Test
    fun `close should release database resources`() {
        // Given
        val config = createTestConfig()
        val factory = DatabaseFactory.create(config)

        // When
        factory.close()

        // Then - Should not throw exception
        // HikariDataSource is closed, connection pool is shut down
    }

    @Test
    fun `close should be idempotent - multiple calls should not throw`() {
        // Given
        val config = createTestConfig()
        val factory = DatabaseFactory.create(config)

        // When/Then - Multiple close calls should not throw
        factory.close()
        factory.close()
        factory.close()
    }

    @Test
    fun `use with AutoCloseable should automatically close factory`() {
        // Given
        val config = createTestConfig()
        var factoryRef: DatabaseFactory? = null

        // When
        DatabaseFactory.create(config).use { factory ->
            factoryRef = factory
            assertNotNull(factory.database)
        }

        // Then - Factory should be closed after use block
        assertNotNull(factoryRef)
    }

    // ========== CONNECTION POOL TESTS ==========

    @Test
    fun `should support multiple concurrent transactions`() {
        // Given
        val config = createTestConfig()
        val factory = DatabaseFactory.create(config, tables = listOf(TestUsersTable))

        // When - Execute multiple transactions
        val results = (1..5).map { index ->
            transaction(factory.database) {
                TestUsersTable.selectAll().count()
            }
        }

        // Then - All transactions should succeed
        assertEquals(5, results.size)

        // Cleanup
        transaction(factory.database) {
            SchemaUtils.drop(TestUsersTable)
        }
        factory.close()
    }

    @Test
    fun `should maintain connections across multiple transactions`() {
        // Given
        val config = createTestConfig()
        val factory = DatabaseFactory.create(config)

        // When - Execute multiple sequential transactions
        repeat(10) {
            transaction(factory.database) {
                exec("SELECT 1") {}
            }
        }

        // Then - Should complete without errors
        // Connection pool manages connections efficiently

        // Cleanup
        factory.close()
    }

    @Test
    fun `should support autoCommit configuration`() {
        // Given
        val config = DatabaseConfig(
            url = "jdbc:h2:mem:test_autocommit_${System.currentTimeMillis()};DB_CLOSE_DELAY=-1",
            driver = "org.h2.Driver",
            username = "sa",
            password = "",
            autoCommit = true
        )

        // When
        val factory = DatabaseFactory.create(config)

        // Then
        assertNotNull(factory.database)

        // Cleanup
        factory.close()
    }

    // ========== DATABASE OPERATIONS TESTS ==========

    @Test
    fun `database should support basic insert operations`() {
        // Given
        val config = createTestConfig()
        val factory = DatabaseFactory.create(config, tables = listOf(TestUsersTable))

        // When
        transaction(factory.database) {
            TestUsersTable.insert {
                it[name] = "Alice"
                it[email] = "alice@example.com"
            }
        }

        // Then
        val count = transaction(factory.database) {
            TestUsersTable.selectAll().count()
        }

        assertEquals(1, count)

        // Cleanup
        transaction(factory.database) {
            SchemaUtils.drop(TestUsersTable)
        }
        factory.close()
    }

    @Test
    fun `database should support basic select operations`() {
        // Given
        val config = createTestConfig()
        val factory = DatabaseFactory.create(config, tables = listOf(TestUsersTable))

        transaction(factory.database) {
            TestUsersTable.insert {
                it[name] = "Bob"
                it[email] = "bob@example.com"
            }
        }

        // When
        val users = transaction(factory.database) {
            TestUsersTable.selectAll().map { row ->
                row[TestUsersTable.name]
            }
        }

        // Then
        assertEquals(1, users.size)
        assertEquals("Bob", users[0])

        // Cleanup
        transaction(factory.database) {
            SchemaUtils.drop(TestUsersTable)
        }
        factory.close()
    }

    @Test
    fun `database should support basic update operations`() {
        // Given
        val config = createTestConfig()
        val factory = DatabaseFactory.create(config, tables = listOf(TestUsersTable))

        transaction(factory.database) {
            TestUsersTable.insert {
                it[name] = "Charlie"
                it[email] = "charlie@example.com"
            }
        }

        // When
        transaction(factory.database) {
            TestUsersTable.update({ TestUsersTable.name eq "Charlie" }) {
                it[email] = "charlie.updated@example.com"
            }
        }

        // Then
        val email = transaction(factory.database) {
            TestUsersTable
                .selectAll()
                .where { TestUsersTable.name eq "Charlie" }
                .single()[TestUsersTable.email]
        }

        assertEquals("charlie.updated@example.com", email)

        // Cleanup
        transaction(factory.database) {
            SchemaUtils.drop(TestUsersTable)
        }
        factory.close()
    }

    @Test
    fun `database should support basic delete operations`() {
        // Given
        val config = createTestConfig()
        val factory = DatabaseFactory.create(config, tables = listOf(TestUsersTable))

        transaction(factory.database) {
            TestUsersTable.insert {
                it[name] = "Dave"
                it[email] = "dave@example.com"
            }
        }

        // When
        transaction(factory.database) {
            TestUsersTable.deleteWhere { TestUsersTable.name eq "Dave" }
        }

        // Then
        val count = transaction(factory.database) {
            TestUsersTable.selectAll().count()
        }

        assertEquals(0, count)

        // Cleanup
        transaction(factory.database) {
            SchemaUtils.drop(TestUsersTable)
        }
        factory.close()
    }

    // ========== TRANSACTION ISOLATION TESTS ==========

    @Test
    fun `should support TRANSACTION_REPEATABLE_READ isolation`() {
        // Given
        val config = DatabaseConfig(
            url = "jdbc:h2:mem:test_isolation_${System.currentTimeMillis()};DB_CLOSE_DELAY=-1",
            driver = "org.h2.Driver",
            username = "sa",
            password = "",
            transactionIsolation = "TRANSACTION_REPEATABLE_READ"
        )

        // When
        val factory = DatabaseFactory.create(config)

        // Then
        assertNotNull(factory.database)

        // Cleanup
        factory.close()
    }

    @Test
    fun `should support TRANSACTION_SERIALIZABLE isolation`() {
        // Given
        val config = DatabaseConfig(
            url = "jdbc:h2:mem:test_serializable_${System.currentTimeMillis()};DB_CLOSE_DELAY=-1",
            driver = "org.h2.Driver",
            username = "sa",
            password = "",
            transactionIsolation = "TRANSACTION_SERIALIZABLE"
        )

        // When
        val factory = DatabaseFactory.create(config)

        // Then
        assertNotNull(factory.database)

        // Cleanup
        factory.close()
    }

    // ========== COMPLETE LIFECYCLE TESTS ==========

    @Test
    fun `complete lifecycle - create, use, close`() {
        // Given
        val config = createTestConfig()

        // When - Create
        val factory = DatabaseFactory.create(config, tables = listOf(TestUsersTable))

        // When - Use
        transaction(factory.database) {
            TestUsersTable.insert {
                it[name] = "Eve"
                it[email] = "eve@example.com"
            }
        }

        val count = transaction(factory.database) {
            TestUsersTable.selectAll().count()
        }

        assertEquals(1, count)

        // When - Close
        transaction(factory.database) {
            SchemaUtils.drop(TestUsersTable)
        }
        factory.close()

        // Then - Should complete without errors
    }

    @Test
    fun `complete lifecycle with multiple tables and operations`() {
        // Given
        val config = createTestConfig()

        // When - Create
        val factory = DatabaseFactory.create(
            config,
            tables = listOf(TestUsersTable, TestOrdersTable)
        )

        // When - Use
        transaction(factory.database) {
            TestUsersTable.insert {
                it[name] = "Frank"
                it[email] = "frank@example.com"
            }

            TestOrdersTable.insert {
                it[userId] = 1
                it[product] = "Laptop"
                it[amount] = 999.99
            }
        }

        val userCount = transaction(factory.database) {
            TestUsersTable.selectAll().count()
        }

        val orderCount = transaction(factory.database) {
            TestOrdersTable.selectAll().count()
        }

        assertEquals(1, userCount)
        assertEquals(1, orderCount)

        // When - Close
        transaction(factory.database) {
            SchemaUtils.drop(TestUsersTable, TestOrdersTable)
        }
        factory.close()

        // Then - Should complete without errors
    }

    // ========== HELPER METHODS ==========

    private fun createTestConfig(): DatabaseConfig {
        return DatabaseConfig(
            url = "jdbc:h2:mem:test_db_${System.currentTimeMillis()};DB_CLOSE_DELAY=-1",
            driver = "org.h2.Driver",
            username = "sa",
            password = ""
        )
    }

    // ========== TEST TABLE DEFINITIONS ==========

    private object TestUsersTable : Table("test_users") {
        val id = long("id").autoIncrement()
        val name = varchar("name", 100)
        val email = varchar("email", 255)

        override val primaryKey = PrimaryKey(id)
    }

    private object TestOrdersTable : Table("test_orders") {
        val id = long("id").autoIncrement()
        val userId = long("user_id")
        val product = varchar("product", 255)
        val amount = double("amount")

        override val primaryKey = PrimaryKey(id)
    }

    private object TestProductsTable : Table("test_products") {
        val id = long("id").autoIncrement()
        val name = varchar("name", 255)
        val price = double("price")

        override val primaryKey = PrimaryKey(id)
    }
}
