package io.github.darkryh.katalyst.di.analysis

import io.github.darkryh.katalyst.core.config.ConfigProvider
import io.github.darkryh.katalyst.di.test.TestBeanEngine
import io.github.darkryh.katalyst.events.bus.EventBus
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class KnownPlatformTypesDependencyAnalyzerTest {
    private lateinit var engine: TestBeanEngine

    @BeforeTest
    fun setUp() {
        engine = TestBeanEngine()
    }

    @AfterTest
    fun tearDown() {
        engine.stop()
    }

    @Test
    fun `known platform dependencies are resolvable without explicit koin definitions`() {
        val analyzer = DependencyAnalyzer(
            discoveredTypes = mapOf(
                "services" to setOf(NeedsConfigProvider::class, NeedsEventBus::class)
            ),
            container = engine.container,
            scanPackages = emptyArray()
        )

        val graph = analyzer.buildGraph()
        val configDep = graph.nodes.getValue(NeedsConfigProvider::class).dependencies.single()
        val eventBusDep = graph.nodes.getValue(NeedsEventBus::class).dependencies.single()

        assertEquals(ConfigProvider::class, configDep.type)
        assertEquals(EventBus::class, eventBusDep.type)
        assertTrue(configDep.isResolvable)
        assertTrue(eventBusDep.isResolvable)
    }

    @Test
    fun `unknown dependency remains unresolved`() {
        val analyzer = DependencyAnalyzer(
            discoveredTypes = mapOf("services" to setOf(NeedsUnknownContract::class)),
            container = engine.container,
            scanPackages = emptyArray()
        )

        val graph = analyzer.buildGraph()
        val unknownDep = graph.nodes.getValue(NeedsUnknownContract::class).dependencies.single()
        assertFalse(unknownDep.isResolvable)
    }

    @Test
    fun `scheduler optional contract when present uses service package`() {
        val scheduler = KnownPlatformTypes.schedulerServiceKClassOrNull()
        if (scheduler != null) {
            assertEquals(
                "io.github.darkryh.katalyst.scheduler.service.SchedulerService",
                scheduler.qualifiedName
            )
        }
    }
}

private class NeedsConfigProvider(val configProvider: ConfigProvider)
private class NeedsEventBus(val eventBus: EventBus)
private interface UnknownContract
private class NeedsUnknownContract(val unknownContract: UnknownContract)
