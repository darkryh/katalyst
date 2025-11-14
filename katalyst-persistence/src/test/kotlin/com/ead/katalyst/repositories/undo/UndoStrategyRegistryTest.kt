package com.ead.katalyst.repositories.undo

import com.ead.katalyst.transactions.workflow.SimpleTransactionOperation
import com.ead.katalyst.transactions.workflow.TransactionOperation
import kotlinx.coroutines.test.runTest
import kotlin.test.*

/**
 * Comprehensive tests for UndoStrategyRegistry.
 *
 * Tests cover:
 * - Strategy registration and retrieval
 * - Strategy lookup by operation type
 * - Fallback to NoOp strategy
 * - createDefault() pre-configuration
 * - First-match strategy selection
 */
class UndoStrategyRegistryTest {
    private fun createOperation(
        workflowId: String = "workflow-registry",
        operationIndex: Int = 0,
        operationType: String = "UNKNOWN",
        resourceType: String = "User",
        resourceId: String? = "123",
        undoData: Map<String, Any?>? = null,
        operationData: Map<String, Any?>? = null,
    ) = SimpleTransactionOperation(
        workflowId = workflowId,
        operationIndex = operationIndex,
        operationType = operationType,
        resourceType = resourceType,
        resourceId = resourceId,
        operationData = operationData,
        undoData = undoData
    )

    // ========== REGISTRATION TESTS ==========

    @Test
    fun `register should add strategy to registry`() {
        // Given
        val registry = UndoStrategyRegistry()
        val strategy = InsertUndoStrategy()

        // When
        registry.register(strategy)

        // Then
        val strategies = registry.getStrategies()
        assertEquals(1, strategies.size)
        assertTrue(strategies.contains(strategy))
    }

    @Test
    fun `register should support method chaining`() {
        // Given
        val registry = UndoStrategyRegistry()

        // When
        val result = registry
            .register(InsertUndoStrategy())
            .register(DeleteUndoStrategy())
            .register(UpdateUndoStrategy())

        // Then
        assertSame(registry, result)
        assertEquals(3, registry.getStrategies().size)
    }

    @Test
    fun `register should add multiple strategies`() {
        // Given
        val registry = UndoStrategyRegistry()

        // When
        registry.register(InsertUndoStrategy())
        registry.register(DeleteUndoStrategy())
        registry.register(UpdateUndoStrategy())
        registry.register(APICallUndoStrategy())

        // Then
        assertEquals(4, registry.getStrategies().size)
    }

    @Test
    fun `register should preserve insertion order`() {
        // Given
        val registry = UndoStrategyRegistry()
        val insert = InsertUndoStrategy()
        val delete = DeleteUndoStrategy()
        val update = UpdateUndoStrategy()

        // When
        registry.register(insert)
        registry.register(delete)
        registry.register(update)

        // Then
        val strategies = registry.getStrategies()
        assertSame(insert, strategies[0])
        assertSame(delete, strategies[1])
        assertSame(update, strategies[2])
    }

    // ========== STRATEGY LOOKUP TESTS ==========

    @Test
    fun `findStrategy should return matching strategy for INSERT`() {
        // Given
        val registry = UndoStrategyRegistry()
            .register(InsertUndoStrategy())
            .register(DeleteUndoStrategy())

        // When
        val strategy = registry.findStrategy("INSERT", "User")

        // Then
        assertTrue(strategy is InsertUndoStrategy)
    }

    @Test
    fun `findStrategy should return matching strategy for DELETE`() {
        // Given
        val registry = UndoStrategyRegistry()
            .register(InsertUndoStrategy())
            .register(DeleteUndoStrategy())

        // When
        val strategy = registry.findStrategy("DELETE", "User")

        // Then
        assertTrue(strategy is DeleteUndoStrategy)
    }

    @Test
    fun `findStrategy should return matching strategy for UPDATE`() {
        // Given
        val registry = UndoStrategyRegistry()
            .register(UpdateUndoStrategy())

        // When
        val strategy = registry.findStrategy("UPDATE", "User")

        // Then
        assertTrue(strategy is UpdateUndoStrategy)
    }

    @Test
    fun `findStrategy should return matching strategy for API_CALL`() {
        // Given
        val registry = UndoStrategyRegistry()
            .register(APICallUndoStrategy())

        // When
        val strategy = registry.findStrategy("API_CALL", "EmailService")

        // Then
        assertTrue(strategy is APICallUndoStrategy)
    }

    @Test
    fun `findStrategy should be case insensitive`() {
        // Given
        val registry = UndoStrategyRegistry()
            .register(InsertUndoStrategy())

        // When
        val strategy1 = registry.findStrategy("insert", "User")
        val strategy2 = registry.findStrategy("INSERT", "User")
        val strategy3 = registry.findStrategy("InSeRt", "User")

        // Then
        assertTrue(strategy1 is InsertUndoStrategy)
        assertTrue(strategy2 is InsertUndoStrategy)
        assertTrue(strategy3 is InsertUndoStrategy)
    }

    @Test
    fun `findStrategy should return first matching strategy`() {
        // Given - Two strategies that both handle INSERT
        val registry = UndoStrategyRegistry()
        val firstStrategy = InsertUndoStrategy()
        val secondStrategy = InsertUndoStrategy()

        registry.register(firstStrategy)
        registry.register(secondStrategy)

        // When
        val found = registry.findStrategy("INSERT", "User")

        // Then - Should return the first registered one
        assertSame(firstStrategy, found)
    }

    // ========== FALLBACK / NO-OP STRATEGY TESTS ==========

    @Test
    fun `findStrategy should return NoOp strategy for unknown operation`() {
        // Given
        val registry = UndoStrategyRegistry()
            .register(InsertUndoStrategy())

        // When
        val strategy = registry.findStrategy("UNKNOWN_OPERATION", "User")

        // Then - Should not be null, should be NoOp
        assertNotNull(strategy)
        assertFalse(strategy is InsertUndoStrategy)
        assertFalse(strategy is DeleteUndoStrategy)
        assertFalse(strategy is UpdateUndoStrategy)
        assertFalse(strategy is APICallUndoStrategy)
    }

    @Test
    fun `NoOp strategy should return true when undoing`() = runTest {
        // Given
        val registry = UndoStrategyRegistry()  // Empty registry
        val operation = createOperation(
            operationType = "UNKNOWN",
            resourceType = "User",
            resourceId = "123",
            undoData = null,
            operationData = null
        )

        // When
        val strategy = registry.findStrategy("UNKNOWN", "User")
        val result = strategy.undo(operation)

        // Then - NoOp should return true to continue with other operations
        assertTrue(result)
    }

    @Test
    fun `NoOp strategy should handle operation without exception`() = runTest {
        // Given
        val registry = UndoStrategyRegistry()
        val operation = createOperation(
            operationType = "TOTALLY_UNKNOWN",
            resourceType = "SomeResource",
            resourceId = "999",
            undoData = mapOf("some" to "data"),
            operationData = null
        )

        // When
        val strategy = registry.findStrategy("TOTALLY_UNKNOWN", "SomeResource")
        val result = strategy.undo(operation)

        // Then - Should complete without exception
        assertTrue(result)
    }

    // ========== createDefault() TESTS ==========

    @Test
    fun `createDefault should register all standard strategies`() {
        // When
        val registry = UndoStrategyRegistry.createDefault()

        // Then
        assertEquals(4, registry.getStrategies().size)
    }

    @Test
    fun `createDefault should include InsertUndoStrategy`() {
        // When
        val registry = UndoStrategyRegistry.createDefault()
        val strategy = registry.findStrategy("INSERT", "User")

        // Then
        assertTrue(strategy is InsertUndoStrategy)
    }

    @Test
    fun `createDefault should include DeleteUndoStrategy`() {
        // When
        val registry = UndoStrategyRegistry.createDefault()
        val strategy = registry.findStrategy("DELETE", "User")

        // Then
        assertTrue(strategy is DeleteUndoStrategy)
    }

    @Test
    fun `createDefault should include UpdateUndoStrategy`() {
        // When
        val registry = UndoStrategyRegistry.createDefault()
        val strategy = registry.findStrategy("UPDATE", "User")

        // Then
        assertTrue(strategy is UpdateUndoStrategy)
    }

    @Test
    fun `createDefault should include APICallUndoStrategy`() {
        // When
        val registry = UndoStrategyRegistry.createDefault()
        val strategy = registry.findStrategy("API_CALL", "EmailService")

        // Then
        assertTrue(strategy is APICallUndoStrategy)
    }

    @Test
    fun `createDefault should handle all standard operations`() {
        // Given
        val registry = UndoStrategyRegistry.createDefault()

        // When
        val insertStrategy = registry.findStrategy("INSERT", "User")
        val deleteStrategy = registry.findStrategy("DELETE", "User")
        val updateStrategy = registry.findStrategy("UPDATE", "User")
        val apiCallStrategy = registry.findStrategy("API_CALL", "EmailService")
        val externalCallStrategy = registry.findStrategy("EXTERNAL_CALL", "PaymentGateway")
        val notificationStrategy = registry.findStrategy("NOTIFICATION", "SMSService")

        // Then
        assertTrue(insertStrategy is InsertUndoStrategy)
        assertTrue(deleteStrategy is DeleteUndoStrategy)
        assertTrue(updateStrategy is UpdateUndoStrategy)
        assertTrue(apiCallStrategy is APICallUndoStrategy)
        assertTrue(externalCallStrategy is APICallUndoStrategy)
        assertTrue(notificationStrategy is APICallUndoStrategy)
    }

    // ========== CUSTOM STRATEGY TESTS ==========

    @Test
    fun `registry should support custom strategies`() {
        // Given - Custom strategy
        val customStrategy = object : UndoStrategy {
            override fun canHandle(operationType: String, resourceType: String): Boolean {
                return operationType.uppercase() == "CUSTOM"
            }

            override suspend fun undo(operation: TransactionOperation): Boolean {
                return true
            }
        }

        val registry = UndoStrategyRegistry()
            .register(customStrategy)

        // When
        val found = registry.findStrategy("CUSTOM", "Resource")

        // Then
        assertSame(customStrategy, found)
    }

    @Test
    fun `registry should support resource-type-specific strategies`() {
        // Given - Strategy that only handles User resources
        val userSpecificStrategy = object : UndoStrategy {
            override fun canHandle(operationType: String, resourceType: String): Boolean {
                return operationType.uppercase() == "SPECIAL" && resourceType == "User"
            }

            override suspend fun undo(operation: TransactionOperation): Boolean {
                return true
            }
        }

        val registry = UndoStrategyRegistry()
            .register(userSpecificStrategy)

        // When
        val userStrategy = registry.findStrategy("SPECIAL", "User")
        val orderStrategy = registry.findStrategy("SPECIAL", "Order")

        // Then
        assertSame(userSpecificStrategy, userStrategy)
        assertNotSame(userSpecificStrategy, orderStrategy)  // Should be NoOp
    }

    // ========== EDGE CASES ==========

    @Test
    fun `empty registry should return NoOp for any operation`() {
        // Given
        val registry = UndoStrategyRegistry()

        // When
        val strategy1 = registry.findStrategy("INSERT", "User")
        val strategy2 = registry.findStrategy("DELETE", "Order")
        val strategy3 = registry.findStrategy("ANYTHING", "Resource")

        // Then - All should be NoOp (not null)
        assertNotNull(strategy1)
        assertNotNull(strategy2)
        assertNotNull(strategy3)
    }

    @Test
    fun `getStrategies should return empty list for empty registry`() {
        // Given
        val registry = UndoStrategyRegistry()

        // When
        val strategies = registry.getStrategies()

        // Then
        assertEquals(0, strategies.size)
    }

    @Test
    fun `getStrategies should return immutable copy`() {
        // Given
        val registry = UndoStrategyRegistry()
            .register(InsertUndoStrategy())

        // When
        val strategies1 = registry.getStrategies()
        val strategies2 = registry.getStrategies()

        // Then - Should be different instances (copies)
        assertNotSame(strategies1, strategies2)
        assertEquals(strategies1.size, strategies2.size)
    }

    @Test
    fun `registry should handle duplicate strategy registration`() {
        // Given
        val registry = UndoStrategyRegistry()
        val strategy = InsertUndoStrategy()

        // When
        registry.register(strategy)
        registry.register(strategy)  // Register same instance twice

        // Then
        assertEquals(2, registry.getStrategies().size)
    }
}
