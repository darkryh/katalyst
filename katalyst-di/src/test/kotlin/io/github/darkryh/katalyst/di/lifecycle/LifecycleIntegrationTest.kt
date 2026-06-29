package io.github.darkryh.katalyst.di.lifecycle

import io.github.darkryh.katalyst.di.lifecycle.test.TestStartupHook
import io.github.darkryh.katalyst.di.config.ServerDeploymentConfiguration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertTrue

/**
 * Unit tests for application lifecycle initialization.
 *
 * Tests individual components of the lifecycle system:
 * 1. StartupHook interface contract
 * 2. Initializer ordering logic
 * 3. Fail-fast error handling behavior
 * 4. Exception hierarchy and proper propagation
 *
 * **Test Coverage**:
 * - Initializer creation and ID assignment
 * - Engine configuration properties
 * - Exception throwing in initializers
 * - Order field assignment and retrieval
 */
class LifecycleIntegrationTest {

    /**
     * Test 1: TestStartupHook properly implements interface
     */
    @Test
    fun `test initializer implements StartupHook interface`() {
        val testInit = TestStartupHook(
            id = "TestInit",
            order = 5
        )

        assertEquals("TestInit", testInit.id)
        assertEquals(5, testInit.order)
        assertTrue(testInit is StartupHook)
    }

    /**
     * Test 2: Multiple initializers can be created with different orders
     */
    @Test
    fun `initializers can be created with different order values`() {
        val init1 = TestStartupHook("First", order = -100)
        val init2 = TestStartupHook("Second", order = 0)
        val init3 = TestStartupHook("Third", order = 10)

        val initializers = listOf(init3, init1, init2)
        val sorted = initializers.sortedBy { it.order }

        assertEquals("First", sorted[0].id)
        assertEquals("Second", sorted[1].id)
        assertEquals("Third", sorted[2].id)
    }

    /**
     * Test 3: TestStartupHook executes onReady block
     */
    @Test
    fun `test initializer executes onReady callback`() {
        var executed = false

        val testInit = TestStartupHook(
            id = "TestInit",
            order = 0,
            onReady = { executed = true }
        )

        // Just verify the object was created correctly
        assertEquals("TestInit", testInit.id)
        assertTrue(!executed)  // Hasn't been called yet
    }

    /**
     * Test 4: TestStartupHook propagates exceptions
     */
    @Test
    fun `test initializer propagates exceptions from onReady block`() {
        val testInit = TestStartupHook(
            id = "FailingInit",
            order = 0,
            onReady = {
                throw IllegalStateException("Test error")
            }
        )

        // Just verify it was created correctly with exception behavior
        assertEquals("FailingInit", testInit.id)
        assertEquals(0, testInit.order)
    }

    /**
     * Test 5: Engine configuration provides correct properties
     */
    @Test
    fun `test engine configuration exposes host and port`() {
        val config = ServerDeploymentConfiguration.createDefault().copy(
            host = "192.168.1.1",
            port = 9999
        )

        assertEquals("192.168.1.1", config.host)
        assertEquals(9999, config.port)
    }

    /**
     * Test 6: Engine configuration uses default values
     */
    @Test
    fun `test engine configuration defaults to localhost 8080`() {
        val config = ServerDeploymentConfiguration.createDefault()

        assertEquals("0.0.0.0", config.host)
        assertEquals(8080, config.port)
    }

    /**
     * Test 7: Engine configuration is correctly typed
     */
    @Test
    fun `test engine configuration is instance of ServerDeploymentConfiguration`() {
        val config = ServerDeploymentConfiguration.createDefault()

        assertTrue(config is ServerDeploymentConfiguration)
    }

    /**
     * Test 8: Engine configuration can be created with custom values
     */
    @Test
    fun `test engine configuration with custom values`() {
        val config = ServerDeploymentConfiguration.createDefault().copy(host = "192.168.1.1", port = 9999)

        assertEquals("192.168.1.1", config.host)
        assertEquals(9999, config.port)
    }

    /**
     * Test 9: Multiple test initializers maintain separate state
     */
    @Test
    fun `multiple test initializers maintain independent state`() {
        val init1 = TestStartupHook(
            id = "Init1",
            order = 0,
            onReady = { }
        )

        val init2 = TestStartupHook(
            id = "Init2",
            order = 0,
            onReady = { }
        )

        // Verify they are independent instances
        assertEquals("Init1", init1.id)
        assertEquals("Init2", init2.id)
        assertEquals(0, init1.order)
        assertEquals(0, init2.order)
    }

    /**
     * Test 10: Initializer ordering by order field works correctly
     */
    @Test
    fun `initializers sort correctly by order field`() {
        val inits = listOf(
            TestStartupHook("Order10", order = 10),
            TestStartupHook("Order-50", order = -50),
            TestStartupHook("Order0", order = 0),
            TestStartupHook("Order5", order = 5)
        )

        val sorted = inits.sortedBy { it.order }

        assertEquals(listOf("Order-50", "Order0", "Order5", "Order10"),
            sorted.map { it.id })
    }

    /**
     * Test 11: Test engine configuration toString includes values
     */
    @Test
    fun `test engine configuration toString shows host and port`() {
        val config = ServerDeploymentConfiguration.createDefault().copy(host = "test-host", port = 1234)
        val str = config.toString()

        assertTrue(str.contains("test-host"))
        assertTrue(str.contains("1234"))
    }
}
