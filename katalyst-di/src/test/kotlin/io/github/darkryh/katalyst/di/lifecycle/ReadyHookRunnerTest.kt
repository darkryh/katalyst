package io.github.darkryh.katalyst.di.lifecycle

import io.github.darkryh.katalyst.di.registry.RegistryManager
import io.github.darkryh.katalyst.di.test.TestBeanEngine
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReadyHookRunnerTest {
    private lateinit var engine: TestBeanEngine
    private lateinit var probe: RuntimeReadyProbe

    @BeforeTest
    fun setUp() {
        RegistryManager.resetAll()
        probe = RuntimeReadyProbe()
        engine = TestBeanEngine()
        engine.registerInstance(probe, RuntimeReadyProbe::class)
        engine.registerInstance(RuntimeReadyInitializerA(probe), ReadyHook::class)
    }

    @AfterTest
    fun tearDown() {
        RegistryManager.resetAll()
        engine.stop()
    }

    @Test
    fun `runtime-ready initializers execute in deterministic order`() = runBlocking {
        ReadyHookRegistry.register(RuntimeReadyInitializerB(probe))
        ReadyHookRegistry.register(RuntimeReadyInitializerA(probe))
        ReadyHookRunner(engine.container).invokeAll()
        assertEquals(listOf("A", "B"), probe.executionOrder)
    }

    @Test
    fun `runtime-ready initializers support constructor injection`() = runBlocking {
        ReadyHookRunner(engine.container).invokeAll()
        assertTrue(probe.executed)
    }
}

private class RuntimeReadyProbe {
    val executionOrder = mutableListOf<String>()
    var executed: Boolean = false
}

private class RuntimeReadyInitializerA(
    private val probe: RuntimeReadyProbe
) : ReadyHook {
    override val id: String = "RuntimeReadyInitializerA"
    override val order: Int = 10

    override suspend fun onReady() {
        probe.executed = true
        probe.executionOrder += "A"
    }
}

private class RuntimeReadyInitializerB(
    private val probe: RuntimeReadyProbe
) : ReadyHook {
    override val id: String = "RuntimeReadyInitializerB"
    override val order: Int = 20

    override suspend fun onReady() {
        probe.executed = true
        probe.executionOrder += "B"
    }
}
