package io.github.darkryh.katalyst.testing.core

import io.github.darkryh.katalyst.core.di.KatalystContainerProvider
import io.github.darkryh.katalyst.di.feature.KatalystBeanEngine
import io.github.darkryh.katalyst.di.lifecycle.ReadyHook
import io.github.darkryh.katalyst.koin.KoinBeanEngine
import io.github.darkryh.katalyst.scheduler.SchedulerFeature
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertTrue
import org.koin.core.context.stopKoin

/**
 * Regression coverage for issue #31.
 *
 * `schedulerModule()` declares `single<ReadyHook> { SchedulerInitializer() }`, so the marker was
 * the definition's only bound type and therefore its only index key. An application component
 * implementing `ReadyHook` is bound with that same marker as a secondary type, and the Koin
 * registry is a map: the second write replaced the first and `SchedulerInitializer` disappeared
 * from `getAll<ReadyHook>()` entirely — no error, no warning, and every scheduled job silently
 * dead.
 *
 * The engines are exercised side by side because the in-memory test engine used to be a
 * multimap, where the eviction is unrepresentable: it passed this test while production failed.
 */
class SchedulerReadyHookEvictionTest {

    class AppReadyHook : ReadyHook {
        override val id: String = "app-ready-hook"
        override val order: Int = 60
        override suspend fun onReady() = Unit
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
            val engine = factory()
            try {
                block(name, engine)
            } finally {
                runCatching { engine.stop() }
                KatalystContainerProvider.reset()
                runCatching { stopKoin() }
            }
        }
    }

    @Test
    fun `SchedulerInitializer survives an application component binding ReadyHook`() {
        withEachEngine { name, engine ->
            val container = engine.start(SchedulerFeature.provideBeanModules(), allowOverrides = true)

            val before = container.getAll(ReadyHook::class).map { it::class.simpleName }
            assertTrue(before.contains("SchedulerInitializer"), "$name: baseline, got $before")

            // Exactly what AutoBindingRegistrar does for a scanned component implementing ReadyHook.
            engine.registerInstance(AppReadyHook(), AppReadyHook::class, listOf(ReadyHook::class), null)

            val after = container.getAll(ReadyHook::class).map { it::class.simpleName }
            assertTrue(
                after.contains("SchedulerInitializer"),
                "$name: registering an application ReadyHook must not evict SchedulerInitializer, got $after"
            )
            assertTrue(after.contains("AppReadyHook"), "$name: got $after")
        }
    }

    @Test
    fun `registering the scheduler module last keeps both ReadyHooks`() {
        withEachEngine { name, engine ->
            val container = engine.start(emptyList(), allowOverrides = true)
            engine.registerInstance(AppReadyHook(), AppReadyHook::class, listOf(ReadyHook::class), null)
            engine.loadModules(SchedulerFeature.provideBeanModules(), allowOverrides = true)

            val hooks = container.getAll(ReadyHook::class).map { it::class.simpleName }
            assertTrue(hooks.contains("SchedulerInitializer"), "$name: got $hooks")
            assertTrue(hooks.contains("AppReadyHook"), "$name: got $hooks")
        }
    }
}
