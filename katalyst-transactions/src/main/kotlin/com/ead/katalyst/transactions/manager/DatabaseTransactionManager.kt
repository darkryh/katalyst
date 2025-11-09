package com.ead.katalyst.transactions.manager

import com.ead.katalyst.transactions.adapter.TransactionAdapter
import com.ead.katalyst.transactions.adapter.TransactionAdapterRegistry
import com.ead.katalyst.transactions.context.TransactionEventContext
import com.ead.katalyst.transactions.hooks.TransactionPhase
import com.ead.katalyst.transactions.workflow.CurrentWorkflowContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.transactions.TransactionManager as ExposedTransactionManager
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.slf4j.LoggerFactory
import java.util.UUID

/**
 * Database transaction manager for handling suspended transactions with adapter support.
 *
 * This manager wraps Exposed's `newSuspendedTransaction` to provide:
 * - Clean suspend function API
 * - Automatic transaction management (commit/rollback)
 * - Adapter system for decoupled cross-cutting concerns
 * - Event context for queueing events until after commit
 * - IO dispatcher by default for non-blocking operations
 * - Proper error handling and cleanup
 *
 * **Architecture:**
 * Services use this manager to execute repository operations within a transaction.
 * Repositories do NOT have suspended functions - they execute synchronously within
 * the transaction context provided by this manager.
 *
 * Modules register `TransactionAdapter` instances to handle their concerns:
 * - Persistence module registers adapter for DB state
 * - Events module registers adapter for event publishing
 * - Future modules register adapters for their concerns
 *
 * ```
 * Service Layer (suspend):
 *   └─ transactionManager.transaction {
 *       ├─ Repository.findById(id)        // sync, uses connection from context
 *       ├─ validate(entity)               // sync
 *       ├─ Repository.save(entity)        // sync, uses connection from context
 *       └─ eventDispatcher.dispatch(...)  // sync or suspend
 *   }
 * ```
 *
 * **Transaction Lifecycle with Adapters:**
 * ```
 * 1. transaction { block }
 * 2. Create TransactionEventContext
 * 3. Execute BEFORE_BEGIN adapters
 * 4. Begin Exposed transaction
 * 5. Execute AFTER_BEGIN adapters
 * 6. Execute user block
 * 7. Execute BEFORE_COMMIT adapters (still in TX)
 * 8. Commit database transaction
 * 9. AFTER_COMMIT hooks (outside TX)
 * 10. Return result
 *
 * Or on error:
 * 1-5. (same as above)
 * 6. Exception thrown in block
 * 7. ON_ROLLBACK hooks
 * 8. Rollback database transaction
 * 9. AFTER_ROLLBACK hooks
 * 10. Re-throw exception
 * ```
 *
 * **Example Usage:**
 * ```kotlin
 * class UserService(
 *     private val userRepository: UserRepository,
 *     private val transactionManager: DatabaseTransactionManager,
 *     private val eventBus: EventBus
 * ) {
 *     override lateinit var transactionManager: DatabaseTransactionManager
 *
 *     suspend fun createUser(dto: CreateUserDTO): User {
 *         // Validate input (sync)
 *         userValidator.validate(dto)
 *
 *         // Run repository operations in transaction
 *         return transactionManager.transaction {
 *             // All these run within the same database transaction
 *             val existing = userRepository.findByEmail(dto.email)
 *             if (existing != null) {
 *                 throw ValidationException("Email already exists")
 *             }
 *
 *             val user = userRepository.save(User.from(dto))
 *
 *             // This event is queued, not published yet
 *             eventBus.publish(UserCreatedEvent(user.id))
 *
 *             user
 *             // Transaction commits here if no exception
 *         }
 *         // After transaction commits: queued events are published
 *     }
 * }
 * ```
 *
 * **Error Handling:**
 * If any exception is thrown within the transaction block, the transaction
 * will be automatically rolled back. The exception will be propagated to the caller.
 *
 * @property database The Exposed Database instance
 * @property adapterRegistry The registry managing transaction adapters
 */
class DatabaseTransactionManager(
    private val database: Database,
    private val adapterRegistry: TransactionAdapterRegistry = TransactionAdapterRegistry()
) : TransactionManager {

    companion object {
        private val logger = LoggerFactory.getLogger(DatabaseTransactionManager::class.java)
    }

    /**
     * Executes a block of code within a database transaction with workflow tracking support.
     *
     * @param workflowId Optional workflow ID for operation tracking
     * @param T The return type of the block
     * @param block The suspend function to execute within the transaction
     * @return The result of the block
     * @throws Exception If the block throws or transaction fails
     */
    override suspend fun <T> transaction(
        workflowId: String?,
        block: suspend Transaction.() -> T
    ): T {
        // If we're already inside an Exposed transaction, just reuse it instead of starting a new one.
        val existingTransaction = ExposedTransactionManager.currentOrNull()
        if (existingTransaction != null) {
            logger.debug(
                "Joining existing transaction context (workflowId={})",
                workflowId ?: CurrentWorkflowContext.get() ?: "unknown"
            )
            return block(existingTransaction)
        }

        // Use provided workflowId or generate a new one
        val txId = workflowId ?: UUID.randomUUID().toString()
        logger.debug("Starting transaction with workflowId: {}", txId)

        // Set workflow context for auto-tracking
        CurrentWorkflowContext.set(txId)

        // Create transaction event context for queuing events during transaction
        val transactionEventContext = TransactionEventContext()

        return try {
            // Phase 1: BEFORE_BEGIN adapters
            logger.debug("Executing BEFORE_BEGIN adapters for workflow: {}", txId)
            adapterRegistry.executeAdapters(TransactionPhase.BEFORE_BEGIN, transactionEventContext)

            // Phase 2-6: Execute the transaction with the event context
            val result = withContext(transactionEventContext) {
                // Phase 2: AFTER_BEGIN adapters (within context)
                logger.debug("Executing AFTER_BEGIN adapters")
                adapterRegistry.executeAdapters(TransactionPhase.AFTER_BEGIN, transactionEventContext)

                // DON'T pass dispatcher to preserve coroutine context inheritance
                // The context element (transactionEventContext) must be inherited by the transaction block
                newSuspendedTransaction(null, database) {
                    logger.debug("Transaction context established, executing block")
                    val outcome = block()

                    logger.debug("Executing BEFORE_COMMIT adapters inside transaction")
                    adapterRegistry.executeAdapters(
                        TransactionPhase.BEFORE_COMMIT,
                        transactionEventContext,
                        failFast = true
                    )

                    outcome
                }
            }

            // Phase 4: AFTER_COMMIT adapters (after transaction commits)
            logger.debug("Executing AFTER_COMMIT adapters")
            adapterRegistry.executeAdapters(TransactionPhase.AFTER_COMMIT, transactionEventContext)

            logger.info("Transaction succeeded for workflow: {}", txId)
            result
        } catch (e: Exception) {
            logger.error("Transaction failed for workflow: {} - {}", txId, e.message, e)

            // Phase 5: ON_ROLLBACK adapters
            logger.debug("Executing ON_ROLLBACK adapters")
            try {
                withContext(transactionEventContext) {
                    adapterRegistry.executeAdapters(TransactionPhase.ON_ROLLBACK, transactionEventContext)
                }
            } catch (adapterError: Exception) {
                logger.warn("Error in ON_ROLLBACK adapter, continuing rollback", adapterError)
            }

            // Phase 6: AFTER_ROLLBACK adapters
            logger.debug("Executing AFTER_ROLLBACK adapters")
            try {
                adapterRegistry.executeAdapters(TransactionPhase.AFTER_ROLLBACK, transactionEventContext)
            } catch (adapterError: Exception) {
                logger.warn("Error in AFTER_ROLLBACK adapter, continuing", adapterError)
            }

            throw e
        } finally {
            // Clear workflow context to prevent context leaks
            CurrentWorkflowContext.clear()
            logger.debug("Workflow context cleared for: {}", txId)
        }
    }

    /**
     * Registers a transaction adapter.
     *
     * Adapters are executed in priority order during transaction phases.
     * Multiple adapters can be registered; they execute independently.
     *
     * @param adapter The adapter to register
     */
    override fun addAdapter(adapter: TransactionAdapter) {
        adapterRegistry.register(adapter)
    }

    /**
     * Unregisters a transaction adapter.
     *
     * Mainly used for testing/cleanup. In production, adapters typically persist
     * for the application lifetime after registration during DI bootstrap.
     *
     * @param adapter The adapter to remove
     */
    override fun removeAdapter(adapter: TransactionAdapter) {
        adapterRegistry.unregister(adapter)
    }
}
