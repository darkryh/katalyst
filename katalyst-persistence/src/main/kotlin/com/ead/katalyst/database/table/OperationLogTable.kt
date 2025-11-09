package com.ead.katalyst.database.table

import org.jetbrains.exposed.sql.Table

/**
 * Persistent storage of all operations in workflows.
 *
 * Tracks each operation for audit, recovery, and undo purposes.
 *
 * Schema:
 * - workflow_id: Links operations to their workflow
 * - operation_index: Sequential position in workflow
 * - operation_type: Type of operation (INSERT, UPDATE, DELETE, API_CALL, etc)
 * - resource_type: Type of resource affected (User, Order, etc)
 * - resource_id: ID of affected resource
 * - operation_data: Original data before operation (stored as JSON string)
 * - undo_data: Data needed to undo the operation (stored as JSON string)
 * - status: Current status (PENDING, COMMITTED, UNDONE, FAILED)
 * - error_message: Error details if operation failed
 * - timestamps: Created, committed, undone times
 */
object OperationLogTable : Table("operation_log") {
    val id = long("id").autoIncrement()
    val workflowId = varchar("workflow_id", 255)
    val operationIndex = integer("operation_index")
    val operationType = varchar("operation_type", 50)  // INSERT, UPDATE, DELETE, API_CALL, EMAIL, etc
    val resourceType = varchar("resource_type", 100)   // User, Order, Payment, etc
    val resourceId = varchar("resource_id", 255).nullable()
    val operationData = text("operation_data").nullable()  // JSON string - Original data
    val undoData = text("undo_data").nullable()            // JSON string - Undo information
    val status = varchar("status", 20).default("PENDING")  // PENDING, COMMITTED, UNDONE, FAILED
    val errorMessage = text("error_message").nullable()
    val createdAt = long("created_at")      // Timestamp in millis
    val committedAt = long("committed_at").nullable()
    val undoneAt = long("undone_at").nullable()

    override val primaryKey = PrimaryKey(id)

    init {
        index(false, workflowId)
        index(false, status)
        index(false, createdAt)
        index(false, workflowId, operationIndex)  // Composite index
    }
}
