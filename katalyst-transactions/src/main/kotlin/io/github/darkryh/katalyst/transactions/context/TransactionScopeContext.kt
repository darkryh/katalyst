package io.github.darkryh.katalyst.transactions.context

import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/**
 * Internal transaction scope metadata bound to coroutine context.
 *
 * This context tracks the lifetime of a single root database transaction and
 * enables safe nested-join behavior without forcing explicit IDs in user code.
 */
class TransactionScopeContext(
    val transactionId: String,
    val workflowId: String?,
    var depth: Int = 1,
    var state: TransactionScopeState = TransactionScopeState.ACTIVE
) : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<TransactionScopeContext>
}

enum class TransactionScopeState {
    ACTIVE,
    COMPLETED,
    ROLLED_BACK,
    CLOSED
}
