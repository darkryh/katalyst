package io.github.darkryh.katalyst.di.validation

import io.github.darkryh.katalyst.di.analysis.ComponentNode
import io.github.darkryh.katalyst.di.analysis.Dependency
import io.github.darkryh.katalyst.di.analysis.DependencyGraph
import io.github.darkryh.katalyst.di.analysis.DependencySource
import io.github.darkryh.katalyst.di.error.CircularDependencyError
import io.github.darkryh.katalyst.di.error.MissingDependencyError
import io.github.darkryh.katalyst.di.error.UninstantiableTypeError
import org.junit.jupiter.api.Test
import kotlin.reflect.KClass
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for DependencyValidator - Phase 3 validation logic.
 *
 * Validates that the dependency validation system correctly detects:
 * - Circular dependencies
 * - Missing dependencies
 * - Uninstantiable types
 * - Well-known property issues
 * - Secondary type binding problems
 */
class DependencyValidatorTest {

    @Test
    fun `should detect simple circular dependency A to B to A`() {
        // Create nodes: A depends on B, B depends on A
        val nodeA = ComponentNode(
            type = TestServiceA::class,
            dependencies = listOf(
                Dependency(
                    type = TestServiceB::class,
                    parameterName = "b",
                    isOptional = false
                )
            )
        )

        val nodeB = ComponentNode(
            type = TestServiceB::class,
            dependencies = listOf(
                Dependency(
                    type = TestServiceA::class,
                    parameterName = "a",
                    isOptional = false
                )
            )
        )

        val graph = DependencyGraph(
            nodes = mapOf(TestServiceA::class to nodeA, TestServiceB::class to nodeB),
            edges = mapOf(
                TestServiceA::class to setOf(TestServiceB::class),
                TestServiceB::class to setOf(TestServiceA::class)
            )
        )

        val validator = DependencyValidator(graph)
        val errors = validator.detectCycles()

        assertEquals(1, errors.size, "Should detect one cycle error")
        val cycleError = errors[0] as CircularDependencyError
        assertTrue(cycleError.cycle.contains(TestServiceA::class), "Cycle should include ServiceA")
        assertTrue(cycleError.cycle.contains(TestServiceB::class), "Cycle should include ServiceB")
    }

    @Test
    fun `should detect three-component circular dependency A to B to C to A`() {
        // Create nodes: A → B → C → A
        val nodeA = ComponentNode(
            type = TestServiceA::class,
            dependencies = listOf(Dependency(TestServiceB::class, "b", isOptional = false))
        )
        val nodeB = ComponentNode(
            type = TestServiceB::class,
            dependencies = listOf(Dependency(TestServiceC::class, "c", isOptional = false))
        )
        val nodeC = ComponentNode(
            type = TestServiceC::class,
            dependencies = listOf(Dependency(TestServiceA::class, "a", isOptional = false))
        )

        val graph = DependencyGraph(
            nodes = mapOf(
                TestServiceA::class to nodeA,
                TestServiceB::class to nodeB,
                TestServiceC::class to nodeC
            ),
            edges = mapOf(
                TestServiceA::class to setOf(TestServiceB::class),
                TestServiceB::class to setOf(TestServiceC::class),
                TestServiceC::class to setOf(TestServiceA::class)
            )
        )

        val validator = DependencyValidator(graph)
        val cycles = validator.detectCycles()

        assertEquals(1, cycles.size, "Should detect one cycle")
    }

    @Test
    fun `should detect missing dependency`() {
        // Create node: A depends on B, but B is not in graph
        val nodeA = ComponentNode(
            type = TestServiceA::class,
            dependencies = listOf(
                Dependency(
                    type = TestServiceB::class,
                    parameterName = "b",
                    isOptional = false,
                    isResolvable = false  // Not found
                )
            )
        )

        val graph = DependencyGraph(
            nodes = mapOf(TestServiceA::class to nodeA),
            edges = mapOf(TestServiceA::class to setOf(TestServiceB::class))
        )

        val validator = DependencyValidator(graph)
        val report = validator.validateAll()

        assertFalse(report.isValid, "Should fail validation")
        assertTrue(report.missingDependencyCount > 0, "Should report missing dependency")
        assertTrue(report.errors.any { it is MissingDependencyError })
    }

    @Test
    fun `should not report error for optional dependencies`() {
        // Create node: A depends on B optionally
        val nodeA = ComponentNode(
            type = TestServiceA::class,
            dependencies = listOf(
                Dependency(
                    type = TestServiceB::class,
                    parameterName = "b",
                    isOptional = true,  // Optional
                    isResolvable = false  // Not in graph
                )
            )
        )

        val graph = DependencyGraph(
            nodes = mapOf(TestServiceA::class to nodeA),
            edges = mapOf()  // No edges for optional dependencies
        )

        val validator = DependencyValidator(graph)
        val report = validator.validateAll()

        assertTrue(report.isValid, "Should pass validation (optional dependency)")
        assertEquals(0, report.missingDependencyCount)
    }

    @Test
    fun `should report error for uninstantiable abstract class`() {
        val abstractNode = ComponentNode(
            type = AbstractService::class,
            isInstantiable = false  // Abstract, cannot instantiate
        )

        val graph = DependencyGraph(
            nodes = mapOf(AbstractService::class to abstractNode)
        )

        val validator = DependencyValidator(graph)
        val report = validator.validateAll()

        assertFalse(report.isValid, "Should fail validation")
        assertTrue(report.uninstantiableTypeCount > 0)
        assertTrue(report.errors.any { it is UninstantiableTypeError })
    }

    @Test
    fun `should pass validation for valid dependency chain`() {
        // C has no dependencies, B depends on C, A depends on B
        val nodeC = ComponentNode(
            type = TestServiceC::class,
            dependencies = emptyList()
        )
        val nodeB = ComponentNode(
            type = TestServiceB::class,
            dependencies = listOf(
                Dependency(
                    type = TestServiceC::class,
                    parameterName = "c",
                    isOptional = false,
                    isResolvable = true  // Found in graph
                )
            )
        )
        val nodeA = ComponentNode(
            type = TestServiceA::class,
            dependencies = listOf(
                Dependency(
                    type = TestServiceB::class,
                    parameterName = "b",
                    isOptional = false,
                    isResolvable = true  // Found in graph
                )
            )
        )

        val graph = DependencyGraph(
            nodes = mapOf(
                TestServiceA::class to nodeA,
                TestServiceB::class to nodeB,
                TestServiceC::class to nodeC
            ),
            edges = mapOf(
                TestServiceA::class to setOf(TestServiceB::class),
                TestServiceB::class to setOf(TestServiceC::class),
                TestServiceC::class to emptySet()
            )
        )

        val validator = DependencyValidator(graph)
        val report = validator.validateAll()

        assertTrue(report.isValid, "Should pass validation")
        assertEquals(0, report.totalErrorCount)
    }

    // Test helper classes
    private class TestServiceA
    private class TestServiceB
    private class TestServiceC
    private abstract class AbstractService
}
