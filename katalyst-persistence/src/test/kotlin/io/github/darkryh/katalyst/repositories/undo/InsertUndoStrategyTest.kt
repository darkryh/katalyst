package io.github.darkryh.katalyst.repositories.undo

import io.github.darkryh.katalyst.transactions.workflow.SimpleTransactionOperation
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.test.*

/**
 * Comprehensive tests for InsertUndoStrategy.
 *
 * `canHandle()` is pure logic and is tested without a database. `undo()` actually
 * performs a compensating DELETE against a real H2 in-memory database, so its tests
 * set up a real table, insert a row, and assert the row is genuinely gone afterward -
 * proving the strategy performs the compensating mutation rather than just claiming
 * success.
 */
class InsertUndoStrategyTest {

    private lateinit var database: Database

    private object TestUsersTable : Table("insert_undo_test_users") {
        val id = long("id")
        val name = varchar("name", 100)

        override val primaryKey = PrimaryKey(id)
    }

    @BeforeTest
    fun setup() {
        database = Database.connect(
            url = "jdbc:h2:mem:test_insert_undo_${System.nanoTime()};DB_CLOSE_DELAY=-1",
            driver = "org.h2.Driver",
            user = "sa",
            password = ""
        )
        transaction(database) { SchemaUtils.create(TestUsersTable) }
    }

    @AfterTest
    fun teardown() {
        transaction(database) { SchemaUtils.drop(TestUsersTable) }
    }

    private fun strategy() = InsertUndoStrategy(database)

    private fun insertRow(id: Long, name: String) {
        transaction(database) {
            TestUsersTable.insert {
                it[TestUsersTable.id] = id
                it[TestUsersTable.name] = name
            }
        }
    }

    private fun rowCount(): Long = transaction(database) { TestUsersTable.selectAll().count() }

    private fun createOperation(
        workflowId: String = "workflow-insert",
        operationIndex: Int = 0,
        operationType: String = "INSERT",
        resourceType: String = "insert_undo_test_users",
        resourceId: String? = "1",
        undoData: Map<String, Any?>? = null,
        operationData: Map<String, Any?>? = null,
    ) = SimpleTransactionOperation(
        workflowId = workflowId,
        operationIndex = operationIndex,
        operationType = operationType,
        resourceType = resourceType,
        resourceId = resourceId,
        operationData = operationData,
        undoData = undoData
    )

    // ========== canHandle() TESTS ==========

    @Test
    fun `canHandle should return true for INSERT operation`() {
        assertTrue(strategy().canHandle("INSERT", "User"))
    }

    @Test
    fun `canHandle should return true for lowercase insert`() {
        assertTrue(strategy().canHandle("insert", "User"))
    }

    @Test
    fun `canHandle should return true for mixed case InSeRt`() {
        assertTrue(strategy().canHandle("InSeRt", "User"))
    }

    @Test
    fun `canHandle should return false for UPDATE operation`() {
        assertFalse(strategy().canHandle("UPDATE", "User"))
    }

    @Test
    fun `canHandle should return false for DELETE operation`() {
        assertFalse(strategy().canHandle("DELETE", "User"))
    }

    @Test
    fun `canHandle should return false for API_CALL operation`() {
        assertFalse(strategy().canHandle("API_CALL", "User"))
    }

    @Test
    fun `canHandle should ignore resourceType parameter`() {
        val s = strategy()
        assertTrue(s.canHandle("INSERT", "User"))
        assertTrue(s.canHandle("INSERT", "Order"))
        assertTrue(s.canHandle("INSERT", "Payment"))
    }

    // ========== undo() TESTS - actually performs the compensating DELETE ==========

    @Test
    fun `undo should actually delete the inserted row`() = runTest {
        // Given - a row that simulates what an INSERT operation produced
        insertRow(1L, "Alice")
        assertEquals(1, rowCount())

        val operation = createOperation(resourceId = "1")

        // When
        val result = strategy().undo(operation)

        // Then - the row is genuinely gone, not just a claimed success
        assertTrue(result)
        assertEquals(0, rowCount())
    }

    @Test
    fun `undo should only delete the targeted row`() = runTest {
        // Given
        insertRow(1L, "Alice")
        insertRow(2L, "Bob")

        // When
        val result = strategy().undo(createOperation(resourceId = "1"))

        // Then
        assertTrue(result)
        assertEquals(1, rowCount())
        val remaining = transaction(database) { TestUsersTable.selectAll().map { it[TestUsersTable.name] } }
        assertEquals(listOf("Bob"), remaining)
    }

    @Test
    fun `undo should return false and change nothing when resourceId is missing`() = runTest {
        // Given - no resourceId means we cannot identify which row to delete
        insertRow(1L, "Alice")
        val operation = createOperation(resourceId = null)

        // When
        val result = strategy().undo(operation)

        // Then - must fail closed, not silently claim success
        assertFalse(result)
        assertEquals(1, rowCount())
    }

    @Test
    fun `undo should return false for an invalid table identifier`() = runTest {
        // Given - resourceType is not a safe SQL identifier
        val operation = createOperation(resourceType = "bad; DROP TABLE insert_undo_test_users; --", resourceId = "1")

        // When
        val result = strategy().undo(operation)

        // Then
        assertFalse(result)
        // Table must still exist and be queryable
        assertEquals(0, rowCount())
    }

    @Test
    fun `undo should return false when the underlying table does not exist`() = runTest {
        // Given - a syntactically valid but non-existent table name
        val operation = createOperation(resourceType = "does_not_exist_table", resourceId = "1")

        // When
        val result = strategy().undo(operation)

        // Then - genuine DB failure must be reported as failure, not swallowed as success
        assertFalse(result)
    }

    @Test
    fun `undo should handle multiple deletions sequentially`() = runTest {
        // Given
        insertRow(1L, "User1")
        insertRow(2L, "User2")
        insertRow(3L, "User3")
        val s = strategy()

        // When
        val results = listOf("1", "2", "3").map { id -> s.undo(createOperation(resourceId = id)) }

        // Then
        assertTrue(results.all { it })
        assertEquals(0, rowCount())
    }
}
