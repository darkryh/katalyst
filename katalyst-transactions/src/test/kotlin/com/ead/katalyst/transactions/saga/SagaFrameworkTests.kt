package com.ead.katalyst.transactions.saga

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Unit tests for Saga Framework.
 *
 * Verifies:
 * - Multi-step transaction execution
 * - Compensation (rollback) in reverse order
 * - Error handling and recovery
 * - Saga state tracking
 * - DSL builder syntax
 */
class SagaFrameworkTests {

    private lateinit var orchestrator: SagaOrchestrator

    @BeforeEach
    fun setUp() {
        orchestrator = SagaOrchestrator()
    }

    @Test
    @Timeout(5)
    fun `single step saga executes successfully`() = runBlocking {
        var executed = false

        val step = object : SagaStep<String> {
            override val stepName = "test-step"
            override suspend fun execute(): String {
                executed = true
                return "success"
            }
            override suspend fun compensate(result: String) {
                // No-op
            }
        }

        val result = orchestrator.step(step)
        orchestrator.commit()

        assertEquals("success", result)
        assertTrue(executed)
        assertEquals(SagaStatus.COMMITTED, orchestrator.getStatus())
    }

    @Test
    @Timeout(5)
    fun `multi-step saga executes all steps`() = runBlocking {
        val executed = mutableListOf<String>()

        val step1 = sagaStep<String>("step1") {
            execute {
                executed.add("step1")
                "result1"
            }
            compensate { executed.add("undo-step1") }
        }

        val step2 = sagaStep<String>("step2") {
            execute {
                executed.add("step2")
                "result2"
            }
            compensate { executed.add("undo-step2") }
        }

        val step3 = sagaStep<String>("step3") {
            execute {
                executed.add("step3")
                "result3"
            }
            compensate { executed.add("undo-step3") }
        }

        orchestrator.step(step1)
        orchestrator.step(step2)
        orchestrator.step(step3)
        orchestrator.commit()

        assertEquals(listOf("step1", "step2", "step3"), executed)
        assertEquals(SagaStatus.COMMITTED, orchestrator.getStatus())
    }

    @Test
    @Timeout(5)
    fun `compensation runs in reverse order on failure`() = runBlocking {
        val executionLog = mutableListOf<String>()

        val step1 = sagaStep<String>("step1") {
            execute {
                executionLog.add("execute-step1")
                "result1"
            }
            compensate {
                executionLog.add("compensate-step1")
            }
        }

        val step2 = sagaStep<String>("step2") {
            execute {
                executionLog.add("execute-step2")
                "result2"
            }
            compensate {
                executionLog.add("compensate-step2")
            }
        }

        val step3 = sagaStep<String>("step3") {
            execute {
                executionLog.add("execute-step3")
                throw RuntimeException("Step 3 failed")
            }
            compensate {
                executionLog.add("compensate-step3")
            }
        }

        try {
            orchestrator.step(step1)
            orchestrator.step(step2)
            orchestrator.step(step3)
        } catch (e: Exception) {
            // Expected
        }

        // Verify execution order: step1, step2, step3 (fails and logs before exception),
        // then compensation in reverse: step2, step1 (step3 doesn't compensate as it failed)
        assertEquals(
            listOf("execute-step1", "execute-step2", "execute-step3", "compensate-step2", "compensate-step1"),
            executionLog
        )
        assertEquals(SagaStatus.COMPENSATED, orchestrator.getStatus())
    }

    @Test
    @Timeout(5)
    fun `saga tracks completed steps`() = runBlocking {
        val step1 = sagaStep<Int>("step1") {
            execute { 42 }
            compensate { }
        }

        val step2 = sagaStep<String>("step2") {
            execute { "result" }
            compensate { }
        }

        orchestrator.step(step1)
        orchestrator.step(step2)

        val context = orchestrator.getContext()
        assertEquals(2, context.getCompletedStepCount())
        assertEquals("step1", context.steps[0].stepName)
        assertEquals("step2", context.steps[1].stepName)
        assertEquals(42, context.steps[0].result)
        assertEquals("result", context.steps[1].result)
    }

    @Test
    @Timeout(5)
    fun `saga tracks errors`() = runBlocking {
        val step1 = sagaStep<String>("step1") {
            execute { "success" }
            compensate { }
        }

        val step2 = sagaStep<String>("step2") {
            execute { throw IllegalArgumentException("Invalid input") }
            compensate { }
        }

        try {
            orchestrator.step(step1)
            orchestrator.step(step2)
        } catch (e: Exception) {
            // Expected
        }

        val context = orchestrator.getContext()
        assertEquals(1, context.getErrorCount())
        assertTrue(context.errors[0] is IllegalArgumentException)
        assertEquals("Invalid input", context.errors[0].message)
    }

    @Test
    @Timeout(5)
    fun `saga context provides summary`() = runBlocking {
        val step1 = sagaStep<String>("step1") {
            execute { "result" }
            compensate { }
        }

        orchestrator.step(step1)
        orchestrator.commit()

        val context = orchestrator.getContext()
        val summary = context.getSummary()

        assertTrue(summary.contains("status=COMMITTED"))
        assertTrue(summary.contains("steps=1"))
    }

    @Test
    @Timeout(5)
    fun `saga is successful after commit`() = runBlocking {
        val step = sagaStep<String>("test") {
            execute { "success" }
            compensate { }
        }

        orchestrator.step(step)
        orchestrator.commit()

        assertTrue(orchestrator.isSuccessful())
        assertEquals(SagaStatus.COMMITTED, orchestrator.getStatus())
    }

    @Test
    @Timeout(5)
    fun `saga is not successful after failure`() = runBlocking {
        val step = sagaStep<String>("test") {
            execute { throw RuntimeException("Failed") }
            compensate { }
        }

        try {
            orchestrator.step(step)
        } catch (e: Exception) {
            // Expected
        }

        assertFalse(orchestrator.isSuccessful())
        assertEquals(SagaStatus.COMPENSATED, orchestrator.getStatus())
    }

    @Test
    @Timeout(5)
    fun `compensation errors are tracked`() = runBlocking {
        val step1 = sagaStep<String>("step1") {
            execute { "result" }
            compensate { throw RuntimeException("Compensation failed") }
        }

        val step2 = sagaStep<String>("step2") {
            execute { throw RuntimeException("Step failed") }
            compensate { }
        }

        try {
            orchestrator.step(step1)
            orchestrator.step(step2)
        } catch (e: Exception) {
            // Expected
        }

        val context = orchestrator.getContext()
        assertTrue(context.errors.any { it.message == "Step failed" })
        assertTrue(context.errors.any { it.message == "Compensation failed" })
    }

    @Test
    @Timeout(5)
    fun `saga context calculates duration`() = runBlocking {
        val step = sagaStep<String>("test") {
            execute { "result" }
            compensate { }
        }

        val context = orchestrator.getContext()
        assertNotNull(context.startTime)

        orchestrator.step(step)
        orchestrator.commit()

        val duration = context.getDurationMs()
        assertNotNull(duration)
        assertTrue(duration >= 0)
    }

    @Test
    @Timeout(5)
    fun `saga is terminal after commit`() = runBlocking {
        val step = sagaStep<String>("test") {
            execute { "result" }
            compensate { }
        }

        orchestrator.step(step)
        assertFalse(orchestrator.getContext().isTerminal())

        orchestrator.commit()
        assertTrue(orchestrator.getContext().isTerminal())
    }

    @Test
    @Timeout(5)
    fun `saga is terminal after failure`() = runBlocking {
        val step = sagaStep<String>("test") {
            execute { throw RuntimeException("Failed") }
            compensate { }
        }

        try {
            orchestrator.step(step)
        } catch (e: Exception) {
            // Expected
        }

        assertTrue(orchestrator.getContext().isTerminal())
    }

    @Test
    @Timeout(5)
    fun `manual compensation triggers compensation flow`() = runBlocking {
        val compensated = mutableListOf<String>()

        val step = sagaStep<String>("test") {
            execute { "result" }
            compensate { compensated.add("compensated") }
        }

        orchestrator.step(step)
        orchestrator.compensate()

        assertEquals(listOf("compensated"), compensated)
        assertEquals(SagaStatus.COMPENSATED, orchestrator.getStatus())
    }

    @Test
    @Timeout(5)
    fun `multiple sagas have independent contexts`() = runBlocking {
        val saga1 = SagaOrchestrator("saga1")
        val saga2 = SagaOrchestrator("saga2")

        val step1 = sagaStep<String>("step") {
            execute { "saga1" }
            compensate { }
        }

        val step2 = sagaStep<String>("step") {
            execute { "saga2" }
            compensate { }
        }

        saga1.step(step1)
        saga2.step(step2)

        assertEquals("saga1", saga1.getContext().sagaId)
        assertEquals("saga2", saga2.getContext().sagaId)
    }

    @Test
    @Timeout(5)
    fun `step results preserve order and data`() = runBlocking {
        data class User(val id: Long, val name: String)
        data class Profile(val userId: Long, val email: String)

        val user = User(123, "John")
        val profile = Profile(123, "john@example.com")

        val step1 = sagaStep<User>("create-user") {
            execute { user }
            compensate { }
        }

        val step2 = sagaStep<Profile>("create-profile") {
            execute { profile }
            compensate { }
        }

        orchestrator.step(step1)
        orchestrator.step(step2)

        val context = orchestrator.getContext()
        assertEquals(user, context.steps[0].result)
        assertEquals(profile, context.steps[1].result)
    }

    @Test
    @Timeout(5)
    fun `empty saga commits successfully`() = runBlocking {
        orchestrator.commit()

        assertEquals(SagaStatus.COMMITTED, orchestrator.getStatus())
        assertTrue(orchestrator.isSuccessful())
    }

    @Test
    @Timeout(5)
    fun `zero steps saga compensates successfully`() = runBlocking {
        orchestrator.compensate()

        assertEquals(SagaStatus.COMPENSATED, orchestrator.getStatus())
        assertTrue(orchestrator.getContext().isTerminal())
    }
}

/**
 * Tests for SagaStepBuilder DSL.
 */
class SagaStepBuilderTests {

    @Test
    @Timeout(5)
    fun `builder creates functional step`() = runBlocking {
        var executeCount = 0
        var compensateCount = 0

        val step = sagaStep<String>("test") {
            execute {
                executeCount++
                "result"
            }
            compensate {
                compensateCount++
            }
        }

        assertEquals("test", step.stepName)
        assertEquals("result", step.execute())
        assertEquals(1, executeCount)

        step.compensate("result")
        assertEquals(1, compensateCount)
    }

    @Test
    @Timeout(5)
    fun `sagaStep DSL creates builder`() = runBlocking {
        val step = sagaStep<Int>("step-name") {
            execute { 42 }
            compensate { }
        }

        assertEquals("step-name", step.stepName)
        assertEquals(42, step.execute())
    }

    @Test
    @Timeout(5)
    fun `builder step handles compensation`() = runBlocking {
        val log = mutableListOf<String>()

        val step = sagaStep<String>("test") {
            execute {
                log.add("executed")
                "result"
            }
            compensate { result ->
                log.add("compensated: $result")
            }
        }

        step.execute()
        step.compensate("result")

        assertEquals(listOf("executed", "compensated: result"), log)
    }
}
