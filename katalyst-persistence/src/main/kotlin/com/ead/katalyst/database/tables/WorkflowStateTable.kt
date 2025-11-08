package com.ead.katalyst.database.tables

import org.jetbrains.exposed.sql.Table

/**
 * Tracks the lifecycle and state of workflows.
 *
 * One row per workflow, updated as workflow progresses through states:
 * - STARTED: Workflow created
 * - COMMITTED: All operations succeeded
 * - FAILED: An operation failed, rollback needed
 * - UNDONE: All operations successfully undone
 * - FAILED_UNDO: Undo failed, manual intervention needed
 *
 * Schema:
 * - workflow_id: Unique identifier
 * - workflow_name: Human-readable name (e.g., "user_registration")
 * - status: Current status
 * - total_operations: How many operations in this workflow
 * - failed_at_operation: If failed, index of failing operation
 * - error_message: Error details
 * - timestamps: Created and completed times
 */
object WorkflowStateTable : Table("workflow_state") {
    val id = long("id").autoIncrement()
    val workflowId = varchar("workflow_id", 255).uniqueIndex()
    val workflowName = varchar("workflow_name", 255)
    val status = varchar("status", 20).default("STARTED")  // STARTED, COMMITTED, FAILED, UNDONE, FAILED_UNDO
    val totalOperations = integer("total_operations").default(0)
    val failedAtOperation = integer("failed_at_operation").nullable()
    val errorMessage = text("error_message").nullable()
    val createdAt = long("created_at")      // Timestamp in millis
    val completedAt = long("completed_at").nullable()

    override val primaryKey = PrimaryKey(id)

    init {
        index(false, status)
        index(false, createdAt)
    }
}
