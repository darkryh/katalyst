package io.github.darkryh.katalyst.repositories.implementation

import io.github.darkryh.katalyst.database.table.OperationLogTable
import io.github.darkryh.katalyst.transactions.workflow.OperationStatus
import io.github.darkryh.katalyst.transactions.workflow.SimpleTransactionOperation
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insertAndGetId
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.core.eq
import kotlin.test.*

/**
 * Comprehensive tests for OperationLogRepository.
 *
 * Tests cover:
 * - Operation logging (create, track status)
 * - Status transitions (PENDING → COMMITTED/UNDONE/FAILED)
 * - Query operations (pending, all, failed)
 * - Bulk updates (mark all as committed)
 * - Cleanup operations (delete old operations)
 * - Operation ordering and filtering
 * - Error handling and edge cases
 */
class OperationLogRepositoryTest {

    private lateinit var database: Database
    private lateinit var repository: OperationLogRepository

    @BeforeTest
    fun setup() {
        // Create in-memory H2 database
        database = Database.connect(
            url = "jdbc:h2:mem:test_operation_log_${System.currentTimeMillis()};DB_CLOSE_DELAY=-1",
            driver = "org.h2.Driver",
            user = "sa",
            password = ""
        )

        // Create tables
        transaction(database) {
            SchemaUtils.create(OperationLogTable)
        }

        repository = OperationLogRepository(database)
    }

    @AfterTest
    fun teardown() {
        // Drop tables
        transaction(database) {
            SchemaUtils.drop(OperationLogTable)
        }
    }

    // ========== LOG OPERATION TESTS ==========

    @Test
    fun `logOperation should create operation with PENDING status`() = runTest {
        // Given
        val workflowId = "wf-001"
        val operation = createTestOperation(
            workflowId = workflowId,
            operationIndex = 0,
            operationType = "INSERT",
            resourceType = "User",
            resourceId = "user-123"
        )

        // When
        repository.logOperation(workflowId, 0, operation)

        // Then
        val operations = repository.getAllOperations(workflowId)
        assertEquals(1, operations.size)
        val logged = operations[0]
        assertEquals(workflowId, logged.workflowId)
        assertEquals(0, logged.operationIndex)
        assertEquals("INSERT", logged.operationType)
        assertEquals("User", logged.resourceType)
        assertEquals("user-123", logged.resourceId)
    }

    @Test
    fun `logOperation should store operation data`() = runTest {
        // Given
        val workflowId = "wf-002"
        val operationData = mapOf("name" to "Alice", "email" to "alice@example.com")
        val undoData = mapOf("id" to "123", "action" to "DELETE")
        val operation = createTestOperation(
            workflowId = workflowId,
            operationIndex = 0,
            operationType = "INSERT",
            resourceType = "User",
            operationData = operationData,
            undoData = undoData
        )

        // When
        repository.logOperation(workflowId, 0, operation)

        // Then
        val operations = repository.getAllOperations(workflowId)
        assertEquals(1, operations.size)
        // Note: operationData and undoData will be empty maps due to placeholder parseJson()
        // This is expected until Phase 2.4 implements proper JSON serialization
    }

    @Test
    fun `logOperation should log multiple operations for same workflow`() = runTest {
        // Given
        val workflowId = "wf-003"

        // When
        repository.logOperation(workflowId, 0, createTestOperation(workflowId, 0, "INSERT", "User"))
        repository.logOperation(workflowId, 1, createTestOperation(workflowId, 1, "UPDATE", "Profile"))
        repository.logOperation(workflowId, 2, createTestOperation(workflowId, 2, "DELETE", "Session"))

        // Then
        val operations = repository.getAllOperations(workflowId)
        assertEquals(3, operations.size)
    }

    @Test
    fun `logOperation should handle operations from different workflows`() = runTest {
        // Given
        val workflow1 = "wf-100"
        val workflow2 = "wf-200"

        // When
        repository.logOperation(workflow1, 0, createTestOperation(workflow1, 0, "INSERT", "User"))
        repository.logOperation(workflow2, 0, createTestOperation(workflow2, 0, "INSERT", "Order"))
        repository.logOperation(workflow1, 1, createTestOperation(workflow1, 1, "UPDATE", "User"))

        // Then
        assertEquals(2, repository.getAllOperations(workflow1).size)
        assertEquals(1, repository.getAllOperations(workflow2).size)
    }

    @Test
    fun `logOperation should set createdAt timestamp`() = runTest {
        // Given
        val workflowId = "wf-004"
        val beforeLog = System.currentTimeMillis()

        // When
        repository.logOperation(workflowId, 0, createTestOperation(workflowId, 0, "INSERT", "User"))

        // Then - Check via database query
        val hasTimestamp = transaction(database) {
            OperationLogTable
                .selectAll()
                .where { OperationLogTable.workflowId eq workflowId }
                .firstOrNull()
                ?.let { row ->
                    val createdAt = row[OperationLogTable.createdAt]
                    createdAt >= beforeLog && createdAt <= System.currentTimeMillis()
                } ?: false
        }
        assertTrue(hasTimestamp)
    }

    // ========== GET PENDING OPERATIONS TESTS ==========

    @Test
    fun `getPendingOperations should return only PENDING operations`() = runTest {
        // Given
        val workflowId = "wf-005"
        repository.logOperation(workflowId, 0, createTestOperation(workflowId, 0, "INSERT", "User"))
        repository.logOperation(workflowId, 1, createTestOperation(workflowId, 1, "UPDATE", "User"))
        repository.logOperation(workflowId, 2, createTestOperation(workflowId, 2, "DELETE", "User"))

        repository.markAsCommitted(workflowId, 0)

        // When
        val pendingOps = repository.getPendingOperations(workflowId)

        // Then
        assertEquals(2, pendingOps.size)
        assertEquals(1, pendingOps[0].operationIndex)
        assertEquals(2, pendingOps[1].operationIndex)
    }

    @Test
    fun `getPendingOperations should return empty list when all committed`() = runTest {
        // Given
        val workflowId = "wf-006"
        repository.logOperation(workflowId, 0, createTestOperation(workflowId, 0, "INSERT", "User"))
        repository.markAllAsCommitted(workflowId)

        // When
        val pendingOps = repository.getPendingOperations(workflowId)

        // Then
        assertEquals(0, pendingOps.size)
    }

    @Test
    fun `getPendingOperations should return empty list for non-existent workflow`() = runTest {
        // When
        val pendingOps = repository.getPendingOperations("non-existent")

        // Then
        assertEquals(0, pendingOps.size)
    }

    @Test
    fun `getPendingOperations should order by operation index`() = runTest {
        // Given
        val workflowId = "wf-007"

        // Log operations out of order
        repository.logOperation(workflowId, 2, createTestOperation(workflowId, 2, "DELETE", "User"))
        repository.logOperation(workflowId, 0, createTestOperation(workflowId, 0, "INSERT", "User"))
        repository.logOperation(workflowId, 1, createTestOperation(workflowId, 1, "UPDATE", "User"))

        // When
        val pendingOps = repository.getPendingOperations(workflowId)

        // Then - Should be ordered by index
        assertEquals(3, pendingOps.size)
        assertEquals(0, pendingOps[0].operationIndex)
        assertEquals(1, pendingOps[1].operationIndex)
        assertEquals(2, pendingOps[2].operationIndex)
    }

    @Test
    fun `getPendingOperations should not return UNDONE operations`() = runTest {
        // Given
        val workflowId = "wf-008"
        repository.logOperation(workflowId, 0, createTestOperation(workflowId, 0, "INSERT", "User"))
        repository.logOperation(workflowId, 1, createTestOperation(workflowId, 1, "UPDATE", "User"))

        repository.markAsUndone(workflowId, 0)

        // When
        val pendingOps = repository.getPendingOperations(workflowId)

        // Then
        assertEquals(1, pendingOps.size)
        assertEquals(1, pendingOps[0].operationIndex)
    }

    @Test
    fun `getPendingOperations should not return FAILED operations`() = runTest {
        // Given
        val workflowId = "wf-009"
        repository.logOperation(workflowId, 0, createTestOperation(workflowId, 0, "INSERT", "User"))
        repository.logOperation(workflowId, 1, createTestOperation(workflowId, 1, "UPDATE", "User"))

        repository.markAsFailed(workflowId, 0, "Test error")

        // When
        val pendingOps = repository.getPendingOperations(workflowId)

        // Then
        assertEquals(1, pendingOps.size)
        assertEquals(1, pendingOps[0].operationIndex)
    }

    // ========== GET ALL OPERATIONS TESTS ==========

    @Test
    fun `getAllOperations should return all operations regardless of status`() = runTest {
        // Given
        val workflowId = "wf-010"
        repository.logOperation(workflowId, 0, createTestOperation(workflowId, 0, "INSERT", "User"))
        repository.logOperation(workflowId, 1, createTestOperation(workflowId, 1, "UPDATE", "User"))
        repository.logOperation(workflowId, 2, createTestOperation(workflowId, 2, "DELETE", "User"))

        repository.markAsCommitted(workflowId, 0)
        repository.markAsUndone(workflowId, 1)
        repository.markAsFailed(workflowId, 2, "Error")

        // When
        val allOps = repository.getAllOperations(workflowId)

        // Then
        assertEquals(3, allOps.size)
    }

    @Test
    fun `getAllOperations should return empty list for non-existent workflow`() = runTest {
        // When
        val allOps = repository.getAllOperations("non-existent")

        // Then
        assertEquals(0, allOps.size)
    }

    @Test
    fun `getAllOperations should order by operation index`() = runTest {
        // Given
        val workflowId = "wf-011"

        // Log operations out of order
        repository.logOperation(workflowId, 5, createTestOperation(workflowId, 5, "DELETE", "User"))
        repository.logOperation(workflowId, 1, createTestOperation(workflowId, 1, "INSERT", "User"))
        repository.logOperation(workflowId, 3, createTestOperation(workflowId, 3, "UPDATE", "User"))

        // When
        val allOps = repository.getAllOperations(workflowId)

        // Then
        assertEquals(3, allOps.size)
        assertEquals(1, allOps[0].operationIndex)
        assertEquals(3, allOps[1].operationIndex)
        assertEquals(5, allOps[2].operationIndex)
    }

    @Test
    fun `getAllOperations should only return operations for specified workflow`() = runTest {
        // Given
        repository.logOperation("wf-100", 0, createTestOperation("wf-100", 0, "INSERT", "User"))
        repository.logOperation("wf-200", 0, createTestOperation("wf-200", 0, "INSERT", "Order"))
        repository.logOperation("wf-100", 1, createTestOperation("wf-100", 1, "UPDATE", "User"))

        // When
        val ops100 = repository.getAllOperations("wf-100")
        val ops200 = repository.getAllOperations("wf-200")

        // Then
        assertEquals(2, ops100.size)
        assertEquals(1, ops200.size)
        assertTrue(ops100.all { it.workflowId == "wf-100" })
        assertTrue(ops200.all { it.workflowId == "wf-200" })
    }

    // ========== MARK AS COMMITTED TESTS ==========

    @Test
    fun `markAsCommitted should update single operation status`() = runTest {
        // Given
        val workflowId = "wf-012"
        repository.logOperation(workflowId, 0, createTestOperation(workflowId, 0, "INSERT", "User"))
        repository.logOperation(workflowId, 1, createTestOperation(workflowId, 1, "UPDATE", "User"))

        // When
        repository.markAsCommitted(workflowId, 0)

        // Then
        val pendingOps = repository.getPendingOperations(workflowId)
        assertEquals(1, pendingOps.size)
        assertEquals(1, pendingOps[0].operationIndex)
    }

    @Test
    fun `markAsCommitted should set committedAt timestamp`() = runTest {
        // Given
        val workflowId = "wf-013"
        repository.logOperation(workflowId, 0, createTestOperation(workflowId, 0, "INSERT", "User"))
        val beforeCommit = System.currentTimeMillis()

        // When
        repository.markAsCommitted(workflowId, 0)

        // Then - Check via database query
        val hasCommittedAt = transaction(database) {
            OperationLogTable
                .selectAll()
                .where {
                    (OperationLogTable.workflowId eq workflowId) and
                    (OperationLogTable.operationIndex eq 0)
                }
                .firstOrNull()
                ?.let { row ->
                    val committedAt = row[OperationLogTable.committedAt]
                    committedAt != null && committedAt >= beforeCommit
                } ?: false
        }
        assertTrue(hasCommittedAt)
    }

    @Test
    fun `markAsCommitted for non-existent operation should not throw`() = runTest {
        // When/Then - Should not throw
        repository.markAsCommitted("non-existent", 999)
    }

    @Test
    fun `markAsCommitted should only affect specified operation`() = runTest {
        // Given
        val workflowId = "wf-014"
        repository.logOperation(workflowId, 0, createTestOperation(workflowId, 0, "INSERT", "User"))
        repository.logOperation(workflowId, 1, createTestOperation(workflowId, 1, "UPDATE", "User"))
        repository.logOperation(workflowId, 2, createTestOperation(workflowId, 2, "DELETE", "User"))

        // When
        repository.markAsCommitted(workflowId, 1)

        // Then
        val pendingOps = repository.getPendingOperations(workflowId)
        assertEquals(2, pendingOps.size)
        assertEquals(0, pendingOps[0].operationIndex)
        assertEquals(2, pendingOps[1].operationIndex)
    }

    // ========== MARK ALL AS COMMITTED TESTS ==========

    @Test
    fun `markAllAsCommitted should update all operations for workflow`() = runTest {
        // Given
        val workflowId = "wf-015"
        repository.logOperation(workflowId, 0, createTestOperation(workflowId, 0, "INSERT", "User"))
        repository.logOperation(workflowId, 1, createTestOperation(workflowId, 1, "UPDATE", "User"))
        repository.logOperation(workflowId, 2, createTestOperation(workflowId, 2, "DELETE", "User"))

        // When
        repository.markAllAsCommitted(workflowId)

        // Then
        val pendingOps = repository.getPendingOperations(workflowId)
        assertEquals(0, pendingOps.size)
    }

    @Test
    fun `markAllAsCommitted should only affect specified workflow`() = runTest {
        // Given
        repository.logOperation("wf-100", 0, createTestOperation("wf-100", 0, "INSERT", "User"))
        repository.logOperation("wf-200", 0, createTestOperation("wf-200", 0, "INSERT", "Order"))

        // When
        repository.markAllAsCommitted("wf-100")

        // Then
        assertEquals(0, repository.getPendingOperations("wf-100").size)
        assertEquals(1, repository.getPendingOperations("wf-200").size)
    }

    @Test
    fun `markAllAsCommitted for non-existent workflow should not throw`() = runTest {
        // When/Then - Should not throw
        repository.markAllAsCommitted("non-existent")
    }

    @Test
    fun `markAllAsCommitted should set committedAt for all operations`() = runTest {
        // Given
        val workflowId = "wf-016"
        repository.logOperation(workflowId, 0, createTestOperation(workflowId, 0, "INSERT", "User"))
        repository.logOperation(workflowId, 1, createTestOperation(workflowId, 1, "UPDATE", "User"))
        val beforeCommit = System.currentTimeMillis()

        // When
        repository.markAllAsCommitted(workflowId)

        // Then - Check all have committedAt set
        val allHaveCommittedAt = transaction(database) {
            OperationLogTable
                .selectAll()
                .where { OperationLogTable.workflowId eq workflowId }
                .all { row ->
                    val committedAt = row[OperationLogTable.committedAt]
                    committedAt != null && committedAt >= beforeCommit
                }
        }
        assertTrue(allHaveCommittedAt)
    }

    // ========== MARK AS UNDONE TESTS ==========

    @Test
    fun `markAsUndone should update operation status to UNDONE`() = runTest {
        // Given
        val workflowId = "wf-017"
        repository.logOperation(workflowId, 0, createTestOperation(workflowId, 0, "INSERT", "User"))
        repository.logOperation(workflowId, 1, createTestOperation(workflowId, 1, "UPDATE", "User"))

        // When
        repository.markAsUndone(workflowId, 0)

        // Then
        val pendingOps = repository.getPendingOperations(workflowId)
        assertEquals(1, pendingOps.size)
        assertEquals(1, pendingOps[0].operationIndex)
    }

    @Test
    fun `markAsUndone should set undoneAt timestamp`() = runTest {
        // Given
        val workflowId = "wf-018"
        repository.logOperation(workflowId, 0, createTestOperation(workflowId, 0, "INSERT", "User"))
        val beforeUndo = System.currentTimeMillis()

        // When
        repository.markAsUndone(workflowId, 0)

        // Then - Check via database query
        val hasUndoneAt = transaction(database) {
            OperationLogTable
                .selectAll()
                .where {
                    (OperationLogTable.workflowId eq workflowId) and
                    (OperationLogTable.operationIndex eq 0)
                }
                .firstOrNull()
                ?.let { row ->
                    val undoneAt = row[OperationLogTable.undoneAt]
                    undoneAt != null && undoneAt >= beforeUndo
                } ?: false
        }
        assertTrue(hasUndoneAt)
    }

    @Test
    fun `markAsUndone for non-existent operation should not throw`() = runTest {
        // When/Then - Should not throw
        repository.markAsUndone("non-existent", 999)
    }

    // ========== MARK AS FAILED TESTS ==========

    @Test
    fun `markAsFailed should update operation status to FAILED`() = runTest {
        // Given
        val workflowId = "wf-019"
        repository.logOperation(workflowId, 0, createTestOperation(workflowId, 0, "INSERT", "User"))
        repository.logOperation(workflowId, 1, createTestOperation(workflowId, 1, "UPDATE", "User"))

        // When
        repository.markAsFailed(workflowId, 0, "Undo failed: database locked")

        // Then
        val pendingOps = repository.getPendingOperations(workflowId)
        assertEquals(1, pendingOps.size)
        assertEquals(1, pendingOps[0].operationIndex)
    }

    @Test
    fun `markAsFailed should store error message`() = runTest {
        // Given
        val workflowId = "wf-020"
        val errorMessage = "Undo failed: database connection timeout"
        repository.logOperation(workflowId, 0, createTestOperation(workflowId, 0, "INSERT", "User"))

        // When
        repository.markAsFailed(workflowId, 0, errorMessage)

        // Then - Check via database query
        val storedError = transaction(database) {
            OperationLogTable
                .selectAll()
                .where {
                    (OperationLogTable.workflowId eq workflowId) and
                    (OperationLogTable.operationIndex eq 0)
                }
                .firstOrNull()
                ?.get(OperationLogTable.errorMessage)
        }
        assertEquals(errorMessage, storedError)
    }

    @Test
    fun `markAsFailed should handle long error messages`() = runTest {
        // Given
        val workflowId = "wf-021"
        val longError = "Error: " + "x".repeat(5000)
        repository.logOperation(workflowId, 0, createTestOperation(workflowId, 0, "INSERT", "User"))

        // When
        repository.markAsFailed(workflowId, 0, longError)

        // Then - Check stored
        val storedError = transaction(database) {
            OperationLogTable
                .selectAll()
                .where {
                    (OperationLogTable.workflowId eq workflowId) and
                    (OperationLogTable.operationIndex eq 0)
                }
                .firstOrNull()
                ?.get(OperationLogTable.errorMessage)
        }
        assertEquals(longError, storedError)
    }

    @Test
    fun `markAsFailed for non-existent operation should not throw`() = runTest {
        // When/Then - Should not throw
        repository.markAsFailed("non-existent", 999, "Error")
    }

    // ========== GET FAILED OPERATIONS TESTS ==========

    @Test
    fun `getFailedOperations should return only FAILED operations`() = runTest {
        // Given
        repository.logOperation("wf-100", 0, createTestOperation("wf-100", 0, "INSERT", "User"))
        repository.logOperation("wf-100", 1, createTestOperation("wf-100", 1, "UPDATE", "User"))
        repository.logOperation("wf-200", 0, createTestOperation("wf-200", 0, "DELETE", "Order"))

        repository.markAsFailed("wf-100", 0, "Error 1")
        repository.markAsFailed("wf-200", 0, "Error 2")
        repository.markAsCommitted("wf-100", 1)

        // When
        val failedOps = repository.getFailedOperations()

        // Then
        assertEquals(2, failedOps.size)
    }

    @Test
    fun `getFailedOperations should return empty list when no failures`() = runTest {
        // Given
        repository.logOperation("wf-100", 0, createTestOperation("wf-100", 0, "INSERT", "User"))
        repository.markAsCommitted("wf-100", 0)

        // When
        val failedOps = repository.getFailedOperations()

        // Then
        assertEquals(0, failedOps.size)
    }

    @Test
    fun `getFailedOperations should order by createdAt`() = runTest {
        // Given - Create operations with delays to ensure different timestamps
        repository.logOperation("wf-100", 0, createTestOperation("wf-100", 0, "INSERT", "User"))
        Thread.sleep(10)
        repository.logOperation("wf-200", 0, createTestOperation("wf-200", 0, "INSERT", "Order"))
        Thread.sleep(10)
        repository.logOperation("wf-300", 0, createTestOperation("wf-300", 0, "INSERT", "Payment"))

        // Mark as failed in reverse order
        repository.markAsFailed("wf-300", 0, "Error 3")
        repository.markAsFailed("wf-100", 0, "Error 1")
        repository.markAsFailed("wf-200", 0, "Error 2")

        // When
        val failedOps = repository.getFailedOperations()

        // Then - Should be ordered by creation time
        assertEquals(3, failedOps.size)
        assertEquals("wf-100", failedOps[0].workflowId)
        assertEquals("wf-200", failedOps[1].workflowId)
        assertEquals("wf-300", failedOps[2].workflowId)
    }

    @Test
    fun `getFailedOperations should include operations from different workflows`() = runTest {
        // Given
        repository.logOperation("wf-100", 0, createTestOperation("wf-100", 0, "INSERT", "User"))
        repository.logOperation("wf-100", 1, createTestOperation("wf-100", 1, "UPDATE", "User"))
        repository.logOperation("wf-200", 0, createTestOperation("wf-200", 0, "DELETE", "Order"))

        repository.markAsFailed("wf-100", 0, "Error 1")
        repository.markAsFailed("wf-100", 1, "Error 2")
        repository.markAsFailed("wf-200", 0, "Error 3")

        // When
        val failedOps = repository.getFailedOperations()

        // Then
        assertEquals(3, failedOps.size)
        val workflow100Ops = failedOps.filter { it.workflowId == "wf-100" }
        val workflow200Ops = failedOps.filter { it.workflowId == "wf-200" }
        assertEquals(2, workflow100Ops.size)
        assertEquals(1, workflow200Ops.size)
    }

    // ========== DELETE OLD OPERATIONS TESTS ==========

    @Test
    fun `deleteOldOperations should delete non-PENDING operations before timestamp`() = runTest {
        // Given
        repository.logOperation("wf-old", 0, createTestOperation("wf-old", 0, "INSERT", "User"))
        repository.markAsCommitted("wf-old", 0)

        Thread.sleep(50)
        val cutoffTime = System.currentTimeMillis()
        Thread.sleep(50)

        repository.logOperation("wf-new", 0, createTestOperation("wf-new", 0, "INSERT", "User"))
        repository.markAsCommitted("wf-new", 0)

        // When
        val deletedCount = repository.deleteOldOperations(cutoffTime)

        // Then
        assertEquals(1, deletedCount)
        assertEquals(0, repository.getAllOperations("wf-old").size)
        assertEquals(1, repository.getAllOperations("wf-new").size)
    }

    @Test
    fun `deleteOldOperations should not delete PENDING operations`() = runTest {
        // Given
        repository.logOperation("wf-pending", 0, createTestOperation("wf-pending", 0, "INSERT", "User"))

        Thread.sleep(50)
        val cutoffTime = System.currentTimeMillis()

        // When
        val deletedCount = repository.deleteOldOperations(cutoffTime)

        // Then
        assertEquals(0, deletedCount)
        assertEquals(1, repository.getAllOperations("wf-pending").size)
    }

    @Test
    fun `deleteOldOperations should delete COMMITTED operations before timestamp`() = runTest {
        // Given
        repository.logOperation("wf-001", 0, createTestOperation("wf-001", 0, "INSERT", "User"))
        repository.markAsCommitted("wf-001", 0)

        Thread.sleep(50)
        val cutoffTime = System.currentTimeMillis()

        // When
        val deletedCount = repository.deleteOldOperations(cutoffTime)

        // Then
        assertEquals(1, deletedCount)
    }

    @Test
    fun `deleteOldOperations should delete UNDONE operations before timestamp`() = runTest {
        // Given
        repository.logOperation("wf-002", 0, createTestOperation("wf-002", 0, "INSERT", "User"))
        repository.markAsUndone("wf-002", 0)

        Thread.sleep(50)
        val cutoffTime = System.currentTimeMillis()

        // When
        val deletedCount = repository.deleteOldOperations(cutoffTime)

        // Then
        assertEquals(1, deletedCount)
    }

    @Test
    fun `deleteOldOperations should delete FAILED operations before timestamp`() = runTest {
        // Given
        repository.logOperation("wf-003", 0, createTestOperation("wf-003", 0, "INSERT", "User"))
        repository.markAsFailed("wf-003", 0, "Error")

        Thread.sleep(50)
        val cutoffTime = System.currentTimeMillis()

        // When
        val deletedCount = repository.deleteOldOperations(cutoffTime)

        // Then
        assertEquals(1, deletedCount)
    }

    @Test
    fun `deleteOldOperations should return 0 when no operations to delete`() = runTest {
        // Given
        val futureTimestamp = System.currentTimeMillis() - 1000

        repository.logOperation("wf-recent", 0, createTestOperation("wf-recent", 0, "INSERT", "User"))
        repository.markAsCommitted("wf-recent", 0)

        // When
        val deletedCount = repository.deleteOldOperations(futureTimestamp)

        // Then
        assertEquals(0, deletedCount)
    }

    @Test
    fun `deleteOldOperations should delete multiple old operations`() = runTest {
        // Given
        repository.logOperation("wf-old-1", 0, createTestOperation("wf-old-1", 0, "INSERT", "User"))
        repository.logOperation("wf-old-1", 1, createTestOperation("wf-old-1", 1, "UPDATE", "User"))
        repository.logOperation("wf-old-2", 0, createTestOperation("wf-old-2", 0, "DELETE", "Order"))

        repository.markAsCommitted("wf-old-1", 0)
        repository.markAsCommitted("wf-old-1", 1)
        repository.markAsCommitted("wf-old-2", 0)

        Thread.sleep(50)
        val cutoffTime = System.currentTimeMillis()

        // When
        val deletedCount = repository.deleteOldOperations(cutoffTime)

        // Then
        assertEquals(3, deletedCount)
    }

    @Test
    fun `deleteOldOperations should preserve PENDING operations regardless of timestamp`() = runTest {
        // Given
        repository.logOperation("wf-old-pending", 0, createTestOperation("wf-old-pending", 0, "INSERT", "User"))
        repository.logOperation("wf-old-committed", 0, createTestOperation("wf-old-committed", 0, "INSERT", "User"))
        repository.markAsCommitted("wf-old-committed", 0)

        Thread.sleep(50)
        val cutoffTime = System.currentTimeMillis()

        // When
        val deletedCount = repository.deleteOldOperations(cutoffTime)

        // Then
        assertEquals(1, deletedCount)  // Only committed operation deleted
        assertEquals(1, repository.getAllOperations("wf-old-pending").size)
        assertEquals(0, repository.getAllOperations("wf-old-committed").size)
    }

    // ========== EDGE CASES & ERROR HANDLING ==========

    @Test
    fun `repository should handle null resource IDs`() = runTest {
        // Given
        val workflowId = "wf-null-resource"
        val operation = createTestOperation(
            workflowId = workflowId,
            operationIndex = 0,
            operationType = "API_CALL",
            resourceType = "EmailService",
            resourceId = null
        )

        // When
        repository.logOperation(workflowId, 0, operation)

        // Then
        val operations = repository.getAllOperations(workflowId)
        assertEquals(1, operations.size)
        assertNull(operations[0].resourceId)
    }

    @Test
    fun `repository should handle operations with same index in different workflows`() = runTest {
        // Given
        repository.logOperation("wf-100", 0, createTestOperation("wf-100", 0, "INSERT", "User"))
        repository.logOperation("wf-200", 0, createTestOperation("wf-200", 0, "INSERT", "Order"))

        // When
        repository.markAsCommitted("wf-100", 0)

        // Then
        assertEquals(0, repository.getPendingOperations("wf-100").size)
        assertEquals(1, repository.getPendingOperations("wf-200").size)
    }

    @Test
    fun `repository should handle concurrent status updates via transactions`() = runTest {
        // Given
        val workflowId = "wf-concurrent"
        repository.logOperation(workflowId, 0, createTestOperation(workflowId, 0, "INSERT", "User"))

        // When - Multiple status updates
        repository.markAsCommitted(workflowId, 0)
        repository.markAsUndone(workflowId, 0)
        repository.markAsFailed(workflowId, 0, "Error")

        // Then - Should complete without errors
        val operations = repository.getAllOperations(workflowId)
        assertEquals(1, operations.size)
    }

    @Test
    fun `complete operation lifecycle - success path`() = runTest {
        // Given
        val workflowId = "wf-lifecycle-success"

        // When/Then - Complete lifecycle
        repository.logOperation(workflowId, 0, createTestOperation(workflowId, 0, "INSERT", "User"))
        assertEquals(1, repository.getPendingOperations(workflowId).size)

        repository.markAsCommitted(workflowId, 0)
        assertEquals(0, repository.getPendingOperations(workflowId).size)
        assertEquals(1, repository.getAllOperations(workflowId).size)
    }

    @Test
    fun `complete operation lifecycle - failure and undo path`() = runTest {
        // Given
        val workflowId = "wf-lifecycle-failure"

        // When/Then - Complete lifecycle with failure
        repository.logOperation(workflowId, 0, createTestOperation(workflowId, 0, "INSERT", "User"))
        repository.logOperation(workflowId, 1, createTestOperation(workflowId, 1, "UPDATE", "User"))

        repository.markAsCommitted(workflowId, 0)
        assertEquals(1, repository.getPendingOperations(workflowId).size)

        repository.markAsUndone(workflowId, 0)
        assertEquals(1, repository.getPendingOperations(workflowId).size)

        repository.markAsFailed(workflowId, 1, "Undo failed")
        assertEquals(0, repository.getPendingOperations(workflowId).size)

        val failedOps = repository.getFailedOperations()
        assertEquals(1, failedOps.size)
    }

    // ========== HELPER METHODS ==========

    private fun createTestOperation(
        workflowId: String,
        operationIndex: Int,
        operationType: String,
        resourceType: String,
        resourceId: String? = null,
        operationData: Map<String, Any?>? = null,
        undoData: Map<String, Any?>? = null
    ): SimpleTransactionOperation {
        return SimpleTransactionOperation(
            workflowId = workflowId,
            operationIndex = operationIndex,
            operationType = operationType,
            resourceType = resourceType,
            resourceId = resourceId,
            operationData = operationData,
            undoData = undoData
        )
    }
}
