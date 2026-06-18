package io.github.darkryh.katalyst.di.lifecycle

import io.github.darkryh.katalyst.di.registry.RegistryManager
import io.github.darkryh.katalyst.di.test.TestBeanEngine
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RuntimeReadyInitializerRunnerTest {
    private lateinit var engine: TestBeanEngine
    private lateinit var probe: RuntimeReadyProbe

    @BeforeTest
    fun setUp() {
        RegistryManager.resetAll()
        probe = RuntimeReadyProbe()
        engine = TestBeanEngine()
        engine.registerInstance(probe, RuntimeReadyProbe::class)
        engine.registerInstance(RuntimeReadyInitializerA(probe), ApplicationReadyInitializer::class)
    }

    @AfterTest
    fun tearDown() {
        RegistryManager.resetAll()
        engine.stop()
    }

    @Test
    fun `runtime-ready initializers execute in deterministic order`() = runBlocking {
        ApplicationReadyInitializerRegistry.register(RuntimeReadyInitializerB(probe))
        ApplicationReadyInitializerRegistry.register(RuntimeReadyInitializerA(probe))
        RuntimeReadyInitializerRunner(engine.container).invokeAll()
        assertEquals(listOf("A", "B"), probe.executionOrder)
    }

    @Test
    fun `runtime-ready initializers support constructor injection`() = runBlocking {
        RuntimeReadyInitializerRunner(engine.container).invokeAll()
        assertTrue(probe.executed)
    }
}

private class RuntimeReadyProbe {
    val executionOrder = mutableListOf<String>()
    var executed: Boolean = false
}

private class RuntimeReadyInitializerA(
    private val probe: RuntimeReadyProbe
) : ApplicationReadyInitializer {
    override val initializerId: String = "RuntimeReadyInitializerA"
    override val order: Int = 10

    override suspend fun onRuntimeReady() {
        probe.executed = true
        probe.executionOrder += "A"
    }
}

private class RuntimeReadyInitializerB(
    private val probe: RuntimeReadyProbe
) : ApplicationReadyInitializer {
    override val initializerId: String = "RuntimeReadyInitializerB"
    override val order: Int = 20

    override suspend fun onRuntimeReady() {
        probe.executed = true
        probe.executionOrder += "B"
    }
}
