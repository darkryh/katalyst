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

        // setUp() already registers a *separate* RuntimeReadyInitializerA instance directly in the
        // container, so this run combines: registry=[B, A], container=[A]. The two A instances are
        // distinct objects that merely share a class, so both must execute (regression guard for
        // finding B: dedup must be by identity, not by runtime class) - hence "A" appears twice,
        // both ordered before "B".
        assertEquals(listOf("A", "A", "B"), probe.executionOrder)
    }

    @Test
    fun `runtime-ready initializers support constructor injection`() = runBlocking {
        ReadyHookRunner(engine.container).invokeAll()
        assertTrue(probe.executed)
    }

    @Test
    fun `two distinct instances of the same hook class both execute`() = runBlocking {
        // setUp() registered one RuntimeReadyInitializerA instance directly into the container.
        // Register a second, distinct instance of the *same* class through the registry.
        val second = RuntimeReadyProbe()
        ReadyHookRegistry.register(RuntimeReadyInitializerA(second))

        ReadyHookRunner(engine.container).invokeAll()

        assertTrue(probe.executed, "container-registered instance must run")
        assertTrue(second.executed, "registry-registered instance of the same class must also run")
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
