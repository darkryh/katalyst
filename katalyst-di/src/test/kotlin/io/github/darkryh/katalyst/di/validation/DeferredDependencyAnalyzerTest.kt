package io.github.darkryh.katalyst.di.validation

import io.github.darkryh.katalyst.di.analysis.DependencyAnalyzer
import io.github.darkryh.katalyst.di.analysis.InjectionMode
import io.github.darkryh.katalyst.di.injection.Provider
import org.koin.core.Koin
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DeferredDependencyAnalyzerTest {
    private lateinit var koin: Koin

    @BeforeTest
    fun setUp() {
        startKoin { }
        koin = GlobalContext.get()
    }

    @AfterTest
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun `analyzer should mark Provider dependencies as deferred and keep hard edge out of graph`() {
        val analyzer = DependencyAnalyzer(
            discoveredTypes = mapOf("services" to setOf(DeferredA::class, DeferredB::class)),
            koin = koin,
            scanPackages = emptyArray()
        )

        val graph = analyzer.buildGraph()
        val deferredA = graph.nodes.getValue(DeferredA::class)
        val dependency = deferredA.dependencies.single()

        assertTrue(dependency.isDeferred)
        assertEquals(InjectionMode.PROVIDER, dependency.injectionMode)
        assertEquals(Provider::class, dependency.requestedType)
        assertEquals(DeferredB::class, dependency.type)
        assertFalse(graph.getDependencies(DeferredA::class).contains(DeferredB::class))
    }

    @Test
    fun `validator should not report cycle when one side is deferred`() {
        val analyzer = DependencyAnalyzer(
            discoveredTypes = mapOf("services" to setOf(DeferredA::class, DeferredB::class)),
            koin = koin,
            scanPackages = emptyArray()
        )
        val graph = analyzer.buildGraph()
        val validator = DependencyValidator(graph)

        val cycles = validator.detectCycles()
        assertTrue(cycles.isEmpty())
    }
}

private class DeferredA(
    val deferredB: Provider<DeferredB>
)

private class DeferredB(
    val a: DeferredA
)
