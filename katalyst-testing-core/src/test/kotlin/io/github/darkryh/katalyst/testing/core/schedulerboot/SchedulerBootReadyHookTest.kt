package io.github.darkryh.katalyst.testing.core.schedulerboot

import io.github.darkryh.katalyst.core.di.KatalystContainerProvider
import io.github.darkryh.katalyst.di.config.KatalystDIOptions
import io.github.darkryh.katalyst.di.config.ServerConfiguration
import io.github.darkryh.katalyst.di.config.ServerDeploymentConfiguration
import io.github.darkryh.katalyst.di.config.initializeKatalystStandalone
import io.github.darkryh.katalyst.di.config.runRuntimeReadyInitializers
import io.github.darkryh.katalyst.di.config.stopKatalystStandalone
import io.github.darkryh.katalyst.di.lifecycle.ReadyHook
import io.github.darkryh.katalyst.koin.KoinBeanEngine
import io.github.darkryh.katalyst.scheduler.SchedulerFeature
import io.github.darkryh.katalyst.scheduler.telemetry.SchedulerTelemetry
import io.github.darkryh.katalyst.testing.core.inMemoryDatabaseConfig
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue
import org.koin.core.context.stopKoin

/**
 * End-to-end coverage for issue #31 on the production bean engine.
 *
 * `enableScheduler()` plus an application class implementing `ReadyHook` used to leave the
 * application with no scheduled jobs at all: the scheduler's own hook was bound to the
 * `ReadyHook` marker and nothing else, so the application hook's registration took its only
 * index key and `SchedulerInitializer.onReady()` was never invoked.
 *
 * The engine here is [KoinBeanEngine] deliberately — the defect is invisible to any test that
 * boots the in-memory engine, and it is the wiring across bootstrap phases (feature modules,
 * then component discovery, then the ready lifecycle) that produces it.
 */
class SchedulerBootReadyHookTest {

    @BeforeTest
    fun setUp() {
        SchedulerBootLog.reset()
    }

    @AfterTest
    fun tearDown() {
        // The discovery registries are JVM-global; stopKatalystStandalone() resets them, and
        // the Koin context has to go with them or the next boot augments this one.
        stopKatalystStandalone()
        KatalystContainerProvider.reset()
        runCatching { stopKoin() }
    }

    @Test
    fun `scheduled jobs register when the application declares its own ready hook`() {
        val container = initializeKatalystStandalone(
            options = KatalystDIOptions(
                databaseConfig = inMemoryDatabaseConfig(),
                beanEngine = KoinBeanEngine,
                scanPackages = arrayOf("io.github.darkryh.katalyst.testing.core.schedulerboot"),
                features = listOf(SchedulerFeature),
            ),
            serverConfiguration = ServerConfiguration(
                engine = null,
                deployment = ServerDeploymentConfiguration.createDefault(),
            ),
            allowOverrides = true,
            activateRuntimeReadyInitializers = false,
        )

        val hooks = container.getAll(ReadyHook::class).map { it::class.simpleName }
        assertTrue(
            hooks.contains("SchedulerInitializer"),
            "the scheduler's ready hook must survive the application hook's registration, got $hooks"
        )

        runRuntimeReadyInitializers(container)

        assertTrue(
            SchedulerBootLog.applicationHookExecuted.get(),
            "the application's own ready hook must run"
        )
        assertTrue(
            SchedulerBootLog.schedulerMethodInvoked.get(),
            "the scheduler must discover and invoke the service's scheduling method"
        )
        assertTrue(
            SchedulerTelemetry.jobs().any { it.name == BOOT_TASK_NAME },
            "the job must actually be registered with the scheduler, got " +
                SchedulerTelemetry.jobs().map { it.name }
        )
    }
}
