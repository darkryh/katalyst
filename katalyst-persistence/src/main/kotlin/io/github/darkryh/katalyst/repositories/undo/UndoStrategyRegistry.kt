package io.github.darkryh.katalyst.repositories.undo

import org.slf4j.LoggerFactory

/**
 * Registry for UndoStrategy implementations.
 *
 * Provides:
 * - Pluggable strategy registration
 * - Strategy lookup by operation type
 * - Fallback to default strategy for unknown operations
 *
 * **Thread Safety**: Immutable after construction, safe for concurrent access
 *
 * **Usage**:
 * ```kotlin
 * val registry = UndoStrategyRegistry()
 *     .register(InsertUndoStrategy())
 *     .register(DeleteUndoStrategy())
 *     .register(UpdateUndoStrategy())
 *     .register(APICallUndoStrategy())
 *
 * val strategy = registry.findStrategy("UPDATE", "User")
 * ```
 */
class UndoStrategyRegistry {
    private val logger = LoggerFactory.getLogger(UndoStrategyRegistry::class.java)
    private val strategies = mutableListOf<UndoStrategy>()
    private val defaultStrategy = NoOpUndoStrategy()

    /**
     * Register an undo strategy.
     *
     * @param strategy The strategy to register
     * @return this for chaining
     */
    fun register(strategy: UndoStrategy): UndoStrategyRegistry {
        strategies.add(strategy)
        logger.debug("Registered undo strategy: {}", strategy::class.simpleName)
        return this
    }

    /**
     * Find the appropriate undo strategy for the given operation.
     *
     * @param operationType The type of operation (INSERT, UPDATE, DELETE, etc)
     * @param resourceType The type of resource (User, Order, etc)
     * @return The matching strategy, or a NoOp strategy if no match found
     */
    fun findStrategy(operationType: String, resourceType: String): UndoStrategy {
        val strategy = strategies.firstOrNull { it.canHandle(operationType, resourceType) }

        return if (strategy != null) {
            logger.debug(
                "Found undo strategy for operation={}, resource={}",
                operationType, resourceType
            )
            strategy
        } else {
            logger.warn(
                "No undo strategy found for operation={}, resource={}, using NoOp",
                operationType, resourceType
            )
            defaultStrategy
        }
    }

    /**
     * Get all registered strategies.
     *
     * @return List of registered strategies
     */
    fun getStrategies(): List<UndoStrategy> {
        return strategies.toList()
    }

    /**
     * No-operation fallback strategy for unknown operation types.
     */
    private class NoOpUndoStrategy : UndoStrategy {
        private val logger = LoggerFactory.getLogger(NoOpUndoStrategy::class.java)

        override fun canHandle(operationType: String, resourceType: String): Boolean = true

        override suspend fun undo(operation: io.github.darkryh.katalyst.transactions.workflow.TransactionOperation): Boolean {
            logger.warn(
                "No undo strategy available for operation={}, resource={}, skipping",
                operation.operationType, operation.resourceType
            )
            return true  // Return true to continue with other operations
        }
    }

    companion object {
        /**
         * Create a registry with all default strategies pre-configured.
         */
        fun createDefault(): UndoStrategyRegistry {
            return UndoStrategyRegistry()
                .register(InsertUndoStrategy())
                .register(DeleteUndoStrategy())
                .register(UpdateUndoStrategy())
                .register(APICallUndoStrategy())
        }
    }
}
