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
 * Comprehensive tests for DeleteUndoStrategy.
 *
 * `canHandle()` is pure logic and is tested without a database. `undo()` actually
 * performs a compensating INSERT (re-inserting the captured row) against a real H2
 * in-memory database, proving the strategy performs the mutation rather than just
 * claiming success.
 */
class DeleteUndoStrategyTest {

    private lateinit var database: Database

    private object TestUsersTable : Table("delete_undo_test_users") {
        val id = long("id")
        val name = varchar("name", 100)
        val email = varchar("email", 150)

        override val primaryKey = PrimaryKey(id)
    }

    @BeforeTest
    fun setup() {
        database = Database.connect(
            url = "jdbc:h2:mem:test_delete_undo_${System.nanoTime()};DB_CLOSE_DELAY=-1",
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

    private fun strategy() = DeleteUndoStrategy(database)

    private fun rowCount(): Long = transaction(database) { TestUsersTable.selectAll().count() }

    private fun rows(): List<Pair<Long, String>> = transaction(database) {
        TestUsersTable.selectAll().map { it[TestUsersTable.id] to it[TestUsersTable.name] }
    }

    private fun createOperation(
        workflowId: String = "workflow-delete",
        operationIndex: Int = 0,
        operationType: String = "DELETE",
        resourceType: String = "delete_undo_test_users",
        resourceId: String? = "123",
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
    fun `canHandle should return true for DELETE operation`() {
        assertTrue(strategy().canHandle("DELETE", "User"))
    }

    @Test
    fun `canHandle should return true for lowercase delete`() {
        assertTrue(strategy().canHandle("delete", "User"))
    }

    @Test
    fun `canHandle should return true for mixed case DeLeTe`() {
        assertTrue(strategy().canHandle("DeLeTe", "User"))
    }

    @Test
    fun `canHandle should return false for INSERT operation`() {
        assertFalse(strategy().canHandle("INSERT", "User"))
    }

    @Test
    fun `canHandle should return false for UPDATE operation`() {
        assertFalse(strategy().canHandle("UPDATE", "User"))
    }

    @Test
    fun `canHandle should return false for API_CALL operation`() {
        assertFalse(strategy().canHandle("API_CALL", "User"))
    }

    @Test
    fun `canHandle should ignore resourceType parameter`() {
        val s = strategy()
        assertTrue(s.canHandle("DELETE", "User"))
        assertTrue(s.canHandle("DELETE", "Order"))
        assertTrue(s.canHandle("DELETE", "Payment"))
    }

    // ========== undo() TESTS - actually performs the compensating INSERT ==========

    @Test
    fun `undo should actually re-insert the deleted row`() = runTest {
        // Given - undoData contains the full deleted record to re-insert
        assertEquals(0, rowCount())
        val operation = createOperation(
            resourceId = "123",
            undoData = mapOf("id" to 123L, "name" to "John Doe", "email" to "john@example.com")
        )

        // When
        val result = strategy().undo(operation)

        // Then - the row genuinely exists again, not just a claimed success
        assertTrue(result)
        assertEquals(1, rowCount())
        assertEquals(listOf(123L to "John Doe"), rows())
    }

    @Test
    fun `undo should return false and insert nothing when undoData is null`() = runTest {
        val operation = createOperation(undoData = null)

        val result = strategy().undo(operation)

        assertFalse(result)
        assertEquals(0, rowCount())
    }

    @Test
    fun `undo should return false and insert nothing when undoData is empty map`() = runTest {
        val operation = createOperation(undoData = emptyMap())

        val result = strategy().undo(operation)

        // An empty record has no columns to insert - this is missing data, not success.
        assertFalse(result)
        assertEquals(0, rowCount())
    }

    @Test
    fun `undo should return false when undoData contains non-scalar values`() = runTest {
        val operation = createOperation(
            undoData = mapOf(
                "id" to 456L,
                "name" to "Jane",
                "metadata" to mapOf("nested" to "not supported")
            )
        )

        val result = strategy().undo(operation)

        assertFalse(result)
        assertEquals(0, rowCount())
    }

    @Test
    fun `undo should return false for an invalid table identifier`() = runTest {
        val operation = createOperation(
            resourceType = "bad; DROP TABLE delete_undo_test_users; --",
            undoData = mapOf("id" to 1L, "name" to "X", "email" to "x@example.com")
        )

        val result = strategy().undo(operation)

        assertFalse(result)
        // Table must still exist and be queryable
        assertEquals(0, rowCount())
    }

    @Test
    fun `undo should return false when the underlying table does not exist`() = runTest {
        val operation = createOperation(
            resourceType = "does_not_exist_table",
            undoData = mapOf("id" to 1L, "name" to "X")
        )

        val result = strategy().undo(operation)

        assertFalse(result)
    }

    @Test
    fun `undo should handle multiple re-insertions sequentially`() = runTest {
        val s = strategy()

        val results = (1..3).map { i ->
            s.undo(
                createOperation(
                    resourceId = i.toString(),
                    undoData = mapOf("id" to i.toLong(), "name" to "User$i", "email" to "user$i@example.com")
                )
            )
        }

        assertTrue(results.all { it })
        assertEquals(3, rowCount())
    }
}
