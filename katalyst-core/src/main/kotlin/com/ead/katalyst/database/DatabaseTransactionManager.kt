package com.ead.katalyst.database

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.slf4j.LoggerFactory

/**
 * Database transaction manager for handling suspend transactions.
 *
 * This manager wraps Exposed's `newSuspendedTransaction` to provide a clean API
 * for running database operations within a transaction context. All database access
 * should happen within the transaction block passed to this manager.
 *
 * **Key Features:**
 * - Suspend function support for async/await patterns
 * - Automatic transaction management (commit/rollback)
 * - IO dispatcher by default for non-blocking operations
 * - Proper error handling and transaction cleanup
 * - Connection pooling integration with HikariCP
 *
 * **Architecture:**
 * Services use this manager to execute repository operations within a transaction.
 * Repositories do NOT have suspend functions - they execute synchronously within
 * the transaction context provided by this manager.
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
 * **Example Usage:**
 * ```kotlin
 * class UserService(
 *     private val userRepository: UserRepository
 * ) : Service {
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
 *             // Transaction commits here if no exception
 *             user
 *         }
 *     }
 * }
 * ```
 *
 * **Error Handling:**
 * If any exception is thrown within the transaction block, the transaction
 * will be automatically rolled back. The exception will be propagated to the caller.
 *
 * @property database The Exposed Database instance
 * @property dispatcher The CoroutineDispatcher to use (default: Dispatchers.IO)
 */
class DatabaseTransactionManager(
    private val database: Database,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    companion object {
        private val logger = LoggerFactory.getLogger(DatabaseTransactionManager::class.java)
    }

    /**
     * Executes a block of code within a database transaction.
     *
     * The block receives a Transaction object as receiver, allowing access to
     * connection-specific operations if needed. However, most interactions should
     * be through repository methods.
     *
     * **Transaction Semantics:**
     * - Commit: Automatic if block completes without exception
     * - Rollback: Automatic if any exception is thrown
     * - Connection: Managed by HikariCP from the pool
     * - Isolation: Configured in DatabaseConfig.transactionIsolation
     *
     * @param T The return type of the block
     * @param block The suspend function to execute within the transaction
     * @return The result of the block
     * @throws Exception If the block throws or transaction fails
     *
     * @sample
     * ```kotlin
     * val user = transactionManager.transaction {
     *     userRepository.findById(1L)
     * }
     * ```
     */
    suspend fun <T> transaction(block: suspend Transaction.() -> T): T {
        logger.debug("Starting new suspended transaction")
        return try {
            newSuspendedTransaction(dispatcher, database) {
                logger.debug("Transaction context established, executing block")
                block()
            }.also {
                logger.debug("Transaction completed successfully")
            }
        } catch (e: Exception) {
            logger.error("Transaction failed and will be rolled back", e)
            throw e
        }
    }
}
