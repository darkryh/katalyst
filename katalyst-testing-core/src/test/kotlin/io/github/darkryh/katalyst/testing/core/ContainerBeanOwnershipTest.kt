package io.github.darkryh.katalyst.testing.core

import io.github.darkryh.katalyst.core.di.KatalystContainerProvider
import io.github.darkryh.katalyst.di.feature.KatalystBeanEngine
import io.github.darkryh.katalyst.di.feature.katalystBeanModule
import io.github.darkryh.katalyst.koin.KoinBeanEngine
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import org.koin.core.context.stopKoin

/**
 * Who owns an `AutoCloseable` bean: the container, always.
 *
 * `BeanEngineShutdownTest` pins that the container closes what it *created*. This file pins the
 * other half — the half a caller can be surprised by. A caller-supplied module may hand the
 * container an instance the caller built (`single<HttpClient> { myClient }`), and the container
 * closes that too. The rule is deliberate, not incidental:
 *
 * - Provenance is not recoverable where the decision is made. `registerInstance` sees an instance
 *   and a type; a captured instance and one the provider lambda just constructed are the same
 *   thing by then.
 * - Nor is it recoverable one level up. Caller modules reach bootstrap through
 *   `bootstrapKatalystContainer(additionalModules = ...)` *and* through
 *   `KatalystFeature.provideBeanModules()` — the only module channel the `katalystApplication { }`
 *   DSL and this testing DSL expose, and the very same channel every framework feature uses. There
 *   is no line between "framework module" and "caller module" to draw.
 * - Skipping caller modules would silently re-open the leak the close-on-stop behaviour was added
 *   to fix: an application that registers its own `SchedulerService` or pool through a custom
 *   feature would go back to leaking it. A leak is silent; a closed shared client fails loudly on
 *   first use, which is the cheaper failure of the two. Spring makes the same call — a `@Bean`
 *   returning an `AutoCloseable` gets `close()` at context shutdown whether the method constructed
 *   it or returned something it captured.
 *
 * So: do not register an instance that has to outlive the container. Build it per container, or
 * register a wrapper that does not implement `AutoCloseable`.
 */
class ContainerBeanOwnershipTest {

    class CloseProbe(private val name: String) : AutoCloseable {
        var closeCount: Int = 0
            private set

        override fun close() {
            closeCount++
        }

        override fun toString(): String = name
    }

    @AfterTest
    fun tearDown() {
        KatalystContainerProvider.reset()
        runCatching { stopKoin() }
    }

    private fun withEachEngine(block: (String, KatalystBeanEngine) -> Unit) {
        listOf<Pair<String, () -> KatalystBeanEngine>>(
            "TestKatalystBeanEngine" to { TestKatalystBeanEngine() },
            "KoinBeanEngine" to { KoinBeanEngine },
        ).forEach { (name, factory) ->
            KatalystContainerProvider.reset()
            runCatching { stopKoin() }
            try {
                block(name, factory())
            } finally {
                KatalystContainerProvider.reset()
                runCatching { stopKoin() }
            }
        }
    }

    @Test
    fun `an instance a caller module captured is closed with the container`() {
        withEachEngine { name, engine ->
            // Built by the caller, handed to the container — the shared-client shape.
            val callerOwned = CloseProbe("caller-owned")

            engine.start(
                listOf(katalystBeanModule { single<CloseProbe> { callerOwned } }),
                allowOverrides = true,
            )
            engine.stop()

            assertEquals(
                1,
                callerOwned.closeCount,
                "$name: the container owns the lifecycle of every AutoCloseable registered in it, " +
                    "including one a caller module captured rather than constructed",
            )
        }
    }

    @Test
    fun `a bean displaced by a caller override is still closed`() {
        withEachEngine { name, engine ->
            // A framework bean, then a caller override of the same type — what
            // `overrideBeanModules` and `additionalModules` exist to do.
            val displaced = CloseProbe("framework-bean")
            val override = CloseProbe("caller-override")

            engine.start(
                listOf(katalystBeanModule { single<CloseProbe> { displaced } }),
                allowOverrides = true,
            )
            engine.loadModules(
                listOf(katalystBeanModule { single<CloseProbe> { override } }),
                allowOverrides = true,
            )
            engine.stop()

            assertEquals(
                1,
                override.closeCount,
                "$name: the winning registration must be closed",
            )
            assertEquals(
                1,
                displaced.closeCount,
                "$name: losing the index key does not transfer ownership — the container created " +
                    "the displaced bean and is still the only thing that can release it",
            )
        }
    }

    @Test
    fun `a closeable handed to the test environment is closed with the environment`() {
        val callerOwned = CloseProbe("caller-owned")

        katalystTestEnvironment {
            disableDefaultFeatures()
            disablePreStartInitializers()
            disableRuntimeReadyInitializers()
            overrideBeanModules(katalystBeanModule { single<CloseProbe> { callerOwned } })
        }.close()

        assertEquals(
            1,
            callerOwned.closeCount,
            "the test environment holds the same contract as production: closing it closes the " +
                "beans in it, so a client shared across tests must not be registered in one",
        )
    }
}
