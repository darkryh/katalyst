package io.github.darkryh.katalyst.transactions.workflow

/**
 * Thread-local storage for current workflow context.
 *
 * Used by repositories and services to auto-track operations
 * without requiring explicit context passing.
 *
 * **Usage**:
 * ```
 * CurrentWorkflowContext.set(workflowId)
 * try {
 *     // Services can get current workflow ID
 *     val currentId = CurrentWorkflowContext.get()
 *     // Operations are auto-tracked to this workflow
 * } finally {
 *     CurrentWorkflowContext.clear()
 * }
 * ```
 *
 * **Thread Safety**: Each thread has its own context. Safe for concurrent requests.
 */
object CurrentWorkflowContext {
    private val context = ThreadLocal<String?>()

    /**
     * Set the current workflow ID.
     *
     * @param workflowId Workflow ID
     */
    fun set(workflowId: String) {
        context.set(workflowId)
    }

    /**
     * Get the current workflow ID.
     *
     * @return Workflow ID, or null if not set
     */
    fun get(): String? = context.get()

    /**
     * Check if workflow context is active.
     *
     * @return true if workflow ID is set, false otherwise
     */
    fun isActive(): Boolean = context.get() != null

    /**
     * Clear the current workflow context.
     *
     * Should be called in a finally block to prevent context leaks.
     */
    fun clear() {
        context.remove()
    }

    /**
     * Execute code with a specific workflow context.
     *
     * Automatically sets and clears the context.
     *
     * **Example**:
     * ```
     * CurrentWorkflowContext.withContext("my_workflow") {
     *     // Code here runs with workflow context set
     *     val id = CurrentWorkflowContext.get()  // Returns "my_workflow"
     * }
     * // Context is automatically cleared
     * ```
     *
     * @param workflowId Workflow ID
     * @param block Code to execute
     * @return Result of block
     */
    suspend inline fun <T> withContext(workflowId: String, block: suspend () -> T): T {
        val previous = get()
        set(workflowId)
        return try {
            block()
        } finally {
            if (previous != null) {
                set(previous)
            } else {
                clear()
            }
        }
    }
}
