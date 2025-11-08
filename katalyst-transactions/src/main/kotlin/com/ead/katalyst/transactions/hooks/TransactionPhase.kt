package com.ead.katalyst.transactions.hooks

/**
 * Defines the phases of a database transaction lifecycle.
 *
 * Hooks can be registered to execute at any of these phases, enabling
 * cross-cutting concerns like event publishing, cache invalidation, audit logging, etc.
 *
 * **Phase Sequence:**
 * 1. `BEFORE_BEGIN` - Before transaction begins
 * 2. `AFTER_BEGIN` - After transaction starts
 * 3. `BEFORE_COMMIT` - Before transaction commits (still in TX context)
 * 4. `AFTER_COMMIT` - After transaction commits (outside TX context)
 * 5. `ON_ROLLBACK` - When transaction rolls back due to error
 * 6. `AFTER_ROLLBACK` - After rollback completes
 *
 * **Example:**
 * ```kotlin
 * enum class TransactionPhase {
 *     BEFORE_BEGIN,   // Setup: allocate resources
 *     AFTER_BEGIN,    // Logging: transaction started
 *     BEFORE_COMMIT,  // Validation: check before committing
 *     AFTER_COMMIT,   // Publish events, invalidate caches
 *     ON_ROLLBACK,    // Cleanup: restore state
 *     AFTER_ROLLBACK  // Logging: transaction rolled back
 * }
 * ```
 */
enum class TransactionPhase {
    /**
     * Fires before the transaction begins.
     * Use for setup and resource allocation.
     */
    BEFORE_BEGIN,

    /**
     * Fires after the transaction has started successfully.
     * Use for logging and initialization within transaction context.
     */
    AFTER_BEGIN,

    /**
     * Fires before the transaction commits.
     * Still within transaction context - can make final database changes.
     * Use for validation and cleanup within transaction.
     */
    BEFORE_COMMIT,

    /**
     * Fires after the transaction commits successfully.
     * Outside transaction context - see all committed changes.
     * Use for event publishing, cache invalidation, external notifications.
     */
    AFTER_COMMIT,

    /**
     * Fires when an exception occurs and transaction rolls back.
     * Transaction is still being rolled back.
     * Use for cleanup and state restoration.
     */
    ON_ROLLBACK,

    /**
     * Fires after transaction rollback completes.
     * All changes have been rolled back.
     * Use for logging and final cleanup.
     */
    AFTER_ROLLBACK
}
