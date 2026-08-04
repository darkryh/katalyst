package io.github.darkryh.katalyst.testing.core

import io.github.darkryh.katalyst.core.di.KatalystContainerProvider
import io.github.darkryh.katalyst.core.di.get
import io.github.darkryh.katalyst.di.feature.KatalystBeanEngine
import io.github.darkryh.katalyst.koin.KoinBeanEngine
import io.github.darkryh.katalyst.scheduler.SchedulerFeature
import io.github.darkryh.katalyst.scheduler.service.SchedulerService
import kotlinx.coroutines.job
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.koin.core.context.stopKoin

/**
 * Stopping the application must release what it started.
 *
 * `SchedulerService` is `AutoCloseable` and cancelling it stops every job coroutine, but nothing
 * invoked it: shutdown ran `engine.stop()` -> `stopKoin()`, and Katalyst registers beans as plain
 * instance factories carrying no close callbacks. Scheduled jobs therefore kept firing after the
 * application stopped, against a torn-down container, until the JVM itself exited — invisible to a
 * plain `main()`, but not to an embedded server, an in-JVM restart, or a test that stops and
 * restarts the app.
 *
 * Closing what the container created is the container's job; both engines are held to it here.
 */
class BeanEngineShutdownTest {

    class CloseProbe : AutoCloseable {
        var closeCount: Int = 0
            private set

        override fun close() {
            closeCount++
        }
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
    fun `stopping the engine closes the registrations it holds`() {
        withEachEngine { name, engine ->
            val probe = CloseProbe()
            engine.start(emptyList(), allowOverrides = true)
            engine.registerInstance(probe, CloseProbe::class, emptyList(), null)

            engine.stop()

            assertTrue(
                probe.closeCount > 0,
                "$name: a registered AutoCloseable must be closed when the container stops",
            )
        }
    }

    @Test
    fun `an instance bound under several types is closed exactly once`() {
        withEachEngine { name, engine ->
            val probe = CloseProbe()
            engine.start(emptyList(), allowOverrides = true)
            // The database factory is rebound this way during boot, so one instance really does
            // end up under more than one key.
            engine.registerInstance(probe, CloseProbe::class, emptyList(), null)
            engine.registerInstance(probe, CloseProbe::class, emptyList(), null)

            engine.stop()

            assertTrue(probe.closeCount > 0, "$name: must be closed")
            assertTrue(
                probe.closeCount == 1,
                "$name: closing must be idempotent per instance, got ${probe.closeCount}",
            )
        }
    }

    @Test
    fun `stopping the application cancels scheduled jobs`() {
        val engine = KoinBeanEngine
        val container = engine.start(SchedulerFeature.provideBeanModules(), allowOverrides = true)
        val scheduler = container.get<SchedulerService>()

        assertTrue(
            scheduler.coroutineContext.job.isActive,
            "baseline: the scheduler scope is live while the application is up",
        )

        engine.stop()

        assertFalse(
            scheduler.coroutineContext.job.isActive,
            "stopping the application must cancel the scheduler scope; otherwise every scheduled " +
                "job keeps firing against a torn-down container until the JVM exits",
        )
    }
}
