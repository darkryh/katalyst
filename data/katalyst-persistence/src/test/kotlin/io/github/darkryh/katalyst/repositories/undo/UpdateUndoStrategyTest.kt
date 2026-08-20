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
 * Comprehensive tests for UpdateUndoStrategy.
 *
 * `canHandle()` is pure logic and is tested without a database. `undo()` actually
 * performs a compensating UPDATE (restoring captured original values) against a real H2
 * in-memory database, proving the strategy performs the mutation rather than just
 * claiming success.
 */
class UpdateUndoStrategyTest {

    private lateinit var database: Database

    private object TestUsersTable : Table("update_undo_test_users") {
        val id = long("id")
        val name = varchar("name", 100)
        val status = varchar("status", 50)

        override val primaryKey = PrimaryKey(id)
    }

    @BeforeTest
    fun setup() {
        database = Database.connect(
            url = "jdbc:h2:mem:test_update_undo_${System.nanoTime()};DB_CLOSE_DELAY=-1",
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

    private fun strategy() = UpdateUndoStrategy(database)

    private fun insertRow(id: Long, name: String, status: String) {
        transaction(database) {
            TestUsersTable.insert {
                it[TestUsersTable.id] = id
                it[TestUsersTable.name] = name
                it[TestUsersTable.status] = status
            }
        }
    }

    private fun rowFor(id: Long): Pair<String, String> = transaction(database) {
        TestUsersTable.selectAll().first { it[TestUsersTable.id] == id }
            .let { it[TestUsersTable.name] to it[TestUsersTable.status] }
    }

    private fun createOperation(
        workflowId: String = "workflow-1",
        operationIndex: Int = 0,
        operationType: String = "UPDATE",
        resourceType: String = "update_undo_test_users",
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
    fun `canHandle should return true for UPDATE operation`() {
        assertTrue(strategy().canHandle("UPDATE", "User"))
    }

    @Test
    fun `canHandle should return true for lowercase update`() {
        assertTrue(strategy().canHandle("update", "User"))
    }

    @Test
    fun `canHandle should return true for mixed case UpDaTe`() {
        assertTrue(strategy().canHandle("UpDaTe", "User"))
    }

    @Test
    fun `canHandle should return false for INSERT operation`() {
        assertFalse(strategy().canHandle("INSERT", "User"))
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
        assertTrue(s.canHandle("UPDATE", "User"))
        assertTrue(s.canHandle("UPDATE", "Order"))
        assertTrue(s.canHandle("UPDATE", "Payment"))
    }

    // ========== undo() TESTS - actually performs the compensating UPDATE ==========

    @Test
    fun `undo should actually restore the original values`() = runTest {
        // Given - row currently holds the post-update value; undoData has the original
        insertRow(1L, "John Doe", "ACTIVE")
        val operation = createOperation(
            resourceId = "1",
            undoData = mapOf("status" to "PENDING")
        )

        // When
        val result = strategy().undo(operation)

        // Then - the row's value is genuinely restored, not just a claimed success
        assertTrue(result)
        assertEquals("John Doe" to "PENDING", rowFor(1L))
    }

    @Test
    fun `undo should only affect the targeted row`() = runTest {
        insertRow(1L, "Alice", "ACTIVE")
        insertRow(2L, "Bob", "ACTIVE")

        val result = strategy().undo(createOperation(resourceId = "1", undoData = mapOf("status" to "INACTIVE")))

        assertTrue(result)
        assertEquals("Alice" to "INACTIVE", rowFor(1L))
        assertEquals("Bob" to "ACTIVE", rowFor(2L))
    }

    @Test
    fun `undo should restore multiple fields at once`() = runTest {
        insertRow(1L, "Changed Name", "ACTIVE")

        val result = strategy().undo(
            createOperation(resourceId = "1", undoData = mapOf("name" to "Original Name", "status" to "PENDING"))
        )

        assertTrue(result)
        assertEquals("Original Name" to "PENDING", rowFor(1L))
    }

    @Test
    fun `undo should return false and change nothing when undoData is null`() = runTest {
        insertRow(1L, "Alice", "ACTIVE")

        val result = strategy().undo(createOperation(resourceId = "1", undoData = null))

        assertFalse(result)
        assertEquals("Alice" to "ACTIVE", rowFor(1L))
    }

    @Test
    fun `undo should return false and change nothing when undoData is empty map`() = runTest {
        insertRow(1L, "Alice", "ACTIVE")

        val result = strategy().undo(createOperation(resourceId = "1", undoData = emptyMap()))

        // Nothing captured to restore - this is missing data, not success.
        assertFalse(result)
        assertEquals("Alice" to "ACTIVE", rowFor(1L))
    }

    @Test
    fun `undo should return false when resourceId is missing`() = runTest {
        insertRow(1L, "Alice", "ACTIVE")

        val result = strategy().undo(createOperation(resourceId = null, undoData = mapOf("status" to "PENDING")))

        assertFalse(result)
        assertEquals("Alice" to "ACTIVE", rowFor(1L))
    }

    @Test
    fun `undo should return false when undoData contains non-scalar values`() = runTest {
        insertRow(1L, "Alice", "ACTIVE")

        val result = strategy().undo(
            createOperation(
                resourceId = "1",
                undoData = mapOf("status" to "PENDING", "preferences" to mapOf("theme" to "dark"))
            )
        )

        assertFalse(result)
        assertEquals("Alice" to "ACTIVE", rowFor(1L))
    }

    @Test
    fun `undo should return false for an invalid table identifier`() = runTest {
        val result = strategy().undo(
            createOperation(
                resourceType = "bad; DROP TABLE update_undo_test_users; --",
                resourceId = "1",
                undoData = mapOf("status" to "PENDING")
            )
        )

        assertFalse(result)
    }

    @Test
    fun `undo should return false when the underlying table does not exist`() = runTest {
        val result = strategy().undo(
            createOperation(
                resourceType = "does_not_exist_table",
                resourceId = "1",
                undoData = mapOf("status" to "PENDING")
            )
        )

        assertFalse(result)
    }

    @Test
    fun `undo should handle multiple updates sequentially`() = runTest {
        insertRow(1L, "User1", "ACTIVE")
        insertRow(2L, "User2", "ACTIVE")
        insertRow(3L, "User3", "ACTIVE")
        val s = strategy()

        val results = listOf("1", "2", "3").map { id ->
            s.undo(createOperation(resourceId = id, undoData = mapOf("status" to "RESTORED")))
        }

        assertTrue(results.all { it })
        assertEquals("User1" to "RESTORED", rowFor(1L))
        assertEquals("User2" to "RESTORED", rowFor(2L))
        assertEquals("User3" to "RESTORED", rowFor(3L))
    }
}
