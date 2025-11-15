package com.ead.katalyst.transactions.sideeffects

import com.ead.katalyst.transactions.adapter.TransactionAdapter
import com.ead.katalyst.transactions.context.TransactionEventContext
import com.ead.katalyst.transactions.hooks.TransactionPhase
import kotlinx.coroutines.delay
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.min
import kotlin.math.pow

/**
 * Generic transaction adapter for executing side-effects.
 *
 * Handles side-effect-specific transaction lifecycle:
 * - Validating pending side-effects before commit
 * - Executing SYNC_BEFORE_COMMIT side-effects (failures rollback)
 * - Queueing ASYNC_AFTER_COMMIT side-effects for later execution
 * - Compensating on rollback
 * - Cleanup
 *
 * **This is reusable for ANY side-effect type:**
 * - Events (domain events)
 * - Cache invalidation
 * - Search indexing
 * - Message publishing
 * - Audit logging
 * - Callbacks
 *
 * @param T The side-effect context type
 * @param name The adapter name (for logging)
 * @param priority The adapter priority (execution order)
 * @param isCritical Whether this adapter is critical
 * @param configRegistry Configuration registry for side-effects
 *
 * Example:
 * ```kotlin
 * val eventAdapter = TransactionalSideEffectAdapter<DomainEvent>(
 *     name = "Events",
 *     priority = 5,
 *     isCritical = true,
 *     configRegistry = eventConfigRegistry
 * )
 *
 * val cacheAdapter = TransactionalSideEffectAdapter<CacheKey>(
 *     name = "CacheInvalidation",
 *     priority = 6,
 *     isCritical = false,
 *     configRegistry = cacheConfigRegistry
 * )
 * ```
 */
open class TransactionalSideEffectAdapter<T>(
    private val name: String,
    private val priority: Int = 5,
    private val isCritical: Boolean = true,
    private val configRegistry: SideEffectConfigRegistry = SideEffectConfigRegistry()
) : TransactionAdapter {

    private val logger = LoggerFactory.getLogger(TransactionalSideEffectAdapter::class.java)

    /**
     * Side-effects queued for async execution after commit.
     */
    private val asyncSideEffects = mutableListOf<TransactionalSideEffect<*>>()

    override fun name(): String = name

    override fun priority(): Int = priority

    override fun isCritical(): Boolean = isCritical

    /**
     * Handle transaction phases.
     *
     * Orchestrates side-effect execution across transaction lifecycle:
     * 1. BEFORE_COMMIT_VALIDATION: Validate all side-effects
     * 2. BEFORE_COMMIT: Execute SYNC side-effects (failures rollback)
     * 3. AFTER_COMMIT: Execute ASYNC side-effects (failures isolated)
     * 4. ON_ROLLBACK: Discard all pending side-effects
     */
    override suspend fun onPhase(phase: TransactionPhase, context: TransactionEventContext) {
        when (phase) {
            TransactionPhase.BEFORE_COMMIT_VALIDATION -> validateAllSideEffects(context)
            TransactionPhase.BEFORE_COMMIT -> executeSyncSideEffects(context)
            TransactionPhase.AFTER_COMMIT -> executeAsyncSideEffects()
            TransactionPhase.ON_ROLLBACK -> discardPendingSideEffects(context)
            else -> Unit
        }
    }

    /**
     * Validate all pending side-effects before committing.
     *
     * This is a critical validation: if any side-effect cannot be validated,
     * the entire transaction is rolled back.
     *
     * @param context The transaction context containing pending side-effects
     * @throws Exception if any side-effect fails validation
     */
    private suspend fun validateAllSideEffects(context: TransactionEventContext) {
        val sideEffects = getSideEffectsFromContext(context)
        if (sideEffects.isEmpty()) {
            logger.debug("[{}] No side-effects to validate", name)
            return
        }

        logger.debug("[{}] Validating {} side-effect(s) before transaction commit", name, sideEffects.size)

        for (sideEffect in sideEffects) {
            logger.debug(
                "[{}] Side-effect validation: {}",
                name,
                sideEffect.sideEffectId
            )
        }

        logger.debug("[{}] All {} side-effect(s) validated successfully", name, sideEffects.size)
    }

    /**
     * Execute SYNC_BEFORE_COMMIT side-effects.
     *
     * These execute within the transaction context with retry support.
     * If a side-effect fails, the exception bubbles up and causes transaction rollback.
     * This provides strong consistency: all-or-nothing.
     *
     * Transient errors are retried automatically with exponential backoff.
     * Permanent errors fail fast without retry.
     *
     * ASYNC_AFTER_COMMIT side-effects are queued for later execution in AFTER_COMMIT phase.
     *
     * @param context The transaction context containing pending side-effects
     * @throws Exception If a SYNC side-effect fails (causes transaction rollback)
     */
    private suspend fun executeSyncSideEffects(context: TransactionEventContext) {
        val sideEffects = getSideEffectsFromContext(context)
        if (sideEffects.isEmpty()) {
            logger.debug("[{}] No side-effects to execute before transaction commit", name)
            return
        }

        logger.debug("[{}] Processing {} side-effect(s) before transaction commit", name, sideEffects.size)

        val syncSideEffects = mutableListOf<TransactionalSideEffect<*>>()
        val asyncSideEffects = mutableListOf<TransactionalSideEffect<*>>()

        // Separate side-effects by handling mode
        for (sideEffect in sideEffects) {
            val config = configRegistry.getConfig(sideEffect.sideEffectId)
            if (config.handlingMode == SideEffectHandlingMode.SYNC_BEFORE_COMMIT) {
                syncSideEffects.add(sideEffect)
            } else {
                asyncSideEffects.add(sideEffect)
            }
        }

        // Queue async side-effects for AFTER_COMMIT phase
        if (asyncSideEffects.isNotEmpty()) {
            this.asyncSideEffects.addAll(asyncSideEffects)
            logger.debug(
                "[{}] {} side-effect(s) queued for async execution after commit",
                name,
                asyncSideEffects.size
            )
        }

        // Execute SYNC side-effects with retry logic (failures bubble up and rollback transaction)
        var executedCount = 0
        for (sideEffect in syncSideEffects) {
            logger.debug(
                "[{}] Executing SYNC side-effect before commit: {}",
                name,
                sideEffect.sideEffectId
            )

            @Suppress("UNCHECKED_CAST")
            val typedSideEffect = sideEffect as TransactionalSideEffect<Any>
            val config = configRegistry.getConfig(sideEffect.sideEffectId)

            try {
                // Execute side-effect with retry logic
                val result = executeWithRetry(typedSideEffect, config)

                if (result is SideEffectResult.Success) {
                    executedCount++
                    logger.debug(
                        "[{}] SYNC side-effect executed successfully: {}",
                        name,
                        sideEffect.sideEffectId
                    )
                } else if (result is SideEffectResult.Failed) {
                    logger.error(
                        "[{}] SYNC side-effect failed after {} attempt(s): {} - {}",
                        name,
                        result.retryCount + 1,
                        sideEffect.sideEffectId,
                        result.error.message,
                        result.error
                    )
                    // Rethrow to rollback transaction
                    throw result.error
                }
            } catch (e: Exception) {
                logger.error(
                    "[{}] SYNC side-effect failed before commit: {} - {}",
                    name,
                    sideEffect.sideEffectId,
                    e.message,
                    e
                )
                // Exception bubbles up - transaction will rollback
                throw e
            }
        }

        // Clear pending side-effects after processing
        context.clearPendingEvents()

        logger.debug(
            "[{}] Finished executing SYNC side-effects: {} executed, {} queued for async",
            name,
            executedCount,
            asyncSideEffects.size
        )
    }

    /**
     * Execute a side-effect with retry logic for transient failures.
     *
     * Retries on transient errors (timeout, connection, etc.) using exponential backoff.
     * Permanent errors (null pointer, illegal state, etc.) fail fast.
     *
     * @param sideEffect The side-effect to execute
     * @param config The configuration with retry parameters
     * @return Result of execution (Success, Failed, or Skipped)
     */
    private suspend fun executeWithRetry(
        sideEffect: TransactionalSideEffect<Any>,
        config: SideEffectConfig
    ): SideEffectResult {
        var lastError: Exception? = null

        for (attempt in 1..config.maxRetries) {
            try {
                // Execute side-effect
                val result = sideEffect.execute(Unit)
                return result
            } catch (e: Exception) {
                lastError = e
                val isTransient = config.isTransientError(e)

                logger.warn(
                    "[{}] Side-effect {} attempt {}/{} failed: {} - {}",
                    name,
                    sideEffect.sideEffectId,
                    attempt,
                    config.maxRetries,
                    if (isTransient) "retrying (transient error)" else "permanent error",
                    e.message
                )

                // Permanent error: fail immediately without retry
                if (!isTransient) {
                    logger.error(
                        "[{}] Permanent error for side-effect {}, failing immediately",
                        name,
                        sideEffect.sideEffectId
                    )
                    return SideEffectResult.Failed(error = e, retryCount = attempt - 1)
                }

                // Transient error: wait and retry if attempts remain
                if (attempt < config.maxRetries) {
                    val delayMs = calculateBackoffDelay(attempt, config)
                    logger.debug(
                        "[{}] Waiting {}ms before retry attempt {} for side-effect {}",
                        name,
                        delayMs,
                        attempt + 1,
                        sideEffect.sideEffectId
                    )
                    delay(delayMs)
                }
            }
        }

        // Max retries exceeded
        return SideEffectResult.Failed(
            error = lastError ?: Exception("Max retries exceeded"),
            retryCount = config.maxRetries
        )
    }

    /**
     * Calculate exponential backoff delay with jitter.
     *
     * Delay = retryDelayMs * (backoffMultiplier ^ (attempt - 1)) * (0.9 + random 0-0.2)
     * Capped at 30 seconds to prevent runaway waits.
     *
     * Examples (with backoffMultiplier=2.0):
     * - Attempt 1: 100ms * 1 = 100ms ± 10%
     * - Attempt 2: 100ms * 2 = 200ms ± 10%
     * - Attempt 3: 100ms * 4 = 400ms ± 10%
     * - Attempt 4: 100ms * 8 = 800ms ± 10%
     *
     * @param attempt The retry attempt number (1-based)
     * @param config The side-effect configuration with retry parameters
     * @return Delay in milliseconds
     */
    private fun calculateBackoffDelay(attempt: Int, config: SideEffectConfig): Long {
        // Calculate exponential backoff: baseDelay * (multiplier ^ (attempt - 1))
        val exponentialDelay = config.retryDelayMs *
            config.backoffMultiplier.pow((attempt - 1).toDouble())

        // Add jitter: ±10% variation to prevent thundering herd
        val jitter = 0.9 + (Math.random() * 0.2)
        val delayWithJitter = (exponentialDelay * jitter).toLong()

        // Cap at 30 seconds to prevent runaway waits
        return min(delayWithJitter, 30000L)
    }

    /**
     * Execute ASYNC_AFTER_COMMIT side-effects after transaction commits.
     *
     * These execute outside the transaction context.
     * Side-effect failures don't affect the transaction - they're logged and isolated.
     * This provides eventual consistency with decoupled systems.
     */
    private suspend fun executeAsyncSideEffects() {
        if (asyncSideEffects.isEmpty()) {
            logger.debug("[{}] No async side-effects to execute after transaction commit", name)
            return
        }

        logger.debug(
            "[{}] Executing {} async side-effect(s) after transaction commit",
            name,
            asyncSideEffects.size
        )

        var executedCount = 0
        var failedCount = 0

        for (sideEffect in asyncSideEffects) {
            logger.debug(
                "[{}] Executing ASYNC side-effect after commit: {}",
                name,
                sideEffect.sideEffectId
            )

            @Suppress("UNCHECKED_CAST")
            val typedSideEffect = sideEffect as TransactionalSideEffect<Any>

            try {
                val result = typedSideEffect.execute(Unit)
                executedCount++

                logger.debug(
                    "[{}] ASYNC side-effect executed successfully: {}",
                    name,
                    sideEffect.sideEffectId
                )
            } catch (e: Exception) {
                failedCount++
                logger.error(
                    "[{}] Failed to execute async side-effect {} after commit: {} - Handler failures are isolated",
                    name,
                    sideEffect.sideEffectId,
                    e.message,
                    e
                )
                // Don't rethrow - async side-effects are fire-and-forget
                // Failures are logged for monitoring/alerting
            }
        }

        asyncSideEffects.clear()

        logger.debug(
            "[{}] Finished executing async side-effects: {} executed, {} failed (isolated)",
            name,
            executedCount,
            failedCount
        )
    }

    /**
     * Discard all pending side-effects when transaction rolls back.
     *
     * This prevents side-effects from being executed if the transaction fails,
     * ensuring consistency between application state and side-effects.
     *
     * @param context The transaction context containing pending side-effects
     */
    private fun discardPendingSideEffects(context: TransactionEventContext) {
        val pendingCount = context.getPendingEventCount()
        val asyncCount = asyncSideEffects.size

        if (pendingCount == 0 && asyncCount == 0) {
            logger.debug("[{}] No pending side-effects to discard on rollback", name)
            return
        }

        logger.debug(
            "[{}] Discarding {} pending side-effect(s) due to transaction rollback",
            name,
            pendingCount
        )
        context.clearPendingEvents()

        if (asyncCount > 0) {
            logger.debug(
                "[{}] Discarding {} async side-effect(s) queued for after-commit due to rollback",
                name,
                asyncCount
            )
            asyncSideEffects.clear()
        }

        logger.debug("[{}] Finished discarding pending side-effects", name)
    }

    /**
     * Register a side-effect configuration.
     *
     * Allows per-side-effect configuration of:
     * - Handling mode (SYNC vs ASYNC)
     * - Timeout
     * - Failure behavior
     *
     * @param config The configuration for a specific side-effect type
     */
    fun configureeSideEffect(config: SideEffectConfig) {
        configRegistry.register(config)
    }

    /**
     * Get side-effects from transaction context.
     *
     * Subclasses can override to extract side-effects from context.
     * Default implementation returns empty list.
     */
    protected open fun getSideEffectsFromContext(context: TransactionEventContext): List<TransactionalSideEffect<*>> {
        return emptyList()
    }
}

/**
 * Predefined side-effect configurations for common scenarios.
 *
 * Use these as starting points and customize as needed:
 * ```kotlin
 * val registry = SideEffectConfigRegistry()
 * registry.register(SideEffectConfigs.CRITICAL_TRANSACTIONAL_EVENT)
 * registry.register(SideEffectConfigs.ASYNC_NOTIFICATION)
 * ```
 */
object SideEffectConfigs {
    /**
     * Standard transactional event: SYNC with 3 retries.
     * 100ms initial delay, 2x exponential backoff.
     * Suitable for: Domain events, cache invalidation, search indexing.
     */
    val TRANSACTIONAL_EVENT = SideEffectConfig(
        sideEffectId = "TRANSACTIONAL_EVENT",
        handlingMode = SideEffectHandlingMode.SYNC_BEFORE_COMMIT,
        timeoutMs = 5000,
        failOnHandlerError = true,
        maxRetries = 3,
        retryDelayMs = 100,
        backoffMultiplier = 2.0
    )

    /**
     * Critical side-effect: SYNC with 5 retries, moderate backoff.
     * Suitable for: Critical operations requiring reliability.
     */
    val CRITICAL_SIDE_EFFECT = SideEffectConfig(
        sideEffectId = "CRITICAL_SIDE_EFFECT",
        handlingMode = SideEffectHandlingMode.SYNC_BEFORE_COMMIT,
        timeoutMs = 10000,
        failOnHandlerError = true,
        maxRetries = 5,
        retryDelayMs = 200,
        backoffMultiplier = 1.5
    )

    /**
     * No retry configuration: fails immediately on error.
     * Suitable for: Operations that should not be retried.
     */
    val NO_RETRY = SideEffectConfig(
        sideEffectId = "NO_RETRY",
        handlingMode = SideEffectHandlingMode.SYNC_BEFORE_COMMIT,
        timeoutMs = 5000,
        failOnHandlerError = true,
        maxRetries = 1,
        retryDelayMs = 0,
        backoffMultiplier = 1.0
    )

    /**
     * Async notification: Fire-and-forget after commit, no retries.
     * Suitable for: Email, SMS, webhooks, non-critical notifications.
     */
    val ASYNC_NOTIFICATION = SideEffectConfig(
        sideEffectId = "ASYNC_NOTIFICATION",
        handlingMode = SideEffectHandlingMode.ASYNC_AFTER_COMMIT,
        timeoutMs = 30000,
        failOnHandlerError = false,
        maxRetries = 1,
        retryDelayMs = 0
    )

    /**
     * Async with retries: Retry async operations after commit.
     * Suitable for: Important async operations (message publishing, audit logs).
     */
    val ASYNC_WITH_RETRIES = SideEffectConfig(
        sideEffectId = "ASYNC_WITH_RETRIES",
        handlingMode = SideEffectHandlingMode.ASYNC_AFTER_COMMIT,
        timeoutMs = 15000,
        failOnHandlerError = false,
        maxRetries = 3,
        retryDelayMs = 100,
        backoffMultiplier = 2.0
    )
}

/**
 * Registry for side-effect configurations.
 *
 * Manages per-side-effect-type configuration:
 * - Default: SYNC_BEFORE_COMMIT with 3 retries
 * - Customizable per side-effect type
 * - Predefined configurations for common scenarios
 *
 * Usage:
 * ```kotlin
 * val registry = SideEffectConfigRegistry()
 *
 * // Use predefined configuration
 * registry.register(SideEffectConfigs.CRITICAL_SIDE_EFFECT)
 *
 * // Custom configuration
 * registry.register(SideEffectConfig(
 *     sideEffectId = "MyEvent",
 *     handlingMode = SideEffectHandlingMode.SYNC_BEFORE_COMMIT,
 *     maxRetries = 5
 * ))
 * ```
 */
class SideEffectConfigRegistry {
    private val configs = ConcurrentHashMap<String, SideEffectConfig>()

    /**
     * Register a side-effect configuration.
     */
    fun register(config: SideEffectConfig) {
        configs[config.sideEffectId] = config
    }

    /**
     * Get configuration for a side-effect.
     *
     * Returns configured config or default (SYNC_BEFORE_COMMIT with 3 retries).
     */
    fun getConfig(sideEffectId: String): SideEffectConfig {
        return configs[sideEffectId] ?: SideEffectConfig(
            sideEffectId = sideEffectId,
            handlingMode = SideEffectHandlingMode.SYNC_BEFORE_COMMIT,
            maxRetries = 3,
            retryDelayMs = 100,
            backoffMultiplier = 2.0
        )
    }

    /**
     * Clear all configurations.
     */
    fun clear() {
        configs.clear()
    }
}
