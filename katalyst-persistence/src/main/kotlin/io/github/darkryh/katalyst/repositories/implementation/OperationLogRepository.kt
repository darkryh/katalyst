package io.github.darkryh.katalyst.repositories.implementation

import io.github.darkryh.katalyst.database.table.OperationLogTable
import io.github.darkryh.katalyst.transactions.workflow.OperationLog
import io.github.darkryh.katalyst.transactions.workflow.OperationStatus
import io.github.darkryh.katalyst.transactions.workflow.SimpleTransactionOperation
import io.github.darkryh.katalyst.transactions.workflow.TransactionOperation
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.slf4j.LoggerFactory
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

/**
 * Persistent repository for operation logs.
 *
 * Provides storage and retrieval of operation tracking data for workflows.
 * Implements the OperationLog interface for workflow tracking.
 *
 * **Thread Safety**: Safe for concurrent access via Exposed transaction management.
 *
 * **Async Behavior**: Writes are intentionally async to avoid blocking the main transaction.
 *
 * **Error Handling**: Every persistence failure is logged with its real cause and then
 * rethrown rather than swallowed. Returning a default (empty list, silently skipping a
 * write) on failure would be indistinguishable from a genuinely empty/successful result,
 * which is unsafe for a subsystem whose whole purpose is tracking what needs to be undone.
 */
internal class OperationLogRepository(private val database: Database) : OperationLog {

    private val logger = LoggerFactory.getLogger(OperationLogRepository::class.java)

    /**
     * Log a new operation (async write).
     *
     * @param workflowId Workflow ID
     * @param operationIndex Sequential index (0-based)
     * @param operation Operation details
     * @throws Exception if the log entry cannot be persisted. Silently swallowing this
     * would mean the operation cannot later be undone during recovery without anyone
     * knowing the log entry is missing, so the real cause is logged and rethrown.
     */
    override suspend fun logOperation(
        workflowId: String,
        operationIndex: Int,
        operation: TransactionOperation
    ) {
        try {
            transaction(database) {
                OperationLogTable.insert {
                    it[OperationLogTable.workflowId] = workflowId
                    it[OperationLogTable.operationIndex] = operationIndex
                    it[OperationLogTable.operationType] = operation.operationType
                    it[OperationLogTable.resourceType] = operation.resourceType
                    it[OperationLogTable.resourceId] = operation.resourceId
                    it[OperationLogTable.operationData] = operation.operationData?.let(::encodeJson)
                    it[OperationLogTable.undoData] = operation.undoData?.let(::encodeJson)
                    it[OperationLogTable.status] = OperationStatus.PENDING.name
                    it[OperationLogTable.createdAt] = System.currentTimeMillis()
                }
            }
            logger.debug(
                "Logged operation: workflow={}, index={}, type={}, resource={}",
                workflowId, operationIndex, operation.operationType, operation.resourceType
            )
        } catch (e: Exception) {
            logger.error(
                "Failed to log operation for workflow: {} ({}/{})",
                workflowId, operation.resourceType, operation.resourceId, e
            )
            throw e
        }
    }

    /**
     * Get all pending operations for a workflow (not yet committed).
     *
     * @param workflowId Workflow ID
     * @return Operations in order (oldest first)
     */
    override suspend fun getPendingOperations(workflowId: String): List<TransactionOperation> {
        return try {
            transaction(database) {
                OperationLogTable
                    .selectAll()
                    .where(
                        (OperationLogTable.workflowId eq workflowId) and
                                (OperationLogTable.status eq OperationStatus.PENDING.name)
                    )
                    .orderBy(OperationLogTable.operationIndex)
                    .map { row ->
                        SimpleTransactionOperation(
                            workflowId = row[OperationLogTable.workflowId],
                            operationIndex = row[OperationLogTable.operationIndex],
                            operationType = row[OperationLogTable.operationType],
                            resourceType = row[OperationLogTable.resourceType],
                            resourceId = row[OperationLogTable.resourceId],
                            operationData = row[OperationLogTable.operationData]?.let { parseJson(it) },
                            undoData = row[OperationLogTable.undoData]?.let { parseJson(it) }
                        )
                    }
            }
        } catch (e: Exception) {
            logger.error("Failed to get pending operations for workflow: {}", workflowId, e)
            throw e
        }
    }

    /**
     * Get all operations for a workflow.
     *
     * @param workflowId Workflow ID
     * @return All operations in order
     */
    override suspend fun getAllOperations(workflowId: String): List<TransactionOperation> {
        return try {
            transaction(database) {
                OperationLogTable
                    .selectAll()
                    .where(OperationLogTable.workflowId eq workflowId)
                    .orderBy(OperationLogTable.operationIndex)
                    .map { row ->
                        SimpleTransactionOperation(
                            workflowId = row[OperationLogTable.workflowId],
                            operationIndex = row[OperationLogTable.operationIndex],
                            operationType = row[OperationLogTable.operationType],
                            resourceType = row[OperationLogTable.resourceType],
                            resourceId = row[OperationLogTable.resourceId],
                            operationData = row[OperationLogTable.operationData]?.let { parseJson(it) },
                            undoData = row[OperationLogTable.undoData]?.let { parseJson(it) }
                        )
                    }
            }
        } catch (e: Exception) {
            logger.error("Failed to get all operations for workflow: {}", workflowId, e)
            throw e
        }
    }

    /**
     * Mark an operation as committed.
     *
     * @param workflowId Workflow ID
     * @param operationIndex Operation index
     */
    override suspend fun markAsCommitted(workflowId: String, operationIndex: Int) {
        try {
            transaction(database) {
                OperationLogTable.update(
                    {
                        (OperationLogTable.workflowId eq workflowId) and
                                (OperationLogTable.operationIndex eq operationIndex)
                    }
                ) {
                    it[OperationLogTable.status] = OperationStatus.COMMITTED.name
                    it[OperationLogTable.committedAt] = System.currentTimeMillis()
                }
            }
            logger.debug(
                "Marked operation as committed: workflow={}, index={}",
                workflowId, operationIndex
            )
        } catch (e: Exception) {
            logger.error(
                "Failed to mark operation as committed: workflow={}, index={}",
                workflowId, operationIndex, e
            )
            throw e
        }
    }

    /**
     * Mark all operations for a workflow as committed.
     *
     * @param workflowId Workflow ID
     */
    override suspend fun markAllAsCommitted(workflowId: String) {
        try {
            transaction(database) {
                OperationLogTable.update(
                    { OperationLogTable.workflowId eq workflowId }
                ) {
                    it[OperationLogTable.status] = OperationStatus.COMMITTED.name
                    it[OperationLogTable.committedAt] = System.currentTimeMillis()
                }
            }
            logger.debug("Marked all operations as committed for workflow: {}", workflowId)
        } catch (e: Exception) {
            logger.error("Failed to mark all operations as committed for workflow: {}", workflowId, e)
            throw e
        }
    }

    /**
     * Mark an operation as undone.
     *
     * @param workflowId Workflow ID
     * @param operationIndex Operation index
     */
    override suspend fun markAsUndone(workflowId: String, operationIndex: Int) {
        try {
            transaction(database) {
                OperationLogTable.update(
                    {
                        (OperationLogTable.workflowId eq workflowId) and
                                (OperationLogTable.operationIndex eq operationIndex)
                    }
                ) {
                    it[OperationLogTable.status] = OperationStatus.UNDONE.name
                    it[OperationLogTable.undoneAt] = System.currentTimeMillis()
                }
            }
            logger.debug(
                "Marked operation as undone: workflow={}, index={}",
                workflowId, operationIndex
            )
        } catch (e: Exception) {
            logger.error(
                "Failed to mark operation as undone: workflow={}, index={}",
                workflowId, operationIndex, e
            )
            throw e
        }
    }

    /**
     * Mark an operation as failed.
     *
     * @param workflowId Workflow ID
     * @param operationIndex Operation index
     * @param error Error message
     */
    override suspend fun markAsFailed(workflowId: String, operationIndex: Int, error: String) {
        try {
            transaction(database) {
                OperationLogTable.update(
                    {
                        (OperationLogTable.workflowId eq workflowId) and
                                (OperationLogTable.operationIndex eq operationIndex)
                    }
                ) {
                    it[OperationLogTable.status] = OperationStatus.FAILED.name
                    it[OperationLogTable.errorMessage] = error
                }
            }
            logger.warn(
                "Marked operation as failed: workflow={}, index={}, error={}",
                workflowId, operationIndex, error
            )
        } catch (e: Exception) {
            logger.error(
                "Failed to mark operation as failed: workflow={}, index={}",
                workflowId, operationIndex, e
            )
            throw e
        }
    }

    /**
     * Get all failed operations.
     *
     * @return Operations with FAILED status
     */
    override suspend fun getFailedOperations(): List<TransactionOperation> {
        return try {
            transaction(database) {
                OperationLogTable
                    .selectAll()
                    .where(OperationLogTable.status eq OperationStatus.FAILED.name)
                    .orderBy(OperationLogTable.createdAt)
                    .map { row ->
                        SimpleTransactionOperation(
                            workflowId = row[OperationLogTable.workflowId],
                            operationIndex = row[OperationLogTable.operationIndex],
                            operationType = row[OperationLogTable.operationType],
                            resourceType = row[OperationLogTable.resourceType],
                            resourceId = row[OperationLogTable.resourceId],
                            operationData = row[OperationLogTable.operationData]?.let { parseJson(it) },
                            undoData = row[OperationLogTable.undoData]?.let { parseJson(it) }
                        )
                    }
            }
        } catch (e: Exception) {
            logger.error("Failed to get failed operations", e)
            throw e
        }
    }

    /**
     * Delete old operation logs (for cleanup/archival).
     *
     * @param beforeTimestamp Delete logs created before this timestamp (millis)
     * @return Number of logs deleted
     */
    override suspend fun deleteOldOperations(beforeTimestamp: Long): Int {
        return try {
            transaction(database) {
                // Delete operations that were created before the timestamp and are not in PENDING state
                OperationLogTable.deleteWhere {
                    (createdAt lessEq beforeTimestamp) and
                            (status neq OperationStatus.PENDING.name)
                }
            }
        } catch (e: Exception) {
            logger.error("Failed to delete old operations", e)
            throw e
        }
    }

    private fun parseJson(json: String): Map<String, Any?>? {
        return try {
            (Json.parseToJsonElement(json) as? JsonObject)?.mapValues { (_, value) -> value.toKotlinValue() }
        } catch (e: Exception) {
            logger.warn("Failed to parse JSON: {}", e.message)
            null
        }
    }

    private fun encodeJson(value: Map<String, Any?>): String = JsonObject(
        value.mapValues { (_, child) -> child.toJsonElement() }
    ).toString()

    private fun Any?.toJsonElement(): JsonElement = when (this) {
        null -> JsonNull
        is JsonElement -> this
        is String -> JsonPrimitive(this)
        is Boolean -> JsonPrimitive(this)
        is Number -> JsonPrimitive(this)
        is Enum<*> -> JsonPrimitive(name)
        is Map<*, *> -> JsonObject(entries.associate { (key, value) -> key.toString() to value.toJsonElement() })
        is Iterable<*> -> JsonArray(map { it.toJsonElement() })
        is Array<*> -> JsonArray(map { it.toJsonElement() })
        else -> JsonPrimitive(toString())
    }

    private fun JsonElement.toKotlinValue(): Any? = when (this) {
        JsonNull -> null
        is JsonObject -> mapValues { (_, value) -> value.toKotlinValue() }
        is JsonArray -> map { it.toKotlinValue() }
        is JsonPrimitive -> when {
            isString -> contentOrNull
            booleanOrNull != null -> booleanOrNull
            longOrNull != null -> longOrNull
            doubleOrNull != null -> doubleOrNull
            else -> contentOrNull
        }
    }
}
