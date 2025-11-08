package com.ead.katalyst.database

import com.ead.katalyst.transactions.manager.DatabaseTransactionManager as TransactionsModuleManager

/**
 * Type alias for backwards compatibility.
 *
 * DatabaseTransactionManager has been moved to katalyst-transactions module
 * to centralize all transaction-related logic.
 *
 * **Migration Path:**
 * ```
 * OLD: import com.ead.katalyst.database.DatabaseTransactionManager
 * NEW: import com.ead.katalyst.transactions.manager.DatabaseTransactionManager
 * ```
 *
 * Both import paths work for now via this type alias, but the latter is preferred.
 * This alias will be removed in a future version - migrate your code at your convenience.
 *
 * **Real Implementation:**
 * The actual implementation is in `com.ead.katalyst.transactions.manager.DatabaseTransactionManager`
 * with full hook support and event context management.
 *
 * @see com.ead.katalyst.transactions.manager.DatabaseTransactionManager
 */
typealias DatabaseTransactionManager = TransactionsModuleManager
