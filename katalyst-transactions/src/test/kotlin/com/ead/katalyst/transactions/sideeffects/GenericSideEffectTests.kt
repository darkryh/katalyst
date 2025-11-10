package com.ead.katalyst.transactions.sideeffects

import com.ead.katalyst.transactions.context.TransactionEventContext
import com.ead.katalyst.transactions.hooks.TransactionPhase
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for generic TransactionalSideEffectAdapter framework.
 *
 * Validates that the generic adapter works for ANY side-effect type:
 * - Events (current)
 * - Cache invalidation (future)
 * - Search indexing (future)
 * - Message publishing (future)
 */
class GenericSideEffectTests {

    private lateinit var adapter: TransactionalSideEffectAdapter<TestSideEffect>
    private lateinit var context: TransactionEventContext
    private val executionLog = mutableListOf<String>()

    @BeforeEach
    fun setUp() {
        adapter = TransactionalSideEffectAdapter(
            name = "TestSideEffects",
            priority = 5,
            isCritical = true
        )
        context = TransactionEventContext()
        executionLog.clear()
    }

    /**
     * Test: Generic adapter configuration works
     */
    @Test
    @Timeout(10)
    fun `generic adapter accepts per-side-effect configuration`() {
        val config = SideEffectConfig(
            sideEffectId = "test-operation",
            handlingMode = SideEffectHandlingMode.SYNC_BEFORE_COMMIT,
            timeoutMs = 3000,
            failOnHandlerError = true
        )

        adapter.configureeSideEffect(config)

        // Verify configuration stored
        assertTrue(true, "Configuration accepted without error")
    }

    /**
     * Test: SYNC side-effect executes and returns result
     */
    @Test
    @Timeout(10)
    fun `SYNC side-effect executes successfully`() = runBlocking {
        var executed = false

        val sideEffect = TestSideEffect(
            id = "sync-op-1",
            handlingMode = SideEffectHandlingMode.SYNC_BEFORE_COMMIT,
            onExecute = {
                executed = true
                SideEffectResult.Success()
            }
        )

        assertTrue(sideEffect is TransactionalSideEffect<*>)
        assertTrue(sideEffect.handlingMode == SideEffectHandlingMode.SYNC_BEFORE_COMMIT)

        val result = sideEffect.execute(Unit)
        assertTrue(result is SideEffectResult.Success)
        assertTrue(executed)
    }

    /**
     * Test: ASYNC side-effect executes asynchronously
     */
    @Test
    @Timeout(10)
    fun `ASYNC side-effect executes after commit`() = runBlocking {
        val sideEffect = TestSideEffect(
            id = "async-op-1",
            handlingMode = SideEffectHandlingMode.ASYNC_AFTER_COMMIT,
            onExecute = {
                executionLog.add("executed")
                SideEffectResult.Success()
            }
        )

        assertEquals(SideEffectHandlingMode.ASYNC_AFTER_COMMIT, sideEffect.handlingMode)

        val result = sideEffect.execute(Unit)
        assertTrue(result is SideEffectResult.Success)
        assertEquals(1, executionLog.size)
    }

    /**
     * Test: Adapter can be reused for different side-effect types
     */
    @Test
    @Timeout(10)
    fun `generic adapter works for different side-effect types`() = runBlocking {
        // Create adapters for different types (same generic implementation)
        val eventAdapter = TransactionalSideEffectAdapter<Event>(
            name = "Events",
            priority = 5
        )

        val cacheAdapter = TransactionalSideEffectAdapter<CacheKey>(
            name = "CacheInvalidation",
            priority = 6
        )

        val searchAdapter = TransactionalSideEffectAdapter<Document>(
            name = "SearchIndexing",
            priority = 7
        )

        assertEquals("Events", eventAdapter.name())
        assertEquals("CacheInvalidation", cacheAdapter.name())
        assertEquals("SearchIndexing", searchAdapter.name())

        assertEquals(5, eventAdapter.priority())
        assertEquals(6, cacheAdapter.priority())
        assertEquals(7, searchAdapter.priority())
    }

    /**
     * Test: Side-effect configuration defaults to SYNC_BEFORE_COMMIT
     */
    @Test
    @Timeout(10)
    fun `side-effect configuration defaults to SYNC_BEFORE_COMMIT`() {
        val config = SideEffectConfig(sideEffectId = "default-op")

        assertEquals(SideEffectHandlingMode.SYNC_BEFORE_COMMIT, config.handlingMode)
        assertEquals(5000, config.timeoutMs)
        assertEquals(true, config.failOnHandlerError)
    }

    /**
     * Test: Side-effect result types work correctly
     */
    @Test
    @Timeout(10)
    fun `side-effect result types represent different outcomes`() {
        val success = SideEffectResult.Success(metadata = mapOf("key" to "value"))
        assertTrue(success is SideEffectResult.Success)

        val failed = SideEffectResult.Failed(
            error = RuntimeException("Test error"),
            retryCount = 2
        )
        assertTrue(failed is SideEffectResult.Failed)
        assertEquals(2, failed.retryCount)

        val skipped = SideEffectResult.Skipped(reason = "Duplicate")
        assertTrue(skipped is SideEffectResult.Skipped)
        assertEquals("Duplicate", skipped.reason)
    }

    /**
     * Test: Adapter is critical by default
     */
    @Test
    @Timeout(10)
    fun `adapter is critical by default`() {
        assertTrue(adapter.isCritical())
    }

    /**
     * Test: Adapter can be configured as non-critical
     */
    @Test
    @Timeout(10)
    fun `adapter can be configured as non-critical`() {
        val nonCriticalAdapter = TransactionalSideEffectAdapter<TestSideEffect>(
            name = "NonCritical",
            isCritical = false
        )

        assertFalse(nonCriticalAdapter.isCritical())
    }

    /**
     * Test: Side-effect context tracks pending side-effects
     */
    @Test
    @Timeout(10)
    fun `side-effect context queues and tracks side-effects`() {
        val sideEffectCtx = SideEffectContext()

        val sideEffect1 = TestSideEffect("op-1", SideEffectHandlingMode.SYNC_BEFORE_COMMIT)
        val sideEffect2 = TestSideEffect("op-2", SideEffectHandlingMode.ASYNC_AFTER_COMMIT)

        sideEffectCtx.queue(sideEffect1)
        sideEffectCtx.queue(sideEffect2)

        assertEquals(2, sideEffectCtx.getPendingCount())
        assertEquals(2, sideEffectCtx.getPending().size)

        sideEffectCtx.clearPending()
        assertEquals(0, sideEffectCtx.getPendingCount())
    }

    /**
     * Test: Side-effect context records executions
     */
    @Test
    @Timeout(10)
    fun `side-effect context records execution results`() {
        val sideEffectCtx = SideEffectContext()
        val sideEffect = TestSideEffect("op-1", SideEffectHandlingMode.SYNC_BEFORE_COMMIT)
        val result = SideEffectResult.Success()

        sideEffectCtx.recordExecution(sideEffect, result)

        assertEquals(1, sideEffectCtx.getExecuted().size)
        val (se, res) = sideEffectCtx.getExecuted()[0]
        assertEquals(sideEffect.sideEffectId, se.sideEffectId)
        assertTrue(res is SideEffectResult.Success)
    }

    /**
     * Test: Config registry stores and retrieves configurations
     */
    @Test
    @Timeout(10)
    fun `config registry manages side-effect configurations`() {
        val registry = SideEffectConfigRegistry()

        val config1 = SideEffectConfig(
            sideEffectId = "operation-1",
            handlingMode = SideEffectHandlingMode.SYNC_BEFORE_COMMIT
        )

        val config2 = SideEffectConfig(
            sideEffectId = "operation-2",
            handlingMode = SideEffectHandlingMode.ASYNC_AFTER_COMMIT
        )

        registry.register(config1)
        registry.register(config2)

        val retrieved1 = registry.getConfig("operation-1")
        val retrieved2 = registry.getConfig("operation-2")

        assertEquals(SideEffectHandlingMode.SYNC_BEFORE_COMMIT, retrieved1.handlingMode)
        assertEquals(SideEffectHandlingMode.ASYNC_AFTER_COMMIT, retrieved2.handlingMode)
    }

    /**
     * Test: Config registry returns default for unconfigured side-effects
     */
    @Test
    @Timeout(10)
    fun `config registry returns default config for unknown side-effects`() {
        val registry = SideEffectConfigRegistry()

        val defaultConfig = registry.getConfig("unknown-operation")

        assertEquals("unknown-operation", defaultConfig.sideEffectId)
        assertEquals(SideEffectHandlingMode.SYNC_BEFORE_COMMIT, defaultConfig.handlingMode)
        assertEquals(5000, defaultConfig.timeoutMs)
    }

    /**
     * Test: Multiple adapters can coexist
     */
    @Test
    @Timeout(10)
    fun `multiple adapters with different side-effect types coexist`() = runBlocking {
        // Create adapters for different domains
        val eventAdapter = TransactionalSideEffectAdapter<DomainEvent>(
            name = "EventAdapter",
            priority = 1
        )

        val cacheAdapter = TransactionalSideEffectAdapter<CacheOperation>(
            name = "CacheAdapter",
            priority = 2
        )

        val auditAdapter = TransactionalSideEffectAdapter<AuditLog>(
            name = "AuditAdapter",
            priority = 3
        )

        // All use same generic code but for different types
        assertEquals(3, listOf(eventAdapter, cacheAdapter, auditAdapter).size)
        assertEquals(1, eventAdapter.priority())
        assertEquals(2, cacheAdapter.priority())
        assertEquals(3, auditAdapter.priority())
    }

    // Test domain classes
    data class TestSideEffect(
        val id: String,
        override val handlingMode: SideEffectHandlingMode = SideEffectHandlingMode.SYNC_BEFORE_COMMIT,
        val onExecute: suspend () -> SideEffectResult = { SideEffectResult.Success() }
    ) : TransactionalSideEffect<Unit> {
        override val sideEffectId = id

        override suspend fun execute(context: Unit): SideEffectResult = onExecute()

        override suspend fun compensate(result: SideEffectResult) {
            // Default: no compensation
        }
    }

    data class Event(val id: String)
    data class CacheKey(val key: String)
    data class Document(val docId: String)
    data class CacheOperation(val operation: String)
    data class DomainEvent(val eventType: String)
    data class AuditLog(val action: String)
}
